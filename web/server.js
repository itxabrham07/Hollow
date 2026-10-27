require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const cors = require('cors');
const path = require('path');

const User = require('./models/User');
const Device = require('./models/Device');
const ModuleModel = require('./models/Module');
const SecurityIncident = require('./models/SecurityIncident');

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

const PORT = process.env.PORT || 3000;
const MONGODB_URI = process.env.MONGODB_URI || '';
const JWT_SECRET = process.env.JWT_SECRET || 'hollow_ultra_secret_jwt_signing_key_2026';
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'hollow_admin_master';
const PAYLOAD_HEX_KEY = process.env.HOLLOW_PAYLOAD_KEY || '4f8b9a2c1d7e6f5031425364758697a8b9c0d1e2f30415263748596a7b8c9d0e';

// Default catalog of dynamic modules that can be toggled per user
const DEFAULT_MODULES = [
  {
    moduleId: 'module_hider',
    name: 'App Hider & SystemUI Stealth',
    description: 'System-wide launcher hiding, Calculator/Notes icon disguise, and SystemUI Recents suppression.',
    version: 3,
    minCoreVersion: 1,
    payloadUrl: '/api/v1/modules/download/module_hider',
    sha256Checksum: crypto.createHash('sha256').update('hollow_module_hider_v3').digest('hex'),
    signatureB64: crypto.createHmac('sha256', JWT_SECRET).update('module_hider:v3').digest('base64'),
    isEnabled: true
  },
  {
    moduleId: 'module_isolation',
    name: 'Storage Shield & Virtual Vault',
    description: 'FUSE root volume mounting, per-app sandboxed virtual drives, and AES-256 encrypted media vault.',
    version: 4,
    minCoreVersion: 1,
    payloadUrl: '/api/v1/modules/download/module_isolation',
    sha256Checksum: crypto.createHash('sha256').update('hollow_module_isolation_v4').digest('hex'),
    signatureB64: crypto.createHmac('sha256', JWT_SECRET).update('module_isolation:v4').digest('base64'),
    isEnabled: true
  },
  {
    moduleId: 'module_privacy',
    name: 'Hardware Spoofer & Privacy Profiles',
    description: 'Per-app IMEI/Android ID/DRM spoofing, Virtual GPS teleport, sensor scrubbing, and Root Deadman Switch.',
    version: 5,
    minCoreVersion: 1,
    payloadUrl: '/api/v1/modules/download/module_privacy',
    sha256Checksum: crypto.createHash('sha256').update('hollow_module_privacy_v5').digest('hex'),
    signatureB64: crypto.createHmac('sha256', JWT_SECRET).update('module_privacy:v5').digest('base64'),
    isEnabled: true
  },
  {
    moduleId: 'module_network',
    name: 'Proxy & V2Ray Suite',
    description: 'Built-in VLESS/VMess/Trojan/Shadowsocks & SOCKS5/HTTP routing with per-app exit servers and kill switch.',
    version: 2,
    minCoreVersion: 1,
    payloadUrl: '/api/v1/modules/download/module_network',
    sha256Checksum: crypto.createHash('sha256').update('hollow_module_network_v2').digest('hex'),
    signatureB64: crypto.createHmac('sha256', JWT_SECRET).update('module_network:v2').digest('base64'),
    isEnabled: true
  }
];

let isMongoConnected = false;

async function connectMongo() {
  if (!MONGODB_URI || MONGODB_URI.includes('<username>')) {
    console.warn('[Hollow Server] MONGODB_URI not configured yet. Set MONGODB_URI to your MongoDB Atlas URI.');
    return;
  }
  try {
    await mongoose.connect(MONGODB_URI);
    isMongoConnected = true;
    console.log('[Hollow Server] Connected to MongoDB Atlas successfully.');
    for (const mod of DEFAULT_MODULES) {
      await ModuleModel.findOneAndUpdate(
        { moduleId: mod.moduleId },
        mod,
        { upsert: true, new: true }
      );
    }
  } catch (err) {
    console.error('[Hollow Server] MongoDB Atlas connection error:', err.message);
  }
}
connectMongo();

// Anti-MITM AES-256-GCM Envelope Helper
function encryptEnvelope(plainObj) {
  const key = Buffer.from(PAYLOAD_HEX_KEY, 'hex');
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const jsonStr = JSON.stringify(plainObj);
  const encrypted = Buffer.concat([cipher.update(jsonStr, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  const payloadB64 = Buffer.concat([iv, encrypted, tag]).toString('base64');
  const signatureB64 = crypto.createHmac('sha256', JWT_SECRET).update(payloadB64).digest('base64');
  return { payload_enc: payloadB64, signature_b64: signatureB64, ts: Date.now() };
}

function decryptEnvelope(body) {
  if (!body || !body.payload_enc) return body;
  try {
    const key = Buffer.from(PAYLOAD_HEX_KEY, 'hex');
    const raw = Buffer.from(body.payload_enc, 'base64');
    const iv = raw.subarray(0, 12);
    const tag = raw.subarray(raw.length - 16);
    const ciphertext = raw.subarray(12, raw.length - 16);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(tag);
    const plain = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
    return JSON.parse(plain);
  } catch (e) {
    return body;
  }
}

function signLicenseToken(deviceDoc) {
  const validUntilMs = deviceDoc.licenseValidUntil
    ? new Date(deviceDoc.licenseValidUntil).getTime()
    : Date.now() + 30 * 24 * 3600 * 1000;
  const payload = {
    sub: deviceDoc.username,
    email: deviceDoc.email,
    hw: deviceDoc.hardwareFingerprint,
    status: deviceDoc.status,
    modules: deviceDoc.allowedModules || [],
    expMs: validUntilMs,
    issuedAtMs: Date.now()
  };
  const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '365d' });
  const hmacSig = crypto
    .createHmac('sha256', JWT_SECRET)
    .update(`${deviceDoc.hardwareFingerprint}:${deviceDoc.status}:${validUntilMs}:${(deviceDoc.allowedModules || []).join(',')}`)
    .digest('base64');
  return { token, hmacSig, validUntilMs };
}

function requireAdminAuth(req, res, next) {
  const authHeader = req.headers.authorization || '';
  const token = authHeader.replace('Bearer ', '').trim();
  if (!token) return res.status(401).json({ error: 'Missing admin token' });
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    if (decoded.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
    req.admin = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Invalid or expired admin token' });
  }
}

// ============================================================================
// 1. CLIENT ENDPOINTS (Anti-MITM Encrypted Registration, Approval Check, Heartbeat)
// ============================================================================

app.post('/api/v1/auth/register', async (req, res) => {
  try {
    const data = decryptEnvelope(req.body);
    const {
      username,
      email,
      password,
      hardwareFingerprint,
      socSerial,
      androidId,
      storageCid,
      deviceModel,
      clientVersion
    } = data;

    if (!username || !email || !password || !hardwareFingerprint) {
      return res.status(400).json({ error: 'Username, email, password, and hardware fingerprint are required.' });
    }

    const existingDevice = await Device.findOne({ hardwareFingerprint });
    if (existingDevice && existingDevice.status === 'banned') {
      return res.status(403).json(encryptEnvelope({
        status: 'banned',
        banReason: existingDevice.banReason || 'Hardware fingerprint permanently blacklisted by Administrator.',
        wipeRootVault: true
      }));
    }

    const passwordHash = await bcrypt.hash(password, 10);
    let user = await User.findOne({ $or: [{ email: email.toLowerCase() }, { username }] });
    if (!user) {
      user = await User.create({
        username,
        email: email.toLowerCase(),
        passwordHash,
        role: 'user'
      });
    }

    const device = await Device.findOneAndUpdate(
      { hardwareFingerprint },
      {
        userId: user._id,
        username,
        email: email.toLowerCase(),
        hardwareFingerprint,
        socSerial: socSerial || '',
        androidId: androidId || '',
        storageCid: storageCid || '',
        deviceModel: deviceModel || 'Android Root Device',
        clientVersion: clientVersion || '1.0.0',
        lastHeartbeatAt: new Date(),
        lastIp: req.ip || ''
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );

    const signed = signLicenseToken(device);
    return res.json(encryptEnvelope({
      status: device.status,
      username: device.username,
      email: device.email,
      hardwareFingerprint: device.hardwareFingerprint,
      allowedModules: device.allowedModules,
      licenseValidUntil: signed.validUntilMs,
      licenseToken: signed.token,
      signature: signed.hmacSig
    }));
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/auth/status', async (req, res) => {
  try {
    const data = decryptEnvelope(req.body);
    const { hardwareFingerprint, monotonicUptimeTick } = data;
    if (!hardwareFingerprint) {
      return res.status(400).json({ error: 'Missing hardwareFingerprint' });
    }

    const device = await Device.findOne({ hardwareFingerprint });
    if (!device) {
      return res.status(404).json(encryptEnvelope({ status: 'unregistered' }));
    }

    if (device.status === 'banned') {
      return res.json(encryptEnvelope({
        status: 'banned',
        banReason: device.banReason || 'Device permanently banned by Administrator.',
        wipeRootVault: true
      }));
    }

    if (typeof monotonicUptimeTick === 'number' && monotonicUptimeTick > device.monotonicUptimeTick) {
      device.monotonicUptimeTick = monotonicUptimeTick;
    }
    device.lastHeartbeatAt = new Date();
    device.lastIp = req.ip || device.lastIp;
    await device.save();

    const signed = signLicenseToken(device);
    const modulesCatalog = await ModuleModel.find({
      moduleId: { $in: device.allowedModules },
      isEnabled: true
    });

    return res.json(encryptEnvelope({
      status: device.status,
      username: device.username,
      email: device.email,
      hardwareFingerprint: device.hardwareFingerprint,
      allowedModules: device.allowedModules,
      licenseValidUntil: signed.validUntilMs,
      licenseToken: signed.token,
      signature: signed.hmacSig,
      availableModules: modulesCatalog
    }));
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/auth/heartbeat', async (req, res) => {
  try {
    const data = decryptEnvelope(req.body);
    const { hardwareFingerprint, monotonicUptimeTick, incident } = data;

    if (incident && incident.triggerVector) {
      await SecurityIncident.create({
        hardwareFingerprint: hardwareFingerprint || 'unknown',
        username: data.username || '',
        triggerVector: incident.triggerVector,
        details: incident.details || ''
      });
    }

    const device = await Device.findOne({ hardwareFingerprint });
    if (!device) {
      return res.json(encryptEnvelope({ status: 'unregistered' }));
    }

    if (device.status === 'banned') {
      return res.json(encryptEnvelope({
        status: 'banned',
        banReason: device.banReason || 'Banned by Administrator',
        wipeRootVault: true
      }));
    }

    device.lastHeartbeatAt = new Date();
    device.lastIp = req.ip || device.lastIp;
    if (typeof monotonicUptimeTick === 'number') {
      device.monotonicUptimeTick = Math.max(device.monotonicUptimeTick || 0, monotonicUptimeTick);
    }
    await device.save();

    const signed = signLicenseToken(device);
    return res.json(encryptEnvelope({
      status: device.status,
      allowedModules: device.allowedModules,
      licenseValidUntil: signed.validUntilMs,
      licenseToken: signed.token,
      signature: signed.hmacSig,
      serverTimestampMs: Date.now()
    }));
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.get('/api/v1/modules/download/:moduleId', async (req, res) => {
  try {
    const { moduleId } = req.params;
    const hw = req.headers['x-hollow-hw'] || '';
    const device = await Device.findOne({ hardwareFingerprint: hw });
    if (!device || device.status !== 'approved' || !device.allowedModules.includes(moduleId)) {
      return res.status(403).json({ error: 'Device not authorized for this module' });
    }
    const mod = DEFAULT_MODULES.find((m) => m.moduleId === moduleId);
    if (!mod) return res.status(404).json({ error: 'Module not found' });

    const bundlePayload = {
      moduleId: mod.moduleId,
      name: mod.name,
      version: mod.version,
      boundHardwareFingerprint: hw,
      issuedAtMs: Date.now(),
      dexHeaderMagic: 'dex\n039\0',
      sha256Checksum: mod.sha256Checksum,
      signatureB64: mod.signatureB64
    };
    return res.json(encryptEnvelope(bundlePayload));
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

// ============================================================================
// 2. ADMIN WEB DASHBOARD ENDPOINTS
// ============================================================================

app.post('/api/v1/admin/login', async (req, res) => {
  const { username, password } = req.body || {};
  if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
    const token = jwt.sign({ username, role: 'admin' }, JWT_SECRET, { expiresIn: '24h' });
    return res.json({ token, username, mongoConnected: isMongoConnected });
  }
  return res.status(401).json({ error: 'Invalid administrator credentials' });
});

app.get('/api/v1/admin/overview', requireAdminAuth, async (req, res) => {
  try {
    const devices = await Device.find().sort({ createdAt: -1 }).lean();
    const incidents = await SecurityIncident.find().sort({ timestamp: -1 }).limit(50).lean();
    const modules = await ModuleModel.find().lean();
    return res.json({
      mongoConnected: isMongoConnected,
      devices,
      modules: modules.length ? modules : DEFAULT_MODULES,
      incidents
    });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/admin/devices/approve', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, validityDays, allowedModules } = req.body;
    const days = Number(validityDays) || 30;
    const validUntil = new Date(Date.now() + days * 24 * 3600 * 1000);
    const modules = Array.isArray(allowedModules)
      ? allowedModules
      : ['module_hider', 'module_isolation', 'module_privacy', 'module_network'];

    const updated = await Device.findOneAndUpdate(
      { hardwareFingerprint },
      {
        status: 'approved',
        allowedModules: modules,
        licenseValidUntil: validUntil,
        banReason: ''
      },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Device not found' });
    return res.json({ message: 'Device approved and modules granted', device: updated });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/admin/devices/update-modules', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, allowedModules } = req.body;
    const updated = await Device.findOneAndUpdate(
      { hardwareFingerprint },
      { allowedModules: Array.isArray(allowedModules) ? allowedModules : [] },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Device not found' });
    return res.json({ message: 'Permitted modules updated', device: updated });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/admin/devices/ban', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, banReason } = req.body;
    const updated = await Device.findOneAndUpdate(
      { hardwareFingerprint },
      {
        status: 'banned',
        allowedModules: [],
        banReason: banReason || 'Permanently banned by Administrator'
      },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Device not found' });
    return res.json({ message: 'Device permanently banned', device: updated });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.post('/api/v1/admin/devices/revoke', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint } = req.body;
    const updated = await Device.findOneAndUpdate(
      { hardwareFingerprint },
      { status: 'revoked', allowedModules: [] },
      { new: true }
    );
    if (!updated) return res.status(404).json({ error: 'Device not found' });
    return res.json({ message: 'Device license revoked', device: updated });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`[Hollow Cloud Admin & Licensing Server] Listening on port ${PORT}`);
});
