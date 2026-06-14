<div align="center">
<img src="../../images/icon/vger32.svg" alt="VGER32" width="160" />

# VGER32 Android App

*Companion app for VGER32 devices*
</div>

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![minSdk: 30](https://img.shields.io/badge/minSdk-30-blue.svg)](app/build.gradle.kts)
[![compileSdk: 36](https://img.shields.io/badge/compileSdk-36-blue.svg)](app/build.gradle.kts)

## What it does

Vger32App discovers, monitors, and controls VGER32 devices over local WiFi and MQTT — no cloud, no IPs to remember.

Built for makers and developers who need to:
- Find devices on a network without knowing their IPs
- Inspect live telemetry and historical metrics
- Push configuration or trigger remote actions
- Map device locations using WiFi fingerprinting

## Architecture

| Layer | Technology |
|---|---|
| UI | Single-Activity, Jetpack Navigation, ViewBinding |
| State | ViewModel + LiveData, activity-scoped for cross-tab persistence |
| Networking | OkHttp (HTTP), raw TCP socket with custom MQTT 3.1.1 frame codec |
| Security | EncryptedSharedPreferences (AES-256-GCM), biometric PIN unlock |
| Discovery | NsdManager (mDNS), DatagramSocket (UDP), threaded socket probe (LAN) |

## Features

### Discovery

Five independent methods to locate modules. The app merges results and tracks the source of each discovery.

| Method | Mechanism | Requires firmware support |
|---|---|---|
| **MQTT ping** | Publishes to `vger32/ping`; modules respond on `vger32/{mid}/pong` with full identity | Broker configured |
| **mDNS** | Passively listens for `_vger32._tcp`; resolves TXT records (`mid`, `chip`, `pid`, `ver`) | Yes |
| **UDP broadcast** | Sends `vger32:discover` to `255.255.255.255:4210`; devices reply after random jitter (≤200 ms) to avoid collisions | Yes |
| **Manual IP** | Direct HTTP probe to user-supplied address | No |
| **LAN scan** | Probes `/24` subnet on port 80 with 20 concurrent workers | No |

Discovered modules are persisted to a flat file and auto-purged after a configurable inactivity period (default: 7 days).

### MQTT Monitor

- Subscribes to `vger32/#` and renders all inbound traffic in a time-ordered feed
- Feed survives tab switches (activity-scoped ViewModel)
- Outbound commands: `ping`, `dash_on`, `dash_off`, `wifi_ap`, `publish_now`, `capability`, `sleep`, `reboot`, and free-form messages
- Automatic keepalive (30 s) and reconnection with fixed backoff

### HTTP Panel

Direct, grouped access to the module's REST API:

| Group | Endpoints |
|---|---|
| **Monitor** | `GET /api/system-identity`, `GET /api/system-metrics`, `GET /api/sensors`, `GET /api/boot-history`, `GET /api/logs` |
| **Actions** | `GET /api/preferences`, `POST /api/preferences`, `POST /api/reboot`, browser dashboard |

Results are shown in a copyable bottom sheet. The log viewer (`GET /api/logs`) is a dedicated full-screen fragment with reverse-chronological display and continuation-line merging.

### Localizer

WiFi fingerprinting for indoor positioning.

| Source | How | Saved? |
|---|---|---|
| Phone scan | `WifiManager.startScan()` with cached-result fallback | Yes, as waypoint |
| Module scan | `GET /api/wifi-scan` from the ESP32 | Yes, as waypoint |
| Stored waypoints | Local JSON in `SharedPreferences` | — |

Waypoints are named (1–6 chars, A–Z0–9), timestamped, and can be uploaded to (`PUT /api/wifi-fingerprints`) or downloaded from a module in a compact firmware wire format.

### Security

- **PIN unlock**: 6-digit code with trivial-sequence rejection (no `111111`, `123456`, `654321`)
- **Biometric fallback**: Android BiometricPrompt on supported hardware
- **Per-module credentials**: API key and scrambler key stored per `mid` in `EncryptedSharedPreferences`
- **Scrambling**: XOR-based payload obfuscation for MQTT and HTTP, keyed independently
- **No cloud**: all traffic is local (WiFi LAN) or directly to a user-configured MQTT broker

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # signed release APK (requires signing.properties)
```

### Product flavors

| Flavor | `minSdk` | Target API |
|---|---|---|
| `android11` | 30 | Android 11 |
| `android12` | 31 | Android 12 |
| `android13` | 32 | Android 13 |
| `android14` | 33 | Android 14 |
| `android15` | 34 | Android 15 |
| `android16` | 35 | Android 16 |

Flavors resolve library compatibility differences across API levels. Do not remove.

## License

MIT — see [`LICENSE`](../../LICENSE)