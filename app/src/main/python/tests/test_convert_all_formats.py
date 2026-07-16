"""
Smoke tests: convert a minimal EPUB to every supported output format.

Each test creates a Plumber, runs the conversion, and asserts the output
file exists and is non-empty.  Failures surface missing modules, missing
data files, and runtime crashes — the most common bugs right now.

Run:  python -m pytest tests/ -v
  or: python -m unittest tests.test_convert_all_formats -v
"""
import os
import shutil
import tempfile
import unittest
import zipfile

# All output formats from registered plugins.
OUTPUT_FORMATS = [
    'azw3',
    'docx',
    'epub',
    'fb2',
    'html',
    'htmlz',
    'lrf',
    'mobi',
    'oeb',
    'txt',
    'txtz',
]


def _make_minimal_epub(path):
    """Create the smallest valid EPUB 2 file possible."""
    container_xml = (
        '<?xml version="1.0"?>'
        '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
        '<rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>'
        '</rootfiles></container>'
    )
    content_opf = (
        '<?xml version="1.0"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="2.0">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        '<dc:title>Test Book</dc:title>'
        '<dc:language>en</dc:language>'
        '<dc:identifier id="uid">test-book-001</dc:identifier>'
        '<dc:creator>Test Author</dc:creator>'
        '</metadata>'
        '<manifest>'
        '<item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>'
        '</manifest>'
        '<spine><itemref idref="ch1"/></spine>'
        '</package>'
    )
    chapter_xhtml = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<html xmlns="http://www.w3.org/1999/xhtml">'
        '<head><title>Chapter 1</title></head>'
        '<body><h1>Chapter 1</h1><p>Hello, world.</p></body>'
        '</html>'
    )
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('mimetype', 'application/epub+zip',
                    compress_type=zipfile.ZIP_STORED)
        zf.writestr('META-INF/container.xml', container_xml)
        zf.writestr('content.opf', content_opf)
        zf.writestr('chapter1.xhtml', chapter_xhtml)


class TestConvertAllFormats(unittest.TestCase):
    """Smoke-test EPUB -> every output format."""

    @classmethod
    def setUpClass(cls):
        cls._tmpdir = tempfile.mkdtemp(prefix='econverter_test_')
        cls._epub = os.path.join(cls._tmpdir, 'test.epub')
        _make_minimal_epub(cls._epub)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls._tmpdir, ignore_errors=True)

    def _convert(self, fmt):
        from ebook_converter import logging
        from ebook_converter.ebooks.conversion.plumber import Plumber

        out_path = os.path.join(self._tmpdir, f'output.{fmt}')
        # Clean previous run if any
        if os.path.exists(out_path):
            if os.path.isdir(out_path):
                shutil.rmtree(out_path)
            else:
                os.remove(out_path)

        plumber = Plumber(self._epub, out_path, logging.default_log)
        plumber.run()

        # oeb output is a directory
        if fmt == 'oeb':
            self.assertTrue(os.path.isdir(out_path),
                            f'OEB output directory not created: {out_path}')
            self.assertTrue(os.listdir(out_path),
                            f'OEB output directory is empty: {out_path}')
        else:
            self.assertTrue(os.path.isfile(out_path),
                            f'Output file not created: {out_path}')
            self.assertGreater(os.path.getsize(out_path), 0,
                               f'Output file is empty: {out_path}')


def _make_test(fmt):
    def test(self):
        self._convert(fmt)
    test.__name__ = f'test_epub_to_{fmt}'
    test.__doc__ = f'Convert minimal EPUB to {fmt.upper()}'
    return test


for _fmt in OUTPUT_FORMATS:
    setattr(TestConvertAllFormats, f'test_epub_to_{_fmt}', _make_test(_fmt))


if __name__ == '__main__':
    unittest.main()
