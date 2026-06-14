#!/usr/bin/env python3
"""
build.py — Bundles config-qr-generator into a single HTML file.

Inlines CSS and JS (including secrets.js) in the same order as the HTML.
Strips single-line comments and blank lines from JS/CSS — no renaming,
no semicolon removal, no other transforms.

Output: config-qr-generator.bundle.html (same directory as this script)

Usage:
    python3 build.py
"""

import re
import sys
from pathlib import Path

HERE = Path(__file__).parent

HTML_IN  = HERE / 'config-qr-generator.html'
HTML_OUT = HERE / 'config-qr-generator.bundle.html'


def strip_js(src: str) -> str:
    """Remove single-line comments and collapse blank lines."""
    lines = []
    for line in src.splitlines():
        stripped = line.strip()
        if stripped.startswith('//'):
            continue
        lines.append(line)
    # collapse runs of blank lines to one
    result, prev_blank = [], False
    for line in lines:
        blank = line.strip() == ''
        if blank and prev_blank:
            continue
        result.append(line)
        prev_blank = blank
    return '\n'.join(result).strip()


def strip_css(src: str) -> str:
    """Remove /* */ comments and collapse blank lines."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    result, prev_blank = [], False
    for line in src.splitlines():
        blank = line.strip() == ''
        if blank and prev_blank:
            continue
        result.append(line)
        prev_blank = blank
    return '\n'.join(result).strip()


def inline_resources(html: str) -> str:
    """Replace <link rel=stylesheet> and <script src=> with inline blocks."""

    def replace_css(m):
        href = m.group(1)
        path = HERE / href
        if not path.exists():
            print(f'  [SKIP] CSS not found: {href}', file=sys.stderr)
            return m.group(0)
        print(f'  [CSS] {href}')
        return f'<style>\n{strip_css(path.read_text(encoding="utf-8"))}\n</style>'

    def replace_js(m):
        src = m.group(1)
        path = HERE / src
        if not path.exists():
            print(f'  [SKIP] JS not found: {src}', file=sys.stderr)
            return m.group(0)
        print(f'  [JS]  {src}')
        return f'<script>\n{strip_js(path.read_text(encoding="utf-8"))}\n</script>'

    html = re.sub(r'<link[^>]+rel=["\']stylesheet["\'][^>]*href=["\']([^"\']+)["\'][^>]*>',
                  replace_css, html)
    html = re.sub(r'<link[^>]+href=["\']([^"\']+)["\'][^>]*rel=["\']stylesheet["\'][^>]*>',
                  replace_css, html)
    html = re.sub(r'<script\s+src=["\']([^"\']+)["\']><\/script>',
                  replace_js, html)
    return html


def main():
    if not HTML_IN.exists():
        print(f'Error: {HTML_IN} not found.', file=sys.stderr)
        sys.exit(1)

    print(f'Bundling {HTML_IN.name} ...')
    html = HTML_IN.read_text(encoding='utf-8')
    html = inline_resources(html)
    HTML_OUT.write_text(html, encoding='utf-8')
    size = HTML_OUT.stat().st_size
    print(f'Done → {HTML_OUT.name} ({size:,} bytes)')


if __name__ == '__main__':
    main()
