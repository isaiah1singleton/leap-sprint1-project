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


class SplitTests(unittest.TestCase):
    def test_parquet_roundtrip_matches_proof_without_source_access(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = original(root / 'proof.duckdb', fixture=True)
            extract(root / 'batch', fixture=True)
            with patch('psycopg.connect', side_effect=AssertionError('Warehouse must not contact PostgreSQL')):
                actual = load(root / 'batch', root / 'gold.duckdb')
                self.assertEqual(actual, expected)
                self.assertEqual(load(root / 'batch', root / 'gold.duckdb'), expected)
            with duckdb.connect(str(root / 'gold.duckdb'), read_only=True) as con:
                self.assertEqual(con.execute("SELECT count(*) FROM information_schema.tables WHERE table_schema='bronze'").fetchone()[0], 0)

    def test_batch_is_immutable_and_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            extract(root / 'batch', fixture=True)
            with self.assertRaises(FileExistsError):
                extract(root / 'batch', fixture=True)
            load(root / 'batch', root / 'gold.duckdb')
            with (root / 'batch/executed_trades.parquet').open('ab') as stream:
                stream.write(b'tampered')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                load(root / 'batch', root / 'gold.duckdb')
            with duckdb.connect(str(root / 'gold.duckdb'), read_only=True) as con:
                self.assertEqual(con.execute('SELECT count(*) FROM gold.gold.fact_trade').fetchone()[0], 12)

    def test_failed_load_preserves_previous_gold(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            extract(root / 'batch', fixture=True)
            load(root / 'batch', root / 'gold.duckdb')
            extract(root / 'next-batch', fixture=True)
            with patch('warehouse.load_gold.validate', side_effect=ValueError('injected')):
                with self.assertRaises(ValueError):
                    load(root / 'next-batch', root / 'gold.duckdb')
            with duckdb.connect(str(root / 'gold.duckdb'), read_only=True) as con:
                self.assertEqual(con.execute('SELECT count(*) FROM gold.gold.fact_trade').fetchone()[0], 12)
