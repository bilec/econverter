"""
Upgrade an EPUB2 book to EPUB3.
"""
from ebook_converter.ebooks.oeb.polish.container import OEB_FONTS
from ebook_converter.ebooks.oeb.polish.opf import get_book_language
from ebook_converter.ebooks.oeb.polish.toc import (
    commit_nav_toc, find_existing_ncx_toc, get_landmarks, get_toc)
from ebook_converter.ebooks.oeb.polish.utils import guess_type


# Map EPUB2 <guide> reference types to their EPUB3 epub:type equivalents.
# See http://www.idpf.org/epub/301/spec/epub-contentdocs.html#sec-epub-type-attribute
guide_epubtype_map = {
    'acknowledgements'   : 'acknowledgments',
    'other.afterword'    : 'afterword',
    'other.appendix'     : 'appendix',
    'other.backmatter'   : 'backmatter',
    'bibliography'       : 'bibliography',
    'text'               : 'bodymatter',
    'other.chapter'      : 'chapter',
    'colophon'           : 'colophon',
    'other.conclusion'   : 'conclusion',
    'other.contributors' : 'contributors',
    'copyright-page'     : 'copyright-page',
    'cover'              : 'cover',
    'dedication'         : 'dedication',
    'other.division'     : 'division',
    'epigraph'           : 'epigraph',
    'other.epilogue'     : 'epilogue',
    'other.errata'       : 'errata',
    'other.footnotes'    : 'footnotes',
    'foreword'           : 'foreword',
    'other.frontmatter'  : 'frontmatter',
    'glossary'           : 'glossary',
    'other.halftitlepage': 'halftitlepage',
    'other.imprint'      : 'imprint',
    'other.imprimatur'   : 'imprimatur',
    'index'              : 'index',
    'other.introduction' : 'introduction',
    'other.landmarks'    : 'landmarks',
    'other.loa'          : 'loa',
    'loi'                : 'loi',
    'lot'                : 'lot',
    'other.lov'          : 'lov',
    'notes'              : '',
    'other.notice'       : 'notice',
    'other.other-credits': 'other-credits',
    'other.part'         : 'part',
    'other.preamble'     : 'preamble',
    'preface'            : 'preface',
    'other.prologue'     : 'prologue',
    'other.rearnotes'    : 'rearnotes',
    'other.subchapter'   : 'subchapter',
    'title-page'         : 'titlepage',
    'toc'                : 'toc',
    'other.volume'       : 'volume',
    'other.warning'      : 'warning',
}


def fix_font_mime_types(container):
    changed = False
    for item in container.opf_xpath(
            '//opf:manifest/opf:item[@href and @media-type]'):
        mt = item.get('media-type') or ''
        if mt.lower() in OEB_FONTS:
            name = container.href_to_name(item.get('href'), container.opf_name)
            item.set('media-type', guess_type(name))
            changed = True
    return changed


def create_nav(container, toc, landmarks, previous_nav=None):
    lang = get_book_language(container)
    if landmarks:
        for entry in landmarks:
            entry['type'] = guide_epubtype_map.get(
                entry['type'].lower(), entry['type'])
    commit_nav_toc(container, toc, lang=lang, landmarks=landmarks,
                   previous_nav=previous_nav)


def epub_2_to_3(container, report, previous_nav=None, remove_ncx=True):
    toc = get_toc(container)
    toc_name = find_existing_ncx_toc(container)
    if toc_name and remove_ncx:
        container.remove_item(toc_name)
        for spine in container.opf_xpath('./opf:spine'):
            spine.attrib.pop('toc', None)
    landmarks = get_landmarks(container)
    for guide in container.opf_xpath('./opf:guide'):
        guide.getparent().remove(guide)
    create_nav(container, toc, landmarks, previous_nav)
    container.opf.set('version', '3.0')
    if fix_font_mime_types(container):
        container.refresh_mime_map()
    container.dirty(container.opf_name)
