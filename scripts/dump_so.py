import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'strings /data/data/com.radolyn.ayugram/files/chaquopy/plugins-sdk/ui/settings.so | grep -E "^[A-Z][a-zA-Z0-9]+$"']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
classes = set(line for line in out.splitlines() if len(line) < 30)
print('Symbols in settings.so:', sorted(classes))
