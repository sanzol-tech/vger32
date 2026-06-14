<div align="center">
<img src="../../images/icon/vger32.svg" alt="VGER32" width="160" />

# VGER32 App Android

*App complementaria para dispositivos VGER32*
</div>

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](../../LICENSE)
[![minSdk: 30](https://img.shields.io/badge/minSdk-30-blue.svg)](app/build.gradle.kts)
[![compileSdk: 36](https://img.shields.io/badge/compileSdk-36-blue.svg)](app/build.gradle.kts)

## Qué hace

Vger32App descubre, monitorea y controla dispositivos VGER32 por WiFi local y MQTT — sin nube, sin IPs para recordar.

Pensada para makers y desarrolladores que necesitan:
- Encontrar dispositivos en la red sin conocer sus IPs
- Inspeccionar telemetría en vivo y métricas históricas
- Enviar configuración o disparar acciones remotas
- Mapear ubicaciones de dispositivos mediante huellas WiFi

## Arquitectura

| Capa | Tecnología |
|---|---|
| UI | Single-Activity, Jetpack Navigation, ViewBinding |
| Estado | ViewModel + LiveData, con alcance de Activity para persistencia entre tabs |
| Red | OkHttp (HTTP), socket TCP crudo con codec MQTT 3.1.1 propio |
| Seguridad | EncryptedSharedPreferences (AES-256-GCM), desbloqueo por PIN biométrico |
| Discovery | NsdManager (mDNS), DatagramSocket (UDP), sondeo de socket con hilos (LAN) |

## Funcionalidades

### Discovery

Cinco métodos independientes para localizar módulos. La app combina los resultados y registra el origen de cada descubrimiento.

| Método | Mecanismo | Requiere soporte de firmware |
|---|---|---|
| **MQTT ping** | Publica en `vger32/ping`; los módulos responden en `vger32/{mid}/pong` con identidad completa | Broker configurado |
| **mDNS** | Escucha pasivamente `_vger32._tcp`; resuelve registros TXT (`mid`, `chip`, `pid`, `ver`) | Sí |
| **UDP broadcast** | Envía `vger32:discover` a `255.255.255.255:4210`; los dispositivos responden tras un jitter aleatorio (≤200 ms) para evitar colisiones | Sí |
| **IP manual** | Sondeo HTTP directo a una dirección ingresada por el usuario | No |
| **Escaneo LAN** | Sondea la subred `/24` en el puerto 80 con 20 workers concurrentes | No |

Los módulos descubiertos se persisten en un archivo plano y se purgan automáticamente tras un período de inactividad configurable (default: 7 días).

### Monitor MQTT

- Se suscribe a `vger32/#` y muestra todo el tráfico entrante en un feed ordenado cronológicamente
- El feed sobrevive a cambios de pestaña (ViewModel con alcance de Activity)
- Comandos de salida: `ping`, `dash_on`, `dash_off`, `wifi_ap`, `publish_now`, `capability`, `sleep`, `reboot`, y mensajes libres
- Keepalive automático (30 s) y reconexión con backoff fijo

### Panel HTTP

Acceso directo y agrupado a la API REST del módulo:

| Grupo | Endpoints |
|---|---|
| **Monitor** | `GET /api/system-identity`, `GET /api/system-metrics`, `GET /api/sensors`, `GET /api/boot-history`, `GET /api/logs` |
| **Acciones** | `GET /api/preferences`, `POST /api/preferences`, `POST /api/reboot`, dashboard del navegador |

Los resultados se muestran en un bottom sheet copiable. El visor de logs (`GET /api/logs`) es un fragmento dedicado a pantalla completa con orden cronológico inverso y fusión de líneas de continuación.

### Localizer

Huellas WiFi para posicionamiento en interiores.

| Fuente | Cómo | ¿Se guarda? |
|---|---|---|
| Escaneo del teléfono | `WifiManager.startScan()` con fallback a resultados cacheados | Sí, como waypoint |
| Escaneo del módulo | `GET /api/wifi-scan` desde el ESP32 | Sí, como waypoint |
| Waypoints guardados | JSON local en `SharedPreferences` | — |

Los waypoints tienen nombre (1–6 caracteres, A–Z0–9), timestamp, y pueden subirse (`PUT /api/wifi-fingerprints`) o descargarse de un módulo en un formato compacto de firmware.

### Seguridad

- **Desbloqueo por PIN**: código de 6 dígitos con rechazo de secuencias triviales (no `111111`, `123456`, `654321`)
- **Fallback biométrico**: BiometricPrompt de Android en hardware compatible
- **Credenciales por módulo**: API key y clave del scrambler guardadas por `mid` en `EncryptedSharedPreferences`
- **Scrambling**: ofuscación de payload basada en XOR para MQTT y HTTP, con claves independientes
- **Sin nube**: todo el tráfico es local (WiFi LAN) o directo a un broker MQTT configurado por el usuario

## Build

```bash
./gradlew assembleDebug          # APK debug
./gradlew assembleRelease        # APK release firmado (requiere signing.properties)
```

### Product flavors

| Flavor | `minSdk` | API objetivo |
|---|---|---|
| `android11` | 30 | Android 11 |
| `android12` | 31 | Android 12 |
| `android13` | 32 | Android 13 |
| `android14` | 33 | Android 14 |
| `android15` | 34 | Android 15 |
| `android16` | 35 | Android 16 |

Los flavors resuelven diferencias de compatibilidad de librerías entre niveles de API. No eliminar.

## Licencia

MIT — ver [`LICENSE`](../../LICENSE)