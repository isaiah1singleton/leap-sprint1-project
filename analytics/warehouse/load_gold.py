"""Load one full Silver batch into a dedicated Gold warehouse, without PostgreSQL."""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import duckdb

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'proof'))
from proof import HERE, reports, validate


def run(batch, db):
    batch = Path(batch).resolve()
    manifest = json.loads((batch / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('version') != 1 or manifest.get('mode') != 'full' or manifest.get('timezone') != 'UTC':
        raise ValueError('Unsupported batch contract')
    count = manifest.get('fill_count')
    if type(count) is not int or count < 0:
        raise ValueError('Invalid fill count')
    silver = batch / 'executed_trades.parquet'
    with silver.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != manifest.get('silver_sha256'):
            raise ValueError('Silver checksum mismatch')
    # A stable catalog alias avoids collisions when the filename is gold.duckdb.
    with duckdb.connect() as con:
        escaped_path = str(Path(db).resolve()).replace("'", "''")
        con.execute(f"ATTACH '{escaped_path}' AS warehouse")
        con.execute('USE warehouse')
        con.execute("SET TimeZone='UTC'")
        con.execute('BEGIN')
        try:
            for schema in ('silver', 'gold'):
                con.execute(f'CREATE SCHEMA IF NOT EXISTS {schema}')
            con.execute('CREATE OR REPLACE TABLE silver.executed_trades AS SELECT * FROM read_parquet(?)', [str(silver)])
            for name in ('client', 'account', 'instrument', 'date', 'time'):
                con.execute((HERE / f'sql/create_dim_{name}.sql').read_text())
            con.execute((HERE / 'sql/create_fact_trade.sql').read_text())
            validate(con, expected_fills=count)
            result = reports(con)
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
    args = parser.parse_args()
    print(json.dumps(run(args.batch, args.db), default=str, indent=2))
