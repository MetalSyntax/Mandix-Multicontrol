#!/usr/bin/env python3
"""Genera el ícono de Mandix desde una sola definición:
- res/drawable/ic_launcher_foreground.xml + res/mipmap-anydpi-v26/ic_launcher.xml (adaptativo, API 26+)
- res/mipmap/ic_launcher.xml (vector con fondo, API 21-25)
- play/icon_512.png y play/feature_1024x500.png (Google Play) vía rsvg-convert
Uso: python3 -I tools/build_icon.py  (desde la raíz del proyecto)"""
import os, subprocess

BG = '#1E3A8A'
# Lienzo 108x108 (zona segura del ícono adaptativo: círculo de 66 en el centro)
SHAPES = [
    # ondas IR: tres equipos
    ('stroke', '#2DD4BF', 3.2, 'M49.05,35.05 A7,7 0 0,1 58.95,35.05'),
    ('stroke', '#F59E0B', 3.2, 'M45.51,31.51 A12,12 0 0,1 62.49,31.51'),
    ('stroke', '#A78BFA', 3.2, 'M41.98,27.98 A17,17 0 0,1 66.02,27.98'),
    # control
    ('fill', '#F8FAFC', 0, 'M50,44 H58 A6,6 0 0,1 64,50 V80 A6,6 0 0,1 58,86 H50 A6,6 0 0,1 44,80 V50 A6,6 0 0,1 50,44 Z'),
    ('fill', '#EF4444', 0, 'M52.4,49 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z'),
    ('fill', '#DC2626', 0, 'M51,57 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0 Z'),
    ('stroke', '#334155', 2, 'M49,68 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0 Z'),
    ('fill', '#334155', 0, 'M52.4,68 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z'),
    ('fill', '#64748B', 0, 'M48.4,79 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z'),
    ('fill', '#64748B', 0, 'M56.4,79 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 Z'),
]

def vector_paths(indent):
    out = []
    for kind, color, w, d in SHAPES:
        attrs = ('android:fillColor="%s"' % color) if kind == 'fill' else \
            ('android:strokeColor="%s" android:strokeWidth="%s" android:strokeLineCap="round"' % (color, w))
        out.append('%s<path %s android:pathData="%s" />' % (indent, attrs, d))
    return '\n'.join(out)

def svg_paths():
    return '\n'.join(('<path fill="%s" d="%s"/>' % (c, d)) if k == 'fill' else
                     ('<path fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" d="%s"/>' % (c, w, d))
                     for k, c, w, d in SHAPES)

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w').write(text)

res = 'app/src/main/res'
write(res + '/drawable/ic_launcher_foreground.xml', '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/build_icon.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
%s
</vector>
''' % vector_paths('    '))
write(res + '/mipmap-anydpi-v26/ic_launcher.xml', '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/build_icon.py -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''')
write(res + '/values/ic_launcher.xml', '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">%s</color>
</resources>
''' % BG)
write(res + '/mipmap/ic_launcher.xml', '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/build_icon.py: ícono para Android 5-7 (sin ícono adaptativo) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="%s" android:pathData="M30,12 H78 A18,18 0 0,1 96,30 V78 A18,18 0 0,1 78,96 H30 A18,18 0 0,1 12,78 V30 A18,18 0 0,1 30,12 Z" />
%s
</vector>
''' % (BG, vector_paths('    ')))

# Google Play: ícono 512 (sin transparencia, Play aplica la máscara) y gráfico destacado 1024x500
os.makedirs('play', exist_ok=True)
icon_svg = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="14 14 80 80" width="512" height="512">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E40AF"/><stop offset="1" stop-color="#0F172A"/></linearGradient></defs>
<rect x="14" y="14" width="80" height="80" fill="url(#g)"/>
%s
</svg>''' % svg_paths()
feature_svg = '''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1E40AF"/><stop offset="1" stop-color="#0F172A"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#g)"/>
<g transform="translate(40,-10) scale(4.8)">%s</g>
<text x="440" y="215" font-family="Helvetica, Arial, sans-serif" font-size="96" font-weight="700" fill="#F8FAFC">Mandix</text>
<text x="444" y="280" font-family="Helvetica, Arial, sans-serif" font-size="38" fill="#CBD5E1">Control remoto universal IR</text>
<text x="444" y="330" font-family="Helvetica, Arial, sans-serif" font-size="30" fill="#F59E0B">TV · Aire acondicionado · Cornetas · Roku</text>
</svg>''' % svg_paths()
for name, svg in (('icon_512', icon_svg), ('feature_1024x500', feature_svg)):
    open('play/%s.svg' % name, 'w').write(svg)
    subprocess.run(['rsvg-convert', 'play/%s.svg' % name, '-o', 'play/%s.png' % name], check=True)
    os.remove('play/%s.svg' % name)
print('ok')
