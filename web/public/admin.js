/**
 * Hollow Material 3 Web App & Cloud Admin Controller
 */

let adminToken = localStorage.getItem('hollow_admin_jwt') || '';
let cachedOverview = { modules: [], devices: [], auditLogs: [] };
let currentDeviceFilter = 'ALL';

const MODULE_LABELS = {
  module_hider: { label: 'App Hider', icon: 'visibility_off', color: '#7C3AED' },
  module_isolation: { label: 'Storage Isolate', icon: 'folder_special', color: '#0D9488' },
  module_privacy: { label: 'Privacy & Identity', icon: 'smartphone', color: '#4F46E5' },
  module_network: { label: 'V2Ray & Proxy', icon: 'public', color: '#0284C7' },
  module_sensor_firewall: { label: 'Sensor Firewall', icon: 'sensors', color: '#E11D48' }
};

// Navigation & Theme Setup
const views = {
  home: document.getElementById('viewHome'),
  modules: document.getElementById('viewModules'),
  register: document.getElementById('viewRegister'),
  admin: document.getElementById('viewAdmin')
};

function switchTab(tabName) {
  Object.entries(views).forEach(([key, el]) => {
    if (el) el.classList.toggle('hidden', key !== tabName);
  });
  document.querySelectorAll('.m3-nav-tab').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === tabName);
  });
  if (tabName === 'admin' && adminToken) {
    loadAdminOverview();
  }
}

document.querySelectorAll('.m3-nav-tab').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

document.getElementById('brandHomeLink')?.addEventListener('click', () => switchTab('home'));
document.getElementById('heroOpenRegisterBtn')?.addEventListener('click', () => switchTab('register'));
document.getElementById('heroOpenAdminBtn')?.addEventListener('click', () => switchTab('admin'));

// Theme Toggle (Light Colorful M3 by default, Dark M3 optional)
const themeBtn = document.getElementById('themeToggleBtn');
const themeIcon = document.getElementById('themeToggleIcon');
if (localStorage.getItem('hollow_web_theme') === 'dark') {
  document.body.classList.add('dark-theme');
  if (themeIcon) themeIcon.textContent = 'light_mode';
}
themeBtn?.addEventListener('click', () => {
  const isDark = document.body.classList.toggle('dark-theme');
  localStorage.setItem('hollow_web_theme', isDark ? 'dark' : 'light');
  if (themeIcon) themeIcon.textContent = isDark ? 'light_mode' : 'dark_mode';
});

// Auto-open Admin tab if URL path is /admin
if (window.location.pathname.startsWith('/admin')) {
  switchTab('admin');
}

// ---------------------------------------------------------------------------
// 1. Public Home & Modules Catalog Loader
// ---------------------------------------------------------------------------
function renderModuleCards(modules, containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = modules.map((mod) => {
    const meta = MODULE_LABELS[mod.moduleId] || { label: mod.displayName, icon: 'extension', color: '#4F46E5' };
    const features = (mod.features || []).map((f) => `<span class="m3-feature-chip">${f.title}</span>`).join('');
    return `
      <div class="m3-module-card">
        <div>
          <div class="m3-module-top">
            <div class="m3-module-badge-icon" style="background:${meta.color}">
              <span class="material-symbols-rounded">${meta.icon}</span>
            </div>
            <span class="m3-module-version">v${mod.version || '2.4.0'} • ${mod.moduleId}</span>
          </div>
          <h3 style="margin-top:14px">${mod.displayName || meta.label}</h3>
          <p style="margin-top:4px">${mod.description || mod.cardSubtitle || ''}</p>
        </div>
        <div class="m3-module-features">${features}</div>
      </div>
    `;
  }).join('');
}

async function loadPublicCatalog() {
  try {
    const res = await fetch('/api/v1/public/catalog');
    const data = await res.json();
    if (!data.ok) return;

    const mongoText = document.getElementById('mongoStatusText');
    if (mongoText) {
      mongoText.textContent = data.mongodbState === 'CONNECTED' ? 'MongoDB Atlas Live' : 'Cloud Hub Ready';
    }

    const stats = data.stats || {};
    const statModEl = document.getElementById('homeStatModules');
    const statDevEl = document.getElementById('homeStatDevices');
    const statAppEl = document.getElementById('homeStatApproved');
    if (statModEl) statModEl.textContent = stats.modulesCount ?? 5;
    if (statDevEl) statDevEl.textContent = stats.totalDevices ?? 3;
    if (statAppEl) statAppEl.textContent = stats.approvedDevices ?? 2;

    renderModuleCards(data.modules || [], 'homeModulesCatalogGrid');
    renderModuleCards(data.modules || [], 'fullModulesCatalogGrid');
  } catch (err) {
    console.error('Failed to load public catalog:', err);
  }
}

// ---------------------------------------------------------------------------
// 2. Public Device Registration & Live Status Check
// ---------------------------------------------------------------------------
document.getElementById('publicRegisterForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const box = document.getElementById('registerResultBox');
  const payload = {
    username: document.getElementById('regUsername').value.trim(),
    email: document.getElementById('regEmail').value.trim(),
    password: document.getElementById('regPassword').value,
    hardwareFingerprint: document.getElementById('regFingerprint').value.trim(),
    deviceModel: document.getElementById('regModel').value.trim()
  };

  try {
    const res = await fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    box.classList.remove('hidden');
    box.innerHTML = `
      <strong>Status: ${data.status || 'SUBMITTED'}</strong>
      <p style="margin-top:4px">${data.message || 'Device registered.'}</p>
      <p style="margin-top:6px;font-size:0.8rem;color:var(--text-secondary)">Hardware ID: <code>${payload.hardwareFingerprint}</code></p>
    `;
    loadPublicCatalog();
  } catch (err) {
    box.classList.remove('hidden');
    box.textContent = `Registration error: ${err.message}`;
  }
});

document.getElementById('publicStatusForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const fp = document.getElementById('statusFingerprint').value.trim();
  const box = document.getElementById('statusResultBox');
  try {
    const res = await fetch('/api/v1/auth/status', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hardwareFingerprint: fp, elapsedRealtimeMs: 1000 })
    });
    const data = await res.json();
    const mods = (data.allowedModules || []).map((m) => `<span class="m3-feature-chip">${m}</span>`).join(' ') || '<em>None assigned yet</em>';
    box.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <strong>${data.username || 'Device Lookup'}</strong>
        <span class="m3-status-badge ${data.status}">${data.status}</span>
      </div>
      <p style="font-size:0.82rem;color:var(--text-secondary);margin-bottom:8px">Hardware ID: <code>${fp}</code></p>
      <div><strong>Unlocked Modules:</strong> <div class="m3-module-features" style="margin-top:6px">${mods}</div></div>
    `;
  } catch (err) {
    box.textContent = `Status lookup failed: ${err.message}`;
  }
});

// ---------------------------------------------------------------------------
// 3. Admin Console Authentication & Device Management
// ---------------------------------------------------------------------------
const loginCard = document.getElementById('loginCard');
const dashboardView = document.getElementById('dashboardView');
const loginError = document.getElementById('loginError');

function updateAdminAuthView() {
  if (adminToken) {
    loginCard?.classList.add('hidden');
    dashboardView?.classList.remove('hidden');
    loadAdminOverview();
  } else {
    loginCard?.classList.remove('hidden');
    dashboardView?.classList.add('hidden');
  }
}

document.getElementById('loginForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  loginError.textContent = '';
  const username = document.getElementById('usernameInput').value.trim();
  const password = document.getElementById('passwordInput').value;
  try {
    const res = await fetch('/api/v1/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    if (!res.ok || !data.ok) {
      loginError.textContent = data.error || 'Invalid credentials';
      return;
    }
    adminToken = data.token;
    localStorage.setItem('hollow_admin_jwt', adminToken);
    updateAdminAuthView();
  } catch (err) {
    loginError.textContent = err.message;
  }
});

document.getElementById('logoutBtn')?.addEventListener('click', () => {
  adminToken = '';
  localStorage.removeItem('hollow_admin_jwt');
  updateAdminAuthView();
});

document.getElementById('refreshBtn')?.addEventListener('click', () => loadAdminOverview());

document.querySelectorAll('.m3-filter-chip').forEach((btn) => {
  btn.addEventListener('click', () => {
    currentDeviceFilter = btn.dataset.statusFilter || 'ALL';
    document.querySelectorAll('.m3-filter-chip').forEach((b) => b.classList.remove('active'));
    btn.classList.add('active');
    renderAdminDevices();
  });
});

async function loadAdminOverview() {
  if (!adminToken) return;
  try {
    const res = await fetch('/api/v1/admin/overview', {
      headers: { Authorization: `Bearer ${adminToken}` }
    });
    if (res.status === 401) {
      adminToken = '';
      localStorage.removeItem('hollow_admin_jwt');
      updateAdminAuthView();
      return;
    }
    const data = await res.json();
    if (!data.ok) return;

    cachedOverview = data;
    document.getElementById('statTotal').textContent = data.stats.totalDevices;
    document.getElementById('statPending').textContent = data.stats.pendingCount;
    document.getElementById('statApproved').textContent = data.stats.approvedCount;
    document.getElementById('statBanned').textContent = data.stats.bannedCount;

    renderAdminDevices();
    renderAdminHostedModules(data.modules || []);
    renderAdminAuditLogs(data.auditLogs || []);
  } catch (err) {
    console.error('Admin overview failed:', err);
  }
}

function renderAdminDevices() {
  const container = document.getElementById('devicesContainer');
  if (!container) return;

  const allModules = (cachedOverview.modules || []).map((m) => m.moduleId);
  const filtered = (cachedOverview.devices || []).filter((d) =>
    currentDeviceFilter === 'ALL' ? true : d.status === currentDeviceFilter
  );

  if (filtered.length === 0) {
    container.innerHTML = `<div class="m3-surface-card"><p>No devices in this filter.</p></div>`;
    return;
  }

  container.innerHTML = filtered.map((dev) => {
    const allowed = new Set(dev.allowedModules || []);
    const chipsHtml = allModules.map((modId) => {
      const meta = MODULE_LABELS[modId] || { label: modId };
      const isGranted = allowed.has(modId);
      return `
        <button class="m3-mod-toggle ${isGranted ? 'active' : ''}"
                onclick="toggleDeviceModule('${dev.hardwareFingerprint}', '${modId}')">
          <span class="material-symbols-rounded" style="font-size:15px">${isGranted ? 'check_circle' : 'add_circle'}</span>
          <span>${meta.label}</span>
        </button>
      `;
    }).join('');

    return `
      <div class="m3-device-card">
        <div class="m3-device-header">
          <div class="m3-device-user">
            <h4>${dev.username || 'Operator'} • ${dev.deviceModel || 'Android'}</h4>
            <span>${dev.email || 'No email provided'}</span>
          </div>
          <span class="m3-status-badge ${dev.status}">${dev.status}</span>
        </div>

        <div class="m3-hw-chip">
          <span>Hardware ID</span>
          <code>${dev.hardwareFingerprint}</code>
        </div>

        <div>
          <span style="font-size:0.76rem;font-weight:700;color:var(--text-secondary);display:block;margin-bottom:6px">
            Assigned Cloud Modules (click to toggle):
          </span>
          <div class="m3-mod-chips">${chipsHtml}</div>
        </div>

        <div class="m3-device-actions">
          ${dev.status !== 'APPROVED' ? `
            <button class="m3-btn m3-btn-primary" onclick="approveDevice('${dev.hardwareFingerprint}')">
              <span class="material-symbols-rounded" style="font-size:17px">check</span>
              <span>Approve All Modules</span>
            </button>
          ` : ''}
          ${dev.status !== 'BANNED' ? `
            <button class="m3-btn m3-btn-danger" onclick="banDevice('${dev.hardwareFingerprint}')">
              <span class="material-symbols-rounded" style="font-size:17px">block</span>
              <span>Ban Device</span>
            </button>
          ` : `
            <button class="m3-btn m3-btn-tonal" onclick="unbanDevice('${dev.hardwareFingerprint}')">
              <span class="material-symbols-rounded" style="font-size:17px">restore</span>
              <span>Unban Device</span>
            </button>
          `}
        </div>
      </div>
    `;
  }).join('');
}

function renderAdminHostedModules(modules) {
  const box = document.getElementById('adminModulesList');
  if (!box) return;
  box.innerHTML = modules.map((m) => `
    <div class="m3-compact-row">
      <div>
        <strong>${m.displayName}</strong>
        <div style="font-size:0.76rem;color:var(--text-secondary)"><code>${m.moduleId}</code> • v${m.version}</div>
      </div>
      <span class="m3-feature-chip">SHA256 ${(m.sha256 || '').slice(0, 10)}…</span>
    </div>
  `).join('');
}

function renderAdminAuditLogs(logs) {
  const box = document.getElementById('auditLogsContainer');
  if (!box) return;
  box.innerHTML = logs.map((l) => `
    <div class="m3-audit-row">
      <div>
        <strong>${l.event}</strong> <span style="font-size:0.78rem;color:var(--text-secondary)">(${l.username || l.hardwareFingerprint})</span>
        <div style="font-size:0.8rem;color:var(--text-secondary)">${l.details || ''}</div>
      </div>
      <span style="font-size:0.74rem;color:var(--text-muted)">${new Date(l.timestamp).toLocaleTimeString()}</span>
    </div>
  `).join('');
}

window.approveDevice = async function(hardwareFingerprint) {
  const allMods = (cachedOverview.modules || []).map((m) => m.moduleId);
  await fetch('/api/v1/admin/devices/approve', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminToken}`
    },
    body: JSON.stringify({ hardwareFingerprint, allowedModules: allMods, validityDays: 30 })
  });
  loadAdminOverview();
  loadPublicCatalog();
};

window.toggleDeviceModule = async function(hardwareFingerprint, moduleId) {
  const dev = (cachedOverview.devices || []).find((d) => d.hardwareFingerprint === hardwareFingerprint);
  if (!dev) return;
  const set = new Set(dev.allowedModules || []);
  if (set.has(moduleId)) set.delete(moduleId);
  else set.add(moduleId);

  await fetch('/api/v1/admin/devices/update-modules', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminToken}`
    },
    body: JSON.stringify({ hardwareFingerprint, allowedModules: Array.from(set) })
  });
  loadAdminOverview();
};

window.banDevice = async function(hardwareFingerprint) {
  await fetch('/api/v1/admin/devices/ban', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminToken}`
    },
    body: JSON.stringify({
      hardwareFingerprint,
      banReason: 'Banned via Admin Console — root module vault wiped.'
    })
  });
  loadAdminOverview();
  loadPublicCatalog();
};

window.unbanDevice = async function(hardwareFingerprint) {
  await fetch('/api/v1/admin/devices/unban', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminToken}`
    },
    body: JSON.stringify({ hardwareFingerprint })
  });
  loadAdminOverview();
  loadPublicCatalog();
};

// Initialize both Public Home Catalog & Admin Auth State
loadPublicCatalog();
updateAdminAuthView();
