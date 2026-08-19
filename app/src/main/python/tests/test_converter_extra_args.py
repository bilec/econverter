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

    def test_remove_text_lines_become_search_patterns(self):
        import converter

        options = converter._parse_extra_args(("--remove-text", "-a II E-\nChapter \\d+"))

        self.assertEqual(options["sr1_search"], r"(?:-a II E-|Chapter \d+)")
        self.assertEqual(options["sr1_replace"], "")

    @classmethod
    def setUpClass(cls):
        cls._tmpdir = tempfile.mkdtemp(prefix="econverter_test_")
        cls._epub = os.path.join(cls._tmpdir, "test.epub")
        _make_minimal_epub(cls._epub)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls._tmpdir, ignore_errors=True)

    def _assert_converted(self, result, out_path):
        self.assertTrue(result["success"], result.get("message"))
        self.assertTrue(os.path.isfile(out_path))
        self.assertGreater(os.path.getsize(out_path), 0)

    def test_base_font_size_string_arg_does_not_crash(self):
        """Used to raise: TypeError: '<' not supported between 'str' and 'float'."""
        import converter

        out_path = os.path.join(self._tmpdir, "base_font_size.epub")
        result = converter.convert(self._epub, out_path, "--base-font-size", "12")
        self._assert_converted(result, out_path)

    def test_margin_string_args_do_not_crash(self):
        """Margins default to float(5.0); string values must be coerced too."""
        import converter

        out_path = os.path.join(self._tmpdir, "margins.epub")
        result = converter.convert(
            self._epub,
            out_path,
            "--margin-top",
            "10",
            "--margin-bottom",
            "10",
            "--margin-left",
            "8",
            "--margin-right",
            "8",
        )
        self._assert_converted(result, out_path)

    def test_boolean_string_args_coerced_correctly(self):
        """Boolean options passed as string 'false' must be coerced to False."""
        from ebook_converter import logging
        from ebook_converter.customize.conversion import OptionRecommendation
        from ebook_converter.ebooks.conversion.plumber import Plumber

        out_path = os.path.join(self._tmpdir, "bool_test.mobi")
        p = Plumber(self._epub, out_path, logging.default_log)
        p.merge_ui_recommendations([("enable_heuristics", "false", OptionRecommendation.MED)])
        self.assertIs(p.get_option_by_name("enable_heuristics").recommended_value, False)

    def test_xml_parse_returns_none_on_invalid_xml(self):
        """safe_xml_fromstring should return None rather than raising XMLSyntaxError on invalid XML or empty input."""
        from ebook_converter.utils.xml_parse import safe_xml_fromstring

        self.assertIsNone(safe_xml_fromstring(""))
        self.assertIsNone(safe_xml_fromstring(b""))
        self.assertIsNone(safe_xml_fromstring("not xml at all"))


if __name__ == "__main__":
    unittest.main()
