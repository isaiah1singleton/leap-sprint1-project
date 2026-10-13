import sys
import tempfile
import unittest
from pathlib import Path

import duckdb

ANALYTICS = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ANALYTICS))
sys.path.insert(0, str(ANALYTICS / 'proof'))
from etl.extract_transform import run as extract
from warehouse.load_gold import run as load
from warehouse.publish import publish


class PublishTests(unittest.TestCase):
    def test_gold_only_snapshot_survives_refresh_and_rejects_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            batch, working, snapshot = root/'batch', root/'working.duckdb', root/'v1.duckdb'
            extract(batch, fixture=True)
            expected = load(batch, working)
            publish(working, snapshot)
            with duckdb.connect(str(snapshot), read_only=True) as reader:
                schemas = reader.execute("SELECT DISTINCT table_schema FROM information_schema.tables").fetchall()
                self.assertEqual(schemas, [('gold',)])
                for name, rows in expected.items():
                    cursor = reader.execute(f'SELECT * FROM gold.{name}')
                    actual = [dict(zip([c[0] for c in cursor.description], row)) for row in cursor.fetchall()]
                    self.assertEqual(actual, rows)
                load(batch, working)
                publish(working, root/'v2.duckdb')
                self.assertEqual(reader.execute('SELECT count(*) FROM gold.fact_trade').fetchone()[0], 12)
                with self.assertRaises(duckdb.InvalidInputException):
                    reader.execute('DELETE FROM gold.fact_trade')
                with self.assertRaises(FileExistsError):
                    publish(working, snapshot)
