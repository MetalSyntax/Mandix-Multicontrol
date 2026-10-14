#!/usr/bin/env python3
"""Genera app/src/main/assets/tv_extra.ir: mandos de TV de IRDB (probonopd/irdb) y Flipper-IRDB
(Lucaslhm/Flipper-IRDB) en formato Flipper, sin repetir códigos de tv.ir.
Uso: python3 -I tools/build_tv_extra.py <irdb/codes> <Flipper-IRDB/TVs> <assets/tv.ir> <salida>"""
import csv, glob, os, re, sys

# Marcas chinas / vendidas en Latinoamérica primero: el barrido las prueba antes
PRIORITY = ['Generic', 'Sankey', 'Daewoo', 'Hyundai', 'BGH', 'CCE', 'Konka', 'TCL', 'Hisense', 'Haier', 'Polaroid',
            'Daytron', 'Philco', 'AOC', 'Element', 'Westinghouse', 'Sanyo', 'Aiwa', 'Telefunken', 'Hitachi']

def key(name):
    n = re.sub(r'^KEY_', '', name.upper().strip())
    n = re.sub(r'[\s_\.]', '', n)
    table = {
        'POWER': 'Power', 'POWERTOGGLE': 'Power', 'ONOFF': 'Power', 'ON/OFF': 'Power', 'STANDBY': 'Power', 'PWR': 'Power',
        'VOLUMEUP': 'Vol_up', 'VOLUME+': 'Vol_up', 'VOL+': 'Vol_up', 'VOLUP': 'Vol_up',
        'VOLUMEDOWN': 'Vol_dn', 'VOLUME-': 'Vol_dn', 'VOL-': 'Vol_dn', 'VOLDOWN': 'Vol_dn', 'VOLDN': 'Vol_dn',
        'CHANNELUP': 'Ch_next', 'CHANNEL+': 'Ch_next', 'CH+': 'Ch_next', 'CHUP': 'Ch_next', 'CHNEXT': 'Ch_next', 'PROGRAM+': 'Ch_next', 'P+': 'Ch_next',
        'CHANNELDOWN': 'Ch_prev', 'CHANNEL-': 'Ch_prev', 'CH-': 'Ch_prev', 'CHDOWN': 'Ch_prev', 'CHPREV': 'Ch_prev', 'PROGRAM-': 'Ch_prev', 'P-': 'Ch_prev',
        'MUTE': 'Mute', 'MENU': 'Menu', 'UP': 'Up', 'CURSORUP': 'Up', 'DOWN': 'Down', 'CURSORDOWN': 'Down',
        'LEFT': 'Left', 'CURSORLEFT': 'Left', 'RIGHT': 'Right', 'CURSORRIGHT': 'Right', 'OK': 'Ok', 'SELECT': 'Ok', 'ENTER': 'Ok',
        'EXIT': 'Exit', 'BACK': 'Back', 'RETURN': 'Back', 'INPUT': 'Source', 'SOURCE': 'Source', 'AV': 'Source', 'TV/AV': 'Source',
        'INFO': 'Info', 'DISPLAY': 'Display', 'SLEEP': 'Sleep', 'EPG': 'Guide', 'GUIDE': 'Guide', 'HOME': 'Home',
        'RED': 'Red', 'GREEN': 'Green', 'YELLOW': 'Yellow', 'BLUE': 'Blue', 'PLAY': 'Play', 'PLAYPAUSE': 'Play',
        'STOP': 'Stop', 'REWIND': 'Rew', 'FASTFORWARD': 'Ffwd', 'FAVORITES': 'Fav', 'FAV': 'Fav', 'AUDIO': 'Audio',
        'AGAIN': 'Return', 'RECALL': 'Return', 'PRECH': 'Return', 'CHLIST': 'Ch_list',
    }
    if re.fullmatch(r'\d', n): return n
    return table.get(n)

def irdb_signal(proto, d, s, f):
    """IRDB (D,S,F) → (protocolo Flipper/IrCodec, dirección, comando) o None si no se soporta."""
    if proto in ('NEC', 'NEC1', 'NEC2'):
        if s < 0 or s == (~d & 0xFF): return 'NEC', d, f
        return 'NECext', d | (s << 8), f | ((~f & 0xFF) << 8)
    if proto in ('NECx1', 'NECx2'):
        if s < 0 or s == d: return 'Samsung32', d, f
        return 'NECx', d | (s << 8), f
    if proto == 'Sony12': return 'SIRC', d, f
    if proto == 'Sony15': return 'SIRC15', d, f
    if proto == 'Sony20': return 'SIRC20', d | (max(s, 0) << 5), f
    if proto == 'RC5': return ('RC5' if f < 64 else 'RC5X'), d, f
    if proto == 'RC6': return 'RC6', d, f
    if proto == 'Panasonic': return 'Panasonic', d | (max(s, 0) << 8), f
    if proto == 'JVC': return 'JVC', d, f
    if proto in ('Sharp', 'Sharp{1}'): return 'Sharp', d, f
    return None

def le(v, n=4): return ' '.join('%02X' % ((v >> (8 * i)) & 0xFF) for i in range(n))

def parse_flipper(text):
    out = []
    for block in text.split('\n#'):
        fl = dict(l.split(':', 1) for l in block.splitlines() if ':' in l)
        fl = {k.strip(): v.strip() for k, v in fl.items()}
        if 'name' in fl and 'type' in fl: out.append(fl)
    return out

def sig_id(fl):
    if fl['type'] == 'raw': return ('RAW', fl['data'])
    return (fl['protocol'], int(fl['address'].replace(' ', '')[:8] and ''.join(reversed(fl['address'].split())), 16),
            int(''.join(reversed(fl['command'].split())), 16))

def main(irdb, firdb, tvir, out_path):
    seen = {sig_id(fl) for fl in parse_flipper(open(tvir).read()) if fl['name'] == 'Power'}
    remotes = []  # (brand, [(key, block_lines)])
    for f in sorted(glob.glob(irdb + '/**/*.csv', recursive=True)):
        brand, dev = os.path.relpath(f, irdb).split('/')[:2]
        if not re.search(r'TV|TELEVISION|LCD|LED', dev.upper()) or 'CURTAIN' in dev.upper(): continue
        keys = {}
        for r in csv.DictReader(open(f, errors='ignore')):
            k = key(r['functionname'])
            try: d, s, fn = int(r['device']), int(r['subdevice']), int(r['function'])
            except ValueError: continue
            sig = irdb_signal(r['protocol'], d, s, fn) if k else None
            if sig and k not in keys:
                keys[k] = ['type: parsed', 'protocol: %s' % sig[0], 'address: %s' % le(sig[1]), 'command: %s' % le(sig[2])], sig
        if 'Power' in keys: remotes.append((brand, keys))
    for f in sorted(glob.glob(firdb + '/**/*.ir', recursive=True)):
        brand = os.path.relpath(f, firdb).split('/')[0]
        keys = {}
        for fl in parse_flipper(open(f, errors='ignore').read()):
            k = key(fl['name'])
            if not k or k in keys: continue
            if fl['type'] == 'raw':
                if 'data' not in fl or 'frequency' not in fl: continue
                lines = ['type: raw', 'frequency: %s' % fl['frequency'], 'duty_cycle: 0.330000', 'data: %s' % fl['data']]
            else:
                if fl.get('protocol') not in ('NEC', 'NECext', 'NEC42', 'Samsung32', 'SIRC', 'SIRC15', 'SIRC20',
                                              'RC5', 'RC5X', 'RC6', 'Kaseikyo', 'RCA', 'Pioneer'): continue
                lines = ['type: parsed', 'protocol: %s' % fl['protocol'], 'address: %s' % fl['address'], 'command: %s' % fl['command']]
            keys[k] = lines, sig_id(fl)
        if 'Power' in keys: remotes.append((brand, keys))

    remotes.sort(key=lambda r: (PRIORITY.index(r[0]) if r[0] in PRIORITY else len(PRIORITY), r[0].lower()))
    n = 0
    with open(out_path, 'w') as o:
        o.write('Filetype: IR library file\nVersion: 1\n# Generado por tools/build_tv_extra.py (IRDB + Flipper-IRDB)\n')
        for brand, keys in remotes:
            pid = keys['Power'][1]
            if pid in seen: continue
            seen.add(pid); n += 1
            for k in ['Power'] + [k for k in keys if k != 'Power']:
                o.write('#\nname: %s\nbrand: %s\n%s\n' % (k, brand, '\n'.join(keys[k][0])))
    print('mandos nuevos:', n, 'de', len(remotes))

if __name__ == '__main__':
    main(*sys.argv[1:5])
