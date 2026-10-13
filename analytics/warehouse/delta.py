"""Apply an ordered Silver delta and generate only affected fact rows."""
def merge_silver(con, batch, manifest):
    head = con.execute('SELECT batch_id FROM pipeline.head WHERE id=1').fetchone()
    if not head or head[0] != manifest['parent_batch_id']:
        raise ValueError('Delta parent is not warehouse head; apply missing batches in order')
    con.execute('CREATE OR REPLACE TEMP TABLE incoming AS SELECT * FROM read_parquet(?)', [str(batch/'executed_trades.parquet')])
    con.execute('CREATE OR REPLACE TEMP TABLE deleted AS SELECT * FROM read_parquet(?)', [str(batch/'deleted_fills.parquet')])
    for table, expected in (('incoming', manifest['upsert_count']), ('deleted', manifest['delete_count'])):
        count, unique = con.execute(f'SELECT count(*), count(DISTINCT fill_id) FROM {table}').fetchone()
        if count != expected or unique != count:
            raise ValueError('Invalid delta row count or duplicate fill ID')
    if con.execute('SELECT count(*) FROM incoming JOIN deleted USING(fill_id)').fetchone()[0]:
        raise ValueError('Fill cannot be both upserted and deleted')
    con.execute('DELETE FROM silver.executed_trades WHERE fill_id IN (SELECT fill_id FROM deleted UNION SELECT fill_id FROM incoming)')
    con.execute('INSERT INTO silver.executed_trades SELECT * FROM incoming')


def merge_facts(con, sql):
    con.execute('''INSERT INTO pipeline.trade_keys
        SELECT fill_id, (SELECT coalesce(max(warehouse_key),0) FROM pipeline.trade_keys)
               + row_number() OVER (ORDER BY fill_id)
        FROM incoming WHERE fill_id NOT IN (SELECT source_id FROM pipeline.trade_keys)''')
    sql = sql.replace('gold.fact_trade', 'pipeline.next_facts')
    sql = sql.replace('ROW_NUMBER() OVER (ORDER BY s.execution_time, s.fill_id)', 'tk.warehouse_key')
    sql = sql.replace('FROM silver.executed_trades s', 'FROM incoming s JOIN pipeline.trade_keys tk ON s.fill_id=tk.source_id')
    con.execute(sql)
    con.execute('DELETE FROM gold.fact_trade WHERE fill_id IN (SELECT fill_id FROM deleted UNION SELECT fill_id FROM incoming)')
    con.execute('INSERT INTO gold.fact_trade SELECT * FROM pipeline.next_facts')
    con.execute('DROP TABLE pipeline.next_facts')
