"""Linux host retention coordinator. Dry-run by default."""
import argparse
import fcntl
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def cleanup(env_file, state_dir, days=7, keep=24, apply=False):
    state_dir=Path(state_dir)
    if not state_dir.is_dir():
        raise ValueError('Scheduler state missing; run a successful scheduled refresh first')
    with (state_dir/'refresh.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        protected=set()
        for name in ('pending.json','last-success.json'):
            path=state_dir/name
            if path.exists():
                protected.add(json.loads(path.read_text())['generation'])
        base=['docker','compose','-p','leap-analytics','--env-file',str(Path(env_file).resolve()),
              '-f',str(ROOT/'analytics/compose.yml'),'--profile','bi']
        def run(*args,**kwargs):
            return subprocess.run(base+list(args),cwd=ROOT,check=True,timeout=600,**kwargs)
        stopped=False
        try:
            if apply:
                run('stop','superset')
                stopped=True
            output=run('run','--rm','--entrypoint','/app/.venv/bin/python',
                       'superset-bootstrap','/app/analytics-active-snapshots.py',capture_output=True,text=True)
            lines=[line for line in output.stdout.splitlines() if line.startswith('ACTIVE_SNAPSHOTS=')]
            if len(lines)!=1:
                raise ValueError('Cannot determine active Superset snapshots')
            active=json.loads(lines[0].split('=',1)[1])
            args=['run','--rm','retention','--days',str(days),'--keep',str(keep)]
            for name in active:
                args+=['--active',name]
            for name in sorted(protected):
                args+=['--protect',name]
            if apply:
                args+=['--apply']
            run(*args)
        finally:
            if stopped:
                run('up','-d','--force-recreate','superset')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file',type=Path,default=ROOT/'analytics/.env')
    parser.add_argument('--state-dir',type=Path,default=ROOT/'analytics/runtime')
    parser.add_argument('--days',type=int,default=7)
    parser.add_argument('--keep',type=int,default=24)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    cleanup(args.env_file,args.state_dir,args.days,args.keep,args.apply)
