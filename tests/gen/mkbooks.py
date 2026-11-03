#!/usr/bin/env python3
"""Makes one small sample book for (almost) every format Quiet opens.

    python3 tests/gen/mkbooks.py [out_dir]      # default: tests/books (git-ignored)

Everything is generated from scratch, so there are no copyright questions and nothing big
lives in git. Needs Pillow, python-docx and reportlab (pip install pillow python-docx reportlab).
CB7 isn't made: there's no 7z writer without extra tools.
"""
import io
import os
import struct
import sys
import tarfile
import time
import zipfile
import zlib

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'books')
FORMATS = os.path.join(OUT, 'formats')
os.makedirs(FORMATS, exist_ok=True)

# Plain sentences, long enough to give several pages per chapter.
SENTENCES = [
    'The lamp was low and the room was quiet.',
    'She turned the page slowly, as if the words might run away.',
    'Outside, the rain kept a patient rhythm on the roof.',
    'Nobody in the house was awake except the old clock.',
    'He wrote one line, crossed it out, and wrote it again.',
    'The river below the bridge carried leaves toward the sea.',
]


def paragraphs(chapter, count=14):
    return [' '.join(SENTENCES[(chapter + i + j) % len(SENTENCES)] for j in range(5)) for i in range(count)]


def write(name, data, folder=FORMATS):
    path = os.path.join(folder, name)
    with open(path, 'wb') as f:
        f.write(data if isinstance(data, bytes) else data.encode('utf-8'))
    return path


def cover_png(title, color=(46, 64, 87)):
    img = Image.new('RGB', (600, 900), color)
    d = ImageDraw.Draw(img)
    d.rectangle((40, 40, 560, 860), outline=(240, 230, 210), width=4)
    d.text((80, 400), title, fill=(240, 230, 210))
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return buf.getvalue()


# ---------- EPUB ----------

def epub(title, author, chapters=5, series=None, index=None, footnote=True, lang='en', chapter_css=None):
    ident = f'urn:quiet-test:{title.lower().replace(" ", "-")}'
    series_meta = ''
    if series:
        series_meta = (f'<meta name="calibre:series" content="{series}"/>'
                       f'<meta name="calibre:series_index" content="{index}"/>'
                       f'<meta property="belongs-to-collection" id="c1">{series}</meta>'
                       f'<meta refines="#c1" property="collection-type">series</meta>'
                       f'<meta refines="#c1" property="group-position">{index}</meta>')
    items, spine, nav = [], [], []
    files = {}
    for c in range(1, chapters + 1):
        body = ''.join(f'<p>{p}</p>' for p in paragraphs(c))
        if footnote and c == 1:
            body = ('<p>A sentence with a note<a epub:type="noteref" href="notes.xhtml#n1" id="r1">1</a>.</p>' + body)
        files[f'OEBPS/ch{c}.xhtml'] = (
            '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
            f'xmlns:epub="http://www.idpf.org/2007/ops" lang="{lang}"><head><title>Chapter {c}</title>'
            f'<style>{(chapter_css or {}).get(c, "")}</style></head>'
            f'<body><h1>Chapter {c}</h1>{body}</body></html>')
        items.append(f'<item id="ch{c}" href="ch{c}.xhtml" media-type="application/xhtml+xml"/>')
        spine.append(f'<itemref idref="ch{c}"/>')
        nav.append(f'<li><a href="ch{c}.xhtml">Chapter {c}</a></li>')
    if footnote:
        files['OEBPS/notes.xhtml'] = (
            '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
            'xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Notes</title></head><body>'
            '<aside epub:type="footnote" id="n1"><p>This is the footnote text.</p></aside></body></html>')
        items.append('<item id="notes" href="notes.xhtml" media-type="application/xhtml+xml"/>')
        spine.append('<itemref idref="notes" linear="no"/>')
    files['OEBPS/nav.xhtml'] = (
        '<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
        'xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body>'
        f'<nav epub:type="toc"><ol>{"".join(nav)}</ol></nav></body></html>')
    files['OEBPS/cover.png'] = cover_png(title)
    files['OEBPS/content.opf'] = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">'
        '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        f'<dc:identifier id="id">{ident}</dc:identifier><dc:title>{title}</dc:title>'
        f'<dc:creator>{author}</dc:creator><dc:language>{lang}</dc:language>'
        '<dc:description>A small book made for testing Quiet.</dc:description>'
        f'<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>{series_meta}'
        '<meta name="cover" content="cover"/></metadata><manifest>'
        '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>'
        '<item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>'
        f'{"".join(items)}</manifest><spine>{"".join(spine)}</spine></package>')
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        z.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip')  # stored, first
        z.writestr('META-INF/container.xml',
                   '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
                   '<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
                   '</rootfiles></container>', zipfile.ZIP_DEFLATED)
        for name, data in files.items():
            z.writestr(name, data, zipfile.ZIP_DEFLATED)
    return buf.getvalue()


# ---------- Comics ----------

def pages(n=4):
    out = []
    for i in range(1, n + 1):
        img = Image.new('RGB', (800, 1200), (250 - i * 20, 240, 225))
        d = ImageDraw.Draw(img)
        d.rectangle((60, 60, 740, 560), outline=(30, 30, 30), width=6)
        d.rectangle((60, 620, 740, 1140), outline=(30, 30, 30), width=6)
        d.text((380, 300), f'Page {i}', fill=(0, 0, 0))
        buf = io.BytesIO()
        img.save(buf, 'JPEG', quality=80)
        out.append((f'page{i:02d}.jpg', buf.getvalue()))
    # Out-of-order names and junk to check natural sorting and skipping.
    return out


def cbz(pgs):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data in reversed(pgs):
            z.writestr(name, data)
        z.writestr('__MACOSX/._page01.jpg', b'junk')
        z.writestr('Thumbs.db', b'junk')
    return buf.getvalue()


def cbt(pgs):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode='w') as t:
        for name, data in pgs:
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mtime = int(time.time())
            t.addfile(info, io.BytesIO(data))
    return buf.getvalue()


def rar4_stored(pgs):
    """A RAR 4 archive with stored (uncompressed) entries. Real CBRs are compressed:
    test one of those on the phone too."""
    def block(htype, flags, body, add=b''):
        size = 7 + len(body)
        head = struct.pack('<BHH', htype, flags, size) + body
        crc = zlib.crc32(head) & 0xffff
        return struct.pack('<H', crc) + head + add

    out = b'Rar!\x1a\x07\x00'
    out += block(0x73, 0, struct.pack('<HI', 0, 0))
    for name, data in pgs:
        nb = name.encode()
        body = struct.pack('<IIBIIBBHI', len(data), len(data), 0, zlib.crc32(data) & 0xffffffff,
                           0x5A210000, 20, 0x30, len(nb), 0x20) + nb
        out += block(0x74, 0x8000, body, data)
    out += block(0x7B, 0x4000, b'')
    return out


# ---------- Other book formats ----------

def fb2(title, author):
    body = ''.join(f'<section><title><p>Chapter {c}</p></title>{"".join(f"<p>{p}</p>" for p in paragraphs(c))}</section>'
                   for c in range(1, 4))
    first, _, last = author.partition(' ')
    return ('<?xml version="1.0" encoding="utf-8"?>'
            '<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"><description><title-info>'
            f'<author><first-name>{first}</first-name><last-name>{last}</last-name></author>'
            f'<book-title>{title}</book-title><lang>en</lang></title-info></description>'
            f'<body>{body}</body></FictionBook>')


def docx(title):
    from docx import Document
    d = Document()
    d.core_properties.title = title
    d.core_properties.author = 'Dora Docx'
    for c in range(1, 4):
        d.add_heading(f'Chapter {c}', level=1)
        for p in paragraphs(c, 8):
            d.add_paragraph(p)
    buf = io.BytesIO()
    d.save(buf)
    return buf.getvalue()


def odt(title):
    content = ('<?xml version="1.0" encoding="UTF-8"?>'
               '<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" '
               'xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2"><office:body><office:text>'
               + ''.join(f'<text:h text:outline-level="1">Chapter {c}</text:h>'
                         + ''.join(f'<text:p>{p}</text:p>' for p in paragraphs(c, 8)) for c in range(1, 4))
               + '</office:text></office:body></office:document-content>')
    meta = ('<?xml version="1.0" encoding="UTF-8"?><office:document-meta '
            'xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:dc="http://purl.org/dc/elements/1.1/" '
            f'office:version="1.2"><office:meta><dc:title>{title}</dc:title><dc:creator>Otto Odt</dc:creator>'
            '</office:meta></office:document-meta>')
    manifest = ('<?xml version="1.0" encoding="UTF-8"?><manifest:manifest '
                'xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">'
                '<manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>'
                '<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>'
                '<manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>'
                '</manifest:manifest>')
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        z.writestr(zipfile.ZipInfo('mimetype'), 'application/vnd.oasis.opendocument.text')
        z.writestr('content.xml', content, zipfile.ZIP_DEFLATED)
        z.writestr('meta.xml', meta, zipfile.ZIP_DEFLATED)
        z.writestr('META-INF/manifest.xml', manifest, zipfile.ZIP_DEFLATED)
    return buf.getvalue()


def rtf(title):
    body = ''.join(r'{\b Chapter %d}\par ' % c + ''.join(p + r'\par ' for p in paragraphs(c, 6)) for c in range(1, 4))
    return r'{\rtf1\ansi\deff0{\fonttbl{\f0 Times New Roman;}}{\info{\title %s}{\author Rita Rtf}}%s}' % (title, body)


def text_book():
    return '\n\n'.join(f'Chapter {c}\n\n' + '\n\n'.join(paragraphs(c, 8)) for c in range(1, 4))


def markdown(title):
    return f'# {title}\n\n' + '\n\n'.join(f'## Chapter {c}\n\n' + '\n\n'.join(paragraphs(c, 8)) for c in range(1, 4))


def html(title):
    return (f'<!doctype html><html><head><meta charset="utf-8"><title>{title}</title></head><body>'
            + ''.join(f'<h1>Chapter {c}</h1>' + ''.join(f'<p>{p}</p>' for p in paragraphs(c, 8)) for c in range(1, 4))
            + '</body></html>')


def htmlz(title):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('index.html', html(title))
        z.writestr('metadata.opf', '<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0">'
                   '<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
                   f'<dc:title>{title}</dc:title><dc:creator>Hazel Htmlz</dc:creator></metadata></package>')
    return buf.getvalue()


def palmdoc(title):
    text = text_book().encode('latin1', 'replace')
    recs = [text[i:i + 4096] for i in range(0, len(text), 4096)]
    rec0 = struct.pack('>HHIHHI', 1, 0, len(text), len(recs), 4096, 0)
    records = [rec0] + recs
    n = len(records)
    header = title.encode('latin1')[:31].ljust(32, b'\0')
    header += struct.pack('>HHIIIIII', 0, 0, 0, 0, 0, 0, 0, 0) + b'TEXtREAd' + struct.pack('>IIH', 0, 0, n)
    offset = len(header) + n * 8 + 2
    table = b''
    for i, r in enumerate(records):
        table += struct.pack('>IB', offset, 0) + i.to_bytes(3, 'big')
        offset += len(r)
    return header + table + b'\0\0' + b''.join(records)


def pdf(title):
    from reportlab.lib.pagesizes import A5
    from reportlab.pdfgen import canvas
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A5)
    c.setTitle(title)
    c.setAuthor('Pia Pdf')
    for page in range(1, 4):
        c.setFont('Helvetica-Bold', 16)
        c.drawString(40, 540, f'Chapter {page}')
        c.setFont('Helvetica', 10)
        y = 510
        for p in paragraphs(page, 6):
            for i in range(0, len(p), 70):
                c.drawString(40, y, p[i:i + 70])
                y -= 14
            y -= 8
        c.showPage()
    c.save()
    return buf.getvalue()


def main():
    made = []
    # Main sample library (books/): a short series plus two standalones.
    made.append(write('quiet-river-1.epub', epub('The Quiet River', 'Ada Lane', series='River Tales', index=1), OUT))
    made.append(write('quiet-river-2.epub', epub('The Long Bridge', 'Ada Lane', series='River Tales', index=2), OUT))
    made.append(write('lamp-and-clock.epub', epub('The Lamp and the Clock', 'Omar Vale', chapters=8), OUT))
    made.append(write('short-notes.epub', epub('Short Notes', 'Lina Moss', chapters=2, footnote=False), OUT))
    # Books that colour or hide their own text (the "invisible chapter" bug).
    made.append(write('colours.epub', epub('Coloured Ink', 'Iris Hue', chapters=4, footnote=False, chapter_css={
        1: 'p { color: #000000; }',
        2: 'p { color: #ffffff; }',
        3: 'body { opacity: 0; animation: fade 9s; } @keyframes fade { to { opacity: 1 } }',
        4: 'h1 { color: #b0302a; }',
    }), OUT))

    pgs = pages()
    for name, data in [
        ('sample.epub', epub('Format EPUB', 'Eve Pub', chapters=3)),
        ('sample.fb2', fb2('Format FB2', 'Fyodor Bee')),
        ('sample.fbz', None),
        ('sample.cbz', cbz(pgs)),
        ('sample.cbt', cbt(pgs)),
        ('sample.cbr', rar4_stored(pgs)),
        ('sample.docx', docx('Format DOCX')),
        ('sample.odt', odt('Format ODT')),
        ('sample.rtf', rtf('Format RTF')),
        ('sample.txt', text_book()),
        ('sample.md', markdown('Format Markdown')),
        ('sample.html', html('Format HTML')),
        ('sample.htmlz', htmlz('Format HTMLZ')),
        ('sample.pdb', palmdoc('Format PDB')),
        ('sample.pdf', pdf('Format PDF')),
    ]:
        if name == 'sample.fbz':
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
                z.writestr('book.fb2', fb2('Format FBZ', 'Fiona Zip'))
            data = buf.getvalue()
        made.append(write(name, data))
    for path in made:
        print(f'{os.path.getsize(path):>8}  {os.path.relpath(path, OUT)}')


if __name__ == '__main__':
    main()
