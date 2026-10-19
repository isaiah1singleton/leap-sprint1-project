import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import duckdb

ANALYTICS = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ANALYTICS))
sys.path.insert(0, str(ANALYTICS/'proof'))
from etl.incremental import run as incremental, digest
from etl.extract_transform import run as full_extract
from warehouse.load_gold import run as load


def source_with_changes(output, fixture=False):
    full_extract(output, fixture=True)
    parquet = output/'executed_trades.parquet'
    temporary = output/'changed.parquet'
    with duckdb.connect() as con:
        con.execute('CREATE TABLE rows AS SELECT * FROM read_parquet(?)', [str(parquet)])
        con.execute("UPDATE rows SET client_segment='VIP' WHERE client_id=1")
        con.execute('UPDATE rows SET execution_price=execution_price+1, notional_amount=notional_amount+executed_quantity WHERE fill_id=6')
        con.execute('DELETE FROM rows WHERE fill_id=4')
        con.execute("INSERT INTO rows SELECT * REPLACE (100 AS fill_id, 100 AS order_id, TIMESTAMP '2020-01-01' AS execution_time) FROM rows WHERE fill_id=1")
        con.execute('COPY rows TO ? (FORMAT PARQUET)', [str(temporary)])
    temporary.replace(parquet)


class DeltaTests(unittest.TestCase):
    def test_resume_legacy_pending_batch_then_establish_state(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            batch, state, warehouse = root/'legacy', root/'state.duckdb', root/'warehouse.duckdb'
            legacy = full_extract(batch, fixture=True)
            original_bytes = (batch/'manifest.json').read_bytes()
            expected = load(batch, warehouse)
            with patch('etl.extract_transform.run', side_effect=AssertionError('Must not extract again')):
                self.assertEqual(incremental(batch, state, fixture=True), legacy)
            self.assertEqual((batch/'manifest.json').read_bytes(), original_bytes)
            self.assertReports(load(batch, warehouse), expected)
            baseline = incremental(root/'baseline-v2', state, fixture=True)
            self.assertEqual(baseline['mode'],'full')
            load(root/'baseline-v2', warehouse)
            delta = incremental(root/'delta', state, fixture=True)
            self.assertEqual((delta['mode'],delta['upsert_count'],delta['delete_count']),('delta',0,0))
            self.assertReports(load(root/'delta', warehouse), expected)
            with self.assertRaisesRegex(ValueError,'legacy full batch'):
                incremental(batch,state,fixture=True)

    def test_legacy_corruption_and_missing_v2_checksums_fail_clearly(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            batch=root/'legacy'
            full_extract(batch,fixture=True)
            path=batch/'manifest.json'
            manifest=json.loads(path.read_text())
            manifest['silver_sha256']='invalid'
            path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError,'checksum'):
                incremental(batch,root/'state.duckdb',fixture=True)
            manifest['version']=2
            path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError,'required file checksums'):
                incremental(batch,root/'state.duckdb',fixture=True)

    def assertReports(self, actual, expected):
        # Segment SQL orders by count only; tied segments may change order.
        def normalized(reports):
            return {name: sorted(json.dumps(row, sort_keys=True, default=str) for row in rows) for name, rows in reports.items()}
        self.assertEqual(normalized(actual), normalized(expected))

    def test_deltas_match_full_refresh_and_replay_is_safe(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state, warehouse = root/'checkpoint.duckdb', root/'warehouse.duckdb'
            first = incremental(root/'first', state, fixture=True)
            self.assertEqual(first['mode'], 'full')
            load(root/'first', warehouse)
            with duckdb.connect(str(warehouse), read_only=True) as con:
                key = con.execute('SELECT trade_key FROM gold.fact_trade WHERE fill_id=1').fetchone()
            with patch('etl.extract_transform.run', side_effect=source_with_changes):
                second = incremental(root/'second', state, fixture=True)
            self.assertEqual((second['mode'],second['upsert_count'],second['delete_count']), ('delta',5,1))
            actual = load(root/'second', warehouse)
            full = root/'baseline'
            full.mkdir()
            shutil.copy(root/'second/state_snapshot.parquet', full/'executed_trades.parquet')
            (full/'manifest.json').write_text(json.dumps({
                'version':1, 'mode':'full', 'batch_id':'baseline', 'timezone':'UTC',
                'fill_count':12, 'silver_sha256':digest(full/'executed_trades.parquet'),
            }))
            self.assertReports(actual, load(full, root/'baseline.duckdb'))
            self.assertReports(actual, load(root/'second', warehouse))
            self.assertReports(actual, load(root/'first', warehouse))
            with duckdb.connect(str(warehouse), read_only=True) as con:
                self.assertEqual(con.execute('SELECT trade_key FROM gold.fact_trade WHERE fill_id=1').fetchone(), key)
                self.assertEqual(con.execute('SELECT count(*) FROM gold.fact_trade WHERE fill_id=4').fetchone()[0],0)
            # No changes still produces an ordered, replayable empty delta.
            with patch('etl.extract_transform.run', side_effect=source_with_changes):
                third = incremental(root/'third', state, fixture=True)
            self.assertEqual((third['upsert_count'],third['delete_count']), (0,0))
            self.assertReports(load(root/'third', warehouse), actual)

    def test_missing_parent_rollback_and_checkpoint_recovery(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state, warehouse = root/'checkpoint.duckdb', root/'warehouse.duckdb'
            incremental(root/'first', state, fixture=True)
            second = incremental(root/'second', state, fixture=True)
            with self.assertRaisesRegex(ValueError,'parent'):
                load(root/'second', warehouse)
            load(root/'first', warehouse)
            with patch('warehouse.load_gold.validate', side_effect=ValueError('injected')):
                with self.assertRaises(ValueError):
                    load(root/'second', warehouse)
            with duckdb.connect(str(warehouse), read_only=True) as con:
                self.assertEqual(con.execute('SELECT batch_id FROM pipeline.head').fetchone()[0], 'first')
            load(root/'second', warehouse)
            # Simulate a crash after batch publication but before state commit.
            with duckdb.connect(str(state)) as con:
                con.execute('UPDATE checkpoint SET batch_id=?, path=?', ['first',str(root/'first')])
            self.assertEqual(incremental(root/'second', state, fixture=True), second)
            with duckdb.connect(str(state), read_only=True) as con:
                self.assertEqual(con.execute('SELECT batch_id FROM checkpoint').fetchone()[0], 'second')
