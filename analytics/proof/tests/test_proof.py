import sys
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

import duckdb

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from proof import prepare, run, transform, validate


class ProofTests(unittest.TestCase):
    def test_reports_and_repeatable_full_refresh(self):
        with tempfile.TemporaryDirectory() as directory:
            db = Path(directory) / 'proof.duckdb'
            result = run(db, fixture=True)
            self.assertEqual(result, run(db, fixture=True))
            segments = {r['client_segment']: r['trade_count'] for r in result['activity_by_client_segment']}
            self.assertEqual(segments, {'RETAIL': 5, 'PREMIUM': 3, 'PROFESSIONAL': 4})
            periods = [(r['month'], r['trade_count'], r['total_quantity']) for r in result['activity_by_period']]
            self.assertEqual(periods, [(8,2,Decimal('10.10')),(9,5,Decimal('1655')),(10,5,Decimal('55.25'))])
            expected = {
                'AAPL': ('USD',5,'157','35696.80'),
                'MSFT': ('USD',2,'13','6667'),
                'BARC': ('GBP',2,'550','1794'),
                'GBPUSD': ('USD',1,'1000','1342'),
                'BTCUSD': ('USD',2,'0.35','22710'),
            }
            for row in result['activity_by_instrument']:
                currency, count, qty, notional = expected.pop(row['symbol'])
                self.assertEqual((row['quote_currency'],row['trade_count'],row['total_quantity'],row['total_notional']), (currency,count,Decimal(qty),Decimal(notional)))
            self.assertFalse(expected)
            with duckdb.connect(str(db), read_only=True) as con:
                self.assertEqual(con.execute('SELECT count(*) FROM gold.fact_trade').fetchone()[0],12)
                self.assertEqual(con.execute('SELECT execution_date_key, execution_time_key FROM gold.fact_trade WHERE fill_id=1').fetchone(), (20260828,140005))

    def test_missing_dependency_is_rejected(self):
        with duckdb.connect() as con:
            prepare(con)
            con.execute("INSERT INTO bronze.fills VALUES (1,999,10,'2026-01-01 00:00:00+00')")
            transform(con)
            with self.assertRaises(ValueError):
                validate(con)

    def test_failure_rolls_back_previous_gold(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            db = Path(directory) / 'proof.duckdb'
            run(db, fixture=True)
            with patch('proof.validate', side_effect=ValueError('injected failure')):
                with self.assertRaises(ValueError):
                    run(db, fixture=True)
            with duckdb.connect(str(db), read_only=True) as con:
                self.assertEqual(con.execute('SELECT count(*) FROM gold.fact_trade').fetchone()[0],12)


if __name__ == '__main__':
    unittest.main()
