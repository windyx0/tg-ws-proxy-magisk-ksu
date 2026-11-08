with open('zastogram/TMessagesProj/jni/tgnet/wss/WssSocket.cpp', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if any(k in line.lower() for k in ['1353', 'telegram_proxy', 'proxy_server', 'start_server']):
        print(f"L{i+1}: {line.strip()}")
