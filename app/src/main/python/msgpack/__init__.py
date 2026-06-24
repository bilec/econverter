"""
Pure-Python msgpack shim for Chaquopy (no C extension available).
ponytail: minimal impl covering pack/unpack used by ebook-converter
"""
import struct
import io


def packb(obj, **kwargs):
    buf = io.BytesIO()
    _pack(buf, obj)
    return buf.getvalue()


def unpackb(data, **kwargs):
    buf = io.BytesIO(data)
    return _unpack(buf)


pack = packb
unpack = unpackb


def _pack(buf, obj):
    if obj is None:
        buf.write(b'\xc0')
    elif isinstance(obj, bool):
        buf.write(b'\xc3' if obj else b'\xc2')
    elif isinstance(obj, int):
        if 0 <= obj <= 0x7f:
            buf.write(struct.pack('B', obj))
        elif -32 <= obj < 0:
            buf.write(struct.pack('b', obj))
        elif 0 <= obj <= 0xff:
            buf.write(b'\xcc' + struct.pack('B', obj))
        elif 0 <= obj <= 0xffff:
            buf.write(b'\xcd' + struct.pack('>H', obj))
        elif 0 <= obj <= 0xffffffff:
            buf.write(b'\xce' + struct.pack('>I', obj))
        elif 0 <= obj <= 0xffffffffffffffff:
            buf.write(b'\xcf' + struct.pack('>Q', obj))
        elif -128 <= obj < 0:
            buf.write(b'\xd0' + struct.pack('b', obj))
        elif -32768 <= obj < 0:
            buf.write(b'\xd1' + struct.pack('>h', obj))
        elif -2147483648 <= obj < 0:
            buf.write(b'\xd2' + struct.pack('>i', obj))
        else:
            buf.write(b'\xd3' + struct.pack('>q', obj))
    elif isinstance(obj, float):
        buf.write(b'\xcb' + struct.pack('>d', obj))
    elif isinstance(obj, bytes):
        n = len(obj)
        if n <= 0xff:
            buf.write(b'\xc4' + struct.pack('B', n))
        elif n <= 0xffff:
            buf.write(b'\xc5' + struct.pack('>H', n))
        else:
            buf.write(b'\xc6' + struct.pack('>I', n))
        buf.write(obj)
    elif isinstance(obj, str):
        raw = obj.encode('utf-8')
        n = len(raw)
        if n <= 31:
            buf.write(struct.pack('B', 0xa0 | n))
        elif n <= 0xff:
            buf.write(b'\xd9' + struct.pack('B', n))
        elif n <= 0xffff:
            buf.write(b'\xda' + struct.pack('>H', n))
        else:
            buf.write(b'\xdb' + struct.pack('>I', n))
        buf.write(raw)
    elif isinstance(obj, (list, tuple)):
        n = len(obj)
        if n <= 15:
            buf.write(struct.pack('B', 0x90 | n))
        elif n <= 0xffff:
            buf.write(b'\xdc' + struct.pack('>H', n))
        else:
            buf.write(b'\xdd' + struct.pack('>I', n))
        for item in obj:
            _pack(buf, item)
    elif isinstance(obj, dict):
        n = len(obj)
        if n <= 15:
            buf.write(struct.pack('B', 0x80 | n))
        elif n <= 0xffff:
            buf.write(b'\xde' + struct.pack('>H', n))
        else:
            buf.write(b'\xdf' + struct.pack('>I', n))
        for k, v in obj.items():
            _pack(buf, k)
            _pack(buf, v)
    else:
        raise TypeError(f"Cannot serialize {type(obj)}")


def _unpack(buf):
    b = buf.read(1)
    if not b:
        raise ValueError("Unexpected end of data")
    c = b[0]

    if c <= 0x7f:
        return c
    elif c >= 0xe0:
        return c - 256
    elif c & 0xe0 == 0xa0:
        n = c & 0x1f
        return buf.read(n).decode('utf-8')
    elif c & 0xf0 == 0x90:
        n = c & 0x0f
        return [_unpack(buf) for _ in range(n)]
    elif c & 0xf0 == 0x80:
        n = c & 0x0f
        return {_unpack(buf): _unpack(buf) for _ in range(n)}
    elif c == 0xc0:
        return None
    elif c == 0xc2:
        return False
    elif c == 0xc3:
        return True
    elif c == 0xc4:
        n = struct.unpack('B', buf.read(1))[0]
        return buf.read(n)
    elif c == 0xc5:
        n = struct.unpack('>H', buf.read(2))[0]
        return buf.read(n)
    elif c == 0xc6:
        n = struct.unpack('>I', buf.read(4))[0]
        return buf.read(n)
    elif c == 0xca:
        return struct.unpack('>f', buf.read(4))[0]
    elif c == 0xcb:
        return struct.unpack('>d', buf.read(8))[0]
    elif c == 0xcc:
        return struct.unpack('B', buf.read(1))[0]
    elif c == 0xcd:
        return struct.unpack('>H', buf.read(2))[0]
    elif c == 0xce:
        return struct.unpack('>I', buf.read(4))[0]
    elif c == 0xcf:
        return struct.unpack('>Q', buf.read(8))[0]
    elif c == 0xd0:
        return struct.unpack('b', buf.read(1))[0]
    elif c == 0xd1:
        return struct.unpack('>h', buf.read(2))[0]
    elif c == 0xd2:
        return struct.unpack('>i', buf.read(4))[0]
    elif c == 0xd3:
        return struct.unpack('>q', buf.read(8))[0]
    elif c == 0xd9:
        n = struct.unpack('B', buf.read(1))[0]
        return buf.read(n).decode('utf-8')
    elif c == 0xda:
        n = struct.unpack('>H', buf.read(2))[0]
        return buf.read(n).decode('utf-8')
    elif c == 0xdb:
        n = struct.unpack('>I', buf.read(4))[0]
        return buf.read(n).decode('utf-8')
    elif c == 0xdc:
        n = struct.unpack('>H', buf.read(2))[0]
        return [_unpack(buf) for _ in range(n)]
    elif c == 0xdd:
        n = struct.unpack('>I', buf.read(4))[0]
        return [_unpack(buf) for _ in range(n)]
    elif c == 0xde:
        n = struct.unpack('>H', buf.read(2))[0]
        return {_unpack(buf): _unpack(buf) for _ in range(n)}
    elif c == 0xdf:
        n = struct.unpack('>I', buf.read(4))[0]
        return {_unpack(buf): _unpack(buf) for _ in range(n)}
    else:
        raise ValueError(f"Unknown msgpack type: 0x{c:02x}")


# Compatibility aliases
dumps = packb
loads = unpackb
