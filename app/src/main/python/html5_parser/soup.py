"""
Shim for html5_parser.soup — returns BeautifulSoup trees using bs4's html.parser.
"""
import bs4


def parse(markup, return_root=True, **kwargs):
    """Parse HTML markup into a BeautifulSoup tree."""
    soup = bs4.BeautifulSoup(markup, 'html.parser')
    if return_root:
        return soup.find('html') or soup
    return soup
