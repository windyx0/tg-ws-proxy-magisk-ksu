#!/sbin/sh
##########################################################################################
#
# TgBypass All-In-One - Magisk / KernelSU / APatch Module Installer
# Packages standalone proxy daemon + LSPosed (Vector) module + Network Turbo
#
##########################################################################################

ui_print " "
ui_print "----------------------------------"
ui_print "  TgBypass All-In-One (v1.2.2)"
ui_print "  Author: windyx0"
ui_print "  Magisk / KernelSU / APatch"
ui_print "----------------------------------"
ui_print " "

# 1. Architecture check
if [ "$ARCH" != "arm64" ]; then
    ui_print "[!] Error: Unsupported arch: $ARCH"
    ui_print "[!] Requires arm64-v8a"
    abort "Architecture not supported"
fi

ui_print "[*] Target arch: arm64-v8a (OK)"

# 2. Extract and set permissions
ui_print "[*] Setting file permissions..."
set_perm_recursive "$MODPATH/scripts" 0 0 0755 0755
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/system/bin/tg-ws-proxy" 0 0 0755

# 3. Initialize configuration directory in /data/adb/tg-ws-proxy
CONF_DIR="/data/adb/tg-ws-proxy"
mkdir -p "$CONF_DIR"

if [ ! -f "$CONF_DIR/autostart" ]; then
    echo "1" > "$CONF_DIR/autostart"
fi

if [ ! -f "$CONF_DIR/port" ]; then
    echo "1444" > "$CONF_DIR/port"
fi

if [ ! -f "$CONF_DIR/secret" ]; then
    echo "ee111111111111111111111111111111117777772e636c6f7564666c6172652e636f6d" > "$CONF_DIR/secret"
fi

if [ ! -f "$CONF_DIR/target_tg" ]; then
    echo "auto" > "$CONF_DIR/target_tg"
fi

ui_print "[*] Config: /data/adb/tg-ws-proxy"

# 4. Companion Proxy APK handling
APP_ID="com.windyx0.twgsproxy"
APK_PATH="$MODPATH/bin/tgwsproxy.apk"

if pm path "$APP_ID" >/dev/null 2>&1; then
    ui_print "[+] Proxy backend ready"
else
    if [ -f "$APK_PATH" ]; then
        ui_print "[*] Installing proxy backend..."
        pm install -r "$APK_PATH" >/dev/null 2>&1
        if pm path "$APP_ID" >/dev/null 2>&1; then
            ui_print "[+] Proxy backend ready"
        fi
    fi
fi

# Pre-authorize root in KernelSU if present
if [ -f /data/adb/ksud ] && pm path "$APP_ID" >/dev/null 2>&1; then
    APP_UID=$(stat -c %u "/data/data/$APP_ID" 2>/dev/null)
    if [ -n "$APP_UID" ]; then
        ui_print "[*] Pre-authorizing root access..."
        cat <<EOF > /data/local/tmp/ksu_prof.json
{
  "key": "$APP_ID",
  "uid": $APP_UID,
  "stored": true,
  "allow_su": true,
  "root": {
    "use_default": true,
    "uid": 0,
    "gid": 0,
    "groups": [],
    "capabilities": [],
    "selinux_domain": "u:r:ksu:s0",
    "namespace": "inherited",
    "flags": [
      "NO_NEW_PRIVS"
    ]
  }
}
EOF
        /data/adb/ksud profile set /data/local/tmp/ksu_prof.json >/dev/null 2>&1
        rm -f /data/local/tmp/ksu_prof.json
    fi
fi

# 5. LSPosed / Vector Module APK handling
VECTOR_APP_ID="com.windyx0.tgbypass"
VECTOR_APK_PATH="$MODPATH/bin/tgbypass_vector.apk"

if [ -f "$VECTOR_APK_PATH" ]; then
    ui_print "[*] Installing Vector APK..."
    pm install -r "$VECTOR_APK_PATH" >/dev/null 2>&1
    if pm path "$VECTOR_APP_ID" >/dev/null 2>&1; then
        ui_print "[+] TgBypass APK installed"
        
        # Pre-authorize root in KernelSU if present
        if [ -x /data/adb/ksu/bin/ksud ]; then
            V_UID=$(stat -c %u "/data/data/$VECTOR_APP_ID" 2>/dev/null)
            if [ -n "$V_UID" ]; then
                /data/adb/ksu/bin/ksud profile set "{\"key\":\"$VECTOR_APP_ID\",\"uid\":$V_UID,\"stored\":true,\"allow_su\":true}" >/dev/null 2>&1
            fi
        fi

        # Auto-activate in Vector CLI if Vector is present
        if [ -x /data/adb/lspd/cli ]; then
            ui_print "[*] Activating in Vector..."
            /data/adb/lspd/cli modules enable "$VECTOR_APP_ID" >/dev/null 2>&1
            /data/adb/lspd/cli scope add "$VECTOR_APP_ID" org.telegram.messenger/0 org.telegram.messenger.web/0 app.exteragram.messenger/0 org.telegram.plus/0 org.thunderdog.challegram/0 >/dev/null 2>&1
            /data/adb/lspd/cli scope rm "$VECTOR_APP_ID" com.radolyn.ayugram/0 >/dev/null 2>&1
            ui_print "[+] Module enabled in Vector"
            ui_print "[+] Telegram scope configured"
        fi

        # Auto-install AyuGram Bridge Plugin
        if [ -d /data/data/com.radolyn.ayugram/files/plugins ] && [ -f "$MODPATH/scripts/tg_ws_vector_bridge.py" ]; then
            ui_print "[*] Deploying AyuGram plugin..."
            AYU_UID=$(stat -c %u:%g /data/data/com.radolyn.ayugram 2>/dev/null || echo "10334:10334")
            cp "$MODPATH/scripts/tg_ws_vector_bridge.py" /data/data/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py
            cp "$MODPATH/scripts/tg_ws_vector_bridge.py" /data/data/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin
            chown $AYU_UID /data/data/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py /data/data/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin
            chmod 600 /data/data/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py /data/data/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin
            AYU_PREFS="/data/data/com.radolyn.ayugram/shared_prefs/plugin_settings.xml"
            if [ -f "$AYU_PREFS" ] && ! grep -q "plugin_enabled_tg_ws_vector_bridge" "$AYU_PREFS"; then
                sed -i 's|</map>|    <boolean name="plugin_enabled_tg_ws_vector_bridge" value="true" />\n</map>|' "$AYU_PREFS"
                chown $AYU_UID "$AYU_PREFS"
            fi
            ui_print "[+] AyuGram bridge enabled"
        fi
    fi
fi

# 6. Suppress notification automatically
ui_print "[*] Silencing notifications..."
for aid in "com.windyx0.twgsproxy" "io.github.regstar2.tgwsproxy" "com.windyx0.tgbypass"; do
    if pm list packages 2>/dev/null | grep -q "$aid"; then
        appops set "$aid" POST_NOTIFICATION ignore >/dev/null 2>&1
        appops set "$aid" 11 1 >/dev/null 2>&1
        cmd notification set_bubbles "$aid" 0 >/dev/null 2>&1
        pm revoke "$aid" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
        dumpsys deviceidle whitelist +"$aid" >/dev/null 2>&1
    fi
done

# 7. Apply network acceleration
ui_print "[*] Applying kernel TCP turbo..."
if [ -f "$MODPATH/scripts/net_accelerator.sh" ]; then
    sh "$MODPATH/scripts/net_accelerator.sh" >/dev/null 2>&1
fi

# Ensure clean SELinux context
chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null

ui_print " "
ui_print "----------------------------------"
ui_print "  Installation completed!"
ui_print " "
ui_print "  Features active:"
ui_print "  - TCP BBR, FQ, TFO & 16MB buffer"
ui_print "  - Autostart daemon (port 1444)"
ui_print "  - Vector module active & scoped"
ui_print "  - AyuGram bridge connected"
ui_print "  - Silent background service"
ui_print " "
ui_print "  CLI: su -c tg-ws-proxy"
ui_print "----------------------------------"
ui_print " "
