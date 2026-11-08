# -*- coding: utf-8 -*-
"""
TgBypass Vector Bridge Plugin for AyuGram / ExteraGram
Connects AyuGram to TgBypass / Vector Local Daemon or live ZaSto WSS tunnels.
Includes two-way real-time backend synchronization and full traffic proxying (including VoIP calls).
"""

__id__ = "tg_ws_vector_bridge"
__name__ = "TgBypass Vector Bridge"
__description__ = "Мост AyuGram к сервису TgBypass / ZaSto WSS. Двусторонняя синхронизация бэкенда и проксирование всего трафика (включая звонки)."
__author__ = "@windyx0"
__version__ = "1.2.3"
__icon__ = "Angel_Next_Door_S2_by_MoiStikiBot/19"
__app_version__ = ">=11.0.0"
__sdk_version__ = ">=1.0.0"
__category__ = ["tools", "integrations"]

import os
import json
import time
import socket
import threading
from typing import List, Any

from base_plugin import BasePlugin
from ui.bulletin import BulletinHelper
from ui.settings import Header, Text, Divider, Selector
from android_utils import run_on_ui_thread
from java import jclass

BACKEND_KEYS = [
    "tgbypass",
    "zasto_wss_edge",
    "zasto_wss_node",
    "cloudflare_dc2"
]

BACKEND_NAMES = [
    "TgBypass Daemon (127.0.0.1:1444)",
    "ZaSto WSS Cloudflare (amberwick)",
    "ZaSto WSS Резерв (brackencombe)",
    "Cloudflare Front DC2 (kws2.pclead.co.uk)"
]

BACKENDS = {
    "tgbypass": {
        "name": "TgBypass Daemon (127.0.0.1:1444)",
        "type": "MTPROTO",
        "host": "127.0.0.1",
        "port": 1444,
        "secret": "dd7ee8902211954d7e8235f108a6313d42",
        "user": "",
        "pass": ""
    },
    "zasto_wss_edge": {
        "name": "ZaSto WSS Cloudflare (amberwick)",
        "type": "MTPROTO",
        "host": "edge.amberwick.workers.dev",
        "port": 443,
        "secret": "dd7ee8902211954d7e8235f108a6313d42",
        "user": "",
        "pass": ""
    },
    "zasto_wss_node": {
        "name": "ZaSto WSS Резерв (brackencombe)",
        "type": "MTPROTO",
        "host": "nodeone.brackencombe.workers.dev",
        "port": 443,
        "secret": "dd7ee8902211954d7e8235f108a6313d42",
        "user": "",
        "pass": ""
    },
    "cloudflare_dc2": {
        "name": "Cloudflare Front DC2 (kws2)",
        "type": "MTPROTO",
        "host": "kws2.pclead.co.uk",
        "port": 443,
        "secret": "dd7ee8902211954d7e8235f108a6313d42",
        "user": "",
        "pass": ""
    }
}

DEFAULT_BACKEND = "tgbypass"


def log(msg: str):
    try:
        Log = jclass("android.util.Log")
        Log.i("TgWsBridge", str(msg))
    except Exception:
        print(f"[TgWsBridge] {msg}")


def measure_endpoint_ping(host: str, port: int, timeout=0.6) -> float:
    try:
        t0 = time.time()
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(timeout)
        s.connect((host, port))
        s.close()
        return (time.time() - t0) * 1000.0
    except Exception:
        return -1.0


class TgWsVectorBridgePlugin(BasePlugin):
    def __init__(self):
        super().__init__()
        self._running = False
        self._watchdog_thread = None
        self._last_ping = -1.0
        self._daemon_ok = False
        self._active_backend_key = DEFAULT_BACKEND
        self._last_sync_timestamp = 0.0
        self._sync_file_path = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "tgbypass_sync.json"
        )

    def _read_sync_file(self):
        try:
            if os.path.exists(self._sync_file_path):
                with open(self._sync_file_path, "r", encoding="utf-8") as f:
                    return json.load(f)
        except Exception as e:
            log(f"[TgWsBridge] read sync error: {e}")
        return None

    def _write_sync_file(self, key: str, idx: int, timestamp: float, source: str = "ayugram"):
        try:
            data = {
                "backend": str(key),
                "backend_idx": int(idx),
                "timestamp": int(timestamp),
                "source": str(source)
            }
            with open(self._sync_file_path, "w", encoding="utf-8") as f:
                json.dump(data, f)
            try:
                os.chmod(self._sync_file_path, 0o666)
            except Exception:
                pass
        except Exception as e:
            log(f"[TgWsBridge] write sync error: {e}")

    def _send_broadcast_sync(self, key: str, idx: int, timestamp: float):
        try:
            Intent = jclass("android.content.Intent")
            ApplicationLoader = jclass("org.telegram.messenger.ApplicationLoader")
            ctx = ApplicationLoader.applicationContext
            if ctx is not None:
                intent = Intent("com.windyx0.tgbypass.ACTION_BACKEND_SYNC")
                intent.putExtra("backend", str(key))
                intent.putExtra("backend_idx", int(idx))
                intent.putExtra("timestamp", int(timestamp))
                intent.putExtra("source", "ayugram")
                ctx.sendBroadcast(intent)
                log(f"[TgWsBridge] Sent ACTION_BACKEND_SYNC: backend={key}")
        except Exception as e:
            log(f"[TgWsBridge] send broadcast error: {e}")

    def on_plugin_load(self):
        self._running = True
        log("[TgWsBridge] Plugin loaded. Initializing sync and bridge daemon check...")

        # Sync from external file if present
        sync_data = self._read_sync_file()
        if sync_data:
            s_key = sync_data.get("backend")
            s_idx = sync_data.get("backend_idx")
            s_ts = sync_data.get("timestamp", 0)
            if s_key in BACKENDS:
                self._active_backend_key = s_key
                self._last_sync_timestamp = float(s_ts)
                if s_idx is not None and 0 <= s_idx < len(BACKEND_KEYS):
                    self.set_setting("backend_idx", s_idx)
        else:
            saved_idx = self.get_setting("backend_idx", 0)
            if 0 <= saved_idx < len(BACKEND_KEYS):
                self._active_backend_key = BACKEND_KEYS[saved_idx]

        b = self._get_active_backend()
        p = measure_endpoint_ping(b["host"], b["port"], timeout=0.4)
        if p >= 0:
            self._last_ping = p
            self._daemon_ok = True

        self._watchdog_thread = threading.Thread(
            target=self._watchdog_loop, daemon=True, name="TgWsBridgeWatchdog"
        )
        self._watchdog_thread.start()

    def on_plugin_unload(self):
        self._running = False
        log("[TgWsBridge] Plugin unloaded.")

    def _get_active_backend(self):
        return BACKENDS.get(self._active_backend_key, BACKENDS[DEFAULT_BACKEND])

    def _apply_proxy(self, notify_ui=False, backend_key=None):
        if backend_key:
            self._active_backend_key = backend_key
        b = self._get_active_backend()
        host = b["host"]
        port = b["port"]
        secret = b["secret"]
        user = b["user"]
        pwd = b["pass"]
        b_type = b["type"]

        try:
            SharedConfig = jclass("org.telegram.messenger.SharedConfig")
            ProxyInfoClass = jclass("org.telegram.messenger.SharedConfig$ProxyInfo")

            existing = None
            if SharedConfig.proxyList:
                for i in range(SharedConfig.proxyList.size()):
                    p = SharedConfig.proxyList.get(i)
                    try:
                        addr = str(p.address) if hasattr(p, "address") else str(getattr(getattr(p, "settings", None), "address", ""))
                        p_port = int(p.port) if hasattr(p, "port") else int(getattr(getattr(p, "settings", None), "port", 0))
                        if addr == host and p_port == port:
                            existing = p
                            break
                    except Exception as e:
                        log(f"[TgWsBridge] proxyList inspect error: {e}")

            info = existing
            if info is None:
                try:
                    ProxySettingsClass = jclass("org.telegram.utils.proxy.ProxySettings")
                    ProxyTypeClass = jclass("org.telegram.utils.proxy.ProxySettings$Type")
                    target_type = None
                    for const in ProxyTypeClass.values():
                        if b_type in str(const).upper():
                            target_type = const
                            break
                    if target_type is None:
                        target_type = ProxyTypeClass.values()[0]

                    settings_obj = ProxySettingsClass(target_type, host, port, user, pwd, secret)
                    info = ProxyInfoClass(settings_obj)
                    log(f"[TgWsBridge] Created ProxyInfo ({b_type}) with ProxySettings successfully")
                except Exception as e1:
                    log(f"[TgWsBridge] ProxySettings attempt error: {e1}")
                    try:
                        info = ProxyInfoClass(host, port, user, pwd, secret)
                    except Exception as e2:
                        log(f"[TgWsBridge] Classic ProxyInfo attempt error: {e2}")

                if info is not None:
                    SharedConfig.addProxy(info)

            if info is not None:
                try:
                    SharedConfig.currentProxy = info
                except Exception as e_cur:
                    log(f"[TgWsBridge] currentProxy set error: {e_cur}")

                # Enable proxy for calls (VoIP) in SharedConfig
                try:
                    SharedConfig.useProxyForCalls = True
                except Exception:
                    pass

                if hasattr(SharedConfig, "saveProxyList"):
                    SharedConfig.saveProxyList()

            # Enable proxy and call proxy globally in MessagesController
            try:
                MessagesController = jclass("org.telegram.messenger.MessagesController")
                prefs = MessagesController.getGlobalMainSettings()
                editor = prefs.edit()
                editor.putBoolean("proxy_enabled", True)
                editor.putBoolean("proxy_calls", True)
                if hasattr(editor, "apply"):
                    editor.apply()
                else:
                    editor.commit()
            except Exception as e_mc:
                log(f"[TgWsBridge] GlobalMainSettings proxy_enabled error: {e_mc}")

            # Notify ConnectionsManager for ALL active accounts so entire Telegram traffic is proxied
            try:
                ConnectionsManager = jclass("org.telegram.tgnet.ConnectionsManager")
                UserConfig = jclass("org.telegram.messenger.UserConfig")
                max_acc = getattr(UserConfig, "MAX_ACCOUNT_COUNT", 4)
                for acc_idx in range(max_acc):
                    try:
                        uconf = UserConfig.getInstance(acc_idx)
                        if uconf.isClientActivated():
                            ConnectionsManager.getInstance(acc_idx).setProxySettings(True, host, port, user, pwd, secret)
                    except Exception:
                        pass
                curr_acc = UserConfig.selectedAccount
                ConnectionsManager.getInstance(curr_acc).setProxySettings(True, host, port, user, pwd, secret)
            except Exception as e_cm:
                log(f"[TgWsBridge] CM instance error: {e_cm}")

            NotificationCenter = jclass("org.telegram.messenger.NotificationCenter")
            NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.proxySettingsChanged
            )

            if notify_ui:
                run_on_ui_thread(lambda: BulletinHelper.show_success(f"Подключено: {b['name']}"))

            log(f"[TgWsBridge] Proxy applied: {host}:{port} (calls included)")
            return True
        except Exception as e:
            err_str = str(e)
            log(f"[TgWsBridge] _apply_proxy failure: {err_str}")
            if notify_ui:
                run_on_ui_thread(lambda: BulletinHelper.show_error(f"Ошибка прокси: {err_str}"))
            return False

    def _is_proxy_configured_and_active(self) -> bool:
        try:
            SharedConfig = jclass("org.telegram.messenger.SharedConfig")
            b = self._get_active_backend()
            cur = SharedConfig.currentProxy
            if cur is None:
                return False
            addr = str(cur.address) if hasattr(cur, "address") else str(getattr(getattr(cur, "settings", None), "address", ""))
            p_port = int(cur.port) if hasattr(cur, "port") else int(getattr(getattr(cur, "settings", None), "port", 0))
            return (addr == b["host"] and p_port == b["port"])
        except Exception:
            return False

    def _watchdog_loop(self):
        first_run = True
        while self._running:
            try:
                # 1. Check for sync updates written by TgBypass app
                sync_data = self._read_sync_file()
                if sync_data:
                    ts = float(sync_data.get("timestamp", 0))
                    src = str(sync_data.get("source", ""))
                    key = sync_data.get("backend")
                    idx = sync_data.get("backend_idx", -1)
                    if ts > self._last_sync_timestamp and src != "ayugram" and key in BACKENDS and key != self._active_backend_key:
                        self._last_sync_timestamp = ts
                        self._active_backend_key = key
                        if 0 <= idx < len(BACKEND_KEYS):
                            self.set_setting("backend_idx", idx)
                        log(f"[TgWsBridge] Synced from TgBypass app: switched to {key}")
                        self._apply_proxy(notify_ui=False, backend_key=key)
                        b_name = BACKENDS[key]["name"]
                        run_on_ui_thread(lambda: BulletinHelper.show_success(f"Синхронизировано:\n{b_name}"))

                # 2. Measure active endpoint ping and ensure connected
                b = self._get_active_backend()
                ping = measure_endpoint_ping(b["host"], b["port"])
                self._last_ping = ping
                self._daemon_ok = (ping >= 0)

                if self._daemon_ok:
                    if first_run or not self._is_proxy_configured_and_active():
                        log(f"[TgWsBridge] Backend {b['name']} reachable. Applying...")
                        self._apply_proxy(notify_ui=first_run)
                        first_run = False
            except Exception as e:
                log(f"[TgWsBridge] Watchdog cycle error: {e}")

            for _ in range(2):
                if not self._running:
                    break
                time.sleep(1)

    def trigger_reconnect(self):
        b = self._get_active_backend()
        ping = measure_endpoint_ping(b["host"], b["port"])
        self._last_ping = ping
        self._daemon_ok = (ping >= 0)
        if self._daemon_ok:
            self._apply_proxy(notify_ui=True)
        else:
            run_on_ui_thread(lambda: BulletinHelper.show_error(f"{b['name']} недоступен."))

    def switch_backend(self, key: str):
        self._active_backend_key = key
        b = self._get_active_backend()
        ping = measure_endpoint_ping(b["host"], b["port"])
        self._last_ping = ping
        self._daemon_ok = (ping >= 0)
        self._apply_proxy(notify_ui=True, backend_key=key)

    def _on_backend_selected(self, new_idx: int):
        if 0 <= new_idx < len(BACKEND_KEYS):
            key = BACKEND_KEYS[new_idx]
            self.set_setting("backend_idx", new_idx)
            now = time.time() * 1000.0
            self._last_sync_timestamp = now
            # Bidirectional sync: write sync state file & send broadcast
            self._write_sync_file(key, new_idx, now, "ayugram")
            self._send_broadcast_sync(key, new_idx, now)
            self.switch_backend(key)

    def create_settings(self) -> List[Any]:
        # Sync latest index before displaying Selector
        sync_data = self._read_sync_file()
        if sync_data:
            s_key = sync_data.get("backend")
            s_idx = sync_data.get("backend_idx")
            if s_key in BACKENDS and s_idx is not None and 0 <= s_idx < len(BACKEND_KEYS):
                self._active_backend_key = s_key
                self.set_setting("backend_idx", s_idx)

        b = self._get_active_backend()
        ping = measure_endpoint_ping(b["host"], b["port"], timeout=0.35)
        if ping >= 0:
            self._last_ping = ping
            self._daemon_ok = True
        elif self._last_ping < 0:
            self._daemon_ok = False

        if self._daemon_ok:
            if self._last_ping < 200:
                ping_badge = f"🟢 {self._last_ping:.0f} ms"
            elif self._last_ping < 500:
                ping_badge = f"🟡 {self._last_ping:.0f} ms"
            else:
                ping_badge = f"🔴 {self._last_ping:.0f} ms"
            status_str = f"Активен ({ping_badge})"
        else:
            status_str = "🔴 Ожидание демона..."

        current_idx = self.get_setting("backend_idx", 0)

        items = [
            Header(text="TgBypass Vector Bridge"),
            Text(
                text=f"Статус: {status_str}",
                subtext=f"Текущий узел: {b['name']}",
                icon="msg_info",
                on_click=lambda _: self.trigger_reconnect(),
            ),
            Divider(),
            Header(text="Выбор бэкенда обхода"),
            Selector(
                key="backend_idx",
                text="Активный прокси-сервис",
                default=current_idx,
                items=BACKEND_NAMES,
                icon="msg_select",
                on_change=self._on_backend_selected
            ),
            Divider(),
            Header(text="Действия"),
            Text(
                text="Принудительно переподключить",
                subtext="Применить текущий прокси заново в AyuGram",
                icon="msg_retry",
                on_click=lambda _: self.trigger_reconnect(),
            )
        ]

        return items

__plugin__ = TgWsVectorBridgePlugin
