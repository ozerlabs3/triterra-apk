# One-off: TriTerra PWA icons (prod APK assets/public/icons) -> Android launcher mipmaps.
# Usage (after `npm run from-apk`): python3 scripts/icons-from-prod-apk.py www/icons android/app/src/main/res
# Prints the adaptive background colour to put in values/ic_launcher_background.xml.
import sys
from PIL import Image, ImageDraw
src, res = sys.argv[1], sys.argv[2]
any_ = Image.open(f'{src}/triterra-512.png').convert('RGBA')
mask = Image.open(f'{src}/triterra-maskable-512.png').convert('RGBA')
bg = mask.getpixel((4, 4))
# Maskable safe zone = 80% circle; Android adaptive safe zone = 66/108 dp. Scale so the two match.
scale = (66 / 108) / 0.8
for d, legacy in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    fg_px = legacy * 108 // 48
    fg = Image.new('RGBA', (fg_px, fg_px), (0, 0, 0, 0))
    inner = round(fg_px * scale)
    off = (fg_px - inner) // 2
    fg.paste(mask.resize((inner, inner), Image.LANCZOS), (off, off))
    fg.save(f'{res}/mipmap-{d}/ic_launcher_foreground.png', optimize=True)
    any_.resize((legacy, legacy), Image.LANCZOS).save(f'{res}/mipmap-{d}/ic_launcher.png', optimize=True)
    r = mask.resize((legacy, legacy), Image.LANCZOS)
    m = Image.new('L', (legacy * 4, legacy * 4), 0); ImageDraw.Draw(m).ellipse((0, 0, legacy * 4 - 1, legacy * 4 - 1), 255)
    r.putalpha(m.resize((legacy, legacy), Image.LANCZOS)); r.save(f'{res}/mipmap-{d}/ic_launcher_round.png', optimize=True)
print('#%02X%02X%02X' % bg[:3])
