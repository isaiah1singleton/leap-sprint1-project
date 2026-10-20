import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import duckdb

ANALYTICS = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ANALYTICS))
sys.path.insert(0, str(ANALYTICS / 'proof'))
from etl.extract_transform import run as extract
from warehouse.load_gold import run as load
from proof import run as original


def changed_batch(source, target, sql):
    target.mkdir()
    parquet = target / 'executed_trades.parquet'
    with duckdb.connect() as con:
        con.execute('CREATE TABLE trades AS SELECT * FROM read_parquet(?)', [str(source / 'executed_trades.parquet')])
        con.execute(sql)
        count = con.execute('SELECT count(*) FROM trades').fetchone()[0]
        con.execute('COPY trades TO ? (FORMAT PARQUET)', [str(parquet)])
    manifest = json.loads((source / 'manifest.json').read_text())
    manifest.update(batch_id=target.name, fill_count=count, silver_sha256=hashlib.sha256(parquet.read_bytes()).hexdigest())
    (target / 'manifest.json').write_text(json.dumps(manifest))


def keys(db):
    with duckdb.connect(str(db), read_only=True) as con:
        return {
            table: dict(con.execute(f'SELECT {source}, {key} FROM gold.{table}').fetchall())
            for table, source, key in (
                ('dim_client', 'source_client_id', 'client_key'),
                ('dim_account', 'source_account_id', 'account_key'),
                ('dim_instrument', 'source_instrument_id', 'instrument_key'),
                ('fact_trade', 'fill_id', 'trade_key'),
            )
        }


class StableLoaderTests(unittest.TestCase):
    def test_migrate_then_preserve_keys_across_backdated_trade_and_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            db, batch = root/'working.duckdb', root/'initial'
            original(db, fixture=True)
            before = keys(db)
            extract(batch, fixture=True)
            load(batch, db)
            self.assertEqual(keys(db), before)
            newer = root/'newer'
            changed_batch(batch, newer, """
                UPDATE trades SET client_segment='VIP' WHERE client_id=1;
                INSERT INTO trades SELECT 0, 0, 0, 0, 0, 'RETAIL', 'NEW',
                    'New instrument', 'EQUITY', 'NASDAQ', 'USD', 'BUY',
                    1, 2, TIMESTAMP '2020-01-01', 2;
            """)
            load(newer, db)
            after = keys(db)
            for table, mapping in before.items():
                for source, key in mapping.items():
                    self.assertEqual(after[table][source], key)
                self.assertGreater(after[table][0], max(mapping.values()))
            # Replaying an older full batch must not replace current state.
            current = load(batch, db)
            self.assertEqual(keys(db), after)
            self.assertIn({'client_segment': 'VIP', 'trade_count': 3}, current['activity_by_client_segment'])
            removed = root/'removed'
            changed_batch(newer, removed, 'DELETE FROM trades WHERE client_id=0')
            load(removed, db)
            restored = root/'restored'
            changed_batch(newer, restored, 'SELECT 1')
            load(restored, db)
            self.assertEqual(keys(db), after)

    def test_conflicting_batch_id_and_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            db, batch = root/'working.duckdb', root/'initial'
            extract(batch, fixture=True)
            load(batch, db)
            changed = root/'changed'
            changed_batch(batch, changed, '''
                UPDATE trades SET notional_amount=notional_amount+1;
                INSERT INTO trades SELECT * REPLACE (100 AS fill_id, 100 AS order_id)
                FROM trades WHERE fill_id=1;
            ''')
            with patch('warehouse.load_gold.validate', side_effect=ValueError('injected')):
                with self.assertRaises(ValueError):
                    load(changed, db)
            with duckdb.connect(str(db), read_only=True) as con:
                self.assertEqual(con.execute('SELECT count(*) FROM pipeline.applied_batches').fetchone()[0], 1)
                self.assertEqual(con.execute('SELECT count(*) FROM pipeline.trade_keys').fetchone()[0], 12)
            # A retry after rollback must apply normally.
            load(changed, db)
            manifest_path = changed/'manifest.json'
            manifest = json.loads(manifest_path.read_text())
            manifest['batch_id'] = batch.name
            manifest_path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, 'different content'):
                load(changed, db)
