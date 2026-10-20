"""Standalone full-load BR-16 proof. Never writes to PostgreSQL."""
import argparse
import json
import os
from pathlib import Path

import duckdb

HERE = Path(__file__).resolve().parent
ANALYTICS = HERE.parent
# Explicit projection excludes authentication and unrelated personal data.
SOURCES = {
    'clients': 'client_id INTEGER, client_segment VARCHAR',
    'accounts': 'account_id INTEGER, client_id INTEGER',
    'instruments': 'instrument_id INTEGER, symbol VARCHAR, instrument_name VARCHAR, asset_class VARCHAR, market VARCHAR, quote_currency VARCHAR',
    'orders': 'order_id INTEGER, account_id INTEGER, instrument_id INTEGER, side VARCHAR, requested_quantity DECIMAL(18,8)',
    'fills': 'fill_id INTEGER, order_id INTEGER, execution_price DECIMAL(18,8), execution_time TIMESTAMPTZ',
}


def prepare(con):
    con.execute("SET TimeZone='UTC'")
    for schema in ('bronze', 'silver', 'gold'):
        con.execute(f'CREATE SCHEMA IF NOT EXISTS {schema}')
    for name, columns in SOURCES.items():
        con.execute(f'CREATE OR REPLACE TABLE bronze.{name} ({columns})')


def extract(con):
    import psycopg
    required = ('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')
    if any(not os.environ.get(key) for key in required):
        raise ValueError('Set POSTGRES_DB, POSTGRES_USER and POSTGRES_PASSWORD.')
    with psycopg.connect(
        host=os.environ.get('POSTGRES_HOST', 'localhost'),
        port=os.environ.get('POSTGRES_PORT', '5432'),
        dbname=os.environ['POSTGRES_DB'], user=os.environ['POSTGRES_USER'],
        password=os.environ['POSTGRES_PASSWORD'], connect_timeout=10,
        application_name='leap_br16_proof',
    ) as source:
        source.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY')
        source.execute("SET LOCAL TIME ZONE 'UTC'")
        source.execute("SET LOCAL statement_timeout = '30s'")
        source.execute("SET LOCAL lock_timeout = '2s'")
        for name, definition in SOURCES.items():
            # Table/column names come only from the static allowlist above.
            columns = ', '.join(row[0] for row in con.execute(f'DESCRIBE bronze.{name}').fetchall())
            with source.cursor(name=f'br16_{name}') as cursor:
                cursor.execute(f'SELECT {columns} FROM public.{name}')
                while rows := cursor.fetchmany(1000):
                    placeholders = ','.join('?' for _ in rows[0])
                    con.executemany(f'INSERT INTO bronze.{name} VALUES ({placeholders})', rows)


def transform(con):
    files = ['create_executed_trades.sql'] + [f'create_dim_{name}.sql' for name in ('client', 'account', 'instrument', 'date', 'time')] + ['create_fact_trade.sql']
    for name in files:
        con.execute((HERE / 'sql' / name).read_text())


def validate(con, expected_fills=None):
    fills = expected_fills if expected_fills is not None else con.execute('SELECT count(*) FROM bronze.fills').fetchone()[0]
    for table in ('silver.executed_trades', 'gold.fact_trade'):
        count, unique = con.execute(f'SELECT count(*), count(DISTINCT fill_id) FROM {table}').fetchone()
        if count != fills or unique != fills:
            raise ValueError(f'{table}: missing, duplicated, or unjoinable fills')
    for dim, key, fact_key in (
        ('client', 'client_key', 'client_key'), ('account', 'account_key', 'account_key'),
        ('instrument', 'instrument_key', 'instrument_key'),
        ('date', 'date_key', 'execution_date_key'), ('time', 'time_key', 'execution_time_key'),
    ):
        count, unique = con.execute(f'SELECT count(*), count(DISTINCT {key}) FROM gold.dim_{dim}').fetchone()
        if count != unique:
            raise ValueError(f'Duplicate {dim} keys')
        missing = con.execute(f'SELECT count(*) FROM gold.fact_trade f LEFT JOIN gold.dim_{dim} d ON f.{fact_key}=d.{key} WHERE d.{key} IS NULL').fetchone()[0]
        if missing:
            raise ValueError(f'Orphan {dim} relationships')


def reports(con):
    result = {}
    for path in sorted((ANALYTICS / 'queries').glob('*.sql')):
        cursor = con.execute(path.read_text())
        result[path.stem] = [dict(zip([column[0] for column in cursor.description], row)) for row in cursor.fetchall()]
    return result


def run(db, fixture=False):
    with duckdb.connect(str(db)) as con:
        con.execute('BEGIN')
        try:
            prepare(con)
            if fixture:
                con.execute((HERE / 'tests' / 'fixture.sql').read_text())
            else:
                extract(con)
            transform(con)
            validate(con)
            result = reports(con)
            con.execute('COMMIT')
        except Exception:
            con.execute('ROLLBACK')
            raise
        con.execute('CHECKPOINT')
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--db', type=Path, required=True, help='Dedicated proof output database; do not use a live warehouse')
    parser.add_argument('--fixture', action='store_true', help='Use offline seed-equivalent fixture, without PostgreSQL')
    args = parser.parse_args()
    print(json.dumps(run(args.db, args.fixture), default=str, indent=2))
