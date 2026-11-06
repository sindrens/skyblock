"""Minimal deterministic PNG reading and writing (standard library only).

Writing: 8-bit RGBA (colour type 6) or RGB (colour type 2), filter 0 on every row, zlib level 9, chunks IHDR, IDAT,
IEND only (no metadata, no timestamps), so the same pixels always give the same bytes.
Reading: enough to validate icons (IHDR) and to copy a PNG without its metadata chunks (``clean``).
"""
import struct
import zlib

SIGNATURE = b'\x89PNG\r\n\x1a\n'
# Chunks kept by clean(): everything needed to draw the image, nothing else (no text, time, gamma or colour profile).
KEEP = (b'IHDR', b'PLTE', b'tRNS', b'IDAT', b'IEND')


def _chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)


def encode(width, height, pixels, alpha=True):
    """PNG bytes for ``pixels``: a list of rows, each a list of (r, g, b, a) tuples (a ignored when alpha is False)."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0 (none): deterministic and tiny for flat pixel art
        row = pixels[y]
        for x in range(width):
            p = row[x]
            raw.extend((p[0], p[1], p[2], p[3]) if alpha else (p[0], p[1], p[2]))
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6 if alpha else 2, 0, 0, 0)
    return SIGNATURE + _chunk(b'IHDR', ihdr) + _chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + _chunk(b'IEND', b'')


def chunks(data):
    """List of (kind, payload) of a PNG file; raises ValueError for a file that is not a PNG."""
    if data[:8] != SIGNATURE:
        raise ValueError('not a PNG file')
    out = []
    pos = 8
    while pos < len(data):
        if pos + 8 > len(data):
            raise ValueError('truncated PNG chunk header')
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        payload = data[pos + 8:pos + 8 + length]
        out.append((kind, payload))
        pos += 12 + length
        if kind == b'IEND':
            break
    return out


def header(data):
    """(width, height, bit depth, colour type) from the IHDR chunk."""
    first = chunks(data)[0]
    if first[0] != b'IHDR':
        raise ValueError('PNG does not start with IHDR')
    w, h, depth, ctype = struct.unpack('>IIBB', first[1][:10])
    return w, h, depth, ctype


def clean(data):
    """The same image without metadata chunks (deterministic copy of an existing PNG)."""
    return SIGNATURE + b''.join(_chunk(k, p) for k, p in chunks(data) if k in KEEP)


def decode_rgba(data):
    """Pixels of an 8-bit RGBA or RGB PNG (non-interlaced) as rows of (r, g, b, a). Used by the contact sheet."""
    w, h, depth, ctype = header(data)
    if depth != 8 or ctype not in (2, 6):
        raise ValueError('only 8-bit RGB/RGBA PNGs are supported (got depth %d, colour type %d)' % (depth, ctype))
    bpp = 4 if ctype == 6 else 3
    raw = zlib.decompress(b''.join(p for k, p in chunks(data) if k == b'IDAT'))
    stride = w * bpp
    rows = []
    prev = bytearray(stride)
    pos = 0
    for _ in range(h):
        f = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 0xFF
            elif f == 2:
                line[i] = (line[i] + b) & 0xFF
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        rows.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)])
        prev = line
    return rows
