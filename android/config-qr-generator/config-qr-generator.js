const SEP = '\x1f';
const VER = 'VG1';

const FIELDS = [
  'mqtt_broker_host', 'mqtt_broker_port',
  'unlock_code_enabled', 'api_key', 'scrambler_key'
];

function obfuscate(s) {
  const encoded = scramblerEncode(new TextEncoder().encode(s), QR_KEY);
  return btoa(String.fromCharCode(...encoded));
}

function generate() {
  const parts = [];
  FIELDS.forEach(key => {
    const el  = document.getElementById(`f_${key}`);
    const val = el.value.trim();
    if (el.tagName === 'SELECT' && val === '') return;
    parts.push(`${key}:${val}`);
  });
  const qrDiv = document.getElementById('qrcode');
  qrDiv.innerHTML = '';
  if (!parts.length) return;
  new QRCode(qrDiv, {
    text:         obfuscate(VER + SEP + parts.join(SEP)),
    width:        192,
    height:       192,
    correctLevel: QRCode.CorrectLevel.M
  });
}

function saveConfig() {
  const cfg = {};
  FIELDS.forEach(key => { cfg[key] = document.getElementById(`f_${key}`).value || null; });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify(cfg, null, 2)], { type: 'application/json' }));
  a.download = 'vger32_config.json';
  a.click();
  URL.revokeObjectURL(a.href);
}

function loadConfig(event) {
  const file = event.target.files[0]; if (!file) return;
  const reader = new FileReader();
  reader.onload = e => {
    try {
      const cfg = JSON.parse(e.target.result);
      FIELDS.forEach(key => {
        if (key in cfg) document.getElementById(`f_${key}`).value = cfg[key] || '';
      });
      generate();
    } catch { alert('Invalid file.'); }
  };
  reader.readAsText(file);
  event.target.value = '';
}

async function loadDefaults() {
  try {
    const r = await fetch('defaults.json');
    if (!r.ok) return;
    const cfg = await r.json();
    FIELDS.forEach(key => {
      if (key in cfg) document.getElementById(`f_${key}`).value = cfg[key] || '';
    });
    generate();
  } catch {}
}

loadDefaults();