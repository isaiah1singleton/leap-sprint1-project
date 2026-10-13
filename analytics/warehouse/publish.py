"""Publish a new immutable, Gold-only dashboard database."""
import os
import tempfile
from pathlib import Path

import duckdb

from proof import ANALYTICS


def publish(source, destination):
    destination = Path(destination).resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise FileExistsError('Snapshot already exists; choose a new SNAPSHOT_ID')
    with tempfile.TemporaryDirectory(dir=destination.parent, prefix='.publish-') as directory:
        temporary = Path(directory) / 'snapshot.duckdb'
        with duckdb.connect(str(temporary)) as con:
            path = str(Path(source).resolve()).replace("'", "''")
            con.execute(f"ATTACH '{path}' AS source (READ_ONLY)")
            con.execute('CREATE SCHEMA gold')
            for table in ('dim_client', 'dim_account', 'dim_instrument', 'dim_date', 'dim_time', 'fact_trade'):
                con.execute(f'CREATE TABLE gold.{table} AS SELECT * FROM source.gold.{table}')
            for query in sorted((ANALYTICS / 'queries').glob('*.sql')):
                sql = query.read_text().replace('leap.gold.', 'gold.').strip().rstrip(';')
                con.execute(f'CREATE VIEW gold.{query.stem} AS {sql}')
                con.execute(f'SELECT * FROM gold.{query.stem}').fetchall()
            con.execute('CHECKPOINT')
        with duckdb.connect(str(temporary), read_only=True) as con:
            con.execute('SELECT count(*) FROM gold.fact_trade').fetchone()
        temporary.chmod(0o444)
        # Exclusive publication: never overwrite a generation readers may use.
        os.link(temporary, destination)
    return destination
