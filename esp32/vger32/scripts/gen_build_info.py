#!/usr/bin/env python3
"""
gen_build_info.py

Generates src/config/build_info.h with a random version string and Unix
timestamp. Creates the file if it doesn't exist, overwrites it if it does.

Run before every firmware build:
    python scripts/gen_build_info.py

PlatformIO can invoke it automatically via extra_scripts in platformio.ini:
    extra_scripts = pre:scripts/gen_build_info.py
"""

import random
import string
import time
from pathlib import Path

OUT = Path("src/config/build_info.h")

version = ''.join(random.choices(string.ascii_lowercase + string.digits, k=10))
ts      = int(time.time())

OUT.write_text(
    f"#ifndef BUILD_INFO_H\n"
    f"#define BUILD_INFO_H\n"
    f"\n"
    f'#define BUILD_VERSION "{version}"\n'
    f"#define BUILD_TIME    {ts}\n"
    f"\n"
    f"#endif\n"
)

print(f"[gen_build_info] version={version} time={ts} -> {OUT}")
