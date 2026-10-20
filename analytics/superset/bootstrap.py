"""Provision BR-16 assets for the pinned Superset 5.0 image. Run with web stopped."""
import json
import os
import re
from pathlib import Path

from sqlalchemy import create_engine, inspect, text


def main():
    generation = os.environ['SNAPSHOT_ID']
    if not re.fullmatch(r'[A-Za-z0-9_-]+', generation):
        raise ValueError('SNAPSHOT_ID must contain only letters, digits, underscores or hyphens')
    path = Path('/data/published') / f'{generation}.duckdb'
    if not path.is_file():
        raise FileNotFoundError(f'Publish {path} before provisioning dashboards')
    uri = 'duckdb:///' + str(path)
    engine = create_engine(uri, connect_args={'read_only': True})
    names = ('activity_by_period', 'activity_by_instrument', 'activity_by_client_segment')
    columns = {}
    try:
        inspector = inspect(engine)
        for name in names:
            columns[name] = inspector.get_columns(name, schema='gold')
            with engine.connect() as connection:
                connection.execute(text(f'SELECT * FROM gold.{name}')).fetchall()
    finally:
        engine.dispose()

    from superset.app import create_app
    app = create_app()
    with app.app_context():
        from superset import db, security_manager
        from superset.models.core import Database
        from superset.connectors.sqla.models import SqlaTable, TableColumn
        from superset.models.slice import Slice
        from superset.models.dashboard import Dashboard

        admin = security_manager.find_user(username=os.environ.get('SUPERSET_ADMIN_USERNAME', 'admin'))
        if not admin:
            raise ValueError('Run superset-init first')
        database = db.session.query(Database).filter_by(database_name='LEAP Gold').one_or_none()
        if database is None:
            database = Database(database_name='LEAP Gold')
            db.session.add(database)
        database.sqlalchemy_uri = uri
        database.extra = json.dumps({'engine_params': {'connect_args': {'read_only': True}}})
        database.allow_dml = database.allow_ctas = database.allow_cvas = False
        database.expose_in_sqllab = False
        database.cache_timeout = -1
        db.session.flush()
        charts = []
        for name in names:
            dataset = db.session.query(SqlaTable).filter_by(database_id=database.id, schema='gold', table_name=name).one_or_none()
            if dataset is None:
                dataset = SqlaTable(database=database, schema='gold', table_name=name, owners=[admin])
                dataset.columns = [TableColumn(column_name=c['name'], type=str(c['type'])) for c in columns[name]]
                db.session.add(dataset)
            db.session.flush()
            title = 'BR-16: ' + name.removeprefix('activity_by_').replace('_', ' ').title()
            chart = db.session.query(Slice).filter_by(slice_name=title).one_or_none()
            if chart is None:
                chart = Slice(slice_name=title, owners=[admin])
                db.session.add(chart)
            chart.datasource_id, chart.datasource_type, chart.viz_type = dataset.id, 'table', 'table'
            chart.cache_timeout = -1
            chart.params = json.dumps({
                'viz_type': 'table', 'datasource': f'{dataset.id}__table',
                'query_mode': 'raw', 'all_columns': [c['name'] for c in columns[name]],
                'groupby': [], 'metrics': [], 'adhoc_filters': [], 'order_by_cols': [],
                'row_limit': 10000, 'page_length': 20, 'time_range': 'No filter',
            })
            db.session.flush()
            charts.append(chart)
        dashboard = db.session.query(Dashboard).filter_by(slug='leap-br16').one_or_none()
        if dashboard is None:
            dashboard = Dashboard(slug='leap-br16', dashboard_title='LEAP BR-16 Trading Analytics', owners=[admin])
            db.session.add(dashboard)
        dashboard.slices, dashboard.published = charts, True
        layout = {
            'DASHBOARD_VERSION_KEY': 'v2',
            'ROOT_ID': {'id': 'ROOT_ID', 'type': 'ROOT', 'children': ['GRID_ID']},
            'GRID_ID': {'id': 'GRID_ID', 'type': 'GRID', 'children': [] , 'parents': ['ROOT_ID']},
            'HEADER_ID': {'id': 'HEADER_ID', 'type': 'HEADER', 'meta': {'text': dashboard.dashboard_title}},
        }
        for index, chart in enumerate(charts):
            row, node = f'ROW-{index}', f'CHART-{chart.id}'
            layout['GRID_ID']['children'].append(row)
            layout[row] = {'id': row, 'type': 'ROW', 'children': [node], 'parents': ['ROOT_ID', 'GRID_ID'], 'meta': {'background': 'BACKGROUND_TRANSPARENT'}}
            layout[node] = {'id': node, 'type': 'CHART', 'children': [], 'parents': ['ROOT_ID', 'GRID_ID', row], 'meta': {'chartId': chart.id, 'width': 12, 'height': 50, 'sliceName': chart.slice_name}}
        dashboard.position_json = json.dumps(layout)
        dashboard.json_metadata = json.dumps({'refresh_frequency': 0})
        db.session.commit()
    print(f'BR-16 dashboard provisioned against {generation}; start/recreate Superset now.')


if __name__ == '__main__':
    main()
