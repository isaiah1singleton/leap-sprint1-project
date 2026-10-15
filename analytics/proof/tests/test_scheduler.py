import json
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
if sys.platform != 'win32':
    import fcntl
    from scheduler.refresh import refresh


@unittest.skipIf(sys.platform == 'win32', 'The systemd scheduler targets Linux hosts')
class SchedulerTests(unittest.TestCase):
    def setUp(self):
        patcher=patch('scheduler.refresh.compose_command',return_value=['docker','compose'])
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_failed_warehouse_retries_same_batch_without_new_extraction(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            calls = []

            def failed(args, **kwargs):
                calls.append((args,kwargs['env']['BATCH_ID']))
                if 'warehouse' in args:
                    raise subprocess.CalledProcessError(1,args)

            with self.assertRaises(subprocess.CalledProcessError):
                refresh(root/'.env',root,execute=failed)
            pending = json.loads((root/'pending.json').read_text())
            self.assertEqual(pending['stage'],'warehouse')
            retried = []
            refresh(root/'.env',root,execute=lambda args,**kwargs: retried.append((args,kwargs['env']['BATCH_ID'])))
            self.assertFalse((root/'pending.json').exists())
            self.assertTrue(all(generation==pending['generation'] for _,generation in retried))
            self.assertFalse(any('etl' in args for args,_ in retried))
            self.assertTrue((root/'last-success.json').exists())

    def test_activation_failure_restores_web_and_keeps_pending(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            calls=[]
            def failed(args,**kwargs):
                calls.append(args)
                if 'superset-bootstrap' in args:
                    raise subprocess.CalledProcessError(1,args)
            with self.assertRaises(subprocess.CalledProcessError):
                refresh(root/'.env',root,execute=failed)
            self.assertEqual(json.loads((root/'pending.json').read_text())['stage'],'activate')
            self.assertIn('--force-recreate',calls[-1])

    def test_overlapping_run_is_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            with (root/'refresh.lock').open('a') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
                refresh(root/'.env',root,execute=lambda *args,**kwargs: self.fail('Overlapping run executed'))
            self.assertFalse((root/'pending.json').exists())
