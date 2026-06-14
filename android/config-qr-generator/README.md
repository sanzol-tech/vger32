# Vger32 — Config QR Generator

Generates configuration QR codes for the Vger32 app.
Works fully offline — just double-click the HTML file.

## Files

```
config-qr-generator.html          main page
config-qr-generator.css           styles
config-qr-generator.js            app logic
scrambler.js                          obfuscation algorithm
qrcode.min.js                         QR library (local, no CDN)
build.py                              bundles everything into a single HTML file
secrets.js                            ⚠ obfuscation key — do NOT commit
defaults.json                         optional default values — do NOT commit
config-qr-generator.bundle.html   build output — do NOT commit
```

## Build

Run `build.py` to produce a single self-contained HTML file with all CSS and JS inlined:

```
python3 build.py
```

Output: `config-qr-generator.bundle.html`

## Setup

1. Create `secrets.js` in the same directory with the following content:

   ```js
   const QR_KEY = 'the_key_here';
   ```

   Ask the project owner for the key.

2. Open `config-qr-generator.html` with a double-click.

## .gitignore

`secrets.js` and `*.json` are already listed in `.gitignore`.
Never commit `secrets.js` or `defaults.json`.

## Compatibility

QR codes generated with this version require the Vger32 app with Scrambler v1.2.
Not compatible with earlier versions of the app.