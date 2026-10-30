"""Add the ```json question block of a plan doc to final_q.json as a new area. Usage: add_plan_qs.py <doc> <prefix> <area> <feature>"""
import json, re, sys
doc, prefix, area, feat = sys.argv[1:5]
blocks = re.findall(r'```json\s*(\[.*?\])\s*```', open(doc).read(), re.S)
qs = json.loads(blocks[-1])
p = '/home/claude/skyblock/tools/owner_sheet/final_q.json'
cur = json.load(open(p)); have = {x['id'] for x in cur}
n = 0
for i, x in enumerate(qs, 1):
    qid = x.get('id') or f'{prefix}{i}'
    if qid in have: continue
    cur.append({'id': qid, 'a': area, 'f': feat, 'q': x['q'], 's': x['s'], 'c': x.get('c', ''), 'w': x.get('why', ''), 'p': int(x.get('p', 0))})
    n += 1
json.dump(cur, open(p, 'w'), ensure_ascii=False)
print('added', n, 'total', len(cur))
