English | [Русский](README_RU.md)

# TgBypass All-In-One

Comprehensive Telegram censorship bypass suite and Linux kernel network accelerator for Android.

TgBypass combines an autonomous background WebSocket tunnel daemon, a Vector (LSPosed) socket hook module, Linux network stack optimizations (TCP BBR, TFO, QoS), and an AyuGram bridge plugin into a unified ecosystem.

---

## Architecture Overview

The system consists of four interconnected components:

```
+-------------------------------------------------------------------------+
|                                User Interface                           |
|                  TgBypass Dashboard (com.windyx0.tgbypass)              |
|        Material 3 UI, health monitoring, backend selector, ping         |
+-------------------------------------------------------------------------+
        |                                                 |
        v                                                 v
+-------------------------------+       +---------------------------------+
|     Vector (LSPosed) Module   |       |     AyuGram Bridge Plugin       |
| Socket-level traffic hooks    |       |   (tg_ws_vector_bridge.py)      |
| Supported: Telegram, extera,  |       | Real-time bidirectional sync    |
| Telegram X, Plus, Nekogram    |       | Full proxying including VoIP    |
+-------------------------------+       +---------------------------------+
        |                                                 |
        +-----------------------+-------------------------+
                                |
                                v
+-------------------------------------------------------------------------+
|                Background Proxy Service (127.0.0.1:1444)                |
|             Autonomous system daemon with boot persistence              |
+-------------------------------------------------------------------------+
                                |
                                v
+-------------------------------------------------------------------------+
|                  Linux Kernel Network Turbo (Root)                      |
|       TCP BBR + FQ pacing, TCP Fast Open, 16MB buffers, DSCP AF41       |
+-------------------------------------------------------------------------+
                                |
                                v
+-------------------------------------------------------------------------+
|                            Upstream Backends                            |
|       ZaSto WSS Edge, Cloudflare Front DC2, ZaSto Reserve Node          |
+-------------------------------------------------------------------------+
```

---

## Key Features

- **One-Click Automated Setup**: The "Auto-Repair and Configure All Clients" action in the dashboard automatically verifies and repairs the LSPosed module, configures scope for all installed clients, deploys the bridge plugin into AyuGram, and verifies local daemon responsiveness.
- **Kernel Network Turbo**:
  - Activates TCP BBR congestion control alongside the FQ queue discipline.
  - Enables TCP Fast Open (TFO) for both client and server sockets to eliminate the initial handshake RTT delay.
  - Disables congestion window reset on idle (`tcp_slow_start_after_idle = 0`) to maintain full burst speed.
  - Expands network read/write buffers up to 16 MB for smooth high-bitrate media and video streaming.
  - Prioritizes Telegram packets via QoS (DSCP AF41 / Low Delay).
  - Bypasses conntrack connection tracking overhead (`NOTRACK`) on the loopback interface.
  - Automatically probes Path MTU to avoid packet fragmentation on mobile carrier networks.
- **Bidirectional Backend Synchronization**: Switching between active backends (Local Daemon, ZaSto WSS, Cloudflare) immediately synchronizes across the TgBypass dashboard and the AyuGram plugin using an internal broadcast bus.
- **Complete Traffic Routing**: Routes all types of Telegram traffic including text messages, channels, large files, stories, and encrypted VoIP voice and video calls.
- **Integrated Diagnostic Engine**: Measures real-time latency (RTT) against Cloudflare edge datacenters and ZaSto tunnels with clear status thresholds.
- **Flicker-Free Interface**: Persists component health state across process lifecycles, preventing false negative error flashes during background verification.

---

## Supported Telegram Clients

| Client | Package Name | Integration Method | VoIP Call Routing |
|---|---|---|---|
| AyuGram | `com.radolyn.ayugram` | Python Bridge Plugin | Supported |
| Telegram Official | `org.telegram.messenger` | Vector (LSPosed) Hook | Supported |
| Telegram Direct (Web) | `org.telegram.messenger.web` | Vector (LSPosed) Hook | Supported |
| exteraGram | `app.exteragram.messenger` | Vector Hook / Plugin | Supported |
| Telegram X | `org.thunderdog.challegram` | Vector (LSPosed) Hook | Supported |
| Plus Messenger | `org.telegram.plus` | Vector (LSPosed) Hook | Supported |
| Nekogram | `tw.nekomimi.nekogram` | Vector (LSPosed) Hook | Supported |
| Cherrygram | `cc.modery.cherrygram` | Vector (LSPosed) Hook | Supported |

---

## Supported Backends

1. **TgBypass Local Daemon** (`127.0.0.1:1444`):
   Autonomous on-device MTProto / WebSocket tunnel.
2. **ZaSto WSS Cloudflare Edge** (`edge.amberwick.workers.dev:443`):
   High-performance tunnel routed through Cloudflare Workers edge nodes.
3. **ZaSto WSS Reserve Node** (`nodeone.brackencombe.workers.dev:443`):
   Redundant distributed tunnel with independent upstream peering.
4. **Cloudflare Direct Front DC2** (`kws2.pclead.co.uk:443`):
   Direct European front-end transport located in Amsterdam.

---

## Installation Methods

### Method 1: Magisk / KernelSU / APatch Module (Recommended)

Installs all components via a single flashable zip:
1. Download the latest `TgBypass-Module-v1.2.2.zip` from Releases.
2. Open your root manager (Magisk, KernelSU, or APatch).
3. Navigate to Modules -> Install from storage.
4. Select the archive and confirm installation.
5. Reboot your device.

The module automatically:
- Installs the companion manager app `TgBypass-v1.2.2.apk`.
- Activates the module inside LSPosed / Vector and configures application scopes.
- Deploys and enables the bridge plugin in AyuGram (if detected).
- Applies TCP BBR and kernel buffer optimizations.
- Starts the local proxy daemon on port 1444.

### Method 2: Standalone APK + LSPosed

1. Install `TgBypass-v1.2.2.apk`.
2. Open LSPosed (or Vector Framework).
3. Enable the **TgBypass** module.
4. In the module Scope settings, check your installed Telegram clients (exclude AyuGram).
5. Launch TgBypass and tap **Auto-repair and configure all clients**.

### Method 3: AyuGram Plugin Only (Non-Root)

For devices without root access:
1. Copy `tg_ws_vector_bridge.py` to your device.
2. In AyuGram, open: Settings -> AyuGram Settings -> Plugins.
3. Tap the add button (+) and select `tg_ws_vector_bridge.py`.
4. Enable the plugin in the list.

---

## Root CLI Commands

When the root module is installed, the `tg-ws-proxy` utility is available in any root terminal:

```bash
su
tg-ws-proxy status               # Display daemon status, listening port, and BBR state
tg-ws-proxy start                # Start proxy daemon
tg-ws-proxy stop                 # Stop proxy daemon
tg-ws-proxy restart              # Restart proxy daemon
tg-ws-proxy autostart on|off     # Configure automatic boot startup
tg-ws-proxy apply-tg auto        # Apply proxy to primary detected Telegram client
tg-ws-proxy apply-tg all         # Apply proxy across all detected clients
tg-ws-proxy set-target <package> # Set default target client package
tg-ws-proxy tune-net             # Re-apply kernel network acceleration
```

---

## Building from Source

### Prerequisites

- JDK 17 or newer.
- Android SDK (API 34/35, Build Tools 34.0.0+).
- Python 3.8+ (for release packaging scripts).

### Building the Application

```bash
cd upstream
./gradlew :tgbypass:assembleDebug
```

The compiled APK will be located at:
`upstream/tgbypass/build/outputs/apk/debug/tgbypass-debug.apk`

### Packaging the Flashable Module

```bash
python scripts/package_release.py
```

The script generates the following assets in `release/`:
- `TgBypass-Module-v1.2.2.zip`
- `TgBypass-v1.2.2.apk`
- `tg_ws_vector_bridge.py`

---

## Troubleshooting

- **Status indicates "Port 1444 not responding"**:
  Open the TgBypass dashboard and tap "Auto-repair and configure all clients", or run `su -c tg-ws-proxy restart` from a terminal.
- **AyuGram shows connection failure with the bridge**:
  Ensure `com.radolyn.ayugram` is excluded from the Vector / LSPosed scope. AyuGram uses direct Python plugin integration; running an ART socket hook simultaneously can cause runtime conflicts.
- **TCP BBR does not activate**:
  Some OEM or vendor kernels omit the `tcp_bbr` module. The script automatically falls back to `cubic` or `westwood` while preserving socket buffer and TFO enhancements.

---

## License and Acknowledgments

- Project author: **windyx0**
- Core transport author: **bol-van** (zapret / nfqws)
- Distributed under the MIT License.
