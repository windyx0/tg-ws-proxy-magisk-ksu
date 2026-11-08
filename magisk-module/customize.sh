#!/sbin/sh
##########################################################################################
#
# TgBypass All-In-One - Magisk / KernelSU / APatch Module Installer
# Unified MTProto Proxy Daemon + LSPosed Hook + Network Turbo
#
##########################################################################################

ui_print " "
ui_print "=============================="
ui_print "  TgBypass All-In-One"
ui_print "  Version 1.2.2"
ui_print "  Author: windyx0"
ui_print "  Magisk / KernelSU / APatch"
ui_print "=============================="
ui_print " "

# 1. Architecture check
if [ "$ARCH" != "arm64" ]; then
    ui_print "[!] Error: Unsupported $ARCH"
    ui_print "[!] arm64-v8a required"
    abort "Architecture not supported"
fi

ui_print "[*] Target: arm64-v8a (OK)"

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

ui_print "[*] Config initialized"

# 4. Install Unified TgBypass APK (Vector Module + Proxy Backend)
APP_ID="com.windyx0.tgbypass"
APK_PATH="$MODPATH/bin/tgbypass_vector.apk"

if [ -f "$APK_PATH" ]; then
    ui_print "[*] Installing TgBypass APK..."
    pm install -r "$APK_PATH" >/dev/null 2>&1
    if pm path "$APP_ID" >/dev/null 2>&1; then
        ui_print "[+] TgBypass APK installed"
        
        # Pre-authorize root in KernelSU if present
        if [ -x /data/adb/ksu/bin/ksud ]; then
            V_UID=$(stat -c %u "/data/data/$APP_ID" 2>/dev/null)
            if [ -n "$V_UID" ]; then
                /data/adb/ksu/bin/ksud profile set "{\"key\":\"$APP_ID\",\"uid\":$V_UID,\"stored\":true,\"allow_su\":true}" >/dev/null 2>&1
                ui_print "[+] KernelSU root authorized"
            fi
        fi

        # Auto-activate in Vector CLI if Vector is present
        if [ -x /data/adb/lspd/cli ]; then
            ui_print "[*] Activating in Vector..."
            /data/adb/lspd/cli modules enable "$APP_ID" >/dev/null 2>&1
            /data/adb/lspd/cli scope add "$APP_ID" org.telegram.messenger/0 org.telegram.messenger.web/0 app.exteragram.messenger/0 org.telegram.plus/0 org.thunderdog.challegram/0 >/dev/null 2>&1
            /data/adb/lspd/cli scope rm "$APP_ID" com.radolyn.ayugram/0 >/dev/null 2>&1
            ui_print "[+] Vector hook enabled"
            ui_print "[+] Telegram scope set"
        fi

        # Auto-install AyuGram Bridge Plugin
        if [ -d /data/data/com.radolyn.ayugram/files/plugins ] && [ -f "$MODPATH/scripts/tg_ws_vector_bridge.py" ]; then
            ui_print "[*] Deploying AyuGram bridge..."
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

# 5. Suppress notification automatically
ui_print "[*] Silencing notifications..."
for aid in "com.windyx0.tgbypass" "com.windyx0.twgsproxy"; do
    if pm list packages 2>/dev/null | grep -q "$aid"; then
        appops set "$aid" POST_NOTIFICATION ignore >/dev/null 2>&1
        appops set "$aid" 11 1 >/dev/null 2>&1
        cmd notification set_bubbles "$aid" 0 >/dev/null 2>&1
        pm revoke "$aid" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
        dumpsys deviceidle whitelist +"$aid" >/dev/null 2>&1
    fi
done

# 6. Apply network acceleration
ui_print "[*] Applying TCP turbo..."
if [ -f "$MODPATH/scripts/net_accelerator.sh" ]; then
    sh "$MODPATH/scripts/net_accelerator.sh" >/dev/null 2>&1
fi

# Ensure clean SELinux context
chcon -R u:object_r:system_file:s0 "$MODPATH" 2>/dev/null

ui_print " "
ui_print "=============================="
ui_print "  Installation complete!"
ui_print " "
ui_print "  Active features:"
ui_print "  - Kernel TCP BBR & 16MB turbo"
ui_print "  - Built-in daemon (port 1444)"
ui_print "  - Vector Hook (auto-route)"
ui_print "  - AyuGram Bridge plugin"
ui_print "  - Silent background service"
ui_print " "
ui_print "  CLI: su -c tg-ws-proxy"
ui_print "=============================="
ui_print " "
