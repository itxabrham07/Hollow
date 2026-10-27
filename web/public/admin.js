const ALL_MODULES = [
  { id: 'module_hider', label: 'App Hider & Stealth' },
  { id: 'module_isolation', label: 'Storage Shield & Vault' },
  { id: 'module_privacy', label: 'Hardware Spoofer & Privacy' },
  { id: 'module_network', label: 'Proxy & V2Ray Suite' }
];

let adminToken = localStorage.getItem('hollow_admin_jwt') || '';

const loginView = document.getElementById('login-view');
const dashboardView = document.getElementById('dashboard-view');
const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');

async function apiRequest(path, method = 'GET', body = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (adminToken) headers['Authorization'] = `Bearer ${adminToken}`;
  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

loginForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  loginError.classList.add('hidden');
  try {
    const username = document.getElementById('admin-user').value.trim();
    const password = document.getElementById('admin-pass').value;
    const data = await apiRequest('/api/v1/admin/login', 'POST', { username, password });
    adminToken = data.token;
    localStorage.setItem('hollow_admin_jwt', adminToken);
    showDashboard();
  } catch (err) {
    loginError.textContent = err.message;
    loginError.classList.remove('hidden');
  }
});

document.getElementById('logout-btn').addEventListener('click', () => {
  adminToken = '';
  localStorage.removeItem('hollow_admin_jwt');
  dashboardView.classList.add('hidden');
  loginView.classList.remove('hidden');
});

document.getElementById('refresh-btn').addEventListener('click', loadOverview);

async function showDashboard() {
  loginView.classList.add('hidden');
  dashboardView.classList.remove('hidden');
  await loadOverview();
}

async function loadOverview() {
  try {
    const data = await apiRequest('/api/v1/admin/overview');
    document.getElementById('mongo-status').textContent = data.mongoConnected
      ? 'MongoDB Atlas: Connected'
      : 'MongoDB Atlas: Awaiting MONGODB_URI';

    const devices = data.devices || [];
    const pending = devices.filter((d) => d.status === 'pending');
    const approved = devices.filter((d) => d.status === 'approved');
    const banned = devices.filter((d) => d.status === 'banned');

    document.getElementById('stat-pending').textContent = pending.length;
    document.getElementById('stat-approved').textContent = approved.length;
    document.getElementById('stat-banned').textContent = banned.length;
    document.getElementById('stat-modules').textContent = (data.modules || []).length;

    renderPendingDevices(pending);
    renderAllDevices(devices);
    renderIncidents(data.incidents || []);
  } catch (err) {
    console.error(err);
  }
}

function renderPendingDevices(list) {
  const container = document.getElementById('pending-list');
  if (!list.length) {
    container.innerHTML = `<div class="mono">No pending registrations in queue.</div>`;
    return;
  }
  container.innerHTML = list.map((dev) => `
    <div class="device-card">
      <div class="device-row-top">
        <div>
          <strong>${dev.username}</strong> (${dev.email}) • <span class="mono">${dev.deviceModel}</span>
          <div class="mono">HW Fingerprint: ${dev.hardwareFingerprint}</div>
        </div>
        <div style="display:flex; gap:8px;">
          <button class="btn-primary" onclick="approveDevice('${dev.hardwareFingerprint}')">Approve & Grant Modules</button>
          <button class="btn-danger" onclick="banDevice('${dev.hardwareFingerprint}')">Ban Device</button>
        </div>
      </div>
    </div>
  `).join('');
}

function renderAllDevices(list) {
  const container = document.getElementById('devices-list');
  if (!list.length) {
    container.innerHTML = `<div class="mono">No registered devices yet.</div>`;
    return;
  }
  container.innerHTML = list.map((dev) => {
    const allowed = dev.allowedModules || [];
    const chips = ALL_MODULES.map((m) => {
      const checked = allowed.includes(m.id) ? 'checked' : '';
      return `
        <label class="mod-chip">
          <input type="checkbox" ${checked} onchange="toggleDeviceModule('${dev.hardwareFingerprint}', '${m.id}', this.checked)" />
          ${m.label}
        </label>
      `;
    }).join('');

    return `
      <div class="device-card">
        <div class="device-row-top">
          <div>
            <strong>${dev.username}</strong> (${dev.email}) — <span class="status-pill">${dev.status.toUpperCase()}</span>
            <div class="mono">HW: ${dev.hardwareFingerprint} • SoC: ${dev.socSerial || 'N/A'} • AID: ${dev.androidId || 'N/A'}</div>
          </div>
          <div style="display:flex; gap:8px;">
            <button class="btn-primary" onclick="approveDevice('${dev.hardwareFingerprint}')">Approve All (30d)</button>
            <button class="btn-secondary" onclick="revokeDevice('${dev.hardwareFingerprint}')">Revoke</button>
            <button class="btn-danger" onclick="banDevice('${dev.hardwareFingerprint}')">Ban Hardware</button>
          </div>
        </div>
        <div class="modules-check-row">${chips}</div>
      </div>
    `;
  }).join('');
}

function renderIncidents(list) {
  const container = document.getElementById('incidents-list');
  if (!list.length) {
    container.innerHTML = `<div class="mono">Zero tamper or identity leak incidents recorded.</div>`;
    return;
  }
  container.innerHTML = list.map((inc) => `
    <div class="device-card">
      <div><strong>${inc.triggerVector}</strong> • <span class="mono">${inc.hardwareFingerprint}</span></div>
      <div class="mono">${inc.details}</div>
    </div>
  `).join('');
}

window.approveDevice = async (hardwareFingerprint) => {
  await apiRequest('/api/v1/admin/devices/approve', 'POST', {
    hardwareFingerprint,
    validityDays: 30,
    allowedModules: ALL_MODULES.map((m) => m.id)
  });
  await loadOverview();
};

window.banDevice = async (hardwareFingerprint) => {
  await apiRequest('/api/v1/admin/devices/ban', 'POST', {
    hardwareFingerprint,
    banReason: 'Hardware fingerprint permanently blacklisted by Admin'
  });
  await loadOverview();
};

window.revokeDevice = async (hardwareFingerprint) => {
  await apiRequest('/api/v1/admin/devices/revoke', 'POST', { hardwareFingerprint });
  await loadOverview();
};

window.toggleDeviceModule = async (hardwareFingerprint, moduleId, enabled) => {
  const data = await apiRequest('/api/v1/admin/overview');
  const dev = (data.devices || []).find((d) => d.hardwareFingerprint === hardwareFingerprint);
  if (!dev) return;
  const set = new Set(dev.allowedModules || []);
  if (enabled) set.add(moduleId);
  else set.delete(moduleId);
  await apiRequest('/api/v1/admin/devices/update-modules', 'POST', {
    hardwareFingerprint,
    allowedModules: Array.from(set)
  });
  await loadOverview();
};

if (adminToken) {
  showDashboard();
}
