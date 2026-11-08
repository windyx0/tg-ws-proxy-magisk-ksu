import subprocess

dex_cmd = "strings /data/app/~~oodZ2qmwcd6j7DYQv9njAQ==/com.radolyn.ayugram-NdRpEDKXN7OCXwowa-Iyww==/*.apk | grep -i 'Plugin metadata must contain'"
res = subprocess.run(['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', dex_cmd], capture_output=True, text=True)
print("DEX search result:", res.stdout)
if not res.stdout.strip():
    print("Searching for __id__ in apk...")
    dex_cmd2 = "strings /data/app/~~oodZ2qmwcd6j7DYQv9njAQ==/com.radolyn.ayugram-NdRpEDKXN7OCXwowa-Iyww==/*.apk | grep '__id__'"
    res2 = subprocess.run(['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', dex_cmd2], capture_output=True, text=True)
    print("Dex __id__ search:", res2.stdout)
