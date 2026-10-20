"""Read-only analytics health checks and transition-based SMTP alerts."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import smtplib
import ssl
import subprocess
from email.message import EmailMessage

try:
    from .common import compose_command
except ImportError:
    from common import compose_command

ROOT=Path(__file__).resolve().parents[2]


def assess(state_dir, max_age=90, disk_path='/', min_percent=10, min_bytes=1073741824, now=None):
    now=now or datetime.now(timezone.utc)
    issues={}
    details={}
    try:
        success=json.loads((Path(state_dir)/'last-success.json').read_text())
        completed=datetime.fromisoformat(success['completed_at'])
        if completed.tzinfo is None:
            raise ValueError('Timestamp requires timezone')
        generation=success['generation']
        match=re.fullmatch(r'hourly-(\d{8}T\d{6}Z)-[A-Za-z0-9]+',generation)
        observed=completed
        if match:
            observed=min(completed,datetime.strptime(match[1],'%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc))
        age=(now-observed).total_seconds()/60
        details.update(generation=generation,age_minutes=round(age,1))
        if age < -5:
            issues['clock']='Refresh timestamp is in the future; check host clocks'
        elif age>max_age:
            issues['stale']=f'Analytics source snapshot is {age:.0f} minutes old (limit {max_age})'
    except (OSError,ValueError,KeyError,TypeError):
        issues['missing_success']='No readable successful refresh record'
    pending=Path(state_dir)/'pending.json'
    try:
        if pending.exists():
            data=json.loads(pending.read_text())
            age=(now.timestamp()-pending.stat().st_mtime)/60
            details['pending_stage']=data['stage']
            details['pending_age_minutes']=round(age,1)
            if age>max_age:
                issues['stuck']=f'Pending refresh has not advanced for {age:.0f} minutes'
    except (OSError,ValueError,KeyError,TypeError):
        issues['pending_state']='Cannot read pending refresh state'
    try:
        usage=shutil.disk_usage(disk_path)
        free=100*usage.free/usage.total
        details['disk_free_percent']=round(free,1)
        if free<min_percent or usage.free<min_bytes:
            issues['disk']=f'Low disk space at {disk_path}: {free:.1f}% free, {usage.free} bytes'
    except OSError:
        issues['disk_check']='Cannot inspect configured disk path'
    return issues,details


def runtime_checks(env_file):
    issues={}
    checks=(
        ('timer',['systemctl','is-active','--quiet','leap-analytics.timer']),
        ('refresh_failed',['systemctl','show','leap-analytics.service','--property=Result','--value']),
    )
    for code,command in checks:
        try:
            result=subprocess.run(command,capture_output=True,text=True,timeout=15)
            failed=result.returncode!=0
            if code=='refresh_failed':
                failed=failed or result.stdout.strip()!='success'
            if failed:
                issues[code]='Hourly timer inactive/unavailable' if code=='timer' else 'Refresh service failed or could not be inspected'
        except (OSError,subprocess.SubprocessError):
            issues[code]='Cannot inspect systemd status'
    try:
        command=compose_command()+['-p','leap-analytics','--env-file',str(Path(env_file).resolve()),
            '-f',str(ROOT/'analytics/compose.yml'),'--profile','bi','exec','-T','superset',
            '/app/.venv/bin/python','-c',
            "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8088/health',timeout=5).read()"]
        result=subprocess.run(command,capture_output=True,timeout=20)
        if result.returncode:
            issues['superset']='Superset health endpoint unavailable'
    except (OSError,RuntimeError,subprocess.SubprocessError):
        issues['superset']='Cannot inspect Superset availability'
    return issues


def send_email(config, issues, details, test=False):
    recipients=config.get('recipients')
    if not isinstance(recipients,list) or not recipients or not all(isinstance(v,str) and '@' in v for v in recipients):
        raise ValueError('Configure a nonempty recipients list')
    message=EmailMessage()
    message['From']=config['sender']
    message['To']=', '.join(recipients)
    message['Subject']='LEAP analytics: '+('TEST' if test else 'ACTION REQUIRED' if issues else 'RECOVERED')
    message.set_content(json.dumps({'issues':issues,'details':details},indent=2)+'\nCheck journalctl -u leap-analytics.service on EC2.\n')
    context=ssl.create_default_context()
    mode=config.get('tls','starttls')
    if mode not in ('starttls','ssl'):
        raise ValueError('SMTP must use starttls or ssl')
    factory=smtplib.SMTP_SSL if mode=='ssl' else smtplib.SMTP
    options={'timeout':20}
    if mode=='ssl':
        options['context']=context
    with factory(config['host'],int(config.get('port',465 if mode=='ssl' else 587)),**options) as smtp:
        if mode=='starttls':
            smtp.starttls(context=context)
        if config.get('username'):
            smtp.login(config['username'],config['password'])
        refused=smtp.send_message(message,to_addrs=recipients)
        if refused:
            raise RuntimeError('SMTP refused one or more recipients')


def notify(state_dir, config_path, issues, details):
    state_dir=Path(state_dir)
    previous_path=state_dir/'monitor-notified.json'
    previous=json.loads(previous_path.read_text()) if previous_path.exists() else None
    signature=sorted(issues)
    if previous is not None and previous.get('signature')==signature:
        return 'unchanged'
    if previous is None and not issues:
        outcome='healthy_baseline'
    else:
        if not config_path or not Path(config_path).exists():
            return 'email_not_configured'
        config=json.loads(Path(config_path).read_text())
        send_email(config,issues,details)
        outcome='sent'
    temporary=previous_path.with_suffix('.tmp')
    temporary.write_text(json.dumps({'signature':signature,'checked_at':datetime.now(timezone.utc).isoformat()}))
    temporary.replace(previous_path)
    return outcome


if __name__=='__main__':
    import fcntl
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file',type=Path,default=ROOT/'analytics/.env')
    parser.add_argument('--state-dir',type=Path,default=ROOT/'analytics/runtime')
    parser.add_argument('--email-config',type=Path,default=ROOT/'analytics/monitor.json')
    parser.add_argument('--max-age-minutes',type=int,default=90)
    parser.add_argument('--disk-path',default='/')
    parser.add_argument('--test-email',action='store_true',help='Send one test email to the configured recipients')
    args=parser.parse_args()
    if args.test_email:
        send_email(json.loads(args.email_config.read_text()),{}, {'message':'Gmail alerts are configured'},test=True)
        print('Test email accepted by SMTP for the configured recipients.')
        raise SystemExit(0)
    args.state_dir.mkdir(parents=True,exist_ok=True)
    with (args.state_dir/'monitor.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        issues,details=assess(args.state_dir,args.max_age_minutes,args.disk_path)
        runtime=runtime_checks(args.env_file)
        if details.get('pending_stage')=='activate' and details.get('pending_age_minutes',999)<5:
            runtime.pop('superset',None) # Expected brief restart during activation.
        issues.update(runtime)
        try:
            notification=notify(args.state_dir,args.email_config,issues,details)
        except Exception as error:
            notification='failed: '+type(error).__name__
            issues['email_delivery']='Email failed; next check will retry'
        report={'checked_at':datetime.now(timezone.utc).isoformat(),'healthy':not issues,
                'issues':issues,'details':details,'notification':notification}
        (args.state_dir/'monitor-status.json').write_text(json.dumps(report,indent=2))
        print(json.dumps(report,indent=2),flush=True)
        raise SystemExit(1 if issues else 0)
