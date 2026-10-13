FROM python:3.13.7-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app/analytics
COPY warehouse/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY proof/proof.py proof/proof.py
COPY proof/sql/ proof/sql/
COPY queries/ queries/
COPY warehouse/load_gold.py warehouse/load_gold.py
RUN mkdir -p /data/warehouse && chown -R 10001:10001 /data
USER 10001:10001
ENTRYPOINT ["python", "warehouse/load_gold.py"]
