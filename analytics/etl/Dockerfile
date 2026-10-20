FROM python:3.13.7-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app/analytics
COPY proof/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY proof/proof.py proof/proof.py
COPY proof/sql/ proof/sql/
COPY proof/tests/fixture.sql proof/tests/fixture.sql
COPY etl/extract_transform.py etl/extract_transform.py
COPY etl/incremental.py etl/incremental.py
RUN mkdir -p /data/batches /data/state && chown -R 10001:10001 /data
USER 10001:10001
ENTRYPOINT ["python", "etl/extract_transform.py"]
