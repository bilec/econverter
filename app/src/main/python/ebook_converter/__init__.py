import importlib.resources
import mimetypes
import tempfile


_mime_tmp = tempfile.NamedTemporaryFile(suffix='.types', delete=False)
_mime_tmp.write((importlib.resources.files('ebook_converter') /
                 'data/mime.types').read_bytes())
_mime_tmp.close()
mimetypes.init([_mime_tmp.name])
