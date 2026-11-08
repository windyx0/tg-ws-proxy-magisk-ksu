import subprocess, zipfile

apk_path = subprocess.check_output(['adb', '-s', '192.168.31.47:40543', 'shell', 'pm', 'path', 'com.radolyn.ayugram']).decode('utf-8').strip().replace('package:', '')
print('APK path:', apk_path)

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', f'unzip -l {apk_path} | grep -i settings']
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
print(out)
