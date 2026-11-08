English | [Русский](README_RU.md)

# TgBypass All-In-One (Vector + Service)
### Author: windyx0
### Module for Magisk / KernelSU / APatch
### Version: v1.2.2
### Companion App: com.windyx0.tgbypass

An all-in-one system module for Telegram censorship bypass and Linux root network acceleration. Features an autonomous background WebSocket tunnel daemon (127.0.0.1:1444), automatic installer for the Vector (LSPosed) module, and an integrated AyuGram bridge plugin.

---

## Features

1. **Linux Kernel Network Turbo**:
   - Activates TCP BBR congestion control algorithm with FQ queue discipline.
   - Enables TCP Fast Open (TFO) for client and server (eliminates first-RTT handshake latency).
   - Disables window reset on idle (tcp_slow_start_after_idle = 0) for instantaneous message transmission.
   - Increases kernel TCP read/write buffers up to 16 MB for fast streaming of high-resolution video and media.
   - Packet prioritization via QoS (DSCP AF41 / Low Delay).
   - Bypasses conntrack connection tracking (NOTRACK) on the loopback interface.
   - Automatic Path MTU Discovery (Path MTU Probing).

2. **Autonomous Background Service**:
   - The service starts automatically at system boot via late-start service (`service.sh`).
   - Autostart management via CLI (`tg-ws-proxy autostart on|off`).
   - Silent background execution without persistent system notification clutter.

3. **Vector (LSPosed) Integration**:
   - Automatically installs and activates the `com.windyx0.tgbypass` module.
   - Automatically configures Vector scope for Telegram Official, Telegram X, exteraGram, Plus Messenger, and Nekogram.
   - Excludes AyuGram from Vector scope to eliminate ART runtime conflicts.

4. **AyuGram Integration**:
   - Automatically deploys and enables `tg_ws_vector_bridge.py` inside AyuGram plugins storage.
   - Real-time bidirectional backend synchronization between TgBypass and AyuGram.
   - Full traffic proxying including VoIP voice and video calls.

5. **Action Button (KernelSU / APatch)**:
   - One-click verification, daemon startup, and client launch from the module manager UI.

---

## Installation

1. Download the module archive: `TgBypass-Module-v1.2.2.zip`.
2. Open Magisk, KernelSU, or APatch manager.
3. Go to the "Modules" section -> "Install from storage".
4. Select the archive and confirm installation.
5. Reboot your device.

---

## CLI Management (Root Shell)

In any terminal emulator (Termux, ADB Shell, su), the `tg-ws-proxy` command is available:

```bash
su
tg-ws-proxy status               # View proxy status, listening port, and BBR state
tg-ws-proxy start                # Start proxy service
tg-ws-proxy stop                 # Stop proxy service
tg-ws-proxy restart              # Restart proxy service
tg-ws-proxy autostart on|off     # Toggle autostart at system boot
tg-ws-proxy apply-tg auto        # Locate installed client and apply proxy configuration
tg-ws-proxy apply-tg all         # Apply proxy configuration across all detected clients
tg-ws-proxy set-target <package> # Set default target client package
tg-ws-proxy tune-net             # Re-apply kernel network acceleration
```
