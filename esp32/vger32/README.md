<div align="center">
<img src="../../images/icon/vger32.svg" alt="VGER32" width="160" />

# VGER32 Firmware

*ESP32 firmware — mission and hardware profiles*
</div>

Define what the device does in a mission profile, declare what hardware it has in a hardware profile, and compile. Only the code you need, on the chip you have.

## What it does

- **Web dashboard** with authentication — built-in HTTP server. Visualize sensors, configure WiFi and MQTT, manage runtime capabilities, and check system metrics and logs from any browser on the local network.
- **MQTT** — publishes sensor readings and receives commands via a simple dispatcher.
- **WiFi localization** — saves visible network fingerprints as waypoints. The device detects which area it is in without GPS.
- **Device discovery** — mDNS and UDP broadcast for zero-config discovery from the Android app.
- **Deep sleep** — configurable grace period before sleeping, with dashboard activity tracking.
- **Runtime capabilities** — toggle features on/off without recompiling. Persist across reboots. Controllable from the dashboard or via MQTT.
- **Logging** — five log levels with zero overhead for disabled levels. In-memory buffer queryable via API for chips without reliable Serial (C3/C6).
- **Multi-chip** — ESP32, ESP32-S3, ESP32-C3, ESP32-C6 with Arduino Core 3.x.
- **Demo mode** — simulate sensors and actuators without real hardware.

## Profiles

A **mission profile** defines what the device does. A **hardware profile** defines what it physically has. They are independent — the same mission can run on different wirings, and the same hardware can be used for different missions.

Selection is done in a single file: `src/profiles/active_profile.h`

```c
// Uncomment exactly one mission and exactly one hardware

#define MISSION_WAYPOINT_ALERT   // audible alert when entering a WiFi waypoint
// #define MISSION_WEATHER_STATION  // publishes sensors via MQTT
// #define MISSION_FULL             // all subsystems active, demo mode
// #define MISSION_NOTIFIER_LITE    // waypoint + MQTT display on a 1.47" LCD

#define HARDWARE_WAYPOINT_V1     // ESP32-DevKitC + KY-012 buzzer
// #define HARDWARE_WEATHER_V1      // ESP32-DevKitC + SHT31 + BMP280
// #define HARDWARE_FULL_V1         // no real hardware, simulated sensors
// #define HARDWARE_NOTIFIER_V1     // Waveshare ESP32-C6-LCD-1.47
```

### Available missions

| Mission | Description |
|---|---|
| `MISSION_WAYPOINT_ALERT` | Emits an audible alert when entering a known WiFi waypoint |
| `MISSION_WEATHER_STATION` | Publishes sensor data via MQTT |
| `MISSION_FULL` | All subsystems active, demo mode |
| `MISSION_NOTIFIER_LITE` | Displays waypoints and MQTT messages on a 1.47" LCD |

### Available hardware profiles

| Hardware | Description |
|---|---|
| `HARDWARE_WAYPOINT_V1` | ESP32-DevKitC + KY-012 buzzer on pin 18 |
| `HARDWARE_WEATHER_V1` | ESP32-DevKitC + SHT31 + BMP280 on I2C |
| `HARDWARE_FULL_V1` | No real hardware — simulated sensors |
| `HARDWARE_NOTIFIER_V1` | Waveshare ESP32-C6-LCD-1.47 |

### Adding a new mission

1. Create `src/profiles/mission/mn_<name>.h/.cpp` and `mn_<name>_main.h/.cpp`
2. Add `MISSION_<NAME>` in `active_profile.h`
3. Add the `#ifdef` block in `mission_manager.cpp`
4. Create the corresponding hardware profile in `src/profiles/hardware/`

## LED status

The board LED reflects system state — connection status, AP mode, errors — without needing the dashboard. Strobe sequences cycle through a fixed color pattern; see `board_led.cpp` for the exact timing and sequence.

## Logging

Two complementary logging systems share a common in-memory ring buffer (`log_store`):

- **`sys_logger`** — general-purpose trace logging at five levels (off/F/E/W/I/D), queryable via `GET /api/logs`.
- **`waypoint_logger`** — dedicated log of waypoint detections (entry, exit, score), queryable via `GET /api/location`.

Both are memory-only — entries are not persisted to LittleFS and are lost on reboot.

## Supported sensors

| Driver | Hardware | Metrics |
|---|---|---|
| `sensor_sht31` | SHT31 | temperature, humidity |
| `sensor_bmp180` | BMP180 | pressure, temperature |
| `sensor_bmp280` | BMP280 | pressure, temperature |
| `sensor_hcsr04` | HC-SR04 | distance |
| `sensor_pir` | HC-SR501 | motion |
| `sensor_sw420` | SW-420 | vibration |
| `sensor_guva_s12s` | GUVA-S12S | UV index |
| `sensor_adxl345` | ADXL345 | accelerometer X/Y/Z |
| `sensor_sound` | analog | sound level (dB) |
| `sensor_push_button` | push button | event |

## Build commands

### Requirements

- [PlatformIO](https://platformio.org/) CLI or VS Code extension
- Python 3.x

### Generate build info

Creates `src/config/build_info.h` with a random version string and timestamp. Run before every firmware build — creates the file if it doesn't exist, overwrites it if it does:

```bash
python scripts/gen_build_info.py
```

### Compile and flash firmware

```bash
# compile only
pio run -e esp32dev

# compile and flash
pio run -e esp32dev -t upload
```

### Upload the web dashboard

```bash
# minify first (required — reduces size for LittleFS)
python scripts/minify.py

# upload to filesystem
pio run -e esp32dev -t uploadfs
```

### Serial monitor

```bash
pio device monitor
```

### Available environments

| Environment | Chip |
|---|---|
| `esp32dev` | ESP32-DevKitC |
| `esp32s3` | ESP32-S3 DevKitC-1 |
| `esp32s3supermini` | ESP32-S3 Super Mini |
| `esp32c3` | ESP32-C3 (Lolin C3 Mini) |
| `esp32c6zero` | ESP32-C6 DevKitM-1 |

## HTTP API

All endpoints require the `X-API-Key` header. The key is configured in `keys.vger` (default: `a1b2c3d4e5`). Static dashboard files (HTML/CSS/JS) are public.

In STA mode use the IP assigned by the router. In AP mode the IP is `192.168.4.1`.

### Wire format

All responses are `text/plain`. Two separator constants are used:

| Constant | Value | Used when |
|---|---|---|
| `KV_SEP` | `=` | Values that cannot contain `=`: IDs, IPs, numbers, flags, enums |
| `FIELD_SEP` | `0x1F` (ASCII Unit Separator) | Values that may contain `=` or `:`: passwords, URLs, SSIDs |

**GET responses** return data in one of two layouts:

```
# key=value pairs (one per line)
key=value
key=value

# pipe-separated records (one per line)
field|field|field
field|field|field
```

**POST/DELETE success** — HTTP 200, empty body. Check the status code.

**Errors** — HTTP 4xx/5xx with body:
```
err=Descriptive message
```

### System

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/system-identity` | Module ID, chip, board, profile, IP, connection status, version |
| GET | `/api/system-metrics` | Heap, flash, CPU, temperature, uptime |
| GET | `/api/boot-history` | Boot history with timestamp and reason |
| GET | `/api/logs` | In-memory log buffer (read-only) |
| DELETE | `/api/logs` | Clear the log buffer |

#### `GET /api/system-identity`

```
mid=VGER_29858
chip=ESP32-S3
brd=ESP32S3_DEV
pid=full
ip=192.168.0.8
ver=1.2.3
sts=connected STA
```

| Field | Description |
|---|---|
| `mid` | Module ID |
| `chip` | Chip model |
| `brd` | Board identifier |
| `pid` | Active mission profile |
| `ip` | Current IP address |
| `ver` | Firmware version |
| `sts` | Connection status |

#### `GET /api/sensors`

One line per sensor: `hardware|metric|value|timestamp|unit`

```
sht31|temp|22.50|1745678901|°C
sht31|hum|60.10|1745678901|%
bmp280|pressure|1013.25|1745678901|hPa
```

#### `GET /api/sensor-history?h=sht31&m=temp`

First line is the header `hardware|metric|unit`. Subsequent lines are samples `value|timestamp`, oldest first.

```
sht31|temp|°C
22.50|1745678800
22.55|1745678860
22.48|1745678920
```

On error (sensor not found):

```
err=Sensor not found
```

### Configuration

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/preferences` | Module ID and MQTT configuration |
| POST | `/api/preferences` | Save preferences |
| GET | `/api/known-networks` | Known WiFi networks |
| POST | `/api/known-networks` | Save known WiFi networks |
| GET | `/api/capabilities` | Runtime feature flags |
| POST | `/api/capabilities` | Update runtime feature flags |

Configuration endpoints use `FIELD_SEP` (`0x1F`) as separator because values may contain `=` or `:` (URLs, passwords).

#### `GET /api/preferences`

```
moduleId<FS>VGER_29858
mqttServer<FS>broker.local
mqttPort<FS>1883
mqttInterval<FS>120
```

POST the same format to save. Only include the fields you want to change.

#### `GET /api/capabilities`

```
wifi<FS>full
scmb<FS>0
mqtt<FS>1
dash<FS>1
locl<FS>0
slep<FS>0
log<FS>I
```

POST the same format to update. Only include the fields you want to change.

```bash
# read capabilities
curl -H "X-API-Key: a1b2c3d4e5" http://192.168.0.x/api/capabilities

# set log level to debug
curl -X POST -H "X-API-Key: a1b2c3d4e5" -H "Content-Type: text/plain" \
     --data $'log\x1FD\n' \
     http://192.168.0.x/api/capabilities
```

#### `GET /api/known-networks`

One line per network: `type<FS>identifier<FS>password`

```
S<FS>MyWifi<FS>mypassword
S<FS>OfficeWifi<FS>
M<FS>AABBCCDDEEFF<FS>
```

| Field | Values |
|---|---|
| `type` | `S` = SSID, `M` = MAC address |
| `identifier` | SSID string or 12-char uppercase hex MAC |
| `password` | WiFi password, or empty for open networks |

### Network and localization

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/wifi-scan` | Visible WiFi networks |
| GET | `/api/wifi-fingerprints` | WiFi fingerprint database (raw .dat) |
| POST | `/api/wifi-fingerprints` | Append a WiFi fingerprint as waypoint |
| PUT | `/api/wifi-fingerprints` | Replace entire fingerprint database |
| GET | `/api/location` | Waypoint detection history |

#### `GET /api/wifi-scan`

One line per network: `ssid|mac|rssi|channel`

```
MyWifi|AABBCCDDEEFF|-52|6
OfficeWifi|112233445566|-71|11
```

#### `GET /api/location`

Pipe-separated entries: `waypoint:timestamp:score`

```
LOBBY:1745678901:87|HALL:1745678750:92|-:1745678600:0
```

A waypoint of `-` means location was lost.

### Time

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/time` | Current device timestamp |
| POST | `/api/time` | Set the device clock |

#### `GET /api/time`

```
ts=1745678901
```

| Field | Description |
|---|---|
| `ts` | Unix timestamp if the clock is synced, seconds since boot otherwise |

#### `POST /api/time`

Body in the same KV format:

```
ts=1745678901
```

Useful after deep sleep, when NTP has not had time to sync:

```bash
curl -X POST -H "X-API-Key: a1b2c3d4e5" -H "Content-Type: text/plain" \
     --data "ts=$(date +%s)" \
     http://192.168.0.x/api/time
```

### Control

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/restart` | Restart the device |

## Capabilities

Runtime feature flags that persist in NVS. They can be changed from the dashboard (Config tab) or via MQTT without recompiling.

| Key | Values | Requires reboot | Description |
|---|---|---|---|
| `wifi` | `off` / `scan` / `full` | Yes | WiFi operating mode |
| `scmb` | `0` / `1` | No | Payload scrambler |
| `mqtt` | `0` / `1` | No | MQTT client |
| `dash` | `0` / `1` | No | HTTP dashboard |
| `locl` | `0` / `1` | No | WiFi localizer |
| `slep` | `0` / `1` | No | Deep sleep |
| `log` | `off` / `F` / `E` / `W` / `I` / `D` | No | Runtime log level |

Default values are defined per mission in `mn_<mission>_defaults.h`.

## Sensitive files (.vger)

Sensitive configuration lives in LittleFS, not in the repo. Files use the `.vger` extension — all are excluded by `.gitignore`.

| File | Content |
|---|---|
| `keys.vger` | API key, AP password, scrambler key |
| `known_networks.vger` | Known WiFi networks with passwords |

**Format** (plaintext, before first boot):
```
api_key=yourkey
ap_pass=yourpassword
scrambler_key=yourkey
```

On first load, the device automatically re-saves the file in obfuscated form using a key derived from the chip's eFuseMac (unique and immutable per chip). Subsequent boots load the obfuscated version transparently.

Upload via PlatformIO filesystem tools or place the file in `data/` before running `uploadfs`.

## MQTT

### Connection

Broker, port, and publish interval are configured from the dashboard (Config tab) or via `POST /api/preferences`. The client ID is generated on each connection as `<module_id>_<random_hex>`.

### Outbound topics — the device publishes

| Topic | Content | When |
|---|---|---|
| `vger32/<module_id>/sensors/latest` | Plain text sensor readings (same format as `GET /api/sensors`) | Periodic, per configured interval |
| `vger32/<module_id>/pong` | Plain text device identity (same format as `GET /api/system-identity`) | In response to a `ping` command |

### Commands — the device listens

The device subscribes to `vger32/<module_id>/cmd/#`. The command is the last segment of the topic; the payload is the optional argument.

| Command | Payload | Description |
|---|---|---|
| `reboot` | — | Restart the device |
| `ping` | — | Publish identity to `vger32/<module_id>/pong` |
| `dash_on` | — | Enable the HTTP dashboard (runtime only) |
| `dash_off` | — | Stop the HTTP dashboard (runtime only) |
| `wifi_ap` | — | Force AP mode |
| `msg` | text | Print text to the device log. Mission profiles with a display may also show it on screen. |
| `publish_now` | — | Force an immediate sensor publish, regardless of the configured interval |
| `capability` | `key=value` | Update a runtime capability (see Capabilities table) |

All commands are forwarded to the active mission profile observer after the built-in handler runs.

```bash
# ping
mosquitto_pub -t "vger32/VGER_12345/cmd/ping" -m ""

# set log level to debug
mosquitto_pub -t "vger32/VGER_12345/cmd/capability" -m "log=D"

# disable MQTT on next cycle
mosquitto_pub -t "vger32/VGER_12345/cmd/capability" -m "mqtt=0"
```

## Scrambler

The device includes a payload obfuscator for MQTT messages. It is not cryptography — it provides obfuscation against casual passive observers.

It is enabled at runtime via the `mqtt_scrambled` preference (configurable from the dashboard or via `POST /api/preferences`). No recompilation needed.

**Algorithm:** streaming cipher with key-derived PRNG and XOR. Each byte depends on the previous ciphertext byte, a PRNG step seeded from the key and salt, and the key itself.

**Salt:** 4 random bytes (hardware RNG) prepended to every ciphertext. Same message + same key → different output every time.

**Output length:** `input length + 4` bytes (the 4-byte salt).

**Operates on raw bytes (0x00–0xFF)** — no character restrictions.

The key is configured in `keys.vger`:
```
scrambler_key=vger32xk
```

> No spaces around `=`. Format: `key=value`, one per line.

The scrambler API in `src/scrambler/scrambler.h`:

```cpp
// Encode: out must be at least len + SCRAMBLER_SALT_LEN bytes.
// Returns output length (len + SCRAMBLER_SALT_LEN).
int scrambler_encode(const uint8_t *in, int len, uint8_t *out);

// Decode: in must start with SCRAMBLER_SALT_LEN salt bytes.
// out must be at least len - SCRAMBLER_SALT_LEN bytes.
// Returns output length, or 0 if len is too small to contain a salt.
int scrambler_decode(const uint8_t *in, int len, uint8_t *out);
```

When `cfg_mqtt_scrambled` is true, `mqtt_client.cpp` encodes all outbound payloads and decodes all inbound payloads transparently. Any client producing or consuming MQTT messages must apply the same algorithm with the same key.

> The scrambler does not affect HTTP endpoints.

## License

MIT — see [`LICENSE`](../../LICENSE)