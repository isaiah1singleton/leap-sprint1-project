"""Extract a full read-only source snapshot and publish an immutable Silver batch."""
import argparse
import hashlib
import json
import sys
import tempfile
from pathlib import Path

import duckdb

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'proof'))
from proof import HERE, extract, prepare


def run(output, fixture=False):
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise FileExistsError('Use a new batch directory; existing batches are immutable.')
    # Same filesystem allows atomic directory publication after all files close.
    with tempfile.TemporaryDirectory(dir=output.parent, prefix='.batch-') as staging:
        staging = Path(staging)
        with duckdb.connect() as con:
            prepare(con)
            if fixture:
                con.execute((HERE / 'tests/fixture.sql').read_text())
            else:
                extract(con)
            con.execute((HERE / 'sql/create_executed_trades.sql').read_text())
            count = con.execute('SELECT count(*) FROM bronze.fills').fetchone()[0]
            actual, unique = con.execute('SELECT count(*), count(DISTINCT fill_id) FROM silver.executed_trades').fetchone()
            if actual != count or unique != count:
                raise ValueError('Silver contains missing, duplicate, or unjoinable fills')
            for table in ('clients', 'accounts', 'instruments', 'orders', 'fills'):
                con.execute(f'COPY bronze.{table} TO ? (FORMAT PARQUET)', [str(staging / f'bronze_{table}.parquet')])
            silver = staging / 'executed_trades.parquet'
            con.execute('COPY silver.executed_trades TO ? (FORMAT PARQUET)', [str(silver)])
        with silver.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        manifest = {'version': 1, 'mode': 'full', 'batch_id': output.name, 'timezone': 'UTC', 'fill_count': count,
                    'silver_sha256': digest}
        (staging / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        staging.rename(output)
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--fixture', action='store_true')
    args = parser.parse_args()
    print(json.dumps(run(args.output, args.fixture), indent=2))
