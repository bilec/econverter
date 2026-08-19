"""Regression tests for PDF input conversion."""

import os
import shutil
import tempfile
import unittest
import zipfile
from unittest.mock import patch

from reportlab.lib.pagesizes import letter
from reportlab.pdfgen import canvas


def _make_minimal_pdf(path):
    document = canvas.Canvas(path, pagesize=letter)
    document.drawString(50, 750, "Test PDF Document")
    document.drawString(50, 700, "This PDF contains extractable text.")
    document.drawString(50, 650, "Café")
    document.showPage()
    document.save()


class TestPDFInput(unittest.TestCase):
    """Text-based PDFs can be converted to EPUB."""

    @classmethod
    def setUpClass(cls):
        cls._tmpdir = tempfile.mkdtemp(prefix="econverter_test_pdf_")
        cls._pdf = os.path.join(cls._tmpdir, "test.pdf")
        _make_minimal_pdf(cls._pdf)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls._tmpdir, ignore_errors=True)

    def test_pdf_to_epub_succeeds(self):
        import converter

        output_path = os.path.join(self._tmpdir, "output.epub")
        result = converter.convert(self._pdf, output_path)

        self.assertTrue(result["success"], result["message"])
        self.assertTrue(os.path.isfile(output_path))
        self.assertGreater(os.path.getsize(output_path), 0)
        with zipfile.ZipFile(output_path) as archive:
            self.assertIn("mimetype", archive.namelist())
            self.assertIn("META-INF/container.xml", archive.namelist())
            content = "".join(
                archive.read(name).decode("utf-8") for name in archive.namelist() if name.endswith(".html")
            )
        self.assertIn("Café", content)

    def test_html_preserves_czech_text_without_page_headings(self):
        from ebook_converter.ebooks.pdf.pdftohtml import pypdf_to_html

        page = type("Page", (), {"extract_text": lambda self: "Český text"})()
        reader = type("Reader", (), {"pages": [page]})()
        with patch("pypdf.PdfReader", return_value=reader):
            pypdf_to_html(self._tmpdir, self._pdf)

        with open(os.path.join(self._tmpdir, "index.html"), encoding="utf-8") as index:
            html = index.read()
        self.assertIn("Český text", html)
        self.assertNotIn("<h2>", html)

    def test_html_recovery_preserves_czech_text(self):
        from ebook_converter.ebooks.oeb.parse_utils import parse_html

        document = parse_html(
            "<html><body><p>áľščÁĽŠČ</p></body></html>",
            decoder=lambda data: data,
        )

        self.assertIn("áľščÁĽŠČ", " ".join(document.itertext()))

    def test_missing_pdfinfo_uses_generic_metadata(self):
        from ebook_converter.ebooks.metadata.pdf import read_info

        with patch("subprocess.check_output", side_effect=FileNotFoundError):
            self.assertEqual(read_info(self._tmpdir, get_cover=False), {})


if __name__ == "__main__":
    unittest.main()
