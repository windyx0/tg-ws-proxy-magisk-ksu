#!/system/bin/sh
PREFS="/data/data/com.radolyn.ayugram/shared_prefs/plugin_settings.xml"
PLUGIN_SRC="/data/data/com.radolyn.ayugram/files/plugins/tg_ws_vector_bridge.py"
PLUGIN_TEMP="/data/data/com.radolyn.ayugram/files/plugins/.temp_tg_ws_vector_bridge.plugin"

if [ -f "$PLUGIN_SRC" ]; then
    cp "$PLUGIN_SRC" "$PLUGIN_TEMP"
    chown 10334:10334 "$PLUGIN_TEMP"
    chmod 600 "$PLUGIN_TEMP"
fi

if [ -f "$PREFS" ]; then
    if ! grep -q "plugin_enabled_tg_ws_vector_bridge" "$PREFS"; then
        sed -i 's|</map>|    <boolean name="plugin_enabled_tg_ws_vector_bridge" value="true" />\n</map>|' "$PREFS"
        chown 10334:10334 "$PREFS"
    fi
fi
