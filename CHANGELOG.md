# Changelog

All notable changes to TgBypass All-In-One are documented in this file.

---

## [v1.2.2] - 2026-11-08

### English
- **Vector Hook Module & Autonomous Proxy**:
  - Direct socket traffic redirection for official Telegram, exteraGram, Telegram X, Plus Messenger, and Nekogram.
  - Native background proxy service running on 127.0.0.1:1444 with system boot persistence.
- **AyuGram Bridge Plugin**:
  - Full traffic proxying including encrypted VoIP voice and video calls.
  - Real-time bidirectional backend synchronization via Android broadcast intent bus.
- **Kernel Network Turbo**:
  - Kernel TCP BBR congestion control and FQ pacing scheduler.
  - TCP Fast Open (TFO) enabled for client and server sockets.
  - Window reset on idle disabled (`tcp_slow_start_after_idle = 0`).
  - Read and write network buffers expanded up to 16 MB.
  - Traffic prioritization via QoS (DSCP AF41 / Low Delay).
- **User Interface**:
  - Material Design 3 dashboard with live health monitoring.
  - Cached startup state preventing false error flashes on launch or resume.
  - Cloudflare edge latency checker with strict threshold diagnostics.
  - Android adaptive icon with 72dp safe zone compliance.
- **Packaging & Modules**:
  - Magisk / KernelSU / APatch late-start service autostart.
  - Automated KernelSU root authorization and Vector CLI scope activation.
  - Strict 34-column console output formatting for terminal flashers.
  - Built-in update checker linked to GitHub repository.

### Русский
- **Модуль хуков Vector и автономный прокси**:
  - Прямой перехват сокетов Telegram, exteraGram, Telegram X, Plus Messenger и Nekogram.
  - Автономная служба прокси-демона на порту 127.0.0.1:1444 с автозапуском при включении.
- **Мост-плагин для AyuGram**:
  - Проксирование всего трафика, включая голосовые и видеозвонки (VoIP).
  - Двусторонняя синхронизация выбранного бэкенда через системную Intent-шину.
- **Сетевое ускорение ядра Linux**:
  - Активация алгоритма TCP BBR и дисциплины очередей FQ.
  - Включение TCP Fast Open (TFO) для клиента и сервера.
  - Отключение сброса окна при простое (`tcp_slow_start_after_idle = 0`).
  - Расширение сетевых буферов до 16 МБ для быстрой загрузки видео и медиа.
  - Приоритизация пакетов через QoS (DSCP AF41 / Low Delay).
- **Пользовательский интерфейс**:
  - Менеджер на базе Material Design 3 с живым мониторингом компонентов.
  - Кэширование состояния для устранения мерцания статуса при старте и перезаходе.
  - Диагностика задержки туннелей Cloudflare с цветовой градацией.
  - Адаптивная иконка приложения с соблюдением безопасной зоны (72dp).
- **Сборка и модули**:
  - Прошиваемый модуль Magisk / KernelSU / APatch со службой late-start.
  - Автоматическая пред-авторизация root в KernelSU и настройка области в Vector.
  - Форматирование консольного вывода инсталлера в 34 колонки без переноса строк.
  - Встроенная проверка обновлений через репозиторий GitHub.
