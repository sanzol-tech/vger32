<div align="center">
<img src="../../images/icon/vger32_01b.svg" alt="VGER32" width="160" />

# VGER32 Firmware

*Firmware ESP32 — perfiles de misión y hardware*
</div>

Define qué hace el dispositivo en un perfil de misión, declará qué hardware tiene en un perfil de hardware, y compilá. Solo el código que necesitás, en el chip que tenés.

## Qué hace

- **Dashboard web** con autenticación — servidor HTTP integrado. Visualizá sensores, configurá WiFi y MQTT, administrá capacidades en tiempo de ejecución, y revisá métricas del sistema y logs desde cualquier navegador en la red local.
- **MQTT** — publica lecturas de sensores y recibe comandos a través de un dispatcher simple.
- **Localización WiFi** — guarda huellas de redes visibles como waypoints. El dispositivo detecta en qué área está sin GPS.
- **Discovery de dispositivos** — mDNS y broadcast UDP para descubrimiento sin configuración desde la app Android.
- **Deep sleep** — período de gracia configurable antes de dormir, con seguimiento de actividad del dashboard.
- **Capacidades en tiempo de ejecución** — activá o desactivá funciones sin recompilar. Persisten entre reinicios. Controlables desde el dashboard o vía MQTT.
- **Logging** — cinco niveles de log sin overhead para los niveles deshabilitados. Buffer en memoria consultable vía API para chips sin Serial confiable (C3/C6).
- **Multi-chip** — ESP32, ESP32-S3, ESP32-C3, ESP32-C6 con Arduino Core 3.x.
- **Modo demo** — simula sensores y actuadores sin hardware real.

## Perfiles

Un **perfil de misión** define qué hace el dispositivo. Un **perfil de hardware** define qué tiene físicamente. Son independientes — la misma misión puede correr en distinto cableado, y el mismo hardware puede usarse para distintas misiones.

La selección se hace en un solo archivo: `src/profiles/active_profile.h`

```c
// Descomentar exactamente una misión y exactamente un hardware

#define MISSION_WAYPOINT_ALERT   // alerta sonora al entrar en un waypoint WiFi
// #define MISSION_WEATHER_STATION  // publica sensores vía MQTT
// #define MISSION_FULL             // todos los subsistemas activos, modo demo
// #define MISSION_NOTIFIER_LITE    // waypoint + display MQTT en LCD de 1.47"

#define HARDWARE_WAYPOINT_V1     // ESP32-DevKitC + buzzer KY-012
// #define HARDWARE_WEATHER_V1      // ESP32-DevKitC + SHT31 + BMP280
// #define HARDWARE_FULL_V1         // sin hardware real, sensores simulados
// #define HARDWARE_NOTIFIER_V1     // Waveshare ESP32-C6-LCD-1.47
```

### Misiones disponibles

| Misión | Descripción |
|---|---|
| `MISSION_WAYPOINT_ALERT` | Emite una alerta sonora al entrar en un waypoint WiFi conocido |
| `MISSION_WEATHER_STATION` | Publica datos de sensores vía MQTT |
| `MISSION_FULL` | Todos los subsistemas activos, modo demo |
| `MISSION_NOTIFIER_LITE` | Muestra waypoints y mensajes MQTT en la LCD de 1.47" |

### Perfiles de hardware disponibles

| Hardware | Descripción |
|---|---|
| `HARDWARE_WAYPOINT_V1` | ESP32-DevKitC + buzzer KY-012 en pin 18 |
| `HARDWARE_WEATHER_V1` | ESP32-DevKitC + SHT31 + BMP280 por I2C |
| `HARDWARE_FULL_V1` | Sin hardware real — sensores simulados |
| `HARDWARE_NOTIFIER_V1` | Waveshare ESP32-C6-LCD-1.47 |

### Agregar una nueva misión

1. Crear `src/profiles/mission/mn_<nombre>.h/.cpp` y `mn_<nombre>_main.h/.cpp`
2. Agregar `MISSION_<NOMBRE>` en `active_profile.h`
3. Agregar el bloque `#ifdef` en `mission_manager.cpp`
4. Crear el perfil de hardware correspondiente en `src/profiles/hardware/`

## Estado del LED

El LED de la placa refleja el estado del sistema — conexión, modo AP, errores — sin necesidad del dashboard. Las secuencias de strobe recorren un patrón de colores fijo; ver `board_led.cpp` para el timing y secuencia exactos.

## Logging

Dos sistemas de logging complementarios comparten un buffer circular en memoria común (`log_store`):

- **`sys_logger`** — logging de trazas de propósito general en cinco niveles (off/F/E/W/I/D), consultable vía `GET /api/logs`.
- **`waypoint_logger`** — log dedicado de detecciones de waypoints (entrada, salida, score), consultable vía `GET /api/location`.

Ambos son solo en memoria — las entradas no se persisten en LittleFS y se pierden al reiniciar.

## Sensores soportados

| Driver | Hardware | Métricas |
|---|---|---|
| `sensor_sht31` | SHT31 | temperatura, humedad |
| `sensor_bmp180` | BMP180 | presión, temperatura |
| `sensor_bmp280` | BMP280 | presión, temperatura |
| `sensor_hcsr04` | HC-SR04 | distancia |
| `sensor_pir` | HC-SR501 | movimiento |
| `sensor_sw420` | SW-420 | vibración |
| `sensor_guva_s12s` | GUVA-S12S | índice UV |
| `sensor_adxl345` | ADXL345 | acelerómetro X/Y/Z |
| `sensor_sound` | analógico | nivel de sonido (dB) |
| `sensor_push_button` | pulsador | evento |

## Comandos de build

### Requisitos

- [PlatformIO](https://platformio.org/) CLI o extensión de VS Code
- Python 3.x

### Generar info de build

Crea `src/config/build_info.h` con una cadena de versión aleatoria y timestamp. Ejecutar antes de cada build — crea el archivo si no existe, lo sobreescribe si existe:

```bash
python scripts/gen_build_info.py
```

### Compilar y flashear firmware

```bash
# solo compilar
pio run -e esp32dev

# compilar y flashear
pio run -e esp32dev -t upload
```

### Subir el dashboard web

```bash
# minificar primero (obligatorio — reduce el tamaño para LittleFS)
python scripts/minify.py

# subir al filesystem
pio run -e esp32dev -t uploadfs
```

### Monitor serial

```bash
pio device monitor
```

### Entornos disponibles

| Entorno | Chip |
|---|---|
| `esp32dev` | ESP32-DevKitC |
| `esp32s3` | ESP32-S3 DevKitC-1 |
| `esp32s3supermini` | ESP32-S3 Super Mini |
| `esp32c3` | ESP32-C3 (Lolin C3 Mini) |
| `esp32c6zero` | ESP32-C6 DevKitM-1 |

## API HTTP

Todos los endpoints requieren el header `X-API-Key`. La clave se configura en `keys.vger` (default: `a1b2c3d4e5`). Los archivos estáticos del dashboard (HTML/CSS/JS) son públicos.

En modo STA usar la IP asignada por el router. En modo AP la IP es `192.168.4.1`.

### Formato de comunicación

Todas las respuestas son `text/plain`. Se usan dos constantes separadoras:

| Constante | Valor | Se usa cuando |
|---|---|---|
| `KV_SEP` | `=` | Valores que no pueden contener `=`: IDs, IPs, números, flags, enums |
| `FIELD_SEP` | `0x1F` (ASCII Unit Separator) | Valores que pueden contener `=` o `:`: passwords, URLs, SSIDs |

**Respuestas GET** devuelven datos en uno de dos formatos:

```
# pares clave=valor (uno por línea)
key=value
key=value

# registros separados por pipe (uno por línea)
field|field|field
field|field|field
```

**Éxito en POST/DELETE** — HTTP 200, body vacío. Verificar el código de estado.

**Errores** — HTTP 4xx/5xx con body:
```
err=Mensaje descriptivo
```

### Sistema

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/system-identity` | ID del módulo, chip, board, perfil, IP, estado de conexión, versión |
| GET | `/api/system-metrics` | Heap, flash, CPU, temperatura, uptime |
| GET | `/api/boot-history` | Historial de arranques con timestamp y motivo |
| GET | `/api/logs` | Buffer de logs en memoria (solo lectura) |
| DELETE | `/api/logs` | Limpia el buffer de logs |

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

| Campo | Descripción |
|---|---|
| `mid` | ID del módulo |
| `chip` | Modelo de chip |
| `brd` | Identificador de placa |
| `pid` | Perfil de misión activo |
| `ip` | Dirección IP actual |
| `ver` | Versión del firmware |
| `sts` | Estado de conexión |

#### `GET /api/sensors`

Una línea por sensor: `hardware|metric|value|timestamp|unit`

```
sht31|temp|22.50|1745678901|°C
sht31|hum|60.10|1745678901|%
bmp280|pressure|1013.25|1745678901|hPa
```

#### `GET /api/sensor-history?h=sht31&m=temp`

La primera línea es el header `hardware|metric|unit`. Las siguientes son muestras `value|timestamp`, de más antigua a más reciente.

```
sht31|temp|°C
22.50|1745678800
22.55|1745678860
22.48|1745678920
```

En caso de error (sensor no encontrado):

```
err=Sensor not found
```

### Configuración

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/preferences` | ID del módulo y configuración MQTT |
| POST | `/api/preferences` | Guarda preferencias |
| GET | `/api/known-networks` | Redes WiFi conocidas |
| POST | `/api/known-networks` | Guarda redes WiFi conocidas |
| GET | `/api/capabilities` | Flags de capacidades en tiempo de ejecución |
| POST | `/api/capabilities` | Actualiza flags de capacidades |

Los endpoints de configuración usan `FIELD_SEP` (`0x1F`) como separador porque los valores pueden contener `=` o `:` (URLs, passwords).

#### `GET /api/preferences`

```
moduleId<FS>VGER_29858
mqttServer<FS>broker.local
mqttPort<FS>1883
mqttInterval<FS>120
```

POST con el mismo formato para guardar. Solo incluir los campos a modificar.

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

POST con el mismo formato para actualizar. Solo incluir los campos a modificar.

```bash
# leer capacidades
curl -H "X-API-Key: a1b2c3d4e5" http://192.168.0.x/api/capabilities

# poner el nivel de log en debug
curl -X POST -H "X-API-Key: a1b2c3d4e5" -H "Content-Type: text/plain" \
     --data $'log\x1FD\n' \
     http://192.168.0.x/api/capabilities
```

#### `GET /api/known-networks`

Una línea por red: `type<FS>identifier<FS>password`

```
S<FS>MyWifi<FS>mypassword
S<FS>OfficeWifi<FS>
M<FS>AABBCCDDEEFF<FS>
```

| Campo | Valores |
|---|---|
| `type` | `S` = SSID, `M` = dirección MAC |
| `identifier` | string del SSID o MAC de 12 caracteres hex en mayúsculas |
| `password` | password de WiFi, o vacío para redes abiertas |

### Red y localización

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/wifi-scan` | Redes WiFi visibles |
| GET | `/api/wifi-fingerprints` | Base de datos de huellas WiFi (.dat crudo) |
| POST | `/api/wifi-fingerprints` | Agrega una huella WiFi como waypoint |
| PUT | `/api/wifi-fingerprints` | Reemplaza toda la base de datos de huellas |
| GET | `/api/location` | Historial de detección de waypoints |

#### `GET /api/wifi-scan`

Una línea por red: `ssid|mac|rssi|channel`

```
MyWifi|AABBCCDDEEFF|-52|6
OfficeWifi|112233445566|-71|11
```

#### `GET /api/location`

Entradas separadas por pipe: `waypoint:timestamp:score`

```
LOBBY:1745678901:87|HALL:1745678750:92|-:1745678600:0
```

Un waypoint `-` significa que se perdió la ubicación.

### Tiempo

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/time` | Timestamp actual del dispositivo |
| POST | `/api/time` | Configura el reloj del dispositivo |

#### `GET /api/time`

```
ts=1745678901
```

| Campo | Descripción |
|---|---|
| `ts` | Timestamp Unix si el reloj está sincronizado, segundos desde el boot si no |

#### `POST /api/time`

Body en el mismo formato KV:

```
ts=1745678901
```

Útil después de deep sleep, cuando NTP no tuvo tiempo de sincronizar:

```bash
curl -X POST -H "X-API-Key: a1b2c3d4e5" -H "Content-Type: text/plain" \
     --data "ts=$(date +%s)" \
     http://192.168.0.x/api/time
```

### Control

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/restart` | Reinicia el dispositivo |

## Capacidades

Flags de funciones en tiempo de ejecución que persisten en NVS. Se pueden cambiar desde el dashboard (pestaña Config) o vía MQTT sin recompilar.

| Clave | Valores | Requiere reinicio | Descripción |
|---|---|---|---|
| `wifi` | `off` / `scan` / `full` | Sí | Modo de operación WiFi |
| `scmb` | `0` / `1` | No | Scrambler de payload |
| `mqtt` | `0` / `1` | No | Cliente MQTT |
| `dash` | `0` / `1` | No | Dashboard HTTP |
| `locl` | `0` / `1` | No | Localizador WiFi |
| `slep` | `0` / `1` | No | Deep sleep |
| `log` | `off` / `F` / `E` / `W` / `I` / `D` | No | Nivel de log en tiempo de ejecución |

Los valores por defecto se definen por misión en `mn_<mission>_defaults.h`.

## Archivos sensibles (.vger)

La configuración sensible vive en LittleFS, no en el repositorio. Los archivos usan la extensión `.vger` — todos excluidos por `.gitignore`.

| Archivo | Contenido |
|---|---|
| `keys.vger` | API key, password de AP, clave del scrambler |
| `known_networks.vger` | Redes WiFi conocidas con passwords |

**Formato** (texto plano, antes del primer arranque):
```
api_key=yourkey
ap_pass=yourpassword
scrambler_key=yourkey
```

En la primera carga, el dispositivo automáticamente reescribe el archivo en forma ofuscada usando una clave derivada del eFuseMac del chip (único e inmutable por chip). Los arranques siguientes cargan la versión ofuscada de forma transparente.

Subir vía herramientas de filesystem de PlatformIO o colocar el archivo en `data/` antes de ejecutar `uploadfs`.

## MQTT

### Conexión

El broker, puerto e intervalo de publicación se configuran desde el dashboard (pestaña Config) o vía `POST /api/preferences`. El client ID se genera en cada conexión como `<module_id>_<random_hex>`.

### Topics de salida — el dispositivo publica

| Topic | Contenido | Cuándo |
|---|---|---|
| `vger32/<module_id>/sensors/latest` | Lecturas de sensores en texto plano (mismo formato que `GET /api/sensors`) | Periódico, según intervalo configurado |
| `vger32/<module_id>/pong` | Identidad del dispositivo en texto plano (mismo formato que `GET /api/system-identity`) | En respuesta a un comando `ping` |

### Comandos — el dispositivo escucha

El dispositivo se suscribe a `vger32/<module_id>/cmd/#`. El comando es el último segmento del topic; el payload es el argumento opcional.

| Comando | Payload | Descripción |
|---|---|---|
| `reboot` | — | Reinicia el dispositivo |
| `ping` | — | Publica identidad en `vger32/<module_id>/pong` |
| `dash_on` | — | Habilita el dashboard HTTP (solo en runtime) |
| `dash_off` | — | Detiene el dashboard HTTP (solo en runtime) |
| `wifi_ap` | — | Fuerza modo AP |
| `msg` | texto | Imprime texto en el log del dispositivo. Los perfiles de misión con display también pueden mostrarlo en pantalla. |
| `publish_now` | — | Fuerza una publicación inmediata de sensores, sin importar el intervalo configurado |
| `capability` | `key=value` | Actualiza una capacidad en tiempo de ejecución (ver tabla de Capacidades) |

Todos los comandos se reenvían al observer del perfil de misión activo después de que corre el handler integrado.

```bash
# ping
mosquitto_pub -t "vger32/VGER_12345/cmd/ping" -m ""

# poner el nivel de log en debug
mosquitto_pub -t "vger32/VGER_12345/cmd/capability" -m "log=D"

# deshabilitar MQTT en el próximo ciclo
mosquitto_pub -t "vger32/VGER_12345/cmd/capability" -m "mqtt=0"
```

## Scrambler

El dispositivo incluye un ofuscador de payload para mensajes MQTT. No es criptografía — provee ofuscación contra observadores pasivos casuales.

Se habilita en tiempo de ejecución vía la preferencia `mqtt_scrambled` (configurable desde el dashboard o vía `POST /api/preferences`). No requiere recompilación.

**Algoritmo:** cifrado de flujo con PRNG derivado de la clave y XOR. Cada byte depende del byte anterior del texto cifrado, un paso del PRNG inicializado con la clave y el salt, y la clave misma.

**Salt:** 4 bytes aleatorios (RNG de hardware) anteponen cada texto cifrado. Mismo mensaje + misma clave → output distinto cada vez.

**Longitud de salida:** `largo de entrada + 4` bytes (el salt de 4 bytes).

**Opera sobre bytes crudos (0x00–0xFF)** — sin restricciones de caracteres.

La clave se configura en `keys.vger`:
```
scrambler_key=vger32xk
```

> Sin espacios alrededor del `=`. Formato: `key=value`, uno por línea.

La API del scrambler en `src/scrambler/scrambler.h`:

```cpp
// Encode: out debe tener al menos len + SCRAMBLER_SALT_LEN bytes.
// Devuelve el largo de salida (len + SCRAMBLER_SALT_LEN).
int scrambler_encode(const uint8_t *in, int len, uint8_t *out);

// Decode: in debe empezar con SCRAMBLER_SALT_LEN bytes de salt.
// out debe tener al menos len - SCRAMBLER_SALT_LEN bytes.
// Devuelve el largo de salida, o 0 si len es muy chico para contener un salt.
int scrambler_decode(const uint8_t *in, int len, uint8_t *out);
```

Cuando `cfg_mqtt_scrambled` es true, `mqtt_client.cpp` codifica todos los payloads de salida y decodifica todos los de entrada de forma transparente. Cualquier cliente que produzca o consuma mensajes MQTT debe aplicar el mismo algoritmo con la misma clave.

> El scrambler no afecta los endpoints HTTP.

## Licencia

MIT — ver [`LICENSE`](../../LICENSE)