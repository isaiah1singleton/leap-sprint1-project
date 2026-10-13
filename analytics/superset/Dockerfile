FROM apache/superset:5.0.0
USER root
COPY superset/requirements.txt /tmp/analytics-requirements.txt
RUN uv pip install --python /app/.venv/bin/python -r /tmp/analytics-requirements.txt
COPY superset/superset_config.py /app/pythonpath/superset_config.py
COPY superset/bootstrap.py /app/analytics-bootstrap.py
COPY superset/initialize.py /app/analytics-initialize.py
ENV SUPERSET_CONFIG_PATH=/app/pythonpath/superset_config.py
USER superset
