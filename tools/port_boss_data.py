import sys, json, os, shutil, glob
NEO = 'F:/Java/Abyssia-NeoForge'; FORGE = 'F:/Java/Abyssia'
sys.path.insert(0, NEO + '/tools')
import mc_format as m
R = 'src/main/resources/'
names = ['abyssal_altar','abyss_serpent','trench_wyrm','abyss_kraken','abyss_mother','abyss_heart','kraken_beak','kraken_ink_sac','mother_bell_gel']
files = []
for pat in ['assets/abyssia/blockstates/abyssal_altar.json','assets/abyssia/models/block/abyssal_altar.json','assets/abyssia/models/item/*.json',
            'assets/abyssia/textures/block/abyssal_altar.png','assets/abyssia/textures/entity/*.png','assets/abyssia/textures/item/*.png',
            'data/abyssia/loot_table/blocks/abyssal_altar.json','data/abyssia/loot_table/entities/*.json','data/abyssia/recipe/*.json','data/abyssia/technologies/abyss_summoning.json']:
    for f in glob.glob(NEO + '/' + R + pat):
        b = os.path.basename(f)
        if 'technologies' in f or any(b.startswith(n) for n in names): files.append(os.path.relpath(f, NEO + '/' + R).replace(os.sep, '/'))
dry = '--dry' in sys.argv
for rel in sorted(files):
    src = NEO + '/' + R + rel
    out = m.downgrade(rel) if rel.startswith('data/') else rel
    dst = FORGE + '/' + R + out
    if rel.endswith('.json'):
        data = json.load(open(src, encoding='utf-8'))
        data = m.downgrade_json(rel, data) if rel.startswith('data/') or 'models' in rel else data
        text = json.dumps(data, indent=2, ensure_ascii=False) + '\n'
        old = open(dst, encoding='utf-8').read() if os.path.exists(dst) else None
        print(('NEW ' if old is None else ('SAME' if old == text else 'UPD ')), out)
        if not dry and old != text:
            os.makedirs(os.path.dirname(dst), exist_ok=True); open(dst, 'w', encoding='utf-8', newline='\n').write(text)
    else:
        print('COPY', out)
        if not dry: os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copyfile(src, dst)
# lang: add keys that exist in NeoForge but not in Forge, only boss-related
for lang in ['en_us', 'ja_jp']:
    n = json.load(open(f'{NEO}/{R}assets/abyssia/lang/{lang}.json', encoding='utf-8'))
    fp = f'{FORGE}/{R}assets/abyssia/lang/{lang}.json'
    fo = json.load(open(fp, encoding='utf-8'))
    add = {k: v for k, v in n.items() if k not in fo and any(x in k for x in names + ['altar', 'lure', 'boss', 'abyss_summoning', 'serpent', 'wyrm', 'kraken', 'mother', 'rock_shard'])}
    print(lang, 'add', len(add), sorted(add)[:40])
    if not dry and add:
        fo.update(add); open(fp, 'w', encoding='utf-8', newline='\n').write(json.dumps(fo, indent=2, ensure_ascii=False) + '\n')
