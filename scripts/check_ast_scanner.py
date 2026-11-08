import subprocess

cmd = ['adb', '-s', '192.168.31.47:40543', 'shell', 'su', '-mm', '-c', 'head -n 60 /data/user/0/com.radolyn.ayugram/files/plugins/ElyxPlugins/kangel_plugins_manager/kpmplugin/src/security/ast_scanner.py']
res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
print("AST scanner head:\n", res.stdout)
