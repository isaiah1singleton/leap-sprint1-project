import os
from urllib.parse import quote_plus
from analyst_sso import configure_analyst_sso

SECRET_KEY = os.environ['SUPERSET_SECRET_KEY']
if len(SECRET_KEY) < 32 or not os.environ.get('SUPERSET_DB_PASSWORD'):
    raise ValueError('Set a SUPERSET_SECRET_KEY of at least 32 characters and SUPERSET_DB_PASSWORD')
SQLALCHEMY_DATABASE_URI = (
    'postgresql+psycopg2://superset:'
    + quote_plus(os.environ['SUPERSET_DB_PASSWORD'])
    + '@superset-db:5432/superset'
)
WTF_CSRF_ENABLED = True
FEATURE_FLAGS = {'ALERT_REPORTS': False}
# Disable query-result caching so a generation switch cannot reuse stale data.
DATA_CACHE_CONFIG = {'CACHE_TYPE': 'NullCache'}
CACHE_CONFIG = {'CACHE_TYPE': 'NullCache'}

FLASK_APP_MUTATOR = configure_analyst_sso
