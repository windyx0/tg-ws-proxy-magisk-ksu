with open('zastogram/TMessagesProj/jni/tgnet/wss/WssSocket.cpp', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()
for i in range(20, 110):
    if i < len(lines):
        print(f"L{i+1}: {lines[i].strip()}")
