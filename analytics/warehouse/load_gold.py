"""Load one full Silver batch into a dedicated Gold warehouse, without PostgreSQL."""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import duckdb

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'proof'))
from proof import HERE, reports, validate
try:
    from .stable_keys import prepare_keys, apply_keys
except ImportError:
    from stable_keys import prepare_keys, apply_keys


def run(batch, db):
    batch = Path(batch).resolve()
    manifest = json.loads((batch / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('version') != 1 or manifest.get('mode') != 'full' or manifest.get('timezone') != 'UTC':
        raise ValueError('Unsupported batch contract')
    count = manifest.get('fill_count')
    if type(count) is not int or count < 0:
        raise ValueError('Invalid fill count')
    silver = batch / 'executed_trades.parquet'
    batch_id = manifest.get('batch_id', batch.name)
    if not isinstance(batch_id, str) or not batch_id.strip():
        raise ValueError('Invalid batch ID')
    with silver.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != manifest.get('silver_sha256'):
            raise ValueError('Silver checksum mismatch')
    fingerprint = hashlib.sha256(json.dumps({
        'version': manifest['version'], 'mode': manifest['mode'],
        'timezone': manifest['timezone'], 'fill_count': count,
        'silver_sha256': manifest['silver_sha256'],
    }, sort_keys=True).encode()).hexdigest()
    # A stable catalog alias avoids collisions when the filename is gold.duckdb.
    with duckdb.connect() as con:
        escaped_path = str(Path(db).resolve()).replace("'", "''")
        con.execute(f"ATTACH '{escaped_path}' AS warehouse")
        con.execute('USE warehouse')
        con.execute("SET TimeZone='UTC'")
        con.execute('BEGIN')
        try:
            prepare_keys(con)
            con.execute('CREATE TABLE IF NOT EXISTS pipeline.applied_batches (batch_id VARCHAR PRIMARY KEY, fingerprint VARCHAR NOT NULL, applied_at TIMESTAMPTZ DEFAULT current_timestamp)')
            previous = con.execute('SELECT fingerprint FROM pipeline.applied_batches WHERE batch_id=?', [batch_id]).fetchone()
            if previous:
                if previous[0] != fingerprint:
                    raise ValueError('Batch ID was already applied with different content')
                result = reports(con)
                con.execute('COMMIT')
                return result
            for schema in ('silver', 'gold'):
                con.execute(f'CREATE SCHEMA IF NOT EXISTS {schema}')
            con.execute('CREATE OR REPLACE TABLE silver.executed_trades AS SELECT * FROM read_parquet(?)', [str(silver)])
            for name in ('client', 'account', 'instrument', 'date', 'time'):
                con.execute((HERE / f'sql/create_dim_{name}.sql').read_text())
            # Remap dimension keys BEFORE the fact joins them.
            apply_keys(con)
            con.execute((HERE / 'sql/create_fact_trade.sql').read_text())
            apply_keys(con)
            validate(con, expected_fills=count)
            result = reports(con)
            con.execute('INSERT INTO pipeline.applied_batches (batch_id, fingerprint) VALUES (?,?)', [batch_id, fingerprint])
            con.execute('COMMIT')
        except Exception:
            con.execute('ROLLBACK')
            raise
        con.execute('CHECKPOINT')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--batch', type=Path, required=True)
    parser.add_argument('--db', type=Path, required=True)
    parser.add_argument('--publish', type=Path, help='New immutable snapshot path; never overwritten')
    args = parser.parse_args()
    result = run(args.batch, args.db)
    if args.publish:
        from publish import publish
        publish(args.db, args.publish)
    print(json.dumps(result, default=str, indent=2))
