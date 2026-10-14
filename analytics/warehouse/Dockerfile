FROM python:3.13.7-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app/analytics
COPY warehouse/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY proof/proof.py proof/proof.py
COPY proof/sql/ proof/sql/
COPY queries/ queries/
COPY warehouse/load_gold.py warehouse/load_gold.py
COPY warehouse/stable_keys.py warehouse/stable_keys.py
COPY warehouse/delta.py warehouse/delta.py
COPY warehouse/publish.py warehouse/publish.py
COPY warehouse/retention.py warehouse/retention.py
RUN mkdir -p /data/warehouse /data/published && chown -R 10001:10001 /data
USER 10001:10001
ENTRYPOINT ["python", "warehouse/load_gold.py"]
