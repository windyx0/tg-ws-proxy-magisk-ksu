import subprocess

device = "192.168.31.47:40543"
src = r"D:\Downloads\tg-ws-proxy\upstream\tgbypass\src\main\assets\tg_ws_vector_bridge.py"

subprocess.run(["adb", "-s", device, "push", src, "/data/local/tmp/tg_ws_vector_bridge.py"])

single_cmd = "cp /data/local/tmp/tg_ws_vector_bridge.py /data/user/0/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py && cp /data/local/tmp/tg_ws_vector_bridge.py /data/user/0/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin && chown 10334:10334 /data/user/0/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py /data/user/0/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin && chmod 666 /data/user/0/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py /data/user/0/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin"

res = subprocess.run(["adb", "-s", device, "shell", "su", "-mm", "-c", single_cmd], capture_output=True, text=True)
print("Result stdout:", res.stdout)
print("Result stderr:", res.stderr)

# verify with head
res2 = subprocess.run(["adb", "-s", device, "shell", "su", "-mm", "-c", "head -n 30 /data/user/0/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py"], capture_output=True, text=True)
print("Head:\n", res2.stdout)
