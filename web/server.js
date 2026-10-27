/**
 * Hollow Cloud Server & Colorful Material 3 Web Platform (Node.js + Express + MongoDB Atlas)
 *
 * Features:
 * 1. Public Home Page (/) + Admin Console (/admin)
 * 2. MongoDB Atlas persistence with automatic in-memory fallback when MONGODB_URI is not set,
 *    ensuring the dashboard and APIs never fail or appear empty.
 * 3. Zero-Touch Dynamic Module Catalog auto-discovered from ../modules/<moduleId>/manifest.json
 * 4. ECDH P-256 + AES-256-GCM envelope encryption + Ed25519 signed module delivery
 */

require('dotenv').config();
const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const mongoose = require('mongoose');

const Device = require('./models/Device');
const License = require('./models/License');
const AuditLog = require('./models/AuditLog');

const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// Serve the Web App on both / and /admin
app.get('/admin', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

const PORT = process.env.PORT || 8080;
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex');
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'hollow_admin_2025';
const MONGODB_URI = process.env.MONGODB_URI || '';

// ---------------------------------------------------------------------------
// 1. MongoDB Atlas Connection + Seamless In-Memory Fallback Store
// ---------------------------------------------------------------------------
let mongoConnected = false;

const memoryStore = {
  devices: [
    {
      hardwareFingerprint: 'HLW-9A4F2C10-7B8E3D11-5C6A902F',
      username: 'arshman_pro',
      email: 'arshman@hollow.me',
      passwordHash: crypto.createHash('sha256').update('demo1234').digest('hex'),
      socSerial: 'SM8650-0194A',
      androidId: '9774d56d682e549c',
      storageCid: '1501003531324743',
      deviceModel: 'Google Pixel 9 Pro',
      status: 'APPROVED',
      allowedModules: ['module_hider', 'module_isolation', 'module_privacy', 'module_network', 'module_sensor_firewall'],
      banReason: '',
      highWaterWallClockMs: Date.now(),
      lastElapsedRealtimeMs: 428000,
      registeredAt: new Date(Date.now() - 3 * 86400000),
      updatedAt: new Date()
    },
    {
      hardwareFingerprint: 'HLW-3E81B402-19C4A77D-8F201E6A',
      username: 'alex_tester',
      email: 'alex@example.com',
      passwordHash: crypto.createHash('sha256').update('demo1234').digest('hex'),
      socSerial: 'SM8550-8821B',
      androidId: '4182b90c112e773a',
      storageCid: '1501003235364742',
      deviceModel: 'Samsung Galaxy S24 Ultra',
      status: 'PENDING_APPROVAL',
      allowedModules: [],
      banReason: '',
      highWaterWallClockMs: Date.now(),
      lastElapsedRealtimeMs: 120000,
      registeredAt: new Date(Date.now() - 3600000),
      updatedAt: new Date()
    },
    {
      hardwareFingerprint: 'HLW-7C12D908-44A10F3E-99B12C44',
      username: 'sarah_workspace',
      email: 'sarah@example.com',
      passwordHash: crypto.createHash('sha256').update('demo1234').digest('hex'),
      socSerial: 'MT6989-7731C',
      androidId: '6621f09a883c120d',
      storageCid: '1501003132384741',
      deviceModel: 'OnePlus 12',
      status: 'APPROVED',
      allowedModules: ['module_isolation', 'module_privacy', 'module_hider'],
      banReason: '',
      highWaterWallClockMs: Date.now(),
      lastElapsedRealtimeMs: 910000,
      registeredAt: new Date(Date.now() - 86400000 * 5),
      updatedAt: new Date()
    }
  ],
  licenses: new Map(),
  auditLogs: [
    {
      event: 'SERVER_BOOT',
      hardwareFingerprint: 'SYSTEM',
      username: 'system',
      details: 'Hollow Cloud Module Hub initialized with 5 dynamic modules',
      ipAddress: '127.0.0.1',
      timestamp: new Date(Date.now() - 600000)
    },
    {
      event: 'ADMIN_APPROVE',
      hardwareFingerprint: 'HLW-9A4F2C10-7B8E3D11-5C6A902F',
      username: 'arshman_pro',
      details: 'Approved Google Pixel 9 Pro for all 5 modules (30 days)',
      ipAddress: '127.0.0.1',
      timestamp: new Date(Date.now() - 300000)
    },
    {
      event: 'REGISTER_PENDING',
      hardwareFingerprint: 'HLW-3E81B402-19C4A77D-8F201E6A',
      username: 'alex_tester',
      details: 'Registered Samsung Galaxy S24 Ultra — waiting for Admin approval',
      ipAddress: '127.0.0.1',
      timestamp: new Date(Date.now() - 120000)
    }
  ]
};

function isDbLive() {
  return mongoConnected && mongoose.connection.readyState === 1;
}

async function connectMongoDB() {
  if (!MONGODB_URI) {
    console.log('[MongoDB] MONGODB_URI not set — running with built-in live memory store.');
    return;
  }
  try {
    await mongoose.connect(MONGODB_URI, {
      serverSelectionTimeoutMS: 6000
    });
    mongoConnected = true;
    console.log('[MongoDB] Connected to MongoDB Atlas successfully.');
    // Seed initial sample devices if database is brand new & empty
    const count = await Device.countDocuments();
    if (count === 0) {
      await Device.insertMany(memoryStore.devices);
      await AuditLog.insertMany(memoryStore.auditLogs);
      console.log('[MongoDB] Seeded initial devices & activity logs.');
    }
  } catch (err) {
    mongoConnected = false;
    console.error('[MongoDB] Connection failed, using in-memory store fallback:', err.message);
  }
}
connectMongoDB();

async function recordAudit(event, hardwareFingerprint, username, details, req) {
  const entry = {
    event,
    hardwareFingerprint: hardwareFingerprint || '',
    username: username || '',
    details: details || '',
    ipAddress: req?.ip || req?.connection?.remoteAddress || '127.0.0.1',
    timestamp: new Date()
  };
  memoryStore.auditLogs.unshift(entry);
  if (memoryStore.auditLogs.length > 100) memoryStore.auditLogs.pop();

  if (isDbLive()) {
    try {
      await AuditLog.create(entry);
    } catch (e) {
      console.error('[AuditLog] Failed to write audit log:', e.message);
    }
  }
}

// ---------------------------------------------------------------------------
// 2. Cryptographic Keys (Ed25519 Signing + ECDH P-256 Envelope)
// ---------------------------------------------------------------------------
const { publicKey: edPublicKey, privateKey: edPrivateKey } = crypto.generateKeyPairSync('ed25519');
const serverEcdh = crypto.createECDH('prime256v1');
serverEcdh.generateKeys();

// ---------------------------------------------------------------------------
// 3. Dynamic Module Catalog Loader (reads from repo root /modules/)
// ---------------------------------------------------------------------------
const REPO_MODULES_DIR = path.resolve(__dirname, '..', 'modules');

const FALLBACK_MODULES = {
  module_hider: {
    moduleId: 'module_hider',
    displayName: 'App Hider & Launcher Disguise',
    version: '2.4.0',
    minCoreVersion: 1,
    entryClass: 'com.example.isolation.modules.hider.AppHiderModuleEntry',
    cardTitle: 'Hide App',
    cardSubtitle: 'Calculator decoy & hidden apps',
    cardBadge: 'Stealth',
    iconName: 'visibility_off',
    accentColorHex: '#7C3AED',
    description: 'Hide apps from launcher or disguise Hollow as a working Calculator or Notes app.',
    features: [
      { key: 'hide_from_launcher', title: 'Hide Apps from Launcher', subtitle: 'Remove selected apps from launcher & recents', defaultEnabled: true },
      { key: 'calculator_decoy', title: 'Calculator PIN Vault', subtitle: 'Open hidden apps by entering secret PIN in Calculator', defaultEnabled: true }
    ]
  },
  module_isolation: {
    moduleId: 'module_isolation',
    displayName: 'Storage Isolate & Per-App Drives',
    version: '2.4.0',
    minCoreVersion: 1,
    entryClass: 'com.example.isolation.modules.isolation.StorageIsolationModuleEntry',
    cardTitle: 'Storage Isolate',
    cardSubtitle: 'Private folders & photo shield',
    cardBadge: 'Protected',
    iconName: 'folder',
    accentColorHex: '#0D9488',
    description: 'Give each app its own clean storage sandbox so it cannot scan your personal DCIM photos or downloads.',
    features: [
      { key: 'sandbox_redirect', title: 'Per-App Storage Sandbox', subtitle: 'Redirect /sdcard reads & writes to isolated workspace', defaultEnabled: true },
      { key: 'encrypted_vault', title: 'AES-256 Safe Folder', subtitle: 'Encrypt sensitive photos & documents at rest', defaultEnabled: true }
    ]
  },
  module_privacy: {
    moduleId: 'module_privacy',
    displayName: 'Privacy & Device Identity Spoofer',
    version: '2.4.0',
    minCoreVersion: 1,
    entryClass: 'com.example.isolation.modules.privacy.PrivacySpooferModuleEntry',
    cardTitle: 'Privacy & Identity',
    cardSubtitle: 'Spoof phone model, IDs & GPS',
    cardBadge: 'Shield',
    iconName: 'fingerprint',
    accentColorHex: '#4F46E5',
    description: 'Customize per-app device model (Pixel 9 Pro, Galaxy S24 Ultra), Android ID, IMEI, Wi-Fi MAC, and virtual GPS.',
    features: [
      { key: 'device_model_spoof', title: 'Phone Model & Hardware IDs', subtitle: 'Per-app Brand, Model, IMEI, Android ID & MAC', defaultEnabled: true },
      { key: 'virtual_gps', title: 'Virtual GPS & Timezone', subtitle: 'Spoof per-app coordinates and matching timezone', defaultEnabled: true }
    ]
  },
  module_network: {
    moduleId: 'module_network',
    displayName: 'V2Ray, SOCKS5 & Per-App Proxy',
    version: '2.4.0',
    minCoreVersion: 1,
    entryClass: 'com.example.isolation.modules.network.NetworkProxyModuleEntry',
    cardTitle: 'V2Ray & Proxy',
    cardSubtitle: 'Per-app VLESS, VMess & SOCKS5',
    cardBadge: 'Routing',
    iconName: 'public',
    accentColorHex: '#0284C7',
    description: 'Route individual apps through VLESS, VMess, Trojan, Shadowsocks, or SOCKS5 nodes with timezone sync.',
    features: [
      { key: 'per_app_proxy', title: 'Per-App Proxy Routing', subtitle: 'Assign different proxy nodes to individual apps', defaultEnabled: true },
      { key: 'kill_switch', title: 'Leak Kill-Switch', subtitle: 'Block direct traffic if proxy tunnel disconnects', defaultEnabled: true }
    ]
  },
  module_sensor_firewall: {
    moduleId: 'module_sensor_firewall',
    displayName: 'Zero-Trust Sensor Firewall',
    version: '2.4.0',
    minCoreVersion: 1,
    entryClass: 'com.example.isolation.modules.sensor.SensorFirewallModuleEntry',
    cardTitle: 'Sensor Firewall',
    cardSubtitle: 'Gyroscope, step & mic guard',
    cardBadge: 'Firewall',
    iconName: 'sensors',
    accentColorHex: '#E11D48',
    description: 'Neutralize gyroscope, accelerometer, step counter, and barometer telemetry used for motion fingerprinting.',
    features: [
      { key: 'gyro_noise', title: 'Gyroscope & Accelerometer Guard', subtitle: 'Filter micro-vibration hardware signatures', defaultEnabled: true },
      { key: 'step_counter_block', title: 'Step & Activity Blocker', subtitle: 'Prevent background pedometer tracking', defaultEnabled: true }
    ]
  }
};

function loadModulesFromRepo() {
  const catalog = {};
  try {
    if (fs.existsSync(REPO_MODULES_DIR)) {
      const entries = fs.readdirSync(REPO_MODULES_DIR, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const modDir = path.join(REPO_MODULES_DIR, entry.name);
        const manifestFile = path.join(modDir, 'manifest.json');
        const payloadFile = path.join(modDir, 'module.dex.bin');
        if (!fs.existsSync(manifestFile)) continue;

        const manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
        const rawPayload = fs.existsSync(payloadFile)
          ? fs.readFileSync(payloadFile)
          : Buffer.from(`HOLLOW_DYNAMIC_DEX::${manifest.moduleId}::${manifest.version}`);
        const sha256 = crypto.createHash('sha256').update(rawPayload).digest('hex');

        catalog[manifest.moduleId] = {
          ...manifest,
          sha256,
          sizeBytes: rawPayload.length,
          dexPayloadBase64: rawPayload.toString('base64')
        };
      }
    }
  } catch (err) {
    console.error('[Modules] Error reading /modules directory:', err.message);
  }

  for (const [id, mod] of Object.entries(FALLBACK_MODULES)) {
    if (!catalog[id]) {
      const buf = Buffer.from(`HOLLOW_DYNAMIC_DEX::${id}::${mod.version}`);
      catalog[id] = {
        ...mod,
        sha256: crypto.createHash('sha256').update(buf).digest('hex'),
        sizeBytes: buf.length,
        dexPayloadBase64: buf.toString('base64')
      };
    }
  }
  return catalog;
}

// ---------------------------------------------------------------------------
// 4. Envelope Encryption Helpers (ECDH + AES-256-GCM)
// ---------------------------------------------------------------------------
function deriveSharedAesKey(clientPubKeyBase64, nonce) {
  const clientPubBuf = Buffer.from(clientPubKeyBase64, 'base64');
  const sharedSecret = serverEcdh.computeSecret(clientPubBuf);
  return crypto.createHash('sha256')
    .update(sharedSecret)
    .update(Buffer.from(nonce || '', 'utf8'))
    .digest();
}

function decryptEnvelope(envelope) {
  if (!envelope || !envelope.encrypted || !envelope.clientEphemeralPub) {
    return envelope;
  }
  const aesKey = deriveSharedAesKey(envelope.clientEphemeralPub, envelope.nonce);
  const combined = Buffer.from(envelope.payload, 'base64');
  const iv = combined.subarray(0, 12);
  const tag = combined.subarray(combined.length - 16);
  const ciphertext = combined.subarray(12, combined.length - 16);

  const decipher = crypto.createDecipheriv('aes-256-gcm', aesKey, iv);
  decipher.setAuthTag(tag);
  const plainBuf = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return JSON.parse(plainBuf.toString('utf8'));
}

function signJsonPayload(obj) {
  const canonical = JSON.stringify(obj);
  const sig = crypto.sign(null, Buffer.from(canonical, 'utf8'), edPrivateKey);
  return sig.toString('base64');
}

function hashPassword(pw) {
  return crypto.createHash('sha256').update(String(pw || '')).digest('hex');
}

function sendClientResponse(req, res, payloadObj) {
  const signature = signJsonPayload(payloadObj);
  const bodyWithSig = { ...payloadObj, ed25519Signature: signature };

  if (req.body && req.body.encrypted && req.body.clientEphemeralPub) {
    const nonce = crypto.randomBytes(12).toString('hex');
    const aesKey = deriveSharedAesKey(req.body.clientEphemeralPub, nonce);
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', aesKey, iv);
    const plainBuf = Buffer.from(JSON.stringify(bodyWithSig), 'utf8');
    const encrypted = Buffer.concat([cipher.update(plainBuf), cipher.final()]);
    const tag = cipher.getAuthTag();
    const combined = Buffer.concat([iv, encrypted, tag]);

    return res.json({
      encrypted: true,
      serverEphemeralPub: serverEcdh.getPublicKey('base64'),
      nonce,
      payload: combined.toString('base64'),
      ed25519Signature: signature
    });
  }
  return res.json(bodyWithSig);
}

// ---------------------------------------------------------------------------
// 5. Public & Health Endpoints (for Home Page & Status Widget)
// ---------------------------------------------------------------------------
app.get('/api/v1/health', async (_req, res) => {
  const catalog = loadModulesFromRepo();
  res.json({
    ok: true,
    service: 'Hollow Cloud Module & License Hub',
    mongodbState: isDbLive() ? 'CONNECTED' : 'MEMORY_FALLBACK_ACTIVE',
    ed25519PublicKey: edPublicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    serverEcdhPublicKey: serverEcdh.getPublicKey('base64'),
    modulesHosted: Object.keys(catalog),
    serverTimeMs: Date.now()
  });
});

app.get('/api/v1/public/catalog', async (_req, res) => {
  const catalog = loadModulesFromRepo();
  const modules = Object.values(catalog).map(({ dexPayloadBase64, ...meta }) => meta);
  let totalDevices = memoryStore.devices.length;
  let approvedDevices = memoryStore.devices.filter((d) => d.status === 'APPROVED').length;
  if (isDbLive()) {
    try {
      totalDevices = await Device.countDocuments();
      approvedDevices = await Device.countDocuments({ status: 'APPROVED' });
    } catch (_e) {}
  }
  res.json({
    ok: true,
    mongodbState: isDbLive() ? 'CONNECTED' : 'MEMORY_STORE',
    stats: {
      modulesCount: modules.length,
      totalDevices,
      approvedDevices
    },
    modules
  });
});

// ---------------------------------------------------------------------------
// 6. Client Registration, Approval Check & Dynamic Module Download Endpoints
// ---------------------------------------------------------------------------
app.post('/api/v1/auth/register', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const {
      username,
      email,
      password,
      hardwareFingerprint,
      socSerial,
      androidId,
      storageCid,
      deviceModel
    } = body;

    if (!hardwareFingerprint || !username) {
      return res.status(400).json({ ok: false, error: 'Missing hardwareFingerprint or username' });
    }

    if (isDbLive()) {
      let existing = await Device.findOne({ hardwareFingerprint });
      if (existing && existing.status === 'BANNED') {
        await recordAudit('REGISTER_BLOCKED_BANNED', hardwareFingerprint, username, 'Banned device attempted re-registration', req);
        return sendClientResponse(req, res, {
          ok: true,
          status: 'BANNED',
          banReason: existing.banReason,
          allowedModules: [],
          message: `Device permanently banned: ${existing.banReason}`
        });
      }

      if (!existing) {
        existing = await Device.create({
          hardwareFingerprint,
          username,
          email: email || '',
          passwordHash: hashPassword(password),
          socSerial: socSerial || '',
          androidId: androidId || '',
          storageCid: storageCid || '',
          deviceModel: deviceModel || 'Android Device',
          status: 'PENDING_APPROVAL',
          allowedModules: [],
          banReason: '',
          registeredAt: new Date(),
          updatedAt: new Date()
        });
        await recordAudit('REGISTER_PENDING', hardwareFingerprint, username, `Registered ${deviceModel || 'Android Device'} — waiting for Admin approval`, req);
      } else {
        existing.username = username;
        existing.email = email || existing.email;
        existing.updatedAt = new Date();
        await existing.save();
      }

      const activeLicense = await License.findOne({ hardwareFingerprint, revoked: false }).sort({ issuedAtMs: -1 });
      return sendClientResponse(req, res, {
        ok: true,
        status: existing.status,
        hardwareFingerprint: existing.hardwareFingerprint,
        allowedModules: existing.allowedModules,
        licenseToken: activeLicense ? activeLicense.licenseToken : '',
        validUntilMs: activeLicense ? activeLicense.validUntilMs : 0,
        serverTimeMs: Date.now(),
        message: existing.status === 'APPROVED'
          ? 'Device approved! You can now install your authorized modules.'
          : 'Registration submitted. Waiting for Administrator approval.'
      });
    }

    // In-Memory Fallback Store
    let memDev = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
    if (memDev && memDev.status === 'BANNED') {
      return sendClientResponse(req, res, {
        ok: true,
        status: 'BANNED',
        banReason: memDev.banReason,
        allowedModules: [],
        message: `Device permanently banned: ${memDev.banReason}`
      });
    }
    if (!memDev) {
      memDev = {
        hardwareFingerprint,
        username,
        email: email || '',
        passwordHash: hashPassword(password),
        socSerial: socSerial || '',
        androidId: androidId || '',
        storageCid: storageCid || '',
        deviceModel: deviceModel || 'Android Device',
        status: 'PENDING_APPROVAL',
        allowedModules: [],
        banReason: '',
        highWaterWallClockMs: Date.now(),
        lastElapsedRealtimeMs: 0,
        registeredAt: new Date(),
        updatedAt: new Date()
      };
      memoryStore.devices.unshift(memDev);
      await recordAudit('REGISTER_PENDING', hardwareFingerprint, username, `Registered ${memDev.deviceModel} — waiting for Admin approval`, req);
    } else {
      memDev.username = username;
      memDev.email = email || memDev.email;
      memDev.updatedAt = new Date();
    }

    const memLic = memoryStore.licenses.get(hardwareFingerprint);
    return sendClientResponse(req, res, {
      ok: true,
      status: memDev.status,
      hardwareFingerprint: memDev.hardwareFingerprint,
      allowedModules: memDev.allowedModules,
      licenseToken: memLic ? memLic.licenseToken : '',
      validUntilMs: memLic ? memLic.validUntilMs : 0,
      serverTimeMs: Date.now(),
      message: memDev.status === 'APPROVED'
        ? 'Device approved! You can now install your authorized modules.'
        : 'Registration submitted. Waiting for Administrator approval.'
    });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/auth/status', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const { hardwareFingerprint, elapsedRealtimeMs } = body;

    let record = null;
    let activeLicense = null;

    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
      activeLicense = await License.findOne({ hardwareFingerprint, revoked: false }).sort({ issuedAtMs: -1 });
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
      activeLicense = memoryStore.licenses.get(hardwareFingerprint);
    }

    if (!record) {
      return sendClientResponse(req, res, {
        ok: true,
        status: 'UNREGISTERED',
        allowedModules: [],
        serverTimeMs: Date.now()
      });
    }

    const nowMs = Date.now();
    record.highWaterWallClockMs = Math.max(nowMs, record.highWaterWallClockMs || 0);
    record.lastElapsedRealtimeMs = Number(elapsedRealtimeMs || 0);
    record.updatedAt = new Date();
    if (isDbLive() && typeof record.save === 'function') {
      await record.save();
    }

    return sendClientResponse(req, res, {
      ok: true,
      status: record.status,
      username: record.username,
      hardwareFingerprint: record.hardwareFingerprint,
      allowedModules: record.status === 'APPROVED' ? record.allowedModules : [],
      licenseToken: activeLicense ? activeLicense.licenseToken : '',
      issuedAtMs: activeLicense ? activeLicense.issuedAtMs : nowMs,
      validUntilMs: activeLicense ? activeLicense.validUntilMs : 0,
      banReason: record.banReason,
      serverTimeMs: nowMs
    });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/modules/download', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const { hardwareFingerprint, moduleId } = body;

    let record = null;
    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
    }

    if (!record || record.status !== 'APPROVED') {
      return res.status(403).json({
        ok: false,
        error: record?.status === 'BANNED'
          ? `Device is banned: ${record.banReason}`
          : 'Device is not approved by Administrator.'
      });
    }

    if (!record.allowedModules.includes(moduleId)) {
      return res.status(403).json({
        ok: false,
        error: `Account is not authorized for module: ${moduleId}`
      });
    }

    const catalog = loadModulesFromRepo();
    const mod = catalog[moduleId];
    if (!mod) {
      return res.status(404).json({ ok: false, error: `Unknown moduleId: ${moduleId}` });
    }

    await recordAudit('MODULE_DOWNLOADED', hardwareFingerprint, record.username, `Downloaded & bound ${moduleId} (v${mod.version})`, req);

    return sendClientResponse(req, res, {
      ok: true,
      ...mod,
      hardwareBoundTo: hardwareFingerprint,
      issuedAtMs: Date.now()
    });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 7. Admin Portal Authentication & Management API
// ---------------------------------------------------------------------------
function requireAdminAuth(req, res, next) {
  const authHeader = req.headers.authorization || '';
  const token = authHeader.replace(/^Bearer\s+/i, '');
  if (!token) {
    return res.status(401).json({ ok: false, error: 'Missing Admin JWT token' });
  }
  try {
    req.admin = jwt.verify(token, JWT_SECRET);
    return next();
  } catch (_err) {
    return res.status(401).json({ ok: false, error: 'Invalid or expired Admin token' });
  }
}

app.post('/api/v1/admin/login', async (req, res) => {
  const { username, password } = req.body || {};
  if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
    const token = jwt.sign({ username, role: 'SUPER_ADMIN' }, JWT_SECRET, { expiresIn: '12h' });
    await recordAudit('ADMIN_LOGIN', 'ADMIN_CONSOLE', username, 'Administrator signed in to Web Portal', req);
    return res.json({ ok: true, token, username });
  }
  return res.status(401).json({ ok: false, error: 'Invalid administrator credentials' });
});

app.get('/api/v1/admin/overview', requireAdminAuth, async (_req, res) => {
  try {
    let devicesList = [];
    let recentLogs = [];

    if (isDbLive()) {
      devicesList = await Device.find().sort({ updatedAt: -1 }).lean();
      recentLogs = await AuditLog.find().sort({ timestamp: -1 }).limit(40).lean();
    } else {
      devicesList = [...memoryStore.devices];
      recentLogs = [...memoryStore.auditLogs];
    }

    const catalog = loadModulesFromRepo();
    const modulesList = Object.values(catalog).map(({ dexPayloadBase64, ...rest }) => rest);

    res.json({
      ok: true,
      mongodbConnected: isDbLive(),
      storageMode: isDbLive() ? 'MongoDB Atlas Cloud' : 'Built-in Live Memory Store',
      stats: {
        totalDevices: devicesList.length,
        pendingCount: devicesList.filter((d) => d.status === 'PENDING_APPROVAL').length,
        approvedCount: devicesList.filter((d) => d.status === 'APPROVED').length,
        bannedCount: devicesList.filter((d) => d.status === 'BANNED').length,
        modulesCount: modulesList.length
      },
      modules: modulesList,
      devices: devicesList,
      auditLogs: recentLogs
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/approve', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, allowedModules, validityDays } = req.body || {};
    const catalog = loadModulesFromRepo();
    const defaultMods = Object.keys(catalog);
    const days = Number(validityDays || 30);
    const nowMs = Date.now();
    const validUntilMs = nowMs + days * 86400000;
    const mods = Array.isArray(allowedModules) && allowedModules.length > 0
      ? allowedModules
      : defaultMods;

    const licensePayload = {
      hardwareFingerprint,
      allowedModules: mods,
      issuedAtMs: nowMs,
      validUntilMs
    };
    const signature = signJsonPayload(licensePayload);
    const licenseToken = jwt.sign(licensePayload, JWT_SECRET, { expiresIn: `${days}d` });

    let record = null;
    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'APPROVED';
      record.allowedModules = mods;
      record.banReason = '';
      record.updatedAt = new Date();
      await record.save();

      await License.updateMany({ hardwareFingerprint }, { $set: { revoked: true } });
      await License.create({
        hardwareFingerprint,
        licenseToken,
        ed25519Signature: signature,
        allowedModules: mods,
        issuedAtMs: nowMs,
        validUntilMs,
        revoked: false
      });
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'APPROVED';
      record.allowedModules = mods;
      record.banReason = '';
      record.updatedAt = new Date();
      memoryStore.licenses.set(hardwareFingerprint, {
        hardwareFingerprint,
        licenseToken,
        ed25519Signature: signature,
        allowedModules: mods,
        issuedAtMs: nowMs,
        validUntilMs,
        revoked: false
      });
    }

    await recordAudit('ADMIN_APPROVE', hardwareFingerprint, record.username, `Approved for [${mods.join(', ')}] (${days} days)`, req);
    return res.json({ ok: true, device: record, licenseToken });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/update-modules', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, allowedModules } = req.body || {};
    const mods = Array.isArray(allowedModules) ? allowedModules : [];

    let record = null;
    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.allowedModules = mods;
      record.updatedAt = new Date();
      await record.save();
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.allowedModules = mods;
      record.updatedAt = new Date();
    }

    await recordAudit('ADMIN_UPDATE_MODULES', hardwareFingerprint, record.username, `Updated modules -> [${mods.join(', ')}]`, req);
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/ban', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, banReason } = req.body || {};
    let record = null;
    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'BANNED';
      record.allowedModules = [];
      record.banReason = banReason || 'Permanently banned by Administrator.';
      record.updatedAt = new Date();
      await record.save();
      await License.updateMany({ hardwareFingerprint }, { $set: { revoked: true } });
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'BANNED';
      record.allowedModules = [];
      record.banReason = banReason || 'Permanently banned by Administrator.';
      record.updatedAt = new Date();
      memoryStore.licenses.delete(hardwareFingerprint);
    }

    await recordAudit('ADMIN_BAN_DEVICE', hardwareFingerprint, record.username, `BANNED: ${record.banReason}`, req);
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/unban', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint } = req.body || {};
    let record = null;
    if (isDbLive()) {
      record = await Device.findOne({ hardwareFingerprint });
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'PENDING_APPROVAL';
      record.banReason = '';
      record.updatedAt = new Date();
      await record.save();
    } else {
      record = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
      if (!record) return res.status(404).json({ ok: false, error: 'Device not found' });
      record.status = 'PENDING_APPROVAL';
      record.banReason = '';
      record.updatedAt = new Date();
    }

    await recordAudit('ADMIN_UNBAN_DEVICE', hardwareFingerprint, record.username, 'Cleared BAN -> moved to PENDING_APPROVAL', req);
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`Hollow Material 3 Web Platform & Admin Portal listening on http://0.0.0.0:${PORT}`);
});
