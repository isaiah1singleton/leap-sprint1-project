"""Initialize Superset metadata and create an admin without logging a password."""
import os
import subprocess

if not os.environ.get('SUPERSET_ADMIN_PASSWORD'):
    raise ValueError('Set SUPERSET_ADMIN_PASSWORD before initialization')

subprocess.run(['superset', 'db', 'upgrade'], check=True)
subprocess.run(['superset', 'init'], check=True)

from superset.app import create_app
from superset import security_manager

app = create_app()
with app.app_context():
    username = os.environ.get('SUPERSET_ADMIN_USERNAME', 'admin')
    if not security_manager.find_user(username=username):
        user = security_manager.add_user(
            username=username, first_name='Analytics', last_name='Admin',
            email=os.environ.get('SUPERSET_ADMIN_EMAIL', 'admin@example.test'),
            role=security_manager.find_role('Admin'),
            password=os.environ['SUPERSET_ADMIN_PASSWORD'],
        )
        if not user:
            raise RuntimeError('Admin creation failed')
subprocess.run(['superset', 'init'], check=True)
