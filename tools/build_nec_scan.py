#!/usr/bin/env python3
"""Genera app/src/main/assets/nec_scan.txt: direcciones NEC/NECext y comandos POWER ordenados por
frecuencia real en IRDB + Flipper-IRDB + tv.ir, para la búsqueda profunda de TVs chinas sin código.
Uso: python3 -I tools/build_nec_scan.py <irdb/codes> <Flipper-IRDB> <assets/tv.ir> <salida>"""
import collections, csv, glob, re, sys

def main(irdb, firdb, tvir, out):
    nec, ext, power = collections.Counter(), collections.Counter(), collections.Counter()
    def add(proto, a, c, name):
        (nec if proto == 'NEC' else ext)[a] += 1
        if 'POWER' in name.upper(): power[c & 0xFF] += 1
    for f in glob.glob(irdb + '/**/*.csv', recursive=True):
        for r in csv.DictReader(open(f, errors='ignore', encoding='utf-8-sig')):
            try: p, d, s, c = r['protocol'], int(r['device']), int(r['subdevice']), int(r['function'])
            except (KeyError, ValueError): continue
            if p in ('NEC', 'NEC1', 'NEC2'):
                add('NEC', d, c, r['functionname']) if s < 0 or s == (~d & 0xFF) else add('NECext', d | s << 8, c, r['functionname'])
    for f in [tvir] + [g for g in glob.glob(firdb + '/**/*.ir', recursive=True) if '_Converted_' not in g]:
        for b in open(f, errors='ignore').read().split('\n#'):
            d = dict((k.strip(), v.strip()) for k, v in (l.split(':', 1) for l in b.splitlines() if ':' in l))
            if d.get('protocol') in ('NEC', 'NECext'):
                le = lambda s: int(''.join(reversed(s.split())), 16)
                add(d['protocol'], le(d['address']), le(d['command']), d.get('name', ''))
    nec_order = [a for a, _ in nec.most_common() if a < 256] + [a for a in range(256) if a not in nec]
    with open(out, 'w') as o:
        o.write('NEC: ' + ' '.join('%02X' % a for a in nec_order) + '\n')
        o.write('NECext: ' + ' '.join('%04X' % a for a, _ in ext.most_common()) + '\n')
        o.write('POWER: ' + ' '.join('%02X' % c for c, _ in power.most_common(24)) + '\n')
    print(len(nec_order), len(ext), power.most_common(24)[:8])

if __name__ == '__main__':
    main(*sys.argv[1:5])
