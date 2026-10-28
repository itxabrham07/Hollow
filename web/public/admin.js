/**
 * Hollow Web App & Cloud Admin Controller
 */

let adminToken = localStorage.getItem('hollow_admin_jwt') || '';
let cachedOverview = { modules: [], devices: [], auditLogs: [] };

const MODULE_LABELS = {
  module_hider: { label: 'App Hider', icon: 'visibility_off', color: '#7C3AED' },
  module_isolation: { label: 'Storage Isolate', icon: 'folder_special', color: '#0D9488' },
  module_privacy: { label: 'Privacy & Identity', icon: 'smartphone', color: '#4F46E5' },
  module_network: { label: 'V2Ray & Proxy', icon: 'public', color: '#0284C7' },
  module_sensor_firewall: { label: 'Sensor Firewall', icon: 'sensors', color: '#E11D48' }
};

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
  }
}

document.querySelectorAll('.m3-nav-tab').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

document.getElementById('brandHomeLink')?.addEventListener('click', () => switchTab('home'));
document.getElementById('heroCheckDeviceBtn')?.addEventListener('click', () => switchTab('register'));

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

// Auto-open Admin tab if URL is /admin
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
      mongoText.textContent = data.mongodbState === 'CONNECTED' ? 'MongoDB Atlas Live' : 'Hollow Cloud Ready';
    }

    const container = document.getElementById('publicModuleGrid');
    if (container) {
      container.innerHTML = (data.modules || []).map((mod) => {
        const meta = MODULE_LABELS[mod.moduleId] || { label: mod.displayName, icon: 'extension', color: '#10B981' };
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
    alert('Please enter your Hardware Fingerprint from the Hollow App (e.g. HLW-XXXXXXXX-XXXXXXXX-XXXXXXXX).');
    return;
  }

  resultBox.classList.remove('hidden');
  resultBox.innerHTML = '<p style="color:var(--text-muted)">Querying Hollow Cloud Server...</p>';

  try {
    const res = await fetch('/api/v1/auth/status', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hardwareFingerprint: fp, elapsedRealtimeMs: 1000 })
    });
    const data = await res.json();
    if (!res.ok) {
      resultBox.innerHTML = `
        <div style="padding:14px;border-radius:12px;background:rgba(239,68,68,0.12);border:1px solid #EF4444;color:#F87171">
          <strong>Device Not Found</strong>
          <p style="font-size:12px;margin-top:4px">No registration found for <code>${fp}</code>. Please open Hollow Release APK on your phone and tap Register.</p>
        </div>
      `;
      return;
    }

    const isApproved = data.status === 'APPROVED';
    const statusColor = isApproved ? '#10B981' : (data.status === 'PENDING_APPROVAL' ? '#F59E0B' : '#EF4444');
    const grantedMods = (data.allowedModules || []).map((m) => `<span class="m3-feature-chip">${m}</span>`).join(' ') || '<em>None assigned yet</em>';

    resultBox.innerHTML = `
      <div style="padding:16px;border-radius:14px;background:var(--bg-surface-low);border:1px solid var(--border-subtle)">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
          <div>
            <strong style="font-size:15px">${data.username || 'Device Registered'}</strong>
            <span style="display:block;font-size:11px;color:var(--text-muted);font-family:'JetBrains Mono'">${fp}</span>
          </div>
          <span style="padding:4px 10px;border-radius:50px;font-size:11px;font-weight:700;background:${statusColor}22;color:${statusColor}">${data.status}</span>
        </div>
        <div style="margin-top:10px">
          <strong style="font-size:12px;color:var(--text-secondary)">Unlocked Modules:</strong>
          <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:6px">${grantedMods}</div>
        </div>
        ${isApproved ? '<p style="margin-top:12px;font-size:12px;color:#10B981;font-weight:600">✓ Approved! Open your Hollow App to sync and mount your modules.</p>' : '<p style="margin-top:12px;font-size:12px;color:#F59E0B">Waiting for Hollow Administrator approval in the Admin Console.</p>'}
      </div>
    `;
  } catch (err) {
    resultBox.innerHTML = `<p style="color:#EF4444">Error: ${err.message}</p>`;
  }
});

// ---------------------------------------------------------------------------
// 4. Admin Portal Logic
// ---------------------------------------------------------------------------
const adminLoginCard = document.getElementById('adminLoginCard');
const adminDashboard = document.getElementById('adminDashboard');
const adminLoginErr = document.getElementById('adminLoginErr');

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
      adminLoginErr.textContent = data.error || 'Invalid administrator password';
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
    renderAdminDevices(data.devices || []);
    renderAdminLogs(data.auditLogs || []);
  } catch (err) {
    console.error('Failed to load admin overview:', err);
  }
}

function renderAdminDevices(devices) {
  const container = document.getElementById('adminDevicesContainer');
  if (!container) return;

  if (devices.length === 0) {
    container.innerHTML = `
      <div style="padding: 40px 20px; text-align: center; color: var(--text-muted)">
        <span class="material-symbols-rounded" style="font-size: 36px; display: block; margin-bottom: 8px">devices_other</span>
        <p style="font-weight: 600">No devices registered yet.</p>
        <p style="font-size: 12px; margin-top: 4px">When a user installs Hollow Release APK and taps "Register", their device will appear here instantly for approval.</p>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <table style="width:100%;border-collapse:collapse;text-align:left;font-size:13px">
      <thead>
        <tr style="border-bottom:1px solid var(--border-subtle);color:var(--text-muted);font-size:11px;text-transform:uppercase">
          <th style="padding:12px 16px">Device / User</th>
          <th style="padding:12px 16px">Hardware Fingerprint</th>
          <th style="padding:12px 16px">Status</th>
          <th style="padding:12px 16px">Granted Modules</th>
          <th style="padding:12px 16px;text-align:right">Actions</th>
        </tr>
      </thead>
      <tbody>
        ${devices.map((dev) => {
          const isApproved = dev.status === 'APPROVED';
          const statusColor = isApproved ? '#10B981' : (dev.status === 'PENDING_APPROVAL' ? '#F59E0B' : '#EF4444');
          return `
            <tr style="border-bottom:1px solid var(--border-subtle)">
              <td style="padding:14px 16px">
                <strong>${dev.username || 'Unnamed'}</strong>
                <span style="display:block;font-size:11px;color:var(--text-muted)">${dev.deviceModel || 'Android Device'} • ${dev.email || 'No email'}</span>
              </td>
              <td style="padding:14px 16px;font-family:'JetBrains Mono';font-size:11px">${dev.hardwareFingerprint}</td>
              <td style="padding:14px 16px">
                <span style="padding:3px 8px;border-radius:50px;font-size:10px;font-weight:700;background:${statusColor}22;color:${statusColor}">${dev.status}</span>
              </td>
              <td style="padding:14px 16px">
                <div style="display:flex;gap:4px;flex-wrap:wrap">
                  ${['module_isolation', 'module_privacy', 'module_hider', 'module_network'].map((m) => {
                    const checked = (dev.allowedModules || []).includes(m);
                    return `
                      <label style="font-size:10px;padding:2px 6px;border-radius:4px;background:var(--bg-surface-low);border:1px solid var(--border-subtle);cursor:pointer">
                        <input type="checkbox" onchange="toggleDeviceModule('${dev.hardwareFingerprint}', '${m}', this.checked)" ${checked ? 'checked' : ''} style="margin-right:3px" />
                        ${m.replace('module_', '')}
                      </label>
                    `;
                  }).join('')}
                </div>
              </td>
              <td style="padding:14px 16px;text-align:right">
                ${!isApproved ? `
                  <button onclick="approveDevice('${dev.hardwareFingerprint}')" class="m3-btn" style="background:#10B981;color:#fff;padding:4px 10px;font-size:11px;border-radius:6px">Approve</button>
                ` : `
                  <button onclick="revokeDevice('${dev.hardwareFingerprint}')" class="m3-btn" style="background:rgba(239,68,68,0.15);color:#EF4444;padding:4px 10px;font-size:11px;border-radius:6px">Revoke</button>
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
    container.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-muted)">No audit events recorded yet.</div>';
    return;
  }

  container.innerHTML = `
    <table style="width:100%;border-collapse:collapse;text-align:left;font-size:12px">
      <thead>
        <tr style="border-bottom:1px solid var(--border-subtle);color:var(--text-muted);font-size:11px;text-transform:uppercase">
          <th style="padding:10px 16px">Time</th>
          <th style="padding:10px 16px">Event</th>
          <th style="padding:10px 16px">Hardware Fingerprint</th>
          <th style="padding:10px 16px">Details</th>
        </tr>
      </thead>
      <tbody>
        ${logs.slice(0, 30).map((l) => `
          <tr style="border-bottom:1px solid var(--border-subtle)">
            <td style="padding:10px 16px;color:var(--text-muted);font-family:'JetBrains Mono';font-size:11px">${new Date(l.timestamp).toLocaleTimeString()}</td>
            <td style="padding:10px 16px"><strong>${l.event}</strong></td>
            <td style="padding:10px 16px;font-family:'JetBrains Mono';font-size:11px">${l.hardwareFingerprint || '—'}</td>
            <td style="padding:10px 16px;color:var(--text-secondary)">${l.details || ''}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  `;
}

window.approveDevice = async function(fp) {
  try {
    const res = await fetch('/api/v1/admin/license/issue', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        durationDays: 365,
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
    const res = await fetch('/api/v1/admin/license/revoke', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        reason: 'Revoked by administrator.'
      })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Revocation failed');
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
    const res = await fetch('/api/v1/admin/license/issue', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`
      },
      body: JSON.stringify({
        hardwareFingerprint: fp,
        durationDays: 365,
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
