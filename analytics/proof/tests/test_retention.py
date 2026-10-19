import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import duckdb

sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
from warehouse.retention import plan, apply


class RetentionTests(unittest.TestCase):
    def test_protects_state_pending_unapplied_active_and_recent(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve()
            batches,published=root/'batches',root/'published'
            batches.mkdir(); published.mkdir()
            names=['old','checkpoint','head','pending','unapplied','newest']
            for index,name in enumerate(names):
                batch=batches/name
                batch.mkdir()
                (batch/'manifest.json').write_text(json.dumps({'version':2,'batch_id':name}))
                os.utime(batch,(index,index))
                snapshot=published/f'{name}.duckdb'
                with duckdb.connect(str(snapshot)) as con:
                    con.execute('CREATE SCHEMA gold')
                    for table in ('dim_client','dim_account','dim_instrument','dim_date','dim_time','fact_trade'):
                        con.execute(f'CREATE TABLE gold.{table} (id INTEGER)')
                os.utime(snapshot,(index,index))
            active=published/'old.duckdb'
            expired=published/'expired.duckdb'
            shutil.copyfile(active,expired)
            os.utime(expired,(0,0))
            # Active snapshot is protected even when its source batch expires.
            state,warehouse=root/'state.duckdb',root/'warehouse.duckdb'
            with duckdb.connect(str(state)) as con:
                con.execute('CREATE TABLE checkpoint (id INTEGER,batch_id VARCHAR,path VARCHAR)')
                con.execute('INSERT INTO checkpoint VALUES (1,?,?)',['checkpoint',str(batches/'checkpoint')])
            with duckdb.connect(str(warehouse)) as con:
                con.execute('CREATE SCHEMA pipeline')
                con.execute('CREATE TABLE pipeline.applied_batches (batch_id VARCHAR)')
                con.executemany('INSERT INTO pipeline.applied_batches VALUES (?)',[(name,) for name in names if name!='unapplied'])
                con.execute("CREATE TABLE pipeline.head AS SELECT 1 AS id,'head' AS batch_id")
            unknown=published/'unknown.duckdb'
            unknown.write_text('Not a DuckDB snapshot')
            result=plan(batches,published,state,warehouse,[active.name],['pending'],days=1,keep=1,now=999999)
            self.assertEqual(result['delete_batches'],[str(batches/'old')])
            self.assertEqual(result['delete_snapshots'],[str(expired)])
            self.assertTrue((batches/'old').exists()) # dry-run did not mutate
            apply(result)
            self.assertFalse((batches/'old').exists())
            self.assertTrue(active.exists())
            self.assertFalse(expired.exists())
            self.assertTrue(unknown.exists())
            for name in names[1:]:
                self.assertTrue((batches/name).exists())
            with self.assertRaises(ValueError):
                plan(batches,published,state,warehouse,[],now=999999)
            with self.assertRaises(ValueError):
                plan(batches,published,state,warehouse,['missing.duckdb'],now=999999)

    def test_missing_state_fails_without_creating_database(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            with self.assertRaises(duckdb.Error):
                # Existing active file allows the state safety check to run.
                (root/'active.duckdb').touch()
                plan(root,root,root/'missing.duckdb',root/'working.duckdb',['active.duckdb'])
            self.assertFalse((root/'missing.duckdb').exists())
