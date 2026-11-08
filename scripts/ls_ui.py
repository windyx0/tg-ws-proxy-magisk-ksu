import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'toybox ls -la /data/data/com.radolyn.ayugram/files/chaquopy/plugins-sdk/ui']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
print(out)
