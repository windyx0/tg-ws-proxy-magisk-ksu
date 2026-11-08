import os, re

for root, dirs, files in os.walk('zastogram'):
    for f in files:
        if f.endswith(('.py', '.java', '.cpp', '.h', '.sh')):
            p = os.path.join(root, f)
            try:
                with open(p, encoding='utf-8', errors='ignore') as fp:
                    c = fp.read()
                    if '1353' in c or 'telegram_proxy' in c or 'proxy_server' in c or 'start_server' in c:
                        print('Found in:', p)
            except Exception:
                pass
