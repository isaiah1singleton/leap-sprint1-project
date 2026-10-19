"""Read metadata without changing assets; fail closed on unfamiliar DuckDB paths."""
import json
from pathlib import Path
from sqlalchemy.engine import make_url
from superset.app import create_app

app=create_app()
with app.app_context():
    from superset import db
    from superset.models.core import Database
    active=set()
    for database in db.session.query(Database).all():
        url=make_url(database.sqlalchemy_uri)
        if url.get_backend_name() != 'duckdb':
            continue
        path=Path(url.database or '')
        if path.parent != Path('/data/published') or path.suffix != '.duckdb':
            raise ValueError('Unmanaged DuckDB connection; refusing automatic retention')
        active.add(path.name)
    if not active:
        raise ValueError('No active DuckDB database; refusing retention')
    print('ACTIVE_SNAPSHOTS='+json.dumps(sorted(active)),flush=True)
