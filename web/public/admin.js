(function () {
    'use strict';

    // =========================================================================
    // SHARED UTILITIES
    // =========================================================================
    function esc(str) {
        return String(str == null ? '' : str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function setVisible(el, isVisible, displayMode) {
        if (!el) return;
        if (isVisible) {
            el.classList.remove('hidden');
            el.style.display = displayMode || 'block';
        } else {
            el.classList.add('hidden');
            el.style.display = 'none';
        }
    }

    function normalizeStatus(rawStatus) {
        const s = String(rawStatus || '').toUpperCase().trim();
        if (s === 'APPROVED') return 'APPROVED';
        if (s === 'BANNED' || s === 'REVOKED') return 'BANNED';
        return 'PENDING_APPROVAL';
    }

    function formatTimeAgo(dateStr) {
        if (!dateStr) return 'Just now';
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return 'Recently';
        const diffSec = Math.max(0, Math.floor((Date.now() - d.getTime()) / 1000));
        if (diffSec < 60) return 'Just now';
        if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
        if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
        return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    }

    // =========================================================================
    // 1. PUBLIC PRODUCT PAGE LOGIC (/)
    // =========================================================================
    const mobileMenuBtn = document.getElementById('mobileMenuBtn');
    const mobileNavDrawer = document.getElementById('mobileNavDrawer');
    if (mobileMenuBtn && mobileNavDrawer) {
        mobileMenuBtn.addEventListener('click', () => {
            mobileNavDrawer.classList.toggle('open');
        });
        document.querySelectorAll('.mobile-nav-item').forEach((link) => {
            link.addEventListener('click', () => {
                mobileNavDrawer.classList.remove('open');
            });
        });
    }

    // Interactive Phone Storyboard Preview
    const phoneScreenContent = document.getElementById('phoneScreenContent');
    const phonePills = document.querySelectorAll('.phone-step-tabs .phone-pill');
    const phoneStageBtns = document.querySelectorAll('.phone-stage-btn');

    const phoneStages = [
        // Stage 0: Welcome / Get Started
        () => `
            <div style="text-align:left; width:100%;">
                <div class="phone-screen-title">Take control<br>of your digital life</div>
                <div class="phone-screen-sub">Modular. Secure. Always evolving.</div>
                <div class="phone-feature-list">
                    <div class="phone-feature-card">
                        <div class="phone-feature-icon">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                        </div>
                        <span>Hide, isolate and protect apps</span>
                    </div>
                    <div class="phone-feature-card">
                        <div class="phone-feature-icon">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                        </div>
                        <span>Advanced privacy modules</span>
                    </div>
                    <div class="phone-feature-card">
                        <div class="phone-feature-icon">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9"/></svg>
                        </div>
                        <span>Install only what you need</span>
                    </div>
                </div>
                <div class="phone-cta-black">
                    <span>Create Account</span>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
                </div>
                <div class="phone-cta-muted">Login</div>
            </div>
        `,
        // Stage 1: Root Access Required (with Solid Black Ticks)
        () => `
            <div style="text-align:center; width:100%;">
                <div style="width:68px; height:68px; border-radius:20px; background:#F1F5F9; border:1.5px solid #111827; margin:0 auto 12px; display:flex; align-items:center; justify-content:center; font-weight:800; font-size:1.55rem; color:#111827;">#</div>
                <div class="phone-screen-title" style="font-size:1.18rem;">Root Access Required</div>
                <div class="phone-screen-sub" style="margin-bottom:12px;">Hollow needs root access to install and run privacy modules securely.</div>
                <div class="phone-check-box">
                    <div class="phone-check-item">
                        <span class="black-tick-badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="20 6 9 17 4 12"/></svg></span>
                        <span>Install approved modules</span>
                    </div>
                    <div class="phone-check-item">
                        <span class="black-tick-badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="20 6 9 17 4 12"/></svg></span>
                        <span>Enable system-level privacy</span>
                    </div>
                    <div class="phone-check-item">
                        <span class="black-tick-badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="20 6 9 17 4 12"/></svg></span>
                        <span>Hide and isolate apps</span>
                    </div>
                    <div class="phone-check-item">
                        <span class="black-tick-badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="20 6 9 17 4 12"/></svg></span>
                        <span>Manage device identifiers</span>
                    </div>
                </div>
                <div class="phone-cta-black">Grant Root Access</div>
            </div>
        `,
        // Stage 2: Account Under Review (with Solid Black Tick on Submitted)
        () => `
            <div style="text-align:center; width:100%;">
                <div style="width:64px; height:64px; border-radius:50%; background:#EEF2FF; margin:0 auto 12px; display:flex; align-items:center; justify-content:center; position:relative;">
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#111827" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                </div>
                <div class="phone-screen-title" style="font-size:1.18rem;">Account Under Review</div>
                <div class="phone-screen-sub" style="margin-bottom:14px;">Your account is being reviewed by our team to maintain a trusted community.</div>
                <div style="background:#F8FAFC; border:1px solid #E2E8F0; border-radius:16px; padding:12px 14px; text-align:left; margin-bottom:14px; display:flex; flex-direction:column; gap:10px;">
                    <div style="display:flex; align-items:center; gap:10px;">
                        <span class="black-tick-badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><polyline points="20 6 9 17 4 12"/></svg></span>
                        <div>
                            <div style="font-size:0.8rem; font-weight:700; color:#111827;">Submitted</div>
                            <div style="font-size:0.7rem; color:#64748B;">Hardware identity registered</div>
                        </div>
                    </div>
                    <div style="display:flex; align-items:center; gap:10px;">
                        <span style="width:20px; height:20px; border-radius:50%; background:#111827; display:inline-flex; align-items:center; justify-content:center;">
                            <span style="width:6px; height:6px; border-radius:50%; background:#FFFFFF;"></span>
                        </span>
                        <div>
                            <div style="font-size:0.8rem; font-weight:700; color:#111827;">Under Review</div>
                            <div style="font-size:0.7rem; color:#64748B;">Usually takes a few minutes</div>
                        </div>
                    </div>
                    <div style="display:flex; align-items:center; gap:10px;">
                        <span style="width:20px; height:20px; border-radius:50%; border:2px solid #CBD5E1; display:inline-block;"></span>
                        <div>
                            <div style="font-size:0.8rem; font-weight:600; color:#64748B;">Approved</div>
                        </div>
                    </div>
                </div>
                <div style="background:#EEF2F6; border-radius:12px; padding:10px 12px; font-size:0.73rem; color:#334155; font-weight:600;">
                    You'll get instant access once approved.
                </div>
            </div>
        `
    ];

    function renderPhoneStage(stageIdx) {
        if (!phoneScreenContent) return;
        const idx = Math.max(0, Math.min(phoneStages.length - 1, Number(stageIdx) || 0));
        phoneScreenContent.innerHTML = phoneStages[idx]();
        phonePills.forEach((pill, i) => pill.classList.toggle('active', i === idx));
        phoneStageBtns.forEach((btn, i) => btn.classList.toggle('active', i === idx));
    }

    if (phoneScreenContent) {
        renderPhoneStage(0);
        phoneStageBtns.forEach((btn) => {
            btn.addEventListener('click', () => {
                renderPhoneStage(Number(btn.getAttribute('data-stage') || 0));
            });
        });
        phonePills.forEach((pill) => {
            pill.addEventListener('click', () => {
                renderPhoneStage(Number(pill.getAttribute('data-stage') || 0));
            });
        });
    }

    // Load Public Signed Modules Catalog on `/`
    const publicModuleGrid = document.getElementById('publicModuleGrid');
    async function loadPublicCatalog() {
        if (!publicModuleGrid) return;
        try {
            const res = await fetch('/api/v1/public/catalog');
            if (!res.ok) return;
            const data = await res.json();
            const modules = Array.isArray(data.modules) ? data.modules : [];
            if (modules.length === 0) return;

            publicModuleGrid.innerHTML = modules.map((m) => `
                <article class="module-card">
                    <div class="module-card-top">
                        <span class="module-id-chip">${esc(m.id)}</span>
                        <span class="module-version-chip">v${esc(m.version || '1.0.0')}</span>
                    </div>
                    <h3>${esc(m.title || m.id)}</h3>
                    <p>${esc(m.subtitle || 'Signed modular privacy plugin verified via Ed25519.')}</p>
                    <div class="module-meta-footer">
                        <span>Ed25519 Signed</span>
                        <span>SHA-256: ${esc((m.sha256 || 'verified').slice(0, 12))}...</span>
                    </div>
                </article>
            `).join('');
        } catch (_) {
            // Keep static fallback module cards cleanly
        }
    }
    if (publicModuleGrid) {
        loadPublicCatalog();
    }

    // Public Device Status Lookup on `/`
    const btnCheckDeviceStatus = document.getElementById('btnCheckDeviceStatus');
    const checkHwInput = document.getElementById('checkHwInput');
    const checkDeviceResult = document.getElementById('checkDeviceResult');

    if (btnCheckDeviceStatus && checkHwInput && checkDeviceResult) {
        const runStatusCheck = async () => {
            const query = checkHwInput.value.trim();
            if (!query) {
                setVisible(checkDeviceResult, true, 'block');
                checkDeviceResult.innerHTML = `<span style="color:#DC2626; font-weight:600;">Please enter your username or hardware fingerprint.</span>`;
                return;
            }
            btnCheckDeviceStatus.disabled = true;
            btnCheckDeviceStatus.textContent = 'Checking...';
            try {
                const res = await fetch('/api/v1/auth/status', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        hardwareFingerprint: query,
                        username: query
                    })
                });
                const data = await res.json();
                setVisible(checkDeviceResult, true, 'block');

                if (!data || data.status === 'UNREGISTERED') {
                    checkDeviceResult.innerHTML = `
                        <div style="display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:8px; padding:12px 14px; background:#F8FAFC; border:1px solid #E2E8F0; border-radius:12px;">
                            <div>
                                <div style="font-weight:700; color:#111827;">No Registration Found</div>
                                <div style="font-size:0.84rem; color:#475569; margin-top:2px;">Open the Hollow Android app and tap <strong>Create Account</strong> to submit your device for review.</div>
                            </div>
                            <span class="status-pill pending">UNREGISTERED</span>
                        </div>
                    `;
                } else {
                    const st = normalizeStatus(data.status);
                    const pillClass = st === 'APPROVED' ? 'approved' : (st === 'BANNED' ? 'banned' : 'pending');
                    const mods = Array.isArray(data.allowedModules) ? data.allowedModules : [];
                    checkDeviceResult.innerHTML = `
                        <div style="display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:10px; padding:12px 14px; background:#F8FAFC; border:1px solid #E2E8F0; border-radius:12px;">
                            <div>
                                <div style="font-weight:700; color:#111827; font-size:0.98rem;">Account: ${esc(data.username || query)}</div>
                                <div style="font-size:0.84rem; color:#475569; margin-top:3px;">
                                    ${st === 'APPROVED'
                                        ? `Approved with <strong>${mods.length}</strong> assigned module(s). Open the app to access your dashboard.`
                                        : st === 'BANNED'
                                            ? 'This device registration has been restricted by an administrator.'
                                            : 'Your registration is in the review queue. The app will unlock automatically once approved.'}
                                </div>
                            </div>
                            <span class="status-pill ${pillClass}">${esc(st)}</span>
                        </div>
                    `;
                }
            } catch (err) {
                setVisible(checkDeviceResult, true, 'block');
                checkDeviceResult.innerHTML = `<span style="color:#DC2626; font-weight:600;">Unable to reach verification server. Please try again.</span>`;
            } finally {
                btnCheckDeviceStatus.disabled = false;
                btnCheckDeviceStatus.textContent = 'Check Approval Status';
            }
        };

        btnCheckDeviceStatus.addEventListener('click', runStatusCheck);
        checkHwInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') runStatusCheck();
        });
    }

    // =========================================================================
    // 2. DEDICATED ADMIN CONSOLE LOGIC (/admin)
    // =========================================================================
    const adminLoginCard = document.getElementById('adminLoginCard');
    const adminDashboard = document.getElementById('adminDashboard');
    const btnAdminLogin = document.getElementById('btnAdminLogin');
    const btnAdminRefresh = document.getElementById('btnAdminRefresh');
    const btnAdminLogout = document.getElementById('btnAdminLogout');
    const adminLoginErr = document.getElementById('adminLoginErr');

    if (!adminLoginCard || !adminDashboard) {
        // Not on /admin page
        return;
    }

    let adminToken = sessionStorage.getItem('hollow_admin_token') || '';
    let cachedDevices = [];
    let cachedModules = [];
    let cachedLogs = [];
    let activeFilter = 'ALL';
    let searchQuery = '';
    let autoRefreshTimer = null;

    function setAdminAuthenticatedState(isAuthed) {
        setVisible(adminLoginCard, !isAuthed, 'flex');
        setVisible(adminDashboard, isAuthed, 'block');
        setVisible(btnAdminRefresh, isAuthed, 'inline-flex');
        setVisible(btnAdminLogout, isAuthed, 'inline-flex');

        if (isAuthed) {
            renderAdminStats();
            renderAdminDevices();
            renderAdminLogs();
            if (!autoRefreshTimer) {
                autoRefreshTimer = setInterval(() => {
                    loadAdminOverview(true);
                }, 4000);
            }
        } else if (autoRefreshTimer) {
            clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
    }

    async function performAdminLogin() {
        const username = (document.getElementById('adminUser')?.value || '').trim();
        const password = (document.getElementById('adminPass')?.value || '').trim();
        if (adminLoginErr) {
            adminLoginErr.textContent = '';
            setVisible(adminLoginErr, false);
        }

        if (!username || !password) {
            if (adminLoginErr) {
                adminLoginErr.textContent = 'Please enter both username and password.';
                setVisible(adminLoginErr, true, 'block');
            }
            return;
        }

        btnAdminLogin.disabled = true;
        btnAdminLogin.textContent = 'Signing in...';

        try {
            const res = await fetch('/api/v1/admin/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();
            if (!res.ok || !data.token) {
                if (adminLoginErr) {
                    adminLoginErr.textContent = data.error || 'Invalid administrator credentials.';
                    setVisible(adminLoginErr, true, 'block');
                }
                return;
            }
            adminToken = data.token;
            sessionStorage.setItem('hollow_admin_token', adminToken);
            setAdminAuthenticatedState(true);
            await loadAdminOverview(false);
        } catch (err) {
            if (adminLoginErr) {
                adminLoginErr.textContent = 'Network error while signing in.';
                setVisible(adminLoginErr, true, 'block');
            }
        } finally {
            btnAdminLogin.disabled = false;
            btnAdminLogin.textContent = 'Sign In';
        }
    }

    if (btnAdminLogin) {
        btnAdminLogin.addEventListener('click', performAdminLogin);
    }
    const adminPassInput = document.getElementById('adminPass');
    if (adminPassInput) {
        adminPassInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') performAdminLogin();
        });
    }

    if (btnAdminRefresh) {
        btnAdminRefresh.addEventListener('click', () => loadAdminOverview(false));
    }

    if (btnAdminLogout) {
        btnAdminLogout.addEventListener('click', () => {
            adminToken = '';
            sessionStorage.removeItem('hollow_admin_token');
            setAdminAuthenticatedState(false);
        });
    }

    // Filter Pills & Search
    document.querySelectorAll('.filter-pills .filter-pill').forEach((pill) => {
        pill.addEventListener('click', () => {
            document.querySelectorAll('.filter-pills .filter-pill').forEach((p) => p.classList.remove('active'));
            pill.classList.add('active');
            activeFilter = pill.getAttribute('data-filter') || 'ALL';
            renderAdminDevices();
        });
    });

    const adminSearchInput = document.getElementById('adminSearchInput');
    if (adminSearchInput) {
        adminSearchInput.addEventListener('input', () => {
            searchQuery = adminSearchInput.value.trim().toLowerCase();
            renderAdminDevices();
        });
    }

    // Manual Device Registration / Pre-Approval Bar
    const btnToggleManualAdd = document.getElementById('btnToggleManualAdd');
    const manualAddBox = document.getElementById('manualAddBox');
    const btnManualApproveSubmit = document.getElementById('btnManualApproveSubmit');

    if (btnToggleManualAdd && manualAddBox) {
        btnToggleManualAdd.addEventListener('click', () => {
            const isCurrentlyHidden = manualAddBox.classList.contains('hidden');
            setVisible(manualAddBox, isCurrentlyHidden, 'grid');
        });
    }

    if (btnManualApproveSubmit) {
        btnManualApproveSubmit.addEventListener('click', async () => {
            const usernameInput = document.getElementById('manualUsername');
            const hwInput = document.getElementById('manualHwId');
            const username = (usernameInput?.value || '').trim() || 'hollow_user';
            const hwId = (hwInput?.value || '').trim();

            if (!hwId) {
                alert('Please enter the Device Hardware ID (or Username) to register and approve.');
                return;
            }

            btnManualApproveSubmit.disabled = true;
            btnManualApproveSubmit.textContent = 'Approving...';
            try {
                // Step 1: Ensure registered
                await fetch('/api/v1/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        username,
                        hardwareFingerprint: hwId,
                        deviceModel: 'Manual Admin Entry'
                    })
                });
                // Step 2: Approve with all modules
                const allModuleIds = cachedModules.map((m) => m.moduleId || m.id).filter(Boolean);
                await fetch('/api/v1/admin/devices/approve', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        Authorization: `Bearer ${adminToken}`
                    },
                    body: JSON.stringify({
                        hardwareFingerprint: hwId,
                        allowedModules: allModuleIds
                    })
                });
                if (usernameInput) usernameInput.value = '';
                if (hwInput) hwInput.value = '';
                setVisible(manualAddBox, false);
                await loadAdminOverview(false);
            } catch (e) {
                alert('Failed to manually register/approve device.');
            } finally {
                btnManualApproveSubmit.disabled = false;
                btnManualApproveSubmit.textContent = 'Register & Approve Immediately';
            }
        });
    }

    async function loadAdminOverview(silent) {
        if (!adminToken) return;
        try {
            const res = await fetch('/api/v1/admin/overview', {
                headers: { Authorization: `Bearer ${adminToken}` }
            });
            if (res.status === 401) {
                adminToken = '';
                sessionStorage.removeItem('hollow_admin_token');
                setAdminAuthenticatedState(false);
                return;
            }
            const data = await res.json();
            cachedDevices = Array.isArray(data.devices) ? data.devices : [];
            cachedModules = Array.isArray(data.modules) ? data.modules : [];
            cachedLogs = Array.isArray(data.auditLogs)
                ? data.auditLogs
                : (Array.isArray(data.incidents) ? data.incidents : []);

            renderAdminStats();
            if (!silent || !document.activeElement || !document.activeElement.classList.contains('mod-check')) {
                renderAdminDevices();
            }
            renderAdminLogs();
        } catch (err) {
            // Keep existing view on transient poll error
        }
    }

    function renderAdminStats() {
        const statsGrid = document.getElementById('adminStatsGrid');
        if (!statsGrid) return;

        const pendingCount = cachedDevices.filter((d) => normalizeStatus(d.status) === 'PENDING_APPROVAL').length;
        const approvedCount = cachedDevices.filter((d) => normalizeStatus(d.status) === 'APPROVED').length;
        const bannedCount = cachedDevices.filter((d) => normalizeStatus(d.status) === 'BANNED').length;

        statsGrid.innerHTML = `
            <div class="stat-card">
                <div class="stat-label">Pending Review</div>
                <div class="stat-value">${pendingCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Approved Devices</div>
                <div class="stat-value">${approvedCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Signed Modules</div>
                <div class="stat-value">${cachedModules.length}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Restricted</div>
                <div class="stat-value">${bannedCount}</div>
            </div>
        `;
    }

    function renderAdminDevices() {
        const container = document.getElementById('adminDevicesContainer');
        if (!container) return;

        // Sort PENDING_APPROVAL first, then most recently updated
        const sorted = [...cachedDevices].sort((a, b) => {
            const sa = normalizeStatus(a.status) === 'PENDING_APPROVAL' ? 0 : 1;
            const sb = normalizeStatus(b.status) === 'PENDING_APPROVAL' ? 0 : 1;
            if (sa !== sb) return sa - sb;
            const ta = new Date(a.updatedAt || a.registeredAt || 0).getTime();
            const tb = new Date(b.updatedAt || b.registeredAt || 0).getTime();
            return tb - ta;
        });

        const filtered = sorted.filter((d) => {
            const st = normalizeStatus(d.status);
            if (activeFilter !== 'ALL' && st !== activeFilter) return false;
            if (searchQuery) {
                const hay = `${d.username || ''} ${d.email || ''} ${d.hardwareFingerprint || ''} ${d.deviceModel || ''}`.toLowerCase();
                if (!hay.includes(searchQuery)) return false;
            }
            return true;
        });

        if (filtered.length === 0) {
            container.innerHTML = `
                <div class="empty-queue-box">
                    <h3>No device requests in this view</h3>
                    <p>When a user registers an account in the Hollow Android app, their request appears here automatically within seconds. You can also click <strong>+ Add / Approve Device by ID</strong> above.</p>
                </div>
            `;
            return;
        }

        container.innerHTML = filtered.map((d) => {
            const st = normalizeStatus(d.status);
            const pillClass = st === 'APPROVED' ? 'approved' : (st === 'BANNED' ? 'banned' : 'pending');
            const pillLabel = st === 'PENDING_APPROVAL' ? 'WAITING FOR APPROVAL' : st;
            const allowed = Array.isArray(d.allowedModules) ? d.allowedModules : [];
            const hwAttr = esc(d.hardwareFingerprint);

            const moduleChecksHtml = cachedModules.map((m) => {
                const modId = m.moduleId || m.id;
                // Default check all modules for pending devices so one-click Approve grants them seamlessly
                const isChecked = st === 'PENDING_APPROVAL'
                    ? true
                    : allowed.includes(modId);
                return `
                    <label class="module-check-label">
                        <input type="checkbox" class="mod-check" data-hw="${hwAttr}" value="${esc(modId)}" ${isChecked ? 'checked' : ''} />
                        <span>${esc(m.title || modId)}</span>
                    </label>
                `;
            }).join('');

            return `
                <div class="device-row-card ${st === 'PENDING_APPROVAL' ? 'is-pending' : ''}">
                    <div class="device-row-top">
                        <div>
                            <div class="device-user-title">
                                <span>${esc(d.username || 'Anonymous User')}</span>
                                ${d.email ? `<span style="font-size:0.82rem; font-weight:500; color:#64748B;">(${esc(d.email)})</span>` : ''}
                                <span style="font-size:0.76rem; font-weight:600; color:#64748B;">• ${esc(formatTimeAgo(d.updatedAt || d.registeredAt))}</span>
                            </div>
                            <div class="device-hw-mono">
                                HWID: ${esc(d.hardwareFingerprint)} &nbsp;|&nbsp; Model: ${esc(d.deviceModel || 'Android Device')}
                            </div>
                        </div>
                        <span class="status-pill ${pillClass}">${esc(pillLabel)}</span>
                    </div>

                    <div>
                        <div style="font-size:0.75rem; font-weight:700; text-transform:uppercase; letter-spacing:0.05em; color:#64748B; margin-bottom:8px;">
                            Entitled Signed Modules
                        </div>
                        <div class="module-checkbox-row">
                            ${moduleChecksHtml}
                        </div>
                    </div>

                    <div class="device-actions-row">
                        <button type="button" class="btn-black btn-sm btn-approve-device" data-hw="${hwAttr}">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><polyline points="20 6 9 17 4 12"/></svg>
                            <span>${st === 'APPROVED' ? 'Save Module Entitlements' : 'Approve & Grant Selected'}</span>
                        </button>
                        ${st !== 'BANNED' ? `
                            <button type="button" class="btn-danger-outline btn-ban-device" data-hw="${hwAttr}" data-ban="true">
                                Restrict Device
                            </button>
                        ` : `
                            <button type="button" class="btn-outline btn-sm btn-ban-device" data-hw="${hwAttr}" data-ban="false">
                                Lift Restriction
                            </button>
                        `}
                    </div>
                </div>
            `;
        }).join('');

        container.querySelectorAll('.btn-approve-device').forEach((btn) => {
            btn.addEventListener('click', async () => {
                const hw = btn.getAttribute('data-hw');
                const checks = Array.from(container.querySelectorAll('.mod-check')).filter(
                    (c) => c.getAttribute('data-hw') === hw
                );
                const selectedModules = checks.filter((c) => c.checked).map((c) => c.value);

                btn.disabled = true;
                btn.textContent = 'Saving...';
                try {
                    await fetch('/api/v1/admin/devices/approve', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            Authorization: `Bearer ${adminToken}`
                        },
                        body: JSON.stringify({
                            hardwareFingerprint: hw,
                            allowedModules: selectedModules
                        })
                    });
                    await loadAdminOverview(false);
                } finally {
                    btn.disabled = false;
                }
            });
        });

        container.querySelectorAll('.btn-ban-device').forEach((btn) => {
            btn.addEventListener('click', async () => {
                const hw = btn.getAttribute('data-hw');
                const ban = btn.getAttribute('data-ban') === 'true';
                btn.disabled = true;
                try {
                    const endpoint = ban ? '/api/v1/admin/devices/ban' : '/api/v1/admin/devices/unban';
                    await fetch(endpoint, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            Authorization: `Bearer ${adminToken}`
                        },
                        body: JSON.stringify({
                            hardwareFingerprint: hw,
                            ban
                        })
                    });
                    await loadAdminOverview(false);
                } finally {
                    btn.disabled = false;
                }
            });
        });
    }

    function renderAdminLogs() {
        const container = document.getElementById('adminLogsContainer');
        if (!container) return;

        if (!cachedLogs || cachedLogs.length === 0) {
            container.innerHTML = `<div style="color:#64748B; font-size:0.88rem; padding:18px 20px;">No administrative or security events recorded yet.</div>`;
            return;
        }

        container.innerHTML = `
            <table class="audit-table">
                <thead>
                    <tr>
                        <th>Action</th>
                        <th>Account / Device</th>
                        <th>Details</th>
                        <th>Time</th>
                    </tr>
                </thead>
                <tbody>
                    ${cachedLogs.slice(0, 30).map((log) => `
                        <tr>
                            <td style="font-weight:700; color:#111827;">${esc(log.action || log.incidentType || 'EVENT')}</td>
                            <td style="font-family:var(--font-mono); font-size:0.78rem;">
                                ${log.username ? `<strong>${esc(log.username)}</strong> · ` : ''}${esc((log.hardwareFingerprint || 'system').slice(0, 22))}
                            </td>
                            <td>${esc(log.details || '')}</td>
                            <td style="color:#64748B; white-space:nowrap;">${esc(formatTimeAgo(log.timestamp || log.occurredAt))}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }

    // Initialize Admin Console if session token exists
    if (adminToken) {
        setAdminAuthenticatedState(true);
        loadAdminOverview(false);
    } else {
        setAdminAuthenticatedState(false);
    }
})();
