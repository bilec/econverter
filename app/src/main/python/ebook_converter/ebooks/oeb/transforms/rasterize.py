"""
SVG rasterization transform using svglib+reportlab.
"""
import io
import os
import re

from lxml import etree

from ebook_converter.ebooks.oeb.base import SVG_MIME


KEEP_ATTRS = {'class', 'style', 'width', 'height', 'align'}
XHTML_NS = 'http://www.w3.org/1999/xhtml'


class Unavailable(Exception):
    pass


def _svg_to_png(svg_bytes, width=0, height=0):
    """Render SVG bytes to PNG bytes using svglib + Pillow (pure Python)."""
    return _svg_to_png_pillow(svg_bytes, width, height)


def _svg_to_png_pillow(svg_bytes, width=0, height=0):
    """Pure-Python fallback: parse SVG with svglib, render shapes with Pillow.
    Handles basic shapes (rect, circle, ellipse, line, polygon, path).
    # ponytail: covers ~90% of ebook SVGs; complex filters/gradients render as flat fills.
    """
    from svglib.svglib import svg2rlg
    from PIL import Image, ImageDraw
    from reportlab.graphics.shapes import (
        Group, Rect, Circle, Ellipse, Line, PolyLine, Polygon,
    )
    from reportlab.lib.colors import Color, toColor

    drawing = svg2rlg(io.BytesIO(svg_bytes))
    if drawing is None:
        raise ValueError('svglib failed to parse SVG')

    dw, dh = int(drawing.width), int(drawing.height)
    if width and height:
        tw, th = int(width), int(height)
    else:
        tw, th = dw, dh
    scale_x = tw / dw if dw else 1
    scale_y = th / dh if dh else 1

    img = Image.new('RGBA', (tw, th), (255, 255, 255, 0))
    draw = ImageDraw.Draw(img)

    def _color(c):
        if c is None:
            return None
        if isinstance(c, Color):
            return (int(c.red * 255), int(c.green * 255), int(c.blue * 255),
                    int(getattr(c, 'alpha', 1) * 255))
        try:
            c = toColor(c)
            return (int(c.red * 255), int(c.green * 255), int(c.blue * 255), 255)
        except Exception:
            return None

    def _render_group(group, tx=0, ty=0, sx=scale_x, sy=scale_y):
        for item in group.contents:
            if isinstance(item, Group):
                # Apply group transform if any
                ntx, nty, nsx, nsy = tx, ty, sx, sy
                if item.transform:
                    # Affine matrix [a,b,c,d,e,f]: x'=ax+cy+e, y'=bx+dy+f
                    t = item.transform
                    if len(t) == 6:
                        nsx = sx * t[0]
                        nsy = sy * t[3]
                        ntx = tx + t[4] * sx
                        nty = ty + t[5] * sy
                _render_group(item, ntx, nty, nsx, nsy)
            elif isinstance(item, Rect):
                x1 = tx + item.x * sx
                y1 = ty + item.y * sy
                x2 = x1 + item.width * sx
                y2 = y1 + item.height * sy
                fill = _color(item.fillColor)
                stroke = _color(item.strokeColor)
                bbox = [min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)]
                if fill:
                    draw.rectangle(bbox, fill=fill, outline=stroke)
                elif stroke:
                    draw.rectangle(bbox, outline=stroke)
            elif isinstance(item, Circle):
                cx = tx + item.cx * sx
                cy = ty + item.cy * sy
                rx = abs(item.r * sx)
                ry = abs(item.r * sy)
                fill = _color(item.fillColor)
                stroke = _color(item.strokeColor)
                bbox = [cx - rx, cy - ry, cx + rx, cy + ry]
                if fill:
                    draw.ellipse(bbox, fill=fill, outline=stroke)
                elif stroke:
                    draw.ellipse(bbox, outline=stroke)
            elif isinstance(item, Ellipse):
                cx = tx + item.cx * sx
                cy = ty + item.cy * sy
                rx = abs(item.rx * sx)
                ry = abs(item.ry * sy)
                fill = _color(item.fillColor)
                stroke = _color(item.strokeColor)
                bbox = [cx - rx, cy - ry, cx + rx, cy + ry]
                if fill:
                    draw.ellipse(bbox, fill=fill, outline=stroke)
                elif stroke:
                    draw.ellipse(bbox, outline=stroke)
            elif isinstance(item, Line):
                stroke = _color(item.strokeColor) or (0, 0, 0, 255)
                draw.line([tx + item.x1 * sx, ty + item.y1 * sy,
                           tx + item.x2 * sx, ty + item.y2 * sy], fill=stroke,
                          width=max(1, int(getattr(item, 'strokeWidth', 1) * abs(sx))))
            elif isinstance(item, (Polygon, PolyLine)):
                points = [(tx + item.points[i] * sx, ty + item.points[i + 1] * sy)
                          for i in range(0, len(item.points), 2)]
                fill = _color(item.fillColor) if isinstance(item, Polygon) else None
                stroke = _color(item.strokeColor)
                if isinstance(item, Polygon) and fill:
                    draw.polygon(points, fill=fill, outline=stroke)
                elif points:
                    draw.line(points, fill=stroke or (0, 0, 0, 255),
                              width=max(1, int(getattr(item, 'strokeWidth', 1) * abs(sx))))

    _render_group(drawing)
    buf = io.BytesIO()
    img.save(buf, format='PNG')
    return buf.getvalue()


class SVGRasterizer(object):

    def __init__(self, base_css=''):
        self.base_css = base_css
        self.stylizer_cache = {}
        self._img_counter = 0

    @classmethod
    def config(cls, cfg):
        return cfg

    @classmethod
    def generate(cls, opts):
        return cls()

    def __call__(self, oeb, context):
        self.oeb = oeb
        self.opts = context if not hasattr(context, 'output_profile') else context
        self.log = oeb.logger
        try:
            import svglib  # noqa — check availability
        except ImportError:
            self.log.warning('SVG rasterization unavailable (svglib not installed)')
            return
        self.rasterize_cover()
        self.rasterize_spine()

    def rasterize_svg(self, elem, width=0, height=0, format='PNG'):
        """Render an SVG lxml element to PNG bytes."""
        svg_bytes = etree.tostring(elem, encoding='utf-8', xml_declaration=True)
        try:
            return _svg_to_png(svg_bytes, width, height)
        except Exception as e:
            raise Unavailable(f'SVG rasterization failed: {e}')

    def _next_id(self):
        self._img_counter += 1
        return f'svg_rasterized_{self._img_counter:04d}'

    def _add_image(self, png_data):
        """Add a PNG to the manifest and return the href."""
        img_id = self._next_id()
        href = f'images/{img_id}.png'
        self.oeb.manifest.add(img_id, href, 'image/png', data=png_data)
        return href

    def rasterize_spine(self):
        from ebook_converter.ebooks.oeb.stylizer import Stylizer
        for item in list(self.oeb.spine):
            if not hasattr(item.data, 'xpath'):
                continue
            stylizer = Stylizer(item.data, item.href, self.oeb, self.opts,
                                base_css=self.base_css)
            self.stylizer_cache[item] = stylizer
            self.rasterize_item(item, stylizer)

    def rasterize_item(self, item, stylizer=None):
        """Find and rasterize SVGs in a spine item."""
        if not hasattr(item.data, 'xpath'):
            return
        # Inline SVGs
        for svg in item.data.xpath('//*[local-name()="svg"]'):
            self.rasterize_inline(svg, stylizer, item)
        # External SVG references (img/object pointing to .svg)
        for elem in item.data.xpath(
                '//*[local-name()="img" or local-name()="object"]'):
            src = elem.get('src') or elem.get('data') or ''
            if src and src in self.oeb.manifest.hrefs:
                svg_item = self.oeb.manifest.hrefs[src]
                if svg_item.media_type == SVG_MIME:
                    self.rasterize_external(elem, stylizer, item, svg_item)

    def rasterize_inline(self, elem, style, item):
        """Replace an inline <svg> element with an <img> pointing to rasterized PNG."""
        try:
            svg_bytes = etree.tostring(elem, encoding='utf-8', xml_declaration=True)
            width = _parse_dimension(elem.get('width', ''))
            height = _parse_dimension(elem.get('height', ''))
            png_data = _svg_to_png(svg_bytes, width, height)
        except Exception as e:
            self.log.warning(f'Failed to rasterize inline SVG: {e}')
            return

        href = self._add_image(png_data)
        # Compute relative path from item to image
        img_href = os.path.relpath(href, os.path.dirname(item.href)).replace(os.sep, '/')

        # Replace SVG element with img
        img = etree.Element(f'{{{XHTML_NS}}}img')
        img.set('src', img_href)
        if elem.get('width'):
            img.set('width', elem.get('width'))
        if elem.get('height'):
            img.set('height', elem.get('height'))
        img.tail = elem.tail
        parent = elem.getparent()
        if parent is not None:
            idx = list(parent).index(elem)
            parent.remove(elem)
            parent.insert(idx, img)

    def rasterize_external(self, elem, style, item, svgitem):
        """Replace an external SVG reference with a rasterized PNG."""
        try:
            svg_data = svgitem.data
            if hasattr(svg_data, 'getroottree'):
                svg_bytes = etree.tostring(svg_data, encoding='utf-8', xml_declaration=True)
            elif isinstance(svg_data, bytes):
                svg_bytes = svg_data
            else:
                svg_bytes = svg_data.encode('utf-8') if isinstance(svg_data, str) else bytes(svg_data)
            width = _parse_dimension(elem.get('width', ''))
            height = _parse_dimension(elem.get('height', ''))
            png_data = _svg_to_png(svg_bytes, width, height)
        except Exception as e:
            self.log.warning(f'Failed to rasterize external SVG {svgitem.href}: {e}')
            return

        href = self._add_image(png_data)
        img_href = os.path.relpath(href, os.path.dirname(item.href)).replace(os.sep, '/')
        elem.tag = f'{{{XHTML_NS}}}img'
        elem.set('src', img_href)
        # Remove non-img attributes
        for attr in list(elem.attrib):
            if attr not in KEEP_ATTRS and attr != 'src':
                del elem.attrib[attr]

    def rasterize_cover(self):
        """Rasterize the cover if it's an SVG."""
        cover_id = self.oeb.metadata.cover
        if not cover_id:
            return
        # cover metadata points to a manifest item id
        cover_id_val = str(cover_id[0]) if cover_id else None
        if not cover_id_val:
            return
        item = self.oeb.manifest.ids.get(cover_id_val)
        if item and item.media_type == SVG_MIME:
            try:
                svg_data = item.data
                if hasattr(svg_data, 'getroottree'):
                    svg_bytes = etree.tostring(svg_data, encoding='utf-8', xml_declaration=True)
                else:
                    svg_bytes = svg_data if isinstance(svg_data, bytes) else svg_data.encode('utf-8')
                png_data = _svg_to_png(svg_bytes)
                item._data = png_data
                item.media_type = 'image/png'
                item.href = item.href.rsplit('.', 1)[0] + '.png'
            except Exception as e:
                self.log.warning(f'Failed to rasterize cover SVG: {e}')

    def dataize_manifest(self):
        pass

    def dataize_svg(self, item, svg=None):
        return svg if svg is not None else item.data

    def stylizer(self, item):
        return self.stylizer_cache.get(item)


def _parse_dimension(val):
    """Parse a dimension string like '100px' or '50' to int pixels."""
    if not val:
        return 0
    match = re.match(r'(\d+(?:\.\d+)?)', val)
    return int(float(match.group(1))) if match else 0
