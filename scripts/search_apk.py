import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'find /data/app/ -name "*.apk" | grep -i ayugram']
res = subprocess.run(cmd, capture_output=True, text=True)
apk_path = res.stdout.strip()
print("AyuGram APK path:", apk_path)

# search inside base.apk using strings or unzip
cmd_search = f'strings {apk_path} | grep "Plugin metadata must contain"'
res_search = subprocess.run(['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', cmd_search], capture_output=True, text=True)
print("Search result:", res_search.stdout)
