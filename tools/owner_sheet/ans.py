import json, sys, os
p = 'answers.json'
a = json.load(open(p)) if os.path.exists(p) else {}
for arg in sys.argv[1:]:
    parts = arg.split(':', 2)
    a[parts[0]] = {'a': parts[1], 'note': parts[2] if len(parts) > 2 else ''}
json.dump(a, open(p, 'w'), indent=0, ensure_ascii=False)
q = {x['id']: x for x in json.load(open('final_q.json'))}
order = json.load(open('ask_order.json'))
left = [i for i in order if i not in a]
n = int(os.environ.get('N', 8))
print('answered', len(a), 'left', len(left), 'before-launch left', sum(1 for i in left if q[i]['p']))
for i in left[:n]:
    x = q[i]; print(i, '|', x['a'], '|', x['f'], '|', x['q'], '|', x['s'], '|', x['c'])
