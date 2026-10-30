"""Parse docs/OPEN_QUESTIONS.md (+ optional extra JSON lists) into rows for the owner's question sheet."""
import re, json, sys

GROUPS = [  # (feature name, tag regex)
    ('Look & feel (colors, lores, menus)', r'^(V2|SB|UX\d|CRY|STYLE2|DESIGN|SELL2|PLANRP|PLAN-RP|RP)$'),
    ('Settings', r'^F1$'),
    ('Permissions & ranks', r'^(F2|Ranks?)$'),
    ('Achievements, titles & cosmetics', r'^(F3|Deleted|Cosmetics|Titles?)$'),
    ('Teleports & warps', r'^(F4|Teleports?)$'),
    ('Profiles', r'^PROF$'),
    ('Islands', r'^(Islands|M2\.\d|ISL2|NOTES|R2C|Island)$'),
    ('Island upgrades & stats', r'^(M2\.5|M2\.6)$'),
    ('Economy: money, pay, notes, limits', r'^(Economy|Money|M3\.0|M3\.1|M3\.2|R2B|BAL2|BAL|TUNE)$'),
    ('Bank', r'^M3\.3$'),
    ('Sell', r'^(M3\.4|M2\.6)$'),
    ('Shop', r'^M3\.5$'),
    ('Auction house', r'^(Auction|M3\.6)$'),
    ('Trading', r'^M3\.7$'),
    ('Economy dashboard & alerts', r'^M3\.8$'),
    ('The Depths', r'^(Depths|M4\.[13]|M4\.7|M4\.8|M4\.9|BUILDS)$'),
    ('Satchel', r'^M4\.4$'),
    ('Forge & tools', r'^(M4\.2|Tools?|Forge)$'),
    ('Skills', r'^(Skills|M4\.5|R2A)$'),
    ('Enchants', r'^(Enchants|E\d)$'),
    ('Wardrobe & sets', r'^(Wardrobe|W\d|Loadout)$'),
    ('Stacking & spawners', r'^(STACK|Stacking)$'),
    ('Punishments & staff', r'^(P\d+|Punishments|Logout|Staff|Moderation)$'),
    ('AFK pool', r'^AFK$'),
]
SECTION_GROUP = {'Economy': 'Economy: money, pay, notes, limits', 'The Depths': 'The Depths', 'Gear': 'Forge & tools',
                 'Leaderboards': 'Leaderboards', 'Network (SkybornMC)': 'Network & website', 'Other': 'Other'}

def group_for(tag, section):
    for name, rx in GROUPS:
        if re.match(rx, tag, re.I):
            return name
    for k, v in SECTION_GROUP.items():
        if section.startswith(k):
            return v
    return 'Other'

def row(question, default, tag, section):
    feat = group_for(tag, section)
    if feat == 'Other':
        low = question.lower()
        if any(w in low for w in ('punish', 'ban', 'mute', 'staff', 'helper', 'freeze', 'discord webhook')): feat = 'Punishments & staff'
        elif 'achievement' in low: feat = 'Achievements, titles & cosmetics'
    return {'feature': feat, 'code': tag, 'question': question, 'suggested': default or 'keep it as built (described in the question)',
            'source': 'OPEN_QUESTIONS.md · ' + section[:30]}

def parse(md):
    rows, section = [], ''
    for line in md.split('\n'):
        if line.startswith('## '):
            section = line[3:].strip(); continue
        if not line.startswith('- ') or section.startswith('Decided'):
            continue
        t = line[2:].strip()
        if t.startswith('~~') or 'Answered' in t[:200] and '~~' in t:
            continue
        clean = t.replace('**', '').replace('`', '')
        head = clean.split(':')[0] if ':' in clean[:45] else clean.split(' ')[0]
        tag = re.split(r'[ (]', head.strip())[0].strip('.,')
        m = re.search(r'\((?:default|Default)[:\s]([^()]*(?:\([^()]*\)[^()]*)*)\)\s*\.?\s*$', clean)
        if not m:
            m = re.search(r'\[([^\[\]]+)\]\s*$', clean)
        if not m:
            m = re.search(r'\(default:\s*([^()]*)\)', clean)
            if m:
                default = m.group(1).strip()
                rows.append(row(clean, default, tag, section)); continue
        if not m and '(default)' in clean:
            rows.append(row(clean, 'the option marked (default)', tag, section)); continue
        default = m.group(1).strip() if m else ''
        question = clean[:m.start()].strip() if m else clean
        rows.append(row(question, default, tag, section))
    return rows

if __name__ == '__main__':
    rows = parse(open(sys.argv[1]).read())
    seen, out = set(), []
    for r in rows:
        k = r['question'][:120]
        if k in seen: continue
        seen.add(k); out.append(r)
    json.dump(out, open(sys.argv[2], 'w'), indent=1, ensure_ascii=False)
    import collections
    c = collections.Counter(r['feature'] for r in out)
    print(len(out)); [print(v, k) for k, v in c.most_common()]
    print(sum(1 for r in out if r['suggested'].startswith('keep it')), 'without explicit default')
