import re

with open('zastogram/TMessagesProj/jni/tgnet/wss/WssSocket.cpp', encoding='utf-8', errors='ignore') as f:
    text = f.read()

urls = re.findall(r'[a-zA-Z0-9_\-\.]+\.(?:org|dev|com|ru|co\.uk)', text)
print('Endpoints in WssSocket.cpp:', set(urls))

# search for default port
ports = re.findall(r'port\s*=\s*(\d+)', text)
print('Ports:', ports)
