"""
Minimal EPUB cover/titlepage helpers needed for the EPUB2 -> EPUB3 upgrade
path (see :mod:`ebook_converter.ebooks.oeb.polish.upgrade`).
"""
from ebook_converter.ebooks.oeb.base import OEB_DOCS


def find_cover_page(container):
    """Find the name of the document marked as the cover/titlepage in the
    OPF (guide reference of type "cover" for EPUB2, or the
    "calibre:title-page" property / cover landmark for EPUB3)."""
    ver = container.opf_version_parsed
    mm = container.mime_map
    if ver.major < 3:
        guide_type_map = container.guide_type_map
        for ref_type, name in guide_type_map.items():
            if ref_type.lower() == 'cover' and mm.get(name, '').lower() in OEB_DOCS:
                return name
    else:
        for name in container.manifest_items_with_property('calibre:title-page'):
            return name
        from ebook_converter.ebooks.oeb.polish.toc import get_landmarks
        for landmark in get_landmarks(container):
            if (landmark['type'] == 'cover' and
                    mm.get(landmark['dest'], '').lower() in OEB_DOCS):
                return landmark['dest']


def fix_conversion_titlepage_links_in_nav(container):
    """After converting an EPUB2 book (with a guide-referenced titlepage) to
    EPUB3, the generated nav.xhtml landmarks may contain placeholder links
    for the removed titlepage. Point them at the actual cover page."""
    from ebook_converter.ebooks.oeb.polish.toc import find_existing_nav_toc

    cover_page_name = find_cover_page(container)
    if not cover_page_name:
        return
    nav_page_name = find_existing_nav_toc(container)
    if not nav_page_name:
        return
    for elem in container.parsed(nav_page_name).xpath(
            '//*[@data-calibre-removed-titlepage]'):
        elem.attrib.pop('data-calibre-removed-titlepage')
        elem.set('href', container.name_to_href(cover_page_name, nav_page_name))
    container.dirty(nav_page_name)
