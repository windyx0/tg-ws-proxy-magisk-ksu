#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TgBypass Release Packager
Compiles the application APK, updates module assets, and packages the flashable zip.
"""

import os
import shutil
import subprocess
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM_DIR = os.path.join(ROOT_DIR, "upstream")
MODULE_DIR = os.path.join(ROOT_DIR, "magisk-module")
RELEASE_DIR = os.path.join(ROOT_DIR, "release")
ASSETS_DIR = os.path.join(UPSTREAM_DIR, "tgbypass", "src", "main", "assets")
APK_DEBUG_PATH = os.path.join(UPSTREAM_DIR, "tgbypass", "build", "outputs", "apk", "debug", "tgbypass-debug.apk")

VERSION = "1.2.2"

def ensure_build():
    if not os.path.exists(APK_DEBUG_PATH):
        print("[*] APK not found, building with Gradle...")
        gradlew = os.path.join(UPSTREAM_DIR, "gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.check_call([gradlew, ":tgbypass:assembleDebug"], cwd=UPSTREAM_DIR)
    else:
        print("[*] Found existing APK:", APK_DEBUG_PATH)

def package():
    os.makedirs(RELEASE_DIR, exist_ok=True)

    # 1. Update module binary and python script
    mod_bin_apk = os.path.join(MODULE_DIR, "bin", "tgbypass_vector.apk")
    mod_script = os.path.join(MODULE_DIR, "scripts", "tg_ws_vector_bridge.py")
    src_script = os.path.join(ASSETS_DIR, "tg_ws_vector_bridge.py")

    shutil.copyfile(APK_DEBUG_PATH, mod_bin_apk)
    shutil.copyfile(src_script, mod_script)
    print("[*] Updated module assets.")

    # 2. Package module zip
    zip_name = f"TgBypass-Module-v{VERSION}.zip"
    zip_path = os.path.join(RELEASE_DIR, zip_name)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for root, dirs, files in os.walk(MODULE_DIR):
            if "__pycache__" in root:
                continue
            for file in files:
                if file.endswith(".pyc"):
                    continue
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, MODULE_DIR)
                z.write(full_path, rel_path)
    print(f"[+] Flashable module packaged: {zip_path} ({os.path.getsize(zip_path)} bytes)")

    # 3. Copy standalone assets
    apk_release = os.path.join(RELEASE_DIR, f"TgBypass-v{VERSION}.apk")
    plugin_release = os.path.join(RELEASE_DIR, "tg_ws_vector_bridge.py")
    shutil.copyfile(APK_DEBUG_PATH, apk_release)
    shutil.copyfile(src_script, plugin_release)
    print(f"[+] Standalone APK: {apk_release}")
    print(f"[+] Standalone Plugin: {plugin_release}")
    print("[*] Release preparation completed successfully.")

if __name__ == "__main__":
    ensure_build()
    package()
