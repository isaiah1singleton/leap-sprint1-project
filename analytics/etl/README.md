# Split full-load pipeline

Requires Python 3.11+ and dependencies from `analytics/proof/requirements.txt`.
Run from the repository root. The verified standalone proof remains available.

## Offline verification (PowerShell)

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s analytics/proof/tests -v
.\.venv\Scripts\python.exe analytics/etl/extract_transform.py --fixture --output "$env:TEMP\leap-batch-001"
.\.venv\Scripts\python.exe analytics/warehouse/load_gold.py --batch "$env:TEMP\leap-batch-001" --db "$env:TEMP\leap-gold.duckdb"
```

On macOS/Linux use `.venv/bin/python` and `/tmp/` paths instead.

## PostgreSQL extraction

Set the same POSTGRES_* environment variables as the standalone proof, then
run the ETL command **without `--fixture`**. Environment files are not loaded
automatically. PostgreSQL is accessed only through a read-only repeatable-read
transaction. No source DDL, data changes, or Docker operations occur.

ETL retains five Bronze Parquet projections, produces Silver Parquet, and
publishes a manifest containing the fill count and Silver SHA-256 checksum.
It uses its own in-memory DuckDB engine; it never opens the Gold database.
Each output directory must be new. A temporary sibling directory is renamed
after successful validation, so consumers do not see incomplete batches.
Treat published batch directories as immutable trusted inputs.

The warehouse command requires only the batch and a dedicated output database,
not PostgreSQL credentials. It validates the manifest and checksum, loads
Silver, then creates all five dimensions and the fact in one transaction.
Validation or reporting failures roll back the load. It checkpoints and closes
the database after success. Replaying a batch replaces the full snapshot.
Do not allow concurrent processes to read/write the working Gold file.

All timezone, precision, fill-quantity and full-refresh surrogate-key limitations
from `analytics/proof/README.md` still apply. The warehouse stores a local Silver
copy for validation/lineage; Bronze extraction and Silver transformation run
only in ETL. The two commands share the existing proof SQL/helpers, so future
Docker build contexts must include those files and `analytics/queries`.

Tests establish parity with the offline proof, not a new live PostgreSQL run.
See `analytics/DOCKER.md` for container packaging. Superset integration,
scheduling, incremental loading, and immutable Gold snapshot publication
are not implemented yet.
