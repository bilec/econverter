"""
Shim for html5-parser using lxml.html.
html5-parser requires native compilation which Chaquopy can't do.
This provides the same parse() interface using lxml's HTML parser.
"""
# ponytail: shim replaces native html5-parser; upgrade if parsing edge cases appear
from lxml import html


def parse(markup, treebuilder='lxml', namespace_elements=False,
          maybe_xhtml=False, sanitize_names=True, stack_size=16384,
          fragment_context=None, **kwargs):
    """Parse HTML and return an lxml Element tree, matching html5_parser.parse() API."""
    if isinstance(markup, str):
        markup = markup.encode('utf-8')
    if fragment_context:
        return html.fragments_fromstring(markup.decode('utf-8'))
    doc = html.document_fromstring(markup)
    return doc
