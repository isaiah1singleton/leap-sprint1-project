"""Linux host orchestrator: ordered jobs, durable retries and Superset activation."""
import argparse
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import subprocess
import uuid
try:
    from .common import compose_command
except ImportError:
    from common import compose_command

ROOT = Path(__file__).resolve().parents[2]


def save(path, value):
    temporary = path.with_suffix('.tmp')
    with temporary.open('w') as stream:
        json.dump(value, stream, indent=2)
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def refresh(env_file, state_dir, execute=subprocess.run):
    state_dir = Path(state_dir)
    state_dir.mkdir(parents=True, exist_ok=True)
    pending_path = state_dir/'pending.json'
    with (state_dir/'refresh.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print('Refresh already running; skipping overlapping invocation.', flush=True)
            return
        if pending_path.exists():
            pending = json.loads(pending_path.read_text())
        else:
            generation = 'hourly-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8]
            pending = {'generation': generation, 'stage': 'etl'}
            save(pending_path, pending)
        generation = pending['generation']
        # IDs become container paths: never accept arbitrary path segments.
        import re
        if not re.fullmatch(r'[A-Za-z0-9_-]+', generation) or pending['stage'] not in ('etl','warehouse','activate','done'):
            raise ValueError('Invalid pending refresh state')
        env = os.environ.copy()
        env.update(BATCH_ID=generation, SNAPSHOT_ID=generation)
        base = compose_command()+['-p','leap-analytics','--env-file',str(Path(env_file).resolve()),'-f',str(ROOT/'analytics/compose.yml'),'--profile','bi']

        def command(*args):
            execute(base+list(args), cwd=ROOT, env=env, check=True, timeout=7200)

        while pending['stage'] != 'done':
            stage = pending['stage']
            print(f'{generation}: {stage}', flush=True)
            if stage == 'etl':
                command('run','--rm','etl')
                pending['stage'] = 'warehouse'
            elif stage == 'warehouse':
                command('run','--rm','warehouse',
                        '--batch',f'/data/batches/{generation}',
                        '--db','/data/warehouse/leap_analytics.duckdb',
                        '--publish',f'/data/published/{generation}.duckdb',
                        '--reuse-publication')
                pending['stage'] = 'activate'
            else:
                command('stop','superset')
                try:
                    command('run','--rm','superset-bootstrap')
                finally:
                    # Restore web service even if bootstrap fails; metadata
                    # still points to the previous generation on failure.
                    command('up','-d','--force-recreate','superset')
                command('exec','-T','superset','/app/.venv/bin/python','-c',
                        "import time,urllib.request\n"
                        "for attempt in range(20):\n"
                        "    try:\n"
                        "        urllib.request.urlopen('http://127.0.0.1:8088/health',timeout=2).read(); break\n"
                        "    except Exception:\n"
                        "        if attempt==19: raise\n"
                        "        time.sleep(3)\n")
                pending['stage'] = 'done'
            save(pending_path, pending)
        save(state_dir/'last-success.json', pending | {'completed_at': datetime.now(timezone.utc).isoformat()})
        pending_path.unlink()
        print(f'{generation}: refresh and activation complete', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT/'analytics/.env')
    parser.add_argument('--state-dir', type=Path, default=ROOT/'analytics/runtime')
    args = parser.parse_args()
    refresh(args.env_file, args.state_dir)
