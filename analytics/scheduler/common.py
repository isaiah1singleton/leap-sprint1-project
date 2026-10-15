"""Detect either supported Compose CLI without relying on shell aliases."""
import shutil
import subprocess


def compose_command():
    if shutil.which('docker'):
        result=subprocess.run(['docker','compose','version'],capture_output=True,timeout=15)
        if result.returncode==0:
            return ['docker','compose']
    if shutil.which('docker-compose'):
        return ['docker-compose']
    raise RuntimeError('Install Docker Compose v2 or docker-compose and ensure it is on PATH')
