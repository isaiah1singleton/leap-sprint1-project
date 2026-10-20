"""Cover host/entry-point scripts that unit tests do not otherwise import."""
import ast
from pathlib import Path
import unittest


class PythonSyntaxTests(unittest.TestCase):
    def test_all_analytics_python_files_parse(self):
        analytics = Path(__file__).resolve().parents[2]
        paths = sorted(analytics.rglob('*.py'))
        self.assertTrue(paths)
        for path in paths:
            with self.subTest(path=str(path.relative_to(analytics))):
                ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
