import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
if sys.platform != 'win32':
    from scheduler.cleanup import cleanup


@unittest.skipIf(sys.platform == 'win32', 'Host cleanup coordinator targets Linux')
class CleanupCoordinatorTests(unittest.TestCase):
    def test_dry_run_uses_detected_compose_and_protects_pending_generation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'pending.json').write_text(json.dumps({'generation':'pending-001'}))
            with patch('scheduler.cleanup.compose_command', return_value=['docker','compose']), patch('scheduler.cleanup.subprocess.run') as run:
                run.return_value.stdout = 'ACTIVE_SNAPSHOTS=["active.duckdb"]\n'
                cleanup(root/'.env', root)
                calls = [call.args[0] for call in run.call_args_list]
                self.assertEqual(len(calls), 2)
                self.assertTrue(all(command[:2] == ['docker','compose'] for command in calls))
                self.assertIn('active.duckdb', calls[-1])
                self.assertIn('pending-001', calls[-1])
                self.assertNotIn('--apply', calls[-1])
                self.assertFalse(any('stop' in command for command in calls))

    def test_metadata_failure_restores_superset_without_running_deletion(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            calls = []
            def execute(command, **kwargs):
                calls.append(command)
                if 'superset-bootstrap' in command:
                    raise subprocess.CalledProcessError(1, command)
                return subprocess.CompletedProcess(command, 0)
            with patch('scheduler.cleanup.compose_command', return_value=['docker-compose']), patch('scheduler.cleanup.subprocess.run', side_effect=execute):
                with self.assertRaises(subprocess.CalledProcessError):
                    cleanup(root/'.env', root, apply=True)
            self.assertIn('stop', calls[0])
            self.assertIn('--force-recreate', calls[-1])
            self.assertFalse(any('retention' in command for command in calls))
