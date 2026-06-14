#!/usr/bin/env python3
"""
vger32_discovery_test.py

Prueba los dos mecanismos de discovery del firmware vger32:
  1. UDP active discovery  — broadcast "vger32:discover" al puerto 4210
  2. mDNS                  — busca el servicio _vger32._tcp en la red local

Uso:
  python vger32_discovery_test.py           # prueba ambos
  python vger32_discovery_test.py --udp     # solo UDP
  python vger32_discovery_test.py --mdns    # solo mDNS
  python vger32_discovery_test.py --ip 192.168.1.45  # UDP unicast a IP conocida

Requisitos:
  pip install zeroconf        # solo para mDNS
"""

import argparse
import socket
import time

UDP_PORT  = 4210
UDP_MAGIC = b"vger32:discover"
TIMEOUT   = 2.0  # segundos esperando respuestas


# ─────────────────────────────────────────
# UDP
# ─────────────────────────────────────────

def test_udp(target_ip: str = "255.255.255.255"):
    label = "broadcast" if target_ip == "255.255.255.255" else f"unicast → {target_ip}"
    print(f"\n── UDP discovery ({label}) ──────────────────")

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.settimeout(TIMEOUT)
    sock.bind(("", 0))

    print(f"Enviando '{UDP_MAGIC.decode()}' → {target_ip}:{UDP_PORT}")
    sock.sendto(UDP_MAGIC, (target_ip, UDP_PORT))

    found = 0
    deadline = time.time() + TIMEOUT
    while time.time() < deadline:
        try:
            data, addr = sock.recvfrom(512)
            print(f"\n✓ Respuesta de {addr[0]}:{addr[1]}")
            print("─" * 40)
            print(data.decode(errors="replace"))
            found += 1
        except socket.timeout:
            break

    sock.close()
    print(f"── {found} dispositivo(s) encontrado(s) ──────")


# ─────────────────────────────────────────
# mDNS
# ─────────────────────────────────────────

def test_mdns(timeout: float = 5.0):
    print(f"\n── mDNS discovery (_vger32._tcp) ───────────")
    try:
        from zeroconf import ServiceBrowser, Zeroconf
    except ImportError:
        print("✗  zeroconf no instalado. Ejecutá: pip install zeroconf")
        return

    found = []

    class Listener:
        def add_service(self, zc, type_, name):
            info = zc.get_service_info(type_, name)
            if not info:
                return
            ip  = socket.inet_ntoa(info.addresses[0]) if info.addresses else "?"
            txt = {k.decode(): v.decode(errors="replace")
                   for k, v in info.properties.items()}
            found.append((name, ip, info.port, txt))
            print(f"\n✓ {name}")
            print(f"  ip:{ip}  port:{info.port}")
            for k, v in txt.items():
                print(f"  {k}={v}")

        def remove_service(self, zc, type_, name): pass
        def update_service(self, zc, type_, name): pass

    zc = Zeroconf()
    ServiceBrowser(zc, "_vger32._tcp.local.", Listener())

    print(f"Escuchando {timeout}s...")
    time.sleep(timeout)
    zc.close()
    print(f"\n── {len(found)} dispositivo(s) encontrado(s) ──────")


# ─────────────────────────────────────────
# Main
# ─────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="vger32 discovery tester")
    parser.add_argument("--udp",  action="store_true", help="Solo UDP")
    parser.add_argument("--mdns", action="store_true", help="Solo mDNS")
    parser.add_argument("--ip",   default=None,        help="IP destino (UDP unicast)")
    args = parser.parse_args()

    run_udp  = args.udp  or (not args.udp and not args.mdns)
    run_mdns = args.mdns or (not args.udp and not args.mdns)

    if run_udp:
        test_udp(args.ip or "255.255.255.255")
    if run_mdns:
        test_mdns()
