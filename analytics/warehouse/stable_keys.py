"""Persistent key registries, including migration from existing full-load Gold."""
MAPPINGS = (
    ('client', 'dim_client', 'source_client_id', 'client_key'),
    ('account', 'dim_account', 'source_account_id', 'account_key'),
    ('instrument', 'dim_instrument', 'source_instrument_id', 'instrument_key'),
    ('trade', 'fact_trade', 'fill_id', 'trade_key'),
)


def prepare_keys(con):
    con.execute('CREATE SCHEMA IF NOT EXISTS pipeline')
    for name, table, source, key in MAPPINGS:
        con.execute(f'CREATE TABLE IF NOT EXISTS pipeline.{name}_keys (source_id BIGINT PRIMARY KEY, warehouse_key BIGINT UNIQUE NOT NULL)')
        exists = con.execute("SELECT count(*) FROM information_schema.tables WHERE table_catalog=current_database() AND table_schema='gold' AND table_name=?", [table]).fetchone()[0]
        if exists:
            # Import original keys before any dimension/fact rebuild. Existing
            # mappings must agree rather than silently reassigning references.
            mismatch = con.execute(f'SELECT count(*) FROM gold.{table} g JOIN pipeline.{name}_keys k ON g.{source}=k.source_id WHERE g.{key}<>k.warehouse_key').fetchone()[0]
            if mismatch:
                raise ValueError(f'Existing {name} keys conflict with registry')
            con.execute(f'INSERT INTO pipeline.{name}_keys SELECT {source}, {key} FROM gold.{table} WHERE {source} NOT IN (SELECT source_id FROM pipeline.{name}_keys)')


def apply_keys(con):
    for name, table, source, key in MAPPINGS:
        if not con.execute("SELECT count(*) FROM information_schema.tables WHERE table_catalog=current_database() AND table_schema='gold' AND table_name=?", [table]).fetchone()[0]:
            continue
        con.execute(f'''
            INSERT INTO pipeline.{name}_keys
            SELECT source_id,
                   (SELECT coalesce(max(warehouse_key),0) FROM pipeline.{name}_keys)
                   + row_number() OVER (ORDER BY source_id)
            FROM (SELECT DISTINCT {source} AS source_id FROM gold.{table}
                  WHERE {source} NOT IN (SELECT source_id FROM pipeline.{name}_keys))
        ''')
        con.execute(f'UPDATE gold.{table} g SET {key}=k.warehouse_key FROM pipeline.{name}_keys k WHERE g.{source}=k.source_id')
