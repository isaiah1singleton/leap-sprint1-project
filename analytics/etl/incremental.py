"""Snapshot-diff ETL with durable, replayable batches and checkpoint recovery."""
import hashlib
import json
from pathlib import Path
import tempfile

import duckdb


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify(batch, manifest):
    for name, expected in manifest['files'].items():
        if Path(name).name != name or digest(batch / name) != expected:
            raise ValueError('Batch file checksum mismatch')


def run(output, state, fixture=False):
    try:
        from .extract_transform import run as full_extract
    except ImportError:
        from extract_transform import run as full_extract

    output, state = Path(output).resolve(), Path(state).resolve()
    state.parent.mkdir(parents=True, exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    # DuckDB file locking also prevents simultaneous ETL checkpoint writers.
    with duckdb.connect(str(state)) as con:
        con.execute('CREATE TABLE IF NOT EXISTS checkpoint (id INTEGER PRIMARY KEY, batch_id VARCHAR, path VARCHAR)')
        previous = con.execute('SELECT batch_id, path FROM checkpoint WHERE id=1').fetchone()
        parent = previous[0] if previous else None
        if previous and parent == output.name and previous[1] != str(output):
            raise ValueError('Batch ID is already checkpointed at another path')
        if output.exists():
            manifest = json.loads((output/'manifest.json').read_text())
            verify(output, manifest)
            if manifest.get('version') != 2 or manifest.get('batch_id') != output.name:
                raise ValueError('Existing output is not this incremental batch')
            if parent == output.name:
                return manifest
            if manifest.get('parent_batch_id') != parent:
                raise ValueError('Cannot recover batch with a different parent')
            # Publication may have completed before checkpoint commit.
            con.execute('INSERT OR REPLACE INTO checkpoint VALUES (1,?,?)', [output.name, str(output)])
            return manifest
        with tempfile.TemporaryDirectory(dir=output.parent, prefix='.delta-') as directory:
            staging = Path(directory)/'batch'
            full_extract(staging, fixture=fixture)
            (staging/'executed_trades.parquet').rename(staging/'state_snapshot.parquet')
            con.execute('CREATE OR REPLACE TEMP TABLE current_rows AS SELECT * FROM read_parquet(?)', [str(staging/'state_snapshot.parquet')])
            if previous:
                previous_path = Path(previous[1])
                old_manifest = json.loads((previous_path/'manifest.json').read_text())
                verify(previous_path, old_manifest)
                con.execute('CREATE OR REPLACE TEMP TABLE previous_rows AS SELECT * FROM read_parquet(?)', [str(previous_path/'state_snapshot.parquet')])
            else:
                con.execute('CREATE OR REPLACE TEMP TABLE previous_rows AS SELECT * FROM current_rows WHERE false')
            con.execute('CREATE OR REPLACE TEMP TABLE changed AS SELECT * FROM current_rows EXCEPT SELECT * FROM previous_rows')
            con.execute('CREATE OR REPLACE TEMP TABLE deleted AS SELECT fill_id FROM previous_rows EXCEPT SELECT fill_id FROM current_rows')
            for table, name in (('changed','executed_trades.parquet'), ('deleted','deleted_fills.parquet')):
                con.execute(f'COPY {table} TO ? (FORMAT PARQUET)', [str(staging/name)])
            manifest = {
                'version': 2, 'mode': 'delta' if previous else 'full',
                'batch_id': output.name, 'parent_batch_id': parent, 'timezone': 'UTC',
                'fill_count': con.execute('SELECT count(*) FROM current_rows').fetchone()[0],
                'upsert_count': con.execute('SELECT count(*) FROM changed').fetchone()[0],
                'delete_count': con.execute('SELECT count(*) FROM deleted').fetchone()[0],
                'silver_sha256': digest(staging/'executed_trades.parquet'),
                'files': {p.name: digest(p) for p in staging.glob('*.parquet')},
            }
            (staging/'manifest.json').write_text(json.dumps(manifest, indent=2))
            staging.rename(output)
        con.execute('INSERT OR REPLACE INTO checkpoint VALUES (1,?,?)', [output.name, str(output)])
    return manifest
