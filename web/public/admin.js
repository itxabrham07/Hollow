/**
 * Hollow — Material 3 Expressive Web & Admin Console Controller
 */

let adminToken = localStorage.getItem('hollow_admin_jwt') || '';
let cachedOverview = { modules: [], devices: [], auditLogs: [], stats: {} };
let adminPollTimer = null;

const MODULE_LABELS = {
  module_isolation: { label: 'Storage Isolation', icon: 'folder_supervised' },
  module_privacy: { label: 'Identity & Privacy Shield', icon: 'fingerprint_off' },
  module_hider: { label: 'App Visibility Control', icon: 'visibility_lock' },
  module_network: { label: 'Per-App Network Routing', icon: 'vpn_lock' },
  module_sensor_firewall: { label: 'Sensor Firewall', icon: 'sensors' }
};

const ALL_DEFAULT_MODULES = [
  'module_isolation',
  'module_privacy',
  'module_hider',
  'module_network'
];

// ---------------------------------------------------------------------------
// 1. Navigation & Theme Setup
// ---------------------------------------------------------------------------
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
  if (tabName === 'admin') {
    updateAdminAuthView();
    startAdminAutoRefresh();
  } else {
    stopAdminAutoRefresh();
  }
}

document.querySelectorAll('.m3-nav-tab').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

document.getElementById('brandHomeLink')?.addEventListener('click', () => switchTab('home'));
document.getElementById('heroCheckDeviceBtn')?.addEventListener('click', () => switchTab('register'));
document.getElementById('heroOpenAdminBtn')?.addEventListener('click', () => switchTab('admin'));

// Theme Toggle
const themeBtn = document.getElementById('themeToggleBtn');
const themeIcon = document.getElementById('themeToggleIcon');
if (localStorage.getItem('hollow_web_theme') === 'light') {
  document.body.classList.remove('dark-theme');
  if (themeIcon) themeIcon.textContent = 'dark_mode';
}
themeBtn?.addEventListener('click', () => {
  const isDark = document.body.classList.toggle('dark-theme');
  localStorage.setItem('hollow_web_theme', isDark ? 'dark' : 'light');
  if (themeIcon) themeIcon.textContent = isDark ? 'light_mode' : 'dark_mode';
});

if (window.location.pathname.startsWith('/admin')) {
  switchTab('admin');
}

// ---------------------------------------------------------------------------
// 2. Public Modules Catalog
// ---------------------------------------------------------------------------
async function loadPublicCatalog() {
  try {
    const res = await fetch('/api/v1/public/catalog');
    const data = await res.json();
    if (!data.ok) return;

    const mongoText = document.getElementById('mongoStatusText');
    if (mongoText) {
      mongoText.textContent = data.mongodbState === 'CONNECTED'
        ? 'Cloud DB Connected'
        : 'Privacy Hub Online';
    }

    const container = document.getElementById('publicModuleGrid');
    if (container) {
      container.innerHTML = (data.modules || []).map((mod) => {
        const meta = MODULE_LABELS[mod.moduleId] || { label: mod.displayName, icon: 'extension' };
        const features = (mod.features || [])
          .map((f) => `<span class="m3-feature-chip">${f.title || f}</span>`)
          .join('');
        const shortHash = (mod.sha256Checksum || '').slice(0, 16);
        return `
          <div class="m3-card">
            <div class="m3-card-header" style="justify-content:space-between">
              <div style="display:flex;align-items:center;gap:12px">
                <div class="m3-icon-box">
                  <span class="material-symbols-rounded">${meta.icon}</span>
                </div>
                <div>
                  <h3>${mod.displayName || meta.label}</h3>
                  <span style="font-family:var(--font-mono);font-size:11px;color:var(--text-muted)">
                    v${mod.version || 1} · ${mod.moduleId} · ${mod.sizeKb || 12} KB
                  </span>
                </div>
              </div>
              <span class="m3-badge m3-badge-active">Ed25519 Signed</span>
            </div>
            <p style="margin-bottom:12px">${mod.description || mod.cardSubtitle || ''}</p>
            <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px">${features}</div>
            <div style="font-family:var(--font-mono);font-size:11px;color:var(--text-muted)">
              SHA-256: ${shortHash}…
            </div>
          </div>
        `;
      }).join('');
    }
  } catch (err) {
    console.error('Failed to load public catalog:', err);
  }
}
loadPublicCatalog();

// ---------------------------------------------------------------------------
// 3. Public Device Status Check
// ---------------------------------------------------------------------------
document.getElementById('btnCheckDeviceStatus')?.addEventListener('click', async () => {
  const fp = document.getElementById('checkHwInput').value.trim();
  const resultBox = document.getElementById('checkDeviceResult');
  if (!fp) {
    return;
  }

  resultBox.classList.remove('hidden');
  resultBox.innerHTML = '<p style="color:var(--text-muted)">Checking device status...</p>';

  try {
    const res = await fetch('/api/v1/auth/status', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hardwareFingerprint: fp, elapsedRealtimeMs: 1000 })
    });
    const data = await res.json();
    if (!res.ok || data.status === 'UNREGISTERED') {
      resultBox.innerHTML = `
        <div style="padding:16px;border-radius:12px;background:var(--semantic-warning-bg);color:var(--semantic-warning)">
          <strong>No Registration Found</strong>
          <p style="font-size:13px;margin-top:4px">
            Device <code>${fp}</code> is not registered yet. Open the Hollow app on your phone and create an account first.
          </p>
        </div>
      `;
      return;
    }

    const isApproved = data.status === 'APPROVED';
    const badgeClass = isApproved
      ? 'm3-badge-active'
      : data.status === 'PENDING_APPROVAL'
      ? 'm3-badge-warning'
      : 'm3-badge-error';
    const grantedMods = (data.allowedModules || [])
      .map((m) => `<span class="m3-feature-chip">${(MODULE_LABELS[m]?.label) || m}</span>`)
      .join(' ') || '<span style="font-size:13px;color:var(--text-muted)">Pending administrator assignment</span>';

    resultBox.innerHTML = `
      <div style="padding:16px;border-radius:12px;background:var(--bg-surface-low);border:1px solid var(--border-subtle)">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <div>
            <strong style="font-size:15px">${data.username || 'Registered Device'}</strong>
            <span style="display:block;font-size:12px;color:var(--text-muted);font-family:var(--font-mono)">${fp}</span>
          </div>
          <span class="m3-badge ${badgeClass}">${data.status.replace('_', ' ')}</span>
        </div>
        <div>
          <span style="font-size:12px;font-weight:600;color:var(--text-secondary)">Authorized Modules:</span>
          <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:6px">${grantedMods}</div>
        </div>
      </div>
    `;
  } catch (err) {
    resultBox.innerHTML = `<p style="color:var(--semantic-error)">Error: ${err.message}</p>`;
  }
});

// ---------------------------------------------------------------------------
// 4. Administrator Console Logic (with Live 5s Auto-Refresh)
// ---------------------------------------------------------------------------
const adminLoginCard = document.getElementById('adminLoginCard');
const adminDashboard = document.getElementById('adminDashboard');
const adminLoginErr = document.getElementById('adminLoginErr');

function startAdminAutoRefresh() {
  stopAdminAutoRefresh();
  adminPollTimer = setInterval(() => {
    if (adminToken && !views.admin.classList.contains('hidden')) {
      loadAdminOverview();
    }
  }, 5000);
}

function stopAdminAutoRefresh() {
  if (adminPollTimer) {
    clearInterval(adminPollTimer);
    adminPollTimer = null;
  }
}

function updateAdminAuthView() {
  if (adminToken) {
    adminLoginCard?.classList.add('hidden');
    adminDashboard?.classList.remove('hidden');
    loadAdminOverview();
  } else {
    adminLoginCard?.classList.remove('hidden');
    adminDashboard?.classList.add('hidden');
  }
}

document.getElementById('btnAdminLogin')?.addEventListener('click', async () => {
  adminLoginErr.classList.add('hidden');
  const username = document.getElementById('adminUser').value.trim();
  const password = document.getElementById('adminPass').value;
  try {
    const res = await fetch('/api/v1/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    if (!res.ok || !data.ok) {
      adminLoginErr.textContent = data.error || 'Invalid administrator credentials';
      adminLoginErr.classList.remove('hidden');
      return;
    }
    adminToken = data.token;
    localStorage.setItem('hollow_admin_jwt', adminToken);
    updateAdminAuthView();
  } catch (err) {
    adminLoginErr.textContent = err.message;
    adminLoginErr.classList.remove('hidden');
  }
});

document.getElementById('btnAdminLogout')?.addEventListener('click', () => {
  adminToken = '';
  localStorage.removeItem('hollow_admin_jwt');
  updateAdminAuthView();
});

document.getElementById('btnAdminRefresh')?.addEventListener('click', () => loadAdminOverview());

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
    renderAdminStats(data.stats || {});
    renderAdminDevices(data.devices || []);
    renderAdminLogs(data.auditLogs || []);
  } catch (err) {
    console.error('Failed to load admin overview:', err);
  }
}

function renderAdminStats(stats) {
  const el = document.getElementById('adminStatsGrid');
  if (!el) return;
  el.innerHTML = `
    <div class="m3-card">
      <span style="font-size:12px;font-weight:600;color:var(--text-secondary)">Pending Approval</span>
      <div style="font-size:28px;font-weight:700;color:var(--semantic-warning);margin-top:4px">${stats.pendingCount || 0}</div>
    </div>
    <div class="m3-card">
      <span style="font-size:12px;font-weight:600;color:var(--text-secondary)">Approved Devices</span>
      <div style="font-size:28px;font-weight:700;color:var(--semantic-active);margin-top:4px">${stats.approvedCount || 0}</div>
    </div>
    <div class="m3-card">
      <span style="font-size:12px;font-weight:600;color:var(--text-secondary)">Signed Modules Hosted</span>
      <div style="font-size:28px;font-weight:700;color:var(--accent-primary);margin-top:4px">${stats.modulesCount || 4}</div>
    </div>
  `;
}

function renderAdminDevices(devices) {
  const container = document.getElementById('adminDevicesContainer');
  if (!container) return;

  if (devices.length === 0) {
    container.innerHTML = `
      <div style="padding: 40px 20px; text-align: center; color: var(--text-muted)">
        <span class="material-symbols-rounded" style="font-size: 36px; display: block; margin-bottom: 8px">devices</span>
        <p style="font-weight: 600">No devices registered yet.</p>
        <p style="font-size: 13px; margin-top: 4px">When a user creates an account in the Hollow app, their device will appear here automatically.</p>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <table class="m3-table">
      <thead>
        <tr>
          <th>Account &amp; Device</th>
          <th>Hardware Verification ID</th>
          <th>Status</th>
          <th>Authorized Modules</th>
          <th style="text-align:right">Actions</th>
        </tr>
      </thead>
      <tbody>
        ${devices.map((dev) => {
          const isApproved = dev.status === 'APPROVED';
          const isBanned = dev.status === 'BANNED';
          const badgeClass = isApproved
            ? 'm3-badge-active'
            : isBanned
            ? 'm3-badge-error'
            : 'm3-badge-warning';

          return `
            <tr>
              <td>
                <strong style="font-size:14px">${dev.username || 'User'}</strong>
                <span style="display:block;font-size:12px;color:var(--text-secondary)">
                  ${dev.deviceModel || 'Android Device'}${dev.email ? ` · ${dev.email}` : ''}
                </span>
              </td>
              <td style="font-family:var(--font-mono);font-size:12px;color:var(--accent-primary)">
                ${dev.hardwareFingerprint}
              </td>
              <td>
                <span class="m3-badge ${badgeClass}">${dev.status.replace('_', ' ')}</span>
              </td>
              <td>
                <div style="display:flex;gap:6px;flex-wrap:wrap">
                  ${ALL_DEFAULT_MODULES.map((m) => {
                    const checked = (dev.allowedModules || []).includes(m);
                    const shortLabel = m.replace('module_', '');
                    return `
                      <label class="m3-feature-chip" style="cursor:pointer">
                        <input
                          type="checkbox"
                          onchange="toggleDeviceModule('${dev.hardwareFingerprint}', '${m}', this.checked)"
                          ${checked ? 'checked' : ''}
                          style="margin-right:4px"
                        />
                        ${shortLabel}
                      </label>
                    `;
                  }).join('')}
                </div>
              </td>
              <td style="text-align:right;white-space:nowrap">
                ${!isApproved ? `
                  <button
                    onclick="approveDevice('${dev.hardwareFingerprint}')"
                    class="m3-btn m3-btn-primary"
                    style="padding:6px 14px;min-height:36px;font-size:12px"
                  >
                    Approve &amp; Grant All
                  </button>
                ` : `
                  <button
                    onclick="revokeDevice('${dev.hardwareFingerprint}')"
                    class="m3-btn m3-btn-tonal"
                    style="padding:6px 14px;min-height:36px;font-size:12px;color:var(--semantic-error)"
                  >
                    Restrict
                  </button>
                `}
              </td>
            </tr>
          `;
        }).join('')}
      </tbody>
    </table>
  `;
}

function renderAdminLogs(logs) {
  const container = document.getElementById('adminLogsContainer');
  if (!container) return;

  if (logs.length === 0) {
    container.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-muted)">No activity recorded yet.</div>';
    return;
  }

  container.innerHTML = `
    <table class="m3-table">
      <thead>
        <tr>
          <th>Time</th>
          <th>Event</th>
          <th>Device ID</th>
          <th>Details</th>
        </tr>
      </thead>
      <tbody>
        ${logs.slice(0, 30).map((l) => `
          <tr>
            <td style="color:var(--text-muted);font-family:var(--font-mono);font-size:11px">
              ${new Date(l.timestamp).toLocaleTimeString()}
            </td>
            <td><strong>${l.event}</strong></td>
            <td style="font-family:var(--font-mono);font-size:11px">${l.hardwareFingerprint || '—'}</td>
            <td style="color:var(--text-secondary)">${l.details || ''}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  `;
}

window.approveDevice = async function(fp) {
  try {
    const res = await fetch('/api/v1/admin/devices/approve', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        validityDays: 365,
        allowedModules: ['module_isolation', 'module_privacy', 'module_hider', 'module_network', 'module_sensor_firewall']
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Approval failed');
    loadAdminOverview();
  } catch (err) {
    alert(err.message);
  }
};

window.revokeDevice = async function(fp) {
  try {
    const res = await fetch('/api/v1/admin/devices/ban', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        banReason: 'Access restricted by administrator.'
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Restriction failed');
    loadAdminOverview();
  } catch (err) {
    alert(err.message);
  }
};

window.toggleDeviceModule = async function(fp, modId, isEnabled) {
  const dev = (cachedOverview.devices || []).find((d) => d.hardwareFingerprint === fp);
  if (!dev) return;
  const currentMods = new Set(dev.allowedModules || []);
  if (isEnabled) currentMods.add(modId); else currentMods.delete(modId);

  try {
    const endpoint = dev.status === 'APPROVED'
      ? '/api/v1/admin/devices/update-modules'
      : '/api/v1/admin/devices/approve';
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        validityDays: 365,
        allowedModules: Array.from(currentMods)
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Failed to update modules');
    loadAdminOverview();
  } catch (err) {
    alert(err.message);
  }
};
