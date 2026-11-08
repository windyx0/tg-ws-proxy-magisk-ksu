import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'grep -rn "Selector" /data/data/com.radolyn.ayugram/files/plugins/']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
print(out)
