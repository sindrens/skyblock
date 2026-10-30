#!/usr/bin/env python3
"""Keep bundled YAML resources (modules/*/src/main/resources, plugin/src/main/resources) and the tracked server copies
(server/plugins/PhoenixSkyblock/) in sync. The copy whose last git commit is NEWER wins and is copied over the other,
so owner edits made on GitHub (tracked copy) are never overwritten by an older bundled default, and new defaults from
code changes still reach the server. Uncommitted changes (working tree or staged, e.g. during a merge) count as newest. Missing messages/*.yml are added."""
import glob, os, shutil, filecmp, subprocess, time

def last_change(path):
    if subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--', path]).returncode != 0:
        return time.time()  # uncommitted edit: newest
    out = subprocess.run(['git', 'log', '-1', '--format=%ct', '--', path], capture_output=True, text=True).stdout.strip()
    return int(out) if out else 0

res = {}
for f in glob.glob('modules/*/src/main/resources/**/*.yml', recursive=True) + glob.glob('plugin/src/main/resources/**/*.yml', recursive=True):
    rel = f.split('/resources/', 1)[1]
    if rel.startswith(('permissions/', 'db/')) or rel == 'plugin.yml':
        continue
    res[rel] = f
for rel, src in sorted(res.items()):
    dst = 'server/plugins/PhoenixSkyblock/' + rel
    if os.path.exists(dst):
        if filecmp.cmp(src, dst, shallow=False):
            continue
        if last_change(dst) > last_change(src):
            shutil.copy(dst, src); print('bundled <- tracked (owner/server edit wins):', rel)
        else:
            shutil.copy(src, dst); print('tracked <- bundled:', rel)
    elif rel.startswith('messages/'):
        os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copy(src, dst); print('added', rel)
