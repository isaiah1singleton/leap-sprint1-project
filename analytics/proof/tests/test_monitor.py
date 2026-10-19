from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
from scheduler.monitor import assess, notify, send_email
from scheduler.common import compose_command


class MonitorTests(unittest.TestCase):
    def test_stale_source_and_missing_record(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            now=datetime(2026,10,14,12,tzinfo=timezone.utc)
            with patch('scheduler.monitor.shutil.disk_usage',return_value=MagicMock(total=100000000000,free=50000000000)):
                issues,_=assess(root,now=now)
                self.assertIn('missing_success',issues)
                (root/'last-success.json').write_text(json.dumps({
                    'completed_at':now.isoformat(),'generation':'hourly-20261014T090000Z-abcd',
                }))
                issues,_=assess(root,now=now)
                self.assertIn('stale',issues) # Retry completion must not hide old source age.
                (root/'last-success.json').write_text(json.dumps({
                    'completed_at':now.isoformat(),'generation':'hourly-20261014T120000Z-abcd',
                }))
                self.assertEqual(assess(root,now=now)[0],{})

    def test_deduplication_recovery_and_delivery_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            config=root/'config.json'
            config.write_text('{}')
            with patch('scheduler.monitor.send_email') as send:
                self.assertEqual(notify(root,config,{},{}),'healthy_baseline')
                self.assertEqual(notify(root,config,{'stale':'old'},{}),'sent')
                self.assertEqual(notify(root,config,{'stale':'older'},{}),'unchanged')
                self.assertEqual(notify(root,config,{},{}),'sent')
                self.assertEqual(send.call_count,2)
                send.side_effect=RuntimeError('delivery failed')
                with self.assertRaises(RuntimeError):
                    notify(root,config,{'disk':'low'}, {})
                send.side_effect=None
                self.assertEqual(notify(root,config,{'disk':'low'},{}),'sent')

    def test_tls_and_both_recipients(self):
        config={'host':'smtp.gmail.com','sender':'sender@gmail.com','username':'sender@gmail.com',
                'password':'fake-test-secret','recipients':['jaysongachangi@gmail.com','isaiahco2022@gmail.com']}
        with patch('scheduler.monitor.smtplib.SMTP') as smtp:
            connection=smtp.return_value.__enter__.return_value
            connection.send_message.return_value={}
            send_email(config,{}, {},test=True)
            connection.starttls.assert_called_once()
            connection.login.assert_called_once_with(config['username'],config['password'])
            self.assertEqual(connection.send_message.call_args.kwargs['to_addrs'],config['recipients'])
            self.assertIn('TEST',connection.send_message.call_args.args[0]['Subject'])

    def test_compose_v2_and_legacy_detection(self):
        with patch('scheduler.common.shutil.which',return_value='/usr/bin/tool'), patch('scheduler.common.subprocess.run') as run:
            run.return_value.returncode=0
            self.assertEqual(compose_command(),['docker','compose'])
            run.return_value.returncode=1
            self.assertEqual(compose_command(),['docker-compose'])
