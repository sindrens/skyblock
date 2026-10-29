#!/usr/bin/env python3
"""Copy bundled module/plugin YAML resources over the tracked copies in server/plugins/PhoenixSkyblock/
(resources are the source of truth; the deploy then ships them). Adds missing messages/*.yml."""
import glob, os, shutil, filecmp
res = {}
for f in glob.glob('modules/*/src/main/resources/**/*.yml', recursive=True) + glob.glob('plugin/src/main/resources/**/*.yml', recursive=True):
    rel = f.split('/resources/', 1)[1]
    if rel.startswith(('permissions/', 'db/')) or rel == 'plugin.yml':
        continue
    res[rel] = f
for rel, src in sorted(res.items()):
    dst = 'server/plugins/PhoenixSkyblock/' + rel
    if os.path.exists(dst) and not filecmp.cmp(src, dst, shallow=False):
        shutil.copy(src, dst); print('synced', rel)
    elif not os.path.exists(dst) and rel.startswith('messages/'):
        os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copy(src, dst); print('added', rel)
