// =============================================
//  AUTH
//  Session key lives in sessionStorage — survives F5, lost on tab close.
//  User is prompted once per session via Modal.prompt().
//  On 401: session is cleared and the next request re-prompts.
// =============================================
const SESSION_KEY_NAME = 'vger32_api_key';

// If the app passes ?key= in the URL, store it and clean the address bar.
(function () {
	const params = new URLSearchParams(window.location.search);
	const key = params.get('key');
	if (key) {
		sessionStorage.setItem(SESSION_KEY_NAME, key);
		history.replaceState(null, '', window.location.pathname);
	}
})();

let _promptingKey = false;

async function ensureKey() {
	console.log('[Auth] ensureKey called, hasKey:', !!sessionStorage.getItem(SESSION_KEY_NAME), '_promptingKey:', _promptingKey);
	if (sessionStorage.getItem(SESSION_KEY_NAME)) return;
	if (_promptingKey) { console.log('[Auth] Already prompting, skipping'); return; }
	_promptingKey = true;
	try {
		const key = await Modal.prompt('Enter API key:');
		if (key) sessionStorage.setItem(SESSION_KEY_NAME, key);
		console.log('[Auth] Key stored:', !!key);
	} finally {
		_promptingKey = false;
	}
}

function getKey()   { return sessionStorage.getItem(SESSION_KEY_NAME); }
function clearKey() { sessionStorage.removeItem(SESSION_KEY_NAME); }

// =============================================
//  1. CLIENT API (DeviceAPI)
//     Centralizes all fetch calls with uniform
//     error handling and parsing.
//     Order matches backend endpoint declaration.
// =============================================
const KV_SEP    = '=';      // key=value — valores simples (IDs, IPs, nums, flags)
const FIELD_SEP = '\x1F';   // ASCII Unit Separator — valores arbitrarios (passwords, URLs, SSIDs)
const KNOWN_NET_MAX = 10;   // must match KNOWN_NET_MAX in known_networks.h

const DeviceAPI = {
	async _getText(url) {
		await ensureKey();
		const r = await fetch(url, {headers: {'X-API-Key': getKey()}});
		if (r.status === 401) { clearKey(); return DeviceAPI._getText(url); }
		if (!r.ok) throw new Error(`HTTP ${r.status} — ${url}`);
		return r.text();
	},
	async _post(url, body = null, contentType = 'text/plain') {
		await ensureKey();
		const options = {method: 'POST', headers: {'X-API-Key': getKey()}};
		if (body !== null) {
			options.headers['Content-Type'] = contentType;
			options.body = body;
		}
		const r = await fetch(url, options);
		if (r.status === 401) { clearKey(); return DeviceAPI._post(url, body, contentType); }
		return r.ok;
	},
	async _delete(url) {
		await ensureKey();
		const r = await fetch(url, {method: 'DELETE', headers: {'X-API-Key': getKey()}});
		if (r.status === 401) { clearKey(); return DeviceAPI._delete(url); }
		return r.ok;
	},
	async _put(url, body, contentType = 'text/plain') {
		await ensureKey();
		const options = {method: 'PUT', headers: {'X-API-Key': getKey(), 'Content-Type': contentType}, body};
		const r = await fetch(url, options);
		if (r.status === 401) { clearKey(); return DeviceAPI._put(url, body, contentType); }
		return r.ok;
	},

	// System
	getIdentity:    () => DeviceAPI._getText('/api/system-identity'),
	getMetrics:     () => DeviceAPI._getText('/api/system-metrics'),
	getBootHistory: () => DeviceAPI._getText('/api/boot-history'),
	deleteLogs:     () => DeviceAPI._delete('/api/logs'),

	// Configuration
	getPreferences:       () => DeviceAPI._getText('/api/preferences'),
	postPreferences: (body) => DeviceAPI._post('/api/preferences', body),
	getKnownNetworks:      () => DeviceAPI._getText('/api/known-networks'),
	postKnownNetworks:(body) => DeviceAPI._post('/api/known-networks', body),

	// Sensors
	getSensors:       ()      => DeviceAPI._getText('/api/sensors'),
	getSensorHistory: (h, m)  => DeviceAPI._getText(`/api/sensor-history?h=${h}&m=${m}`),

	// Network
	// Returns array of { ssid, mac, rssi, channel }
	async getWifiScan() {
		const text = await DeviceAPI._getText('/api/wifi-scan');
		if (!text.trim()) return [];
		return text.trim().split('\n').map(line => {
			const [ssid, mac, rssi, channel] = line.split('|');
			return {ssid, mac, rssi: +rssi, channel: +channel};
		});
	},
	postWifiFingerprint:    (payload) => DeviceAPI._post('/api/wifi-fingerprints', payload),
	putWifiFingerprints:    (payload) => DeviceAPI._put('/api/wifi-fingerprints', payload),

	// Location
	// Returns array of { waypoint, ts, score }
	async getLocation() {
		const text = await DeviceAPI._getText('/api/location');
		if (!text.trim()) return [];
		return text.trim().split('|').map(entry => {
			const [waypoint, ts, score] = entry.split(':');
			return {waypoint, ts: +ts, score: +score};
		});
	},

	// Time
	getTime:          ()       => DeviceAPI._getText('/api/time'),
	postTime: (unix_ts) => DeviceAPI._post('/api/time', `ts=${unix_ts}\n`),

	// Control
	postReboot:   () => DeviceAPI._post('/api/reboot'),
	postForceAp:  () => DeviceAPI._post('/api/force-ap'),
};

// =============================================
//  2. TEXT FORMAT HELPERS
//     Convert between compact wire format and JS objects.
// =============================================

// Generic key[sep]value\n parser.
// sep defaults to KV_SEP ('=') for simple values.
// Pass FIELD_SEP for payloads with arbitrary values (passwords, URLs, SSIDs).
function parseKV(text, sep = KV_SEP) {
	const result = {};
	text.trim().split('\n').forEach(line => {
		const idx = line.indexOf(sep);
		if (idx > 0) result[line.substring(0, idx)] = line.substring(idx + 1).trimEnd();
	});
	return result;
}

// preferences text → flat object with all fields (MQTT + WiFi + flags)
function parsePreferencesText(text) { return parseKV(text, FIELD_SEP); }

// object → preferences text
function buildPreferencesText(data) {
	return Object.entries(data)
		.map(([k, v]) => `${k}${FIELD_SEP}${v}`)
		.join('\n') + '\n';
}

// known-networks text → array of { type, identifier, pass }
function parseNetworksText(text) {
	if (!text.trim()) return [];
	return text.trim().split('\n').map(line => {
		const parts = line.split(FIELD_SEP);
		return {type: parts[0] || 'S', identifier: parts[1] || '', pass: parts[2] || ''};
	}).filter(n => n.identifier.length > 0);
}

// array of { type, identifier, pass } → known-networks text
function buildNetworksText(rows) {
	return rows.map(r => `${r.type}${FIELD_SEP}${r.identifier}${FIELD_SEP}${r.pass}`).join('\n') + '\n';
}

// Validate a single network row. Returns error string or null.
function validateNetworkRow(row) {
	if (!row.identifier) return 'SSID or MAC cannot be empty';
	if (row.type === 'S' && row.identifier.length > 32) return 'SSID too long (max 32 chars)';
	if (row.type === 'M' && !/^[0-9A-F]{12}$/.test(row.identifier.toUpperCase()))
		return `Invalid MAC "${row.identifier}" — use 12 uppercase hex chars, no colons`;
	return null;
}

// =============================================
//  3. GLOBAL STATE
// =============================================
const State = {
	system:   { uptime: '' },
	identity: { pid: '', mid: '', chip: '', brd: '', ver: '', ip: '', sts: '' },
	time:     { ts: 0 },
	sensors: [],
	metrics: {},
	wifiNetworks: [],
	location: [],
	activeSensorId: null,
	online: true,
	tabLoaded: {
		config: false,
	},
	panelLoaded: {
		boot: false,
		wifi: false,
		sensor: false,
		location: false,
	},
};

const WIFI_RSSI_PREFERRED    = -90;
const WIFI_SCAN_MAX_NETWORKS = 30; // must match WIFI_SCAN_MAX_APS in wifi_scanner.h

const REFRESH_INTERVALS = {
	time:    10_000,  // clock — always
	content: 30_000,  // identity + metrics (module tab) / sensors (sensors tab)
};

// =============================================
//  4. MODAL
// =============================================
const Modal = (() => {
	function _build(inner) {
		const overlay = document.createElement('div');
		overlay.className = 'modal-overlay';
		overlay.innerHTML = `<div class="modal">${inner}</div>`;
		document.body.appendChild(overlay);
		requestAnimationFrame(() => overlay.classList.add('visible'));
		return overlay;
	}

	function _close(overlay) {
		overlay.classList.remove('visible');
		overlay.addEventListener('transitionend', () => overlay.remove(), {once: true});
	}

	function confirm(message) {
		return new Promise(resolve => {
			const overlay = _build(`
                <div class="modal-message">${message}</div>
                <div class="modal-actions">
                    <button class="modal-btn secondary" id="_modalCancel">Cancel</button>
                    <button class="modal-btn primary"   id="_modalOk">OK</button>
                </div>
            `);
			overlay.querySelector('#_modalOk').addEventListener('click', () => { _close(overlay); resolve(true); });
			overlay.querySelector('#_modalCancel').addEventListener('click', () => { _close(overlay); resolve(false); });
		});
	}

	function prompt(message, placeholder = '', type = 'password') {
		return new Promise(resolve => {
			const overlay = _build(`
                <div class="modal-message">${message}</div>
                <input class="modal-input" id="_modalInput" type="${type}" placeholder="${placeholder}" autocomplete="off" />
                <div class="modal-actions">
                    <button class="modal-btn secondary" id="_modalCancel">Cancel</button>
                    <button class="modal-btn primary"   id="_modalOk">OK</button>
                </div>
            `);
			const input = overlay.querySelector('#_modalInput');
			input.focus();
			const submit = () => { _close(overlay); resolve(input.value); };
			const cancel = () => { _close(overlay); resolve(null); };
			overlay.querySelector('#_modalOk').addEventListener('click', submit);
			overlay.querySelector('#_modalCancel').addEventListener('click', cancel);
			input.addEventListener('keydown', e => { if (e.key === 'Enter') submit(); if (e.key === 'Escape') cancel(); });
		});
	}

	return {confirm, prompt};
})();

// =============================================
//  5. UI MANAGER
// =============================================
const UI = {
	setText(id, value) {
		const el = document.getElementById(id);
		if (el) el.textContent = value;
	},

	setLoading(buttonId, isLoading) {
		const btn = document.getElementById(buttonId);
		if (!btn) return;
		btn.classList.toggle('loading', isLoading);
	},

	toast(message, type = 'success', duration = 3000) {
		const container = document.getElementById('toastContainer');
		if (!container) return;
		const toast = document.createElement('div');
		toast.className = `toast${type !== 'success' ? ' ' + type : ''}`;
		toast.textContent = message;
		container.appendChild(toast);
		setTimeout(() => {
			toast.classList.add('fade-out');
			toast.addEventListener('animationend', () => toast.remove(), {once: true});
		}, duration);
	},

	setOnline(isOnline) {
		if (State.online === isOnline) return;
		State.online = isOnline;
		const banner = document.getElementById('offlineBanner');
		if (banner) banner.classList.toggle('visible', !isOnline);
		if (isOnline) UI.toast('Device reconnected.');
	},
};

// =============================================
//  6. TAB NAVIGATION
// =============================================
let _activeTab = 'module';

async function loadTab(tabId) {
	console.log('[Tab] loadTab:', tabId, 'hasKey:', !!getKey(), '_promptingKey:', _promptingKey);
	if (!getKey() || _promptingKey) { console.log('[Tab] loadTab blocked — no key or prompting'); return; }
	if (tabId === 'module') {
		console.log('[Tab] Loading module tab');
		refreshIdentity();
		refreshSystemMetrics();
	} else if (tabId === 'sensors') {
		console.log('[Tab] Loading sensors tab');
		refreshSensors();
	} else if (tabId === 'config' && !State.tabLoaded.config) {
		console.log('[Tab] Loading config tab (lazy)');
		State.tabLoaded.config = true;
		loadPreferences();
		loadSavedNetworks();
	} else {
		console.log('[Tab] No load needed for:', tabId);
	}
}

function switchTab(tabId) {
	console.log('[Tab] switchTab:', tabId);
	_activeTab = tabId;
	document.querySelectorAll('.tab-btn').forEach(btn => {
		btn.classList.toggle('active', btn.dataset.tab === tabId);
	});
	document.querySelectorAll('.tab-panel').forEach(panel => {
		panel.classList.toggle('active', panel.id === 'tab-' + tabId);
	});
	loadTab(tabId);
}

// =============================================
//  7. PANEL TOGGLE
// =============================================
async function togglePanel(panelId) {
	const panel = document.getElementById(panelId);
	const btn   = document.getElementById(panelId + 'Btn');
	if (!panel || !btn) return;

	const wasCollapsed = panel.classList.contains('collapsed');
	panel.classList.toggle('collapsed', !wasCollapsed);
	btn.textContent = wasCollapsed ? '−' : '+';

	if (wasCollapsed) {
		if      (panelId === 'bootPanel'     && !State.panelLoaded.boot)     { State.panelLoaded.boot     = true; await refreshBootHistory(); }
		else if (panelId === 'wifiPanel'     && !State.panelLoaded.wifi)     { State.panelLoaded.wifi     = true; await refreshWifiScan(); }
		else if (panelId === 'locationPanel' && !State.panelLoaded.location) { State.panelLoaded.location = true; await refreshLocation(); }
	}
}

// =============================================
//  8. RENDER FUNCTIONS
//     Pure DOM writes. No fetch, no state mutation.
// =============================================
function renderSystemInfo() {
	UI.setText('status',    State.identity.sts || 'N/A');
	UI.setText('statusIp',  State.identity.ip  || '—');
	UI.setText('ipAddress', State.identity.ip  || 'N/A');
	UI.setText('connMode',  State.identity.sts || 'N/A');
	if (State.system.uptime) UI.setText('uptime', State.system.uptime);
}

function renderIdentity() {
	UI.setText('moduleId',  State.identity.mid  || 'N/A');
	UI.setText('chipModel', State.identity.chip || '—');
	UI.setText('activePid', State.identity.pid  || 'N/A');

	const pid = State.identity.pid ? `Mission: ${State.identity.pid}` : '';
	const ver = State.identity.ver ? `v: ${State.identity.ver}` : '';
	UI.setText('buildVer', [pid, ver].filter(Boolean).join('  '));
}

function renderTime() {
	if (!State.time.ts) return;
	const date = new Date(State.time.ts * 1000);
	UI.setText('currentTime', date.toLocaleTimeString());
}

function renderSystemMetrics() {
	const grid = document.getElementById('metricsGrid');
	if (!grid) return;

	const entries = Object.entries(State.metrics);
	if (!entries.length) {
		grid.innerHTML = '<div class="empty-state">No metrics available</div>';
		return;
	}

	grid.innerHTML = entries.map(([key, value]) => `
        <div class="metric-item">
            <div class="status-label">${key}</div>
            <div class="status-value">${value}</div>
        </div>
    `).join('');
}

function renderSensors() {
	const grid = document.getElementById('telemetryGrid');
	if (!grid) return;

	if (!State.sensors.length) {
		grid.innerHTML = '<div class="empty-state">No sensors available</div>';
		return;
	}

	grid.innerHTML = State.sensors.map(s => `
        <div class="sensor-card ${s.code === State.activeSensorId ? 'active' : ''}"
             data-sensor-code="${s.code}">
            <div class="sensor-model-label">${s.model} — ${s.metric}</div>
            <div class="sensor-reading">
                ${s.value !== null ? s.value : '--'}
                <span class="sensor-unit">${s.unit}</span>
            </div>
        </div>
    `).join('');
}

function renderPreferences(prefs) {
	if (!prefs) return;

	// Module ID — split on first '_', prefix becomes label, rest is editable
	const fullId = prefs.moduleId || '';
	const sepIdx = fullId.indexOf('_');
	if (sepIdx >= 0) {
		const prefix = document.getElementById('cfgModulePrefix');
		const suffix = document.getElementById('cfgModuleSuffix');
		if (prefix) prefix.textContent = fullId.substring(0, sepIdx + 1);
		if (suffix) suffix.value = fullId.substring(sepIdx + 1);
	} else {
		const prefix = document.getElementById('cfgModulePrefix');
		const suffix = document.getElementById('cfgModuleSuffix');
		if (prefix) prefix.textContent = '';
		if (suffix) suffix.value = fullId;
	}

	// MQTT connection
	const mqttFields = {
		cfgMqttServer:   prefs.mqttServer   || '',
		cfgMqttPort:     prefs.mqttPort     ?? 1883,
		cfgMqttInterval: prefs.mqttInterval ?? 120,
	};
	for (const [id, value] of Object.entries(mqttFields)) {
		const el = document.getElementById(id);
		if (el) el.value = value;
	}
	const scmbEl = document.getElementById('cfgMqttScrambled');
	if (scmbEl) scmbEl.checked = prefs.mqttScrambled === '1';

	// WiFi
	const wifi = document.getElementById('cfgWifi');
	if (wifi) wifi.checked = prefs.wifi === '1';

	const txpwr = document.getElementById('cfgTxpwr');
	if (txpwr) txpwr.value = prefs.txpwr || 'full';

	// Feature flags
	const bools = {
		cfgMqttEnabled: 'mqtt',
		cfgDash:        'dash',
		cfgLocl:        'locl',
		cfgMdns:        'mdns',
		cfgUdp:         'udp',
		cfgSlep:        'slep',
		cfgTime:        'time',
		cfgBlog:        'blog',
	};
	for (const [id, key] of Object.entries(bools)) {
		const el = document.getElementById(id);
		if (el) el.checked = prefs[key] === '1';
	}

	// Log level — combo
	const logEl = document.getElementById('cfgLog');
	if (logEl) logEl.value = prefs.log || 'W';
}

function renderBootHistory(text) {
	const list = document.getElementById('bootHistoryList');
	if (!list) return;

	const lines = text.trim().split('\n').filter(l => l.length > 0);
	if (!lines.length) {
		list.innerHTML = '<div class="empty-state">No boot records found</div>';
		return;
	}

	list.innerHTML = [...lines].reverse().map(line => {
		const [ts, reason] = line.split(' ');
		const date = new Date(+ts * 1000);
		return `
            <div class="list-item history-item">
                <div class="item-label">${reason ?? 'UNKNOWN'}</div>
                <div class="item-time">${date.toLocaleDateString()}<br>${date.toLocaleTimeString()}</div>
            </div>
        `;
	}).join('');
}

// history: { unit, samples: [{v, t}] }
function renderSensorHistory(sensor, history) {
	const list = document.getElementById('sensorHistoryList');
	if (!list) return;

	if (!history?.samples?.length) {
		list.innerHTML = '<div class="empty-state">No sensor history found</div>';
		return;
	}

	list.innerHTML = [...history.samples].reverse().map(entry => {
		const date = new Date(entry.t * 1000);
		return `
            <div class="list-item history-item">
                <div class="item-value">
                    ${entry.v.toFixed(2)} <span class="item-unit">${history.unit}</span>
                </div>
                <div class="item-time">${date.toLocaleDateString()}<br>${date.toLocaleTimeString()}</div>
            </div>
        `;
	}).join('');
}

function renderWifiScan() {
	const list = document.getElementById('wifiList');
	if (!list) return;

	if (!State.wifiNetworks.length) {
		list.innerHTML = '<div class="empty-state">No networks found</div>';
		return;
	}

	list.innerHTML = State.wifiNetworks.map(net => {
		let signalClass = '';
		if (net.rssi < -80) signalClass = 'very-weak';
		else if (net.rssi < -60) signalClass = 'weak';
		return `
            <div class="list-item wifi-item">
                <div class="item-label">${net.ssid || 'Hidden Network'}</div>
                <div class="item-meta">${net.mac || 'N/A'}</div>
                <div class="wifi-signal ${signalClass}">${net.rssi} dBm</div>
                <div class="item-meta">Ch ${net.channel ?? 'N/A'}</div>
            </div>
        `;
	}).join('');
}

function renderLocation() {
	const list = document.getElementById('locationList');
	if (!list) return;

	if (!State.location.length) {
		list.innerHTML = '<div class="empty-state">No location history yet</div>';
		return;
	}

	list.innerHTML = State.location.map(entry => {
		const date      = new Date(entry.ts * 1000);
		const lost      = entry.waypoint === '-';
		const label     = lost ? 'Location lost' : entry.waypoint;
		const itemClass = lost ? 'location-lost' : '';
		const scoreStr  = (!lost && entry.score) ? `${entry.score}%` : '';
		return `
            <div class="list-item location-item ${itemClass}">
                <div class="item-label">${label}</div>
                <div class="item-meta">${scoreStr}</div>
                <div class="item-time">${date.toLocaleDateString()}<br>${date.toLocaleTimeString()}</div>
            </div>
        `;
	}).join('');
}

// =============================================
//  9. SAVED NETWORKS
// =============================================
function addNetworkRow(net = null) {
	const list = document.getElementById('savedNetworksList');
	if (!list) return;

	if (list.children.length >= KNOWN_NET_MAX) {
		UI.toast(`Maximum ${KNOWN_NET_MAX} networks allowed.`, 'warning');
		return;
	}

	const typeVal       = net?.type       || 'S';
	const identifierVal = net?.identifier || '';
	const passVal       = net?.pass       || '';

	const item = document.createElement('div');
	item.className = 'list-item saved-network-item';
	item.innerHTML = `
        <select class="net-select net-type">
            <option value="S" ${typeVal === 'S' ? 'selected' : ''}>SSID</option>
            <option value="M" ${typeVal === 'M' ? 'selected' : ''}>MAC</option>
        </select>
        <input type="text"     class="net-input net-identifier" value="${identifierVal}" placeholder="Network name or MAC">
        <input type="password" class="net-input net-pass"       value="${passVal}"       placeholder="open network">
        <button class="btn-delete-row" title="Remove">✕</button>
    `;

	item.querySelector('.btn-delete-row').addEventListener('click', () => item.remove());

	const passInput = item.querySelector('.net-pass');
	passInput.addEventListener('focus', () => {
		if (passInput.value === '********') passInput.value = '';
	});

	list.appendChild(item);
}

function collectNetworkRows() {
	const rows = [];
	document.querySelectorAll('#savedNetworksList .saved-network-item').forEach(item => {
		const type       = item.querySelector('.net-type').value;
		const identifier = item.querySelector('.net-identifier').value.trim();
		const passRaw    = item.querySelector('.net-pass').value;
		rows.push({
			type,
			identifier: type === 'M' ? identifier.toUpperCase() : identifier,
			pass: passRaw,
		});
	});
	return rows;
}

function renderSavedNetworks(networks) {
	const list = document.getElementById('savedNetworksList');
	if (!list) return;
	list.innerHTML = '';
	networks.forEach(net => addNetworkRow(net));
}

// =============================================
//  10. REFRESH FUNCTIONS
//      Fetch → mutate State → render.
// =============================================
async function refreshIdentity() {
	console.log('[Refresh] refreshIdentity called');
	try {
		const text = await DeviceAPI.getIdentity();
		console.log('[Refresh] refreshIdentity got response, len:', text?.length);
		const data = parseKV(text);
		State.identity = {
			mid:  data.mid  || '',
			chip: data.chip || '',
			brd:  data.brd  || '',
			pid:  data.pid  || '',
			ip:   data.ip   || '',
			ver:  data.ver  || '',
			sts:  data.sts  || '',
		};
		renderIdentity();
		renderSystemInfo();
		UI.setOnline(true);
	} catch (err) {
		console.error('refreshIdentity:', err);
		UI.setOnline(false);
	}
}

async function refreshTime() {
	console.log('[Refresh] refreshTime called');
	try {
		const text = await DeviceAPI.getTime();
		const data = parseKV(text);
		State.time.ts = data.ts ? +data.ts : 0;
		console.log('[Refresh] refreshTime ts:', State.time.ts);
		renderTime();
	} catch (err) {
		console.error('[Refresh] refreshTime error:', err);
	}
}

async function refreshSystemMetrics() {
	console.log('[Refresh] refreshSystemMetrics called');
	try {
		const text = await DeviceAPI.getMetrics();
		const data = parseKV(text);
		State.system.uptime = data.upt ?? 'N/A';
		const {upt, ...rest} = data;
		State.metrics = rest;
		console.log('[Refresh] refreshSystemMetrics keys:', Object.keys(rest));
		renderSystemInfo();
		renderSystemMetrics();
	} catch (err) {
		console.error('[Refresh] refreshSystemMetrics error:', err);
	}
}

async function loadPreferences() {
	console.log('[Load] loadPreferences called');
	try {
		const text  = await DeviceAPI.getPreferences();
		const prefs = parsePreferencesText(text);
		console.log('[Load] loadPreferences keys:', Object.keys(prefs));
		renderPreferences(prefs);
	} catch (err) {
		console.error('[Load] loadPreferences error:', err);
	}
}

async function loadSavedNetworks() {
	try {
		const text     = await DeviceAPI.getKnownNetworks();
		const networks = parseNetworksText(text);
		renderSavedNetworks(networks);
	} catch (err) {
		console.error('loadSavedNetworks:', err);
	}
}

async function refreshSensors() {
	console.log('[Refresh] refreshSensors called');
	try {
		const text = await DeviceAPI.getSensors();
		if (!text.trim()) { console.log('[Refresh] refreshSensors — empty response'); return; }
		State.sensors = text.trim().split('\n').map(line => {
			const parts = line.split('|');
			if (parts.length < 5) return null;
			const [h, m, v, t, u] = parts;
			return {
				id:     `${h}-${m}`,
				code:   `${h}-${m}`,
				model:  h.toUpperCase(),
				metric: m.toUpperCase(),
				value:  parseFloat(v),
				unit:   u ?? '',
			};
		}).filter(s => s !== null);
		console.log('[Refresh] refreshSensors count:', State.sensors.length);
		renderSensors();
	} catch (err) {
		console.error('[Refresh] refreshSensors error:', err);
	}
}

async function refreshSensorHistory(sensor) {
	if (!sensor?.code) return;
	const [hardware, metric] = sensor.code.split('-');
	try {
		const text  = await DeviceAPI.getSensorHistory(hardware, metric);
		const lines = text.trim().split('\n');

		if (lines.length < 2) {
			renderSensorHistory(sensor, {unit: '', samples: []});
			return;
		}

		// First line is the header: h|m|unit
		const headerParts = lines[0].split('|');
		const unit = headerParts.length >= 3 ? headerParts[2] : '';

		// Remaining lines: value|timestamp
		const samples = lines.slice(1).map(line => {
			const [v, t] = line.split('|');
			if (v === undefined || t === undefined) return null;
			return {v: parseFloat(v), t: +t};
		}).filter(s => s !== null);

		renderSensorHistory(sensor, {unit: unit ?? '', samples});
	} catch (err) {
		console.error('refreshSensorHistory:', err);
	}
}

async function refreshActiveSensorHistory() {
	UI.setLoading('sensorRefreshBtn', true);
	try {
		if (State.activeSensorId) {
			const sensor = State.sensors.find(s => s.code === State.activeSensorId);
			if (sensor) await refreshSensorHistory(sensor);
		}
	} finally {
		UI.setLoading('sensorRefreshBtn', false);
	}
}

async function refreshBootHistory() {
	UI.setLoading('bootRefreshBtn', true);
	try {
		const text = await DeviceAPI.getBootHistory();
		renderBootHistory(text);
	} catch (err) {
		console.error('refreshBootHistory:', err);
	} finally {
		UI.setLoading('bootRefreshBtn', false);
	}
}

async function refreshWifiScan() {
	UI.setLoading('wifiRefreshBtn', true);
	const prevSts = State.identity.sts;
	try {
		State.identity.sts = 'Scanning…';
		renderSystemInfo();
		State.wifiNetworks = await DeviceAPI.getWifiScan();
		renderWifiScan();
	} catch (err) {
		console.error('refreshWifiScan:', err);
	} finally {
		State.identity.sts = prevSts;
		renderSystemInfo();
		UI.setLoading('wifiRefreshBtn', false);
	}
}

async function refreshLocation() {
	UI.setLoading('locationRefreshBtn', true);
	try {
		State.location = await DeviceAPI.getLocation();
		renderLocation();
	} catch (err) {
		console.error('refreshLocation:', err);
	} finally {
		UI.setLoading('locationRefreshBtn', false);
	}
}

// =============================================
//  12. USER INTERACTION HANDLERS
// =============================================
async function selectSensor(sensorCode) {
	State.activeSensorId = sensorCode;

	document.querySelectorAll('.sensor-card').forEach(card => {
		card.classList.toggle('active', card.dataset.sensorCode === sensorCode);
	});

	const sensor = State.sensors.find(s => s.code === sensorCode);
	if (!sensor) return;

	UI.setText('sensorPanelTitle', `${sensor.model} ${sensor.metric} History`);

	try {
		await refreshSensorHistory(sensor);

		const panel = document.getElementById('sensorPanel');
		const btn   = document.getElementById('sensorPanelBtn');
		if (panel?.classList.contains('collapsed')) {
			panel.classList.remove('collapsed');
			if (btn) btn.textContent = '−';
		}
		State.panelLoaded.sensor = true;
	} catch (err) {
		console.error('selectSensor:', err);
		const list = document.getElementById('sensorHistoryList');
		if (list) list.innerHTML = '<div class="empty-state error">Error loading history</div>';
	}
}

async function savePreferences() {
	if (!await Modal.confirm('Save preferences?')) return;

	// Reconstruct full module ID from prefix label + editable suffix
	const prefix   = document.getElementById('cfgModulePrefix')?.textContent || '';
	const suffix   = document.getElementById('cfgModuleSuffix')?.value       || '';
	const moduleId = prefix + suffix;

	const data = {
		moduleId:      moduleId,
		mqttServer:    document.getElementById('cfgMqttServer')?.value   || '',
		mqttPort:      document.getElementById('cfgMqttPort')?.value     || '1883',
		mqttInterval:  document.getElementById('cfgMqttInterval')?.value || '120',
		mqttScrambled: document.getElementById('cfgMqttScrambled')?.checked ? '1' : '0',
		wifi:          document.getElementById('cfgWifi')?.checked        ? '1' : '0',
		txpwr:         document.getElementById('cfgTxpwr')?.value         || 'full',
		mqtt:          document.getElementById('cfgMqttEnabled')?.checked ? '1' : '0',
		dash:          document.getElementById('cfgDash')?.checked        ? '1' : '0',
		locl:          document.getElementById('cfgLocl')?.checked        ? '1' : '0',
		mdns:          document.getElementById('cfgMdns')?.checked        ? '1' : '0',
		udp:           document.getElementById('cfgUdp')?.checked         ? '1' : '0',
		slep:          document.getElementById('cfgSlep')?.checked        ? '1' : '0',
		time:          document.getElementById('cfgTime')?.checked        ? '1' : '0',
		blog:          document.getElementById('cfgBlog')?.checked        ? '1' : '0',
		log:           document.getElementById('cfgLog')?.value           || 'W',
	};

	if (!data.moduleId) {
		UI.toast('Module ID cannot be empty.', 'error');
		return;
	}

	try {
		const ok = await DeviceAPI.postPreferences(buildPreferencesText(data));
		if (ok) {
			UI.toast('Preferences saved.');
		} else {
			UI.toast('Error saving preferences.', 'error');
		}
	} catch (err) {
		console.error('savePreferences:', err);
		UI.toast('Error saving preferences.', 'error');
	}
}

async function saveSavedNetworks() {
	const rows = collectNetworkRows();

	for (const row of rows) {
		const err = validateNetworkRow(row);
		if (err) {
			UI.toast(err, 'error');
			return;
		}
	}

	try {
		const ok = await DeviceAPI.postKnownNetworks(buildNetworksText(rows));
		if (ok) {
			UI.toast('Known networks saved.');
		} else {
			UI.toast('Error saving networks.', 'error');
		}
	} catch (err) {
		console.error('saveSavedNetworks:', err);
		UI.toast('Error saving networks.', 'error');
	}
}

async function rebootDevice(message, apiCall, toast) {
	if (!await Modal.confirm(message)) return;
	try {
		await apiCall();
	} catch (_) {
		// Expected — device disconnects during reboot
	}
	UI.toast(toast);
	setTimeout(() => location.reload(), 6000);
}

async function saveWifiFingerprint() {
	if (!State.wifiNetworks.length) {
		UI.toast('No networks available. Refresh the scan first.', 'warning');
		return;
	}

	let waypoint = await Modal.prompt('Enter waypoint name (3-6 uppercase letters/digits):', 'POINT1', 'text');
	if (waypoint === null) return;

	waypoint = waypoint.trim().toUpperCase();
	if (!/^[A-Z0-9]{3,6}$/.test(waypoint)) {
		UI.toast('Invalid waypoint. Use 3-6 uppercase letters or digits.', 'error');
		return;
	}

	const preferred = State.wifiNetworks.filter(n => n.rssi >= WIFI_RSSI_PREFERRED);
	const rest      = State.wifiNetworks.filter(n => n.rssi <  WIFI_RSSI_PREFERRED);
	const nets      = [...preferred, ...rest].slice(0, WIFI_SCAN_MAX_NETWORKS);

	// Compact payload: WAY(1-6) + N × [ MAC(12) + CH(2) + RSSI(3) ]
	let payload = waypoint;
	for (const net of nets) {
		const mac  = net.mac.toUpperCase().padEnd(12, '0');
		const ch   = String(net.channel).padStart(2, '0');
		const rssi = String(Math.abs(net.rssi)).padStart(3, '0');
		payload += mac + ch + rssi;
	}

	try {
		const ok = await DeviceAPI.postWifiFingerprint(payload);
		if (ok) {
			UI.toast(`Waypoint "${waypoint}" saved (${nets.length} networks).`);
		} else {
			UI.toast('Error saving waypoint.', 'error');
		}
	} catch (err) {
		console.error('saveWifiFingerprint:', err);
		UI.toast('Error saving waypoint.', 'error');
	}
}

// =============================================
//  13. ORCHESTRATOR — init + auto-refresh
// =============================================
async function initDashboard() {
	console.log('[Init] initDashboard started');
	await Promise.all([
		refreshIdentity(),
		refreshTime(),
		refreshSystemMetrics(),
	]);
	console.log('[Init] initDashboard complete, sensors:', State.sensors.length);
	if (State.sensors.length > 0) {
		State.activeSensorId = State.sensors[0].code;
		setTimeout(() => selectSensor(State.activeSensorId), 500);
	}
}

function startAutoRefresh() {
	console.log('[AutoRefresh] Starting intervals — time:10s, content:30s');
	setInterval(() => {
		console.log('[AutoRefresh] time tick — hasKey:', !!getKey(), '_promptingKey:', _promptingKey);
		if (!getKey() || _promptingKey) return;
		refreshTime();
	}, REFRESH_INTERVALS.time);

	setInterval(() => {
		console.log('[AutoRefresh] content tick — activeTab:', _activeTab, 'hasKey:', !!getKey());
		if (!getKey() || _promptingKey) return;
		if (_activeTab === 'module')  { refreshIdentity(); refreshSystemMetrics(); }
		if (_activeTab === 'sensors') { refreshSensors(); }
	}, REFRESH_INTERVALS.content);
}

// =============================================
//  14. EVENT BINDING
//      All handlers wired here, no HTML onclicks.
// =============================================
document.addEventListener('DOMContentLoaded', () => {
	// Tab navigation
	document.querySelectorAll('.tab-btn').forEach(btn => {
		btn.addEventListener('click', () => switchTab(btn.dataset.tab));
	});

	// Actions
	document.getElementById('rebootBtn')       ?.addEventListener('click', () => rebootDevice(
		'Reboot the device?\n\nConnection will be lost momentarily.',
		DeviceAPI.postReboot,
		'Rebooting… Reconnect in a few seconds.'
	));
	document.getElementById('forceApBtn')      ?.addEventListener('click', () => rebootDevice(
		'Switch to AP mode?\n\nDevice will reboot and start in AP mode.',
		DeviceAPI.postForceAp,
		'Switching to AP mode… Connect to the device hotspot.'
	));
	document.getElementById('savePrefsBtn')    ?.addEventListener('click', savePreferences);
	document.getElementById('saveNetworksBtn') ?.addEventListener('click', saveSavedNetworks);
	document.getElementById('addNetworkRowBtn')?.addEventListener('click', () => addNetworkRow());
	document.getElementById('wifiSaveBtn')     ?.addEventListener('click', saveWifiFingerprint);

	// Panel refresh buttons
	document.getElementById('identityRefreshBtn') ?.addEventListener('click', async () => {
		console.log('[Click] identityRefreshBtn');
		UI.setLoading('identityRefreshBtn', true);
		await Promise.all([refreshIdentity(), refreshSystemMetrics()]);
		UI.setLoading('identityRefreshBtn', false);
	});
	document.getElementById('metricsRefreshBtn')  ?.addEventListener('click', async () => {
		console.log('[Click] metricsRefreshBtn');
		UI.setLoading('metricsRefreshBtn', true);
		await refreshSystemMetrics();
		UI.setLoading('metricsRefreshBtn', false);
	});
	document.getElementById('sensorsRefreshBtn')  ?.addEventListener('click', async () => {
		console.log('[Click] sensorsRefreshBtn');
		UI.setLoading('sensorsRefreshBtn', true);
		await refreshSensors();
		UI.setLoading('sensorsRefreshBtn', false);
	});
	document.getElementById('bootRefreshBtn')     ?.addEventListener('click', () => { console.log('[Click] bootRefreshBtn'); refreshBootHistory(); });
	document.getElementById('sensorRefreshBtn')   ?.addEventListener('click', () => { console.log('[Click] sensorRefreshBtn'); refreshActiveSensorHistory(); });
	document.getElementById('wifiRefreshBtn')     ?.addEventListener('click', () => { console.log('[Click] wifiRefreshBtn'); refreshWifiScan(); });
	document.getElementById('locationRefreshBtn') ?.addEventListener('click', () => { console.log('[Click] locationRefreshBtn'); refreshLocation(); });

	// Panel toggle headers
	['bootPanelHeader', 'sensorPanelHeader', 'wifiPanelHeader', 'locationPanelHeader'].forEach(headerId => {
		const panelId = headerId.replace('Header', '');
		document.getElementById(headerId)?.addEventListener('click', e => {
			const btn = e.target.closest('button');
			if (btn && !btn.classList.contains('panel-toggle')) return;
			togglePanel(panelId);
		});
	});

	// Sensor card clicks
	document.getElementById('telemetryGrid')?.addEventListener('click', e => {
		const card = e.target.closest('.sensor-card');
		if (card?.dataset.sensorCode) selectSensor(card.dataset.sensorCode);
	});

	ensureKey().then(() => {
		initDashboard();
		startAutoRefresh();
	});
});