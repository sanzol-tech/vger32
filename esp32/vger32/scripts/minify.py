#!/usr/bin/env python3
"""
minify.py

Minifies the web dashboard source files from data-webapp/ into data/,
reducing HTML, CSS and JS size before uploading to LittleFS.
Other file types (images, fonts, etc.) are copied as-is.

Dependencies (auto-installed on first run):
    rcssmin   — CSS minifier
    rjsmin    — JS minifier
    (HTML is minified inline — no external dependency)

Usage:
    python scripts/minify.py

Run this before every filesystem upload:
    python scripts/minify.py
    pio run -e esp32dev -t uploadfs
"""

import sys
import subprocess
import shutil
from pathlib import Path

# ==========================================
# CONFIG
# ==========================================

SRC_DIR = Path("data-webapp")
DST_DIR = Path("data")

# ==========================================
# DEPENDENCY CHECK
# ==========================================

def ensure_deps():
    pkgs = ["rcssmin", "rjsmin"]
    for pkg in pkgs:
        try:
            __import__(pkg)
        except ImportError:
            print(f"[minify] Installing {pkg}...")
            subprocess.check_call([sys.executable, "-m", "pip", "install", pkg, "-q"])

# ==========================================
# MINIFIERS
# ==========================================

def minify_css(text):
    import rcssmin
    return rcssmin.cssmin(text)

def minify_js(text):
    import rjsmin
    return rjsmin.jsmin(text)

def minify_html(text):
    import re
    # Remove HTML comments (<!-- ... -->), excluding IE conditionals
    text = re.sub(r'<!--(?!\[if).*?-->', '', text, flags=re.DOTALL)
    # Collapse runs of whitespace (spaces, tabs, newlines) to a single space
    text = re.sub(r'\s+', ' ', text)
    # Remove spaces around block-level tags
    text = re.sub(r'\s*(<(?:html|head|body|div|section|article|header|footer|nav|main|'
                  r'ul|ol|li|table|thead|tbody|tr|th|td|form|fieldset|'
                  r'h[1-6]|p|pre|blockquote|script|style|link|meta|title)[^>]*>)\s*',
                  r'\1', text)
    return text.strip()

MINIFIERS = {
    ".css":  minify_css,
    ".js":   minify_js,
    ".html": minify_html,
}

# ==========================================
# MAIN
# ==========================================

def run_minify():
    if not SRC_DIR.exists():
        print(f"[minify] Source dir not found: {SRC_DIR} — skipping")
        return

    DST_DIR.mkdir(parents=True, exist_ok=True)

    ensure_deps()

    total = 0
    saved = 0

    for src_file in SRC_DIR.rglob("*"):
        if not src_file.is_file():
            continue

        rel      = src_file.relative_to(SRC_DIR)
        dst_file = DST_DIR / rel
        dst_file.parent.mkdir(parents=True, exist_ok=True)

        ext      = src_file.suffix.lower()
        minifier = MINIFIERS.get(ext)

        if minifier:
            text     = src_file.read_text(encoding="utf-8")
            minified = minifier(text)
            dst_file.write_text(minified, encoding="utf-8")

            orig = len(text.encode("utf-8"))
            mini = len(minified.encode("utf-8"))
            pct  = (1 - mini / orig) * 100 if orig else 0
            print(f"[minify] {rel}  {orig:,} → {mini:,} bytes  ({pct:.0f}% saved)")
            total += orig
            saved += orig - mini
        else:
            shutil.copy2(src_file, dst_file)
            print(f"[minify] {rel}  (copied)")

    pct_total = (saved / total) * 100 if total else 0
    print(f"[minify] Done — total saved: {saved:,} / {total:,} bytes ({pct_total:.0f}%)")


if __name__ == "__main__":
    run_minify()