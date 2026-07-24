"""
Regression tests: converter.convert() extra_args arrive as strings and
must be coerced to match numeric option defaults, not crash on comparison.

Run:  python -m pytest tests/ -v
  or: python -m unittest tests.test_converter_extra_args -v
"""
import os
import shutil
import tempfile
import unittest

from tests.test_convert_all_formats import _make_minimal_epub


class TestConverterExtraArgs(unittest.TestCase):
    """converter.convert() with string-valued numeric extra args."""

    @classmethod
    def setUpClass(cls):
        cls._tmpdir = tempfile.mkdtemp(prefix='econverter_test_')
        cls._epub = os.path.join(cls._tmpdir, 'test.epub')
        _make_minimal_epub(cls._epub)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls._tmpdir, ignore_errors=True)

    def _assert_converted(self, result, out_path):
        self.assertTrue(result['success'], result.get('message'))
        self.assertTrue(os.path.isfile(out_path))
        self.assertGreater(os.path.getsize(out_path), 0)

    def test_base_font_size_string_arg_does_not_crash(self):
        """Used to raise: TypeError: '<' not supported between 'str' and 'float'."""
        import converter

        out_path = os.path.join(self._tmpdir, 'base_font_size.epub')
        result = converter.convert(self._epub, out_path, '--base-font-size', '12')
        self._assert_converted(result, out_path)

    def test_margin_string_args_do_not_crash(self):
        """Margins default to float(5.0); string values must be coerced too."""
        import converter

        out_path = os.path.join(self._tmpdir, 'margins.epub')
        result = converter.convert(
            self._epub, out_path,
            '--margin-top', '10', '--margin-bottom', '10',
            '--margin-left', '8', '--margin-right', '8',
        )
        self._assert_converted(result, out_path)


if __name__ == '__main__':
    unittest.main()
