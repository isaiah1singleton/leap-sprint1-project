"""Plan conservative retention; apply only after the host locks/stops writers/readers."""
import argparse
import json
from pathlib import Path
import shutil
import time

import duckdb


def plan(batches, published, state, warehouse, active, protected=(), days=7, keep=24, now=None):
    if days < 1 or keep < 1 or not active:
        raise ValueError('Require active snapshots, at least one day and one retained generation')
    batches, published = Path(batches).resolve(), Path(published).resolve()
    protected = set(protected)
    snapshots = set()
    for name in active:
        if Path(name).name != name or not name.endswith('.duckdb'):
            raise ValueError('Invalid active snapshot name')
        path = published/name
        if not path.is_file() or path.is_symlink():
            raise ValueError('Active snapshot missing or symlinked; refusing cleanup')
        snapshots.add(name)
    # Missing or locked databases fail closed; never create an empty state file.
    with duckdb.connect(str(state), read_only=True) as con:
        checkpoint = con.execute('SELECT batch_id, path FROM checkpoint WHERE id=1').fetchone()
        if not checkpoint:
            raise ValueError('No ETL checkpoint; refusing cleanup')
        if Path(checkpoint[1]).resolve() != batches/checkpoint[0] or not (batches/checkpoint[0]).is_dir():
            raise ValueError('Checkpoint batch is missing or outside batch volume')
        protected.add(checkpoint[0])
    with duckdb.connect(str(warehouse), read_only=True) as con:
        applied = {row[0] for row in con.execute('SELECT batch_id FROM pipeline.applied_batches').fetchall()}
        head = con.execute('SELECT batch_id FROM pipeline.head WHERE id=1').fetchone()
        if not head:
            raise ValueError('No warehouse head; refusing cleanup')
        protected.add(head[0])
    entries = []
    skipped = []
    for path in batches.iterdir():
        if not path.is_dir() or path.is_symlink() or path.name.startswith('.'):
            skipped.append(str(path)); continue
        try:
            manifest = json.loads((path/'manifest.json').read_text())
            if manifest.get('version') not in (1,2) or manifest.get('batch_id',path.name) != path.name:
                raise ValueError('Unknown batch contract')
        except (ValueError, OSError):
            skipped.append(str(path)); continue
        entries.append(path)
        if path.name not in applied:
            protected.add(path.name)
    snapshots.update(f'{name}.duckdb' for name in protected)
    files = []
    expected_tables = {'dim_client','dim_account','dim_instrument','dim_date','dim_time','fact_trade'}
    for path in published.glob('*.duckdb'):
        if not path.is_file() or path.is_symlink():
            skipped.append(str(path)); continue
        try:
            with duckdb.connect(str(path), read_only=True) as con:
                tables = {row[0] for row in con.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='gold' AND table_type='BASE TABLE'").fetchall()}
                schemas = {row[0] for row in con.execute('SELECT DISTINCT table_schema FROM information_schema.tables').fetchall()}
                if tables != expected_tables or schemas != {'gold'}:
                    raise ValueError('Not a managed Gold snapshot')
        except (ValueError, duckdb.Error):
            skipped.append(str(path)); continue
        files.append(path)
    cutoff = (time.time() if now is None else now) - days*86400

    def candidates(paths, names):
        newest = sorted(paths, key=lambda p:p.stat().st_mtime, reverse=True)[:keep]
        retained = names | {p.name for p in newest}
        return [str(p) for p in sorted(paths) if p.name not in retained and p.stat().st_mtime < cutoff]

    return {'delete_batches': candidates(entries, protected),
            'delete_snapshots': candidates(files, snapshots),
            'protected_batches': sorted(protected), 'protected_snapshots': sorted(snapshots),
            'skipped_unknown': skipped, 'days': days, 'keep': keep}


def apply(result):
    for path in result['delete_batches']:
        shutil.rmtree(path)
    for path in result['delete_snapshots']:
        Path(path).unlink()


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--active', action='append', required=True)
    parser.add_argument('--protect', action='append', default=[])
    parser.add_argument('--days',type=int,default=7)
    parser.add_argument('--keep',type=int,default=24)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    result=plan('/data/batches','/data/published','/data/state/checkpoint.duckdb',
                '/data/warehouse/leap_analytics.duckdb',args.active,args.protect,args.days,args.keep)
    print(json.dumps(result,indent=2),flush=True)
    if args.apply:
        apply(result)
