import subprocess
import time

device = "192.168.31.47:40543"
apk_path = r"D:\Downloads\tg-ws-proxy\upstream\tgbypass\build\outputs\apk\debug\tgbypass-debug.apk"
plugin_path = r"D:\Downloads\tg-ws-proxy\upstream\tgbypass\src\main\assets\tg_ws_vector_bridge.py"

print("1. Installing updated TgBypass APK...")
res_install = subprocess.run(["adb", "-s", device, "install", "-r", apk_path], capture_output=True, text=True)
print("Install output:", res_install.stdout, res_install.stderr)

print("2. Pushing updated plugin to AyuGram...")
subprocess.run(["adb", "-s", device, "push", plugin_path, "/data/local/tmp/tg_ws_vector_bridge.py"])

setup_plugin_cmd = """
targetDir="/data/user/0/com.radolyn.ayugram/files/plugins"
uidGid=$(stat -c %u:%g /data/user/0/com.radolyn.ayugram)
cp /data/local/tmp/tg_ws_vector_bridge.py $targetDir/tg_ws_vector_bridge.py
cp /data/local/tmp/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin
chown $uidGid $targetDir/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin
chmod 666 $targetDir/tg_ws_vector_bridge.py $targetDir/.temp_tg_ws_vector_bridge.plugin
# Initialize or chmod sync file
touch $targetDir/tgbypass_sync.json
chown $uidGid $targetDir/tgbypass_sync.json
chmod 666 $targetDir/tgbypass_sync.json
ls -la $targetDir/tg_ws_vector_bridge.py $targetDir/tgbypass_sync.json
"""
res_push = subprocess.run(["adb", "-s", device, "shell", "su", "-mm", "-c", setup_plugin_cmd], capture_output=True, text=True)
print("Plugin setup output:", res_push.stdout, res_push.stderr)
