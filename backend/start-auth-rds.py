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
    "arn:aws:secretsmanager:us-east-1:314662805698:secret:team8/auth/service-Uj2yMo",
    "--version-stage", "AWSCURRENT",
    "--output", "json",
    "--no-cli-pager"
], capture_output=True, text=True)

if result.returncode:
    raise SystemExit(result.stderr)

secret = json.loads(json.loads(result.stdout)["SecretString"])
if secret["username"] != "leap_auth":
    raise SystemExit("Expected secret username leap_auth.")

override = {
    "services": {
        "auth": {
            "environment": {
                "POSTGRES_AUTH_HOST":
                    "team8-rds-db.c8xuasgw2vz5.us-east-1.rds.amazonaws.com",
                "POSTGRES_AUTH_DB": "leap_auth",
                "POSTGRES_AUTH_USER": secret["username"],
                "POSTGRES_AUTH_PASSWORD":
                    secret["password"].replace("$", "$$")
            },
            "volumes": [
                "/home/ubuntu/rds-certs/global-bundle.pem:"
                "/certs/global-bundle.pem:ro"
            ]
        }
    }
}

descriptor, filename = tempfile.mkstemp(
    prefix="team8-auth-", suffix=".json", dir="/dev/shm"
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
        "up", "-d", "--no-deps", "auth"
    ], cwd=directory, check=True)
finally:
    os.unlink(filename)

print("Auth service started with its Secrets Manager credential.")
