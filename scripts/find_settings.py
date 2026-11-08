import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'toybox find /data/data/com.radolyn.ayugram -name settings.py']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
print('Found settings.py at:', out)

for path in out.splitlines():
    if path.strip():
        cmd2 = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', f'cat {path.strip()}']
        content = subprocess.check_output(cmd2).decode('utf-8', errors='ignore')
        print(f'=== {path} ===')
        print(content[:1500])
