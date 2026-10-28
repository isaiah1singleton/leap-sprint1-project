import json
import os
from pathlib import Path
import subprocess
import tempfile

directory = Path(__file__).resolve().parent

result = subprocess.run([
    "aws", "secretsmanager", "get-secret-value",
    "--region", "us-east-1",
    "--secret-id",
    "arn:aws:secretsmanager:us-east-1:314662805698:secret:team8/transactions/backend-T8sMGR",
    "--version-stage", "AWSCURRENT",
    "--output", "json",
    "--no-cli-pager"
], capture_output=True, text=True)

if result.returncode:
    raise SystemExit(result.stderr)

secret = json.loads(json.loads(result.stdout)["SecretString"])

if secret["username"] != "leap_trading":
    raise SystemExit("Expected secret username leap_trading.")

override = {
    "services": {
        "backend": {
            "environment": {
                # Escape dollar signs for Compose interpolation.
                "SPRING_DATASOURCE_PASSWORD":
                    secret["password"].replace("$", "$$"),
                # Pause background writers during validation.
                "ORDER_EXECUTION_ENABLED": "true",
                "PORTFOLIO_HISTORY_ENABLED": "true"
            }
        }
    }
}

# Create a private temporary configuration in memory-backed storage.
descriptor, filename = tempfile.mkstemp(
    prefix="team8-rds-", suffix=".json", dir="/dev/shm"
)

try:
    with os.fdopen(descriptor, "w") as temporary:
        json.dump(override, temporary)

    subprocess.run([
        "sudo", "-n", "docker", "compose",
        "--project-directory", str(directory),
        "--env-file", str(directory / ".env"),
        "-f", str(directory / "compose.yml"),
        "-f", filename,
        "up", "-d", "--no-deps", "backend"
    ], cwd=directory, check=True)
finally:
    os.unlink(filename)

print("Backend started with its Secrets Manager credential.")
print("Background transaction jobs remain paused for validation.")
