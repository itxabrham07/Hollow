/**
 * Hollow Cloud Server — Product Web Platform & Admin Console (Node.js + Express + MongoDB Atlas)
 *
 * Routes:
 * - GET /        -> Public Product Page (web/public/index.html)
 * - GET /admin   -> Dedicated Administrator Console (web/public/admin.html)
 * - POST /api/v1/auth/register -> Register device (stores in MongoDB + in-memory mirror)
 * - POST /api/v1/auth/login    -> Sign in existing account & bind hardware fingerprint
 * - POST /api/v1/auth/status   -> Poll approval status & auto-register pending device if missing
 * - GET  /api/v1/admin/overview -> Live device queue, module catalog & audit log for Admin Console
 */

try { require('dotenv').config(); } catch (_) {}
const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const mongoose = require('mongoose');

const Device = require('./models/Device');
const License = require('./models/License');
const AuditLog = require('./models/AuditLog');
const Module = require('./models/Module');
const SecurityIncident = require('./models/SecurityIncident');

const app = express();

// CORS support for mobile & web clients
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'Content-Type, Authorization, X-Hollow-HW, X-Hollow-Signature, X-Hollow-Uptime'
  );
  if (req.method === 'OPTIONS') {
    return res.sendStatus(204);
  }
  return next();
});

app.use(express.json({ limit: '5mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// Dedicated Admin Console route (/admin) — completely separated from public product page (/)
app.get(['/admin', '/admin/*'], (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

const PORT = process.env.PORT || 8080;
const JWT_SECRET = process.env.JWT_SECRET || 'hollow_persistent_admin_jwt_secret_2026';
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'hollow_admin_2025';
const MONGODB_URI = process.env.MONGODB_URI || '';

// ---------------------------------------------------------------------------
// 1. MongoDB Atlas Connection + Resilient Dual Store (Mongo + Memory Mirror)
// ---------------------------------------------------------------------------
let mongoConnected = false;

const memoryStore = {
  devices: [],
  licenses: new Map(),
  auditLogs: [],
  securityIncidents: []
};

function isDbLive() {
  return mongoConnected && mongoose.connection.readyState === 1;
}

function normalizeStatus(rawStatus) {
  const s = String(rawStatus || '').trim().toUpperCase();
  if (s === 'APPROVED') return 'APPROVED';
  if (s === 'BANNED' || s === 'REVOKED') return 'BANNED';
  if (s === 'UNREGISTERED') return 'UNREGISTERED';
  return 'PENDING_APPROVAL';
}

function normalizeDeviceDoc(doc) {
  if (!doc) return null;
  const plain = typeof doc.toObject === 'function' ? doc.toObject() : { ...doc };
  plain.status = normalizeStatus(plain.status);
  plain.username = plain.username || 'User';
  plain.email = plain.email || '';
  plain.deviceModel = plain.deviceModel || 'Android Device';
  plain.allowedModules = Array.isArray(plain.allowedModules) ? plain.allowedModules : [];
  plain.banReason = plain.banReason || '';
  plain.registeredAt = plain.registeredAt || plain.createdAt || new Date();
  plain.updatedAt = plain.updatedAt || plain.lastHeartbeatAt || new Date();
  return plain;
}

async function connectMongoDB() {
  if (!MONGODB_URI) {
    console.log('[MongoDB] MONGODB_URI not set — running with live in-memory store.');
    return;
  }
  try {
    await mongoose.connect(MONGODB_URI, {
      serverSelectionTimeoutMS: 6000
    });
    mongoConnected = true;
    console.log('[MongoDB] Connected to MongoDB Atlas successfully.');
  } catch (err) {
    mongoConnected = false;
    console.error('[MongoDB] Connection failed, using in-memory store fallback:', err.message);
  }
}
connectMongoDB();

/**
 * Dual-writes a device record to both `memoryStore.devices` and MongoDB (when connected)
 * using `findOneAndUpdate` with `upsert: true` so Mongoose enum/required validation can never drop a device.
 */
async function upsertDeviceRecord(hardwareFingerprint, fields) {
  const now = new Date();
  let memDev = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
  if (!memDev) {
    memDev = {
      hardwareFingerprint,
      username: fields.username || 'user',
      email: fields.email || '',
      passwordHash: fields.passwordHash || '',
      socSerial: fields.socSerial || '',
      androidId: fields.androidId || '',
      storageCid: fields.storageCid || '',
      deviceModel: fields.deviceModel || 'Android Device',
      status: normalizeStatus(fields.status || 'PENDING_APPROVAL'),
      allowedModules: Array.isArray(fields.allowedModules) ? fields.allowedModules : [],
      banReason: fields.banReason || '',
      highWaterWallClockMs: fields.highWaterWallClockMs || Date.now(),
      lastElapsedRealtimeMs: fields.lastElapsedRealtimeMs || 0,
      registeredAt: now,
      updatedAt: now
    };
    memoryStore.devices.unshift(memDev);
  } else {
    Object.entries(fields).forEach(([k, v]) => {
      if (v !== undefined) {
        memDev[k] = k === 'status' ? normalizeStatus(v) : v;
      }
    });
    memDev.updatedAt = now;
  }

  if (isDbLive()) {
    try {
      const updateSet = {
        ...fields,
        hardwareFingerprint,
        status: normalizeStatus(fields.status || memDev.status),
        updatedAt: now,
        lastHeartbeatAt: now
      };
      const dbDoc = await Device.findOneAndUpdate(
        { hardwareFingerprint },
        {
          $set: updateSet,
          $setOnInsert: { registeredAt: now, createdAt: now }
        },
        { upsert: true, new: true, runValidators: false }
      );
      if (dbDoc) {
        const normalized = normalizeDeviceDoc(dbDoc);
        Object.assign(memDev, normalized);
      }
    } catch (err) {
      console.error('[DeviceUpsert] MongoDB write fallback to memoryStore:', err.message);
    }
  }

  return normalizeDeviceDoc(memDev);
}

async function findDeviceRecord(hardwareFingerprint) {
  if (!hardwareFingerprint) return null;
  let dbDoc = null;
  if (isDbLive()) {
    try {
      dbDoc = await Device.findOne({ hardwareFingerprint }).lean();
    } catch (err) {
      console.error('[DeviceFind] MongoDB read error:', err.message);
    }
  }
  const memDoc = memoryStore.devices.find((d) => d.hardwareFingerprint === hardwareFingerprint);
  if (dbDoc) {
    const normalized = normalizeDeviceDoc(dbDoc);
    if (!memDoc) {
      memoryStore.devices.unshift(normalized);
    } else {
      Object.assign(memDoc, normalized);
    }
    return normalized;
  }
  return memDoc ? normalizeDeviceDoc(memDoc) : null;
}

async function listAllDevicesMerged() {
  const byFp = new Map();
  for (const mem of memoryStore.devices) {
    if (mem && mem.hardwareFingerprint) {
      byFp.set(mem.hardwareFingerprint, normalizeDeviceDoc(mem));
    }
  }
  if (isDbLive()) {
    try {
      const dbList = await Device.find().sort({ updatedAt: -1, createdAt: -1 }).lean();
      for (const d of dbList) {
        if (d && d.hardwareFingerprint) {
          byFp.set(d.hardwareFingerprint, normalizeDeviceDoc(d));
        }
      }
    } catch (err) {
      console.error('[DeviceList] MongoDB list error:', err.message);
    }
  }
  return Array.from(byFp.values()).sort(
    (a, b) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime()
  );
}

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
// 2. Cryptographic Keys (Ed25519 Module Signing + ECDH / AES-256-GCM Envelope)
// ---------------------------------------------------------------------------
const CERTS_DIR = path.resolve(__dirname, '..', 'certs');
const PRIV_KEY_PATH = path.join(CERTS_DIR, 'module_ed25519_private.pem');
const PUB_KEY_PATH = path.join(CERTS_DIR, 'module_ed25519_public.pem');

const { edPublicKey, edPrivateKey } = (() => {
  try {
    if (process.env.MODULE_ED25519_PRIVATE_KEY_PEM && process.env.MODULE_ED25519_PUBLIC_KEY_PEM) {
      return {
        edPrivateKey: crypto.createPrivateKey(process.env.MODULE_ED25519_PRIVATE_KEY_PEM),
        edPublicKey: crypto.createPublicKey(process.env.MODULE_ED25519_PUBLIC_KEY_PEM)
      };
    }
    if (fs.existsSync(PRIV_KEY_PATH) && fs.existsSync(PUB_KEY_PATH)) {
      return {
        edPrivateKey: crypto.createPrivateKey(fs.readFileSync(PRIV_KEY_PATH, 'utf8')),
        edPublicKey: crypto.createPublicKey(fs.readFileSync(PUB_KEY_PATH, 'utf8'))
      };
    }
    if (fs.existsSync(PUB_KEY_PATH)) {
      return {
        edPrivateKey: null,
        edPublicKey: crypto.createPublicKey(fs.readFileSync(PUB_KEY_PATH, 'utf8'))
      };
    }
  } catch (err) {
    console.error('[Crypto] Failed to load Ed25519 keys from certs/:', err.message);
  }
  const generated = crypto.generateKeyPairSync('ed25519');
  return { edPublicKey: generated.publicKey, edPrivateKey: generated.privateKey };
})();

const serverEcdh = crypto.createECDH('prime256v1');
serverEcdh.generateKeys();

const CLIENT_PAYLOAD_HEX_KEY = '4f8b9a2c1d7e6f5031425364758697a8b9c0d1e2f30415263748596a7b8c9d0e';
const CLIENT_HMAC_SECRET = 'hollow_ultra_secret_jwt_signing_key_2026';

// ---------------------------------------------------------------------------
// 3. Dynamic Module Catalog Loader (reads ONLY real compiled DEX from ../modules/)
// ---------------------------------------------------------------------------
const REPO_MODULES_DIR = path.resolve(__dirname, '..', 'modules');

function signModuleSha256(sha256Checksum, existingSignatureB64) {
  const msgBuf = Buffer.from(sha256Checksum, 'utf8');
  if (existingSignatureB64 && edPublicKey) {
    try {
      const sigBuf = Buffer.from(existingSignatureB64, 'base64');
      if (crypto.verify(null, msgBuf, edPublicKey, sigBuf)) {
        return existingSignatureB64;
      }
    } catch (_e) {}
  }
  if (edPrivateKey) {
    return crypto.sign(null, msgBuf, edPrivateKey).toString('base64');
  }
  return existingSignatureB64 || '';
}

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

        if (!fs.existsSync(manifestFile) || !fs.existsSync(payloadFile)) continue;

        const rawPayload = fs.readFileSync(payloadFile);
        if (rawPayload.length < 8 || rawPayload.subarray(0, 4).toString('ascii') !== 'dex\n') {
          continue;
        }

        const manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
        const moduleId = manifest.moduleId || entry.name;
        const sha256Checksum = crypto.createHash('sha256').update(rawPayload).digest('hex');
        const signatureB64 = signModuleSha256(sha256Checksum, manifest.signatureB64);

        catalog[moduleId] = {
          ...manifest,
          moduleId,
          displayName: manifest.displayName || manifest.name || moduleId,
          sha256: sha256Checksum,
          sha256Checksum,
          signatureB64,
          sizeBytes: rawPayload.length,
          sizeKb: Math.max(1, Math.round(rawPayload.length / 1024)),
          dexPayloadBase64: rawPayload.toString('base64')
        };

        if (isDbLive()) {
          Module.findOneAndUpdate(
            { moduleId },
            {
              $set: {
                moduleId,
                name: manifest.displayName || manifest.name || moduleId,
                displayName: manifest.displayName || manifest.name || moduleId,
                description: manifest.description || manifest.cardSubtitle || '',
                version: Number(manifest.version) || 1,
                minCoreVersion: Number(manifest.minCoreVersion) || 1,
                payloadUrl: `/api/v1/modules/download/${moduleId}`,
                sha256Checksum,
                signatureB64,
                isEnabled: true
              }
            },
            { upsert: true, runValidators: false }
          ).catch(() => {});
        }
      }
    }
  } catch (err) {
    console.error('[Modules] Error reading /modules directory:', err.message);
  }
  return catalog;
}

// ---------------------------------------------------------------------------
// 4. Envelope Encryption Helpers (HollowSecureClient AES-256-GCM + ECDH P-256)
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
  if (!envelope) return {};

  // Support HollowSecureClient AES-256-GCM envelope (`payload_enc`)
  if (typeof envelope.payload_enc === 'string') {
    try {
      const combined = Buffer.from(envelope.payload_enc, 'base64');
      const iv = combined.subarray(0, 12);
      const tag = combined.subarray(combined.length - 16);
      const ciphertext = combined.subarray(12, combined.length - 16);
      const keyBuf = Buffer.from(CLIENT_PAYLOAD_HEX_KEY, 'hex');
      const decipher = crypto.createDecipheriv('aes-256-gcm', keyBuf, iv);
      decipher.setAuthTag(tag);
      const plainBuf = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
      return JSON.parse(plainBuf.toString('utf8'));
    } catch (_e) {
      // Fallback to top-level plain fields if present on envelope
      return envelope;
    }
  }

  if (!envelope.encrypted || !envelope.clientEphemeralPub) {
    return envelope;
  }
  try {
    const aesKey = deriveSharedAesKey(envelope.clientEphemeralPub, envelope.nonce);
    const combined = Buffer.from(envelope.payload, 'base64');
    const iv = combined.subarray(0, 12);
    const tag = combined.subarray(combined.length - 16);
    const ciphertext = combined.subarray(12, combined.length - 16);

    const decipher = crypto.createDecipheriv('aes-256-gcm', aesKey, iv);
    decipher.setAuthTag(tag);
    const plainBuf = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
    return JSON.parse(plainBuf.toString('utf8'));
  } catch (_e) {
    return envelope;
  }
}

function signJsonPayload(obj) {
  if (!edPrivateKey) return '';
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

  if (req.body && typeof req.body.payload_enc === 'string') {
    const keyBuf = Buffer.from(CLIENT_PAYLOAD_HEX_KEY, 'hex');
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', keyBuf, iv);
    const plainBuf = Buffer.from(JSON.stringify(bodyWithSig), 'utf8');
    const encrypted = Buffer.concat([cipher.update(plainBuf), cipher.final()]);
    const tag = cipher.getAuthTag();
    const combined = Buffer.concat([iv, encrypted, tag]);
    const payloadB64 = combined.toString('base64');
    const hmacB64 = crypto
      .createHmac('sha256', Buffer.from(CLIENT_HMAC_SECRET, 'utf8'))
      .update(payloadB64)
      .digest('base64');

    return res.json({
      ...bodyWithSig,
      payload_enc: payloadB64,
      signature_b64: hmacB64,
      ts: Date.now()
    });
  }

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
// 5. Public & Catalog Endpoints
// ---------------------------------------------------------------------------
app.get('/api/v1/health', async (_req, res) => {
  const catalog = loadModulesFromRepo();
  res.json({
    ok: true,
    service: 'Hollow Privacy Layer',
    ed25519PublicKey: edPublicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    serverEcdhPublicKey: serverEcdh.getPublicKey('base64'),
    modulesHosted: Object.keys(catalog),
    serverTimeMs: Date.now()
  });
});

app.get('/api/v1/public/catalog', async (_req, res) => {
  const catalog = loadModulesFromRepo();
  const modules = Object.values(catalog).map(({ dexPayloadBase64, ...meta }) => meta);
  const allDevs = await listAllDevicesMerged();
  res.json({
    ok: true,
    stats: {
      modulesCount: modules.length,
      totalDevices: allDevs.length,
      approvedDevices: allDevs.filter((d) => d.status === 'APPROVED').length
    },
    modules
  });
});

// ---------------------------------------------------------------------------
// 6. Client Registration, Sign-In, Approval Check & Module Download
// ---------------------------------------------------------------------------
async function handleRegisterRequest(req, res) {
  try {
    const body = decryptEnvelope(req.body);
    const hardwareFingerprint = String(
      body.hardwareFingerprint || req.headers['x-hollow-hw'] || ''
    ).trim();
    const username = String(body.username || body.identifier || 'user').trim();
    const email = String(body.email || '').trim().toLowerCase();
    const password = body.password || '';
    const socSerial = body.socSerial || '';
    const androidId = body.androidId || '';
    const storageCid = body.storageCid || '';
    const deviceModel = body.deviceModel || 'Android Device';

    if (!hardwareFingerprint) {
      return sendClientResponse(req, res, {
        ok: false,
        error: 'Missing hardwareFingerprint'
      });
    }

    const existing = await findDeviceRecord(hardwareFingerprint);
    if (existing && existing.status === 'BANNED') {
      await recordAudit(
        'REGISTER_BLOCKED_BANNED',
        hardwareFingerprint,
        username,
        'Banned device attempted re-registration',
        req
      );
      return sendClientResponse(req, res, {
        ok: true,
        status: 'BANNED',
        banReason: existing.banReason,
        allowedModules: [],
        message: `Device permanently restricted: ${existing.banReason}`
      });
    }

    const preservedStatus = existing ? existing.status : 'PENDING_APPROVAL';
    const preservedModules = existing ? existing.allowedModules : [];

    const record = await upsertDeviceRecord(hardwareFingerprint, {
      username: username || (existing && existing.username) || 'user',
      email: email || (existing && existing.email) || '',
      passwordHash: password ? hashPassword(password) : (existing && existing.passwordHash) || '',
      socSerial: socSerial || (existing && existing.socSerial) || '',
      androidId: androidId || (existing && existing.androidId) || '',
      storageCid: storageCid || (existing && existing.storageCid) || '',
      deviceModel: deviceModel || (existing && existing.deviceModel) || 'Android Device',
      status: preservedStatus,
      allowedModules: preservedModules
    });

    await recordAudit(
      existing ? 'REGISTER_UPDATED' : 'REGISTER_PENDING',
      hardwareFingerprint,
      record.username,
      `Registered ${record.deviceModel} (${record.email || 'anonymous'}) — status: ${record.status}`,
      req
    );

    let activeLicense = memoryStore.licenses.get(hardwareFingerprint);
    if (isDbLive()) {
      try {
        const dbLic = await License.findOne({ hardwareFingerprint, revoked: false })
          .sort({ issuedAtMs: -1 })
          .lean();
        if (dbLic) activeLicense = dbLic;
      } catch (_e) {}
    }

    return sendClientResponse(req, res, {
      ok: true,
      status: record.status,
      username: record.username,
      email: record.email,
      hardwareFingerprint: record.hardwareFingerprint,
      allowedModules: record.status === 'APPROVED' ? record.allowedModules : [],
      licenseToken: activeLicense ? activeLicense.licenseToken : '',
      validUntilMs: activeLicense ? activeLicense.validUntilMs : 0,
      serverTimeMs: Date.now(),
      message:
        record.status === 'APPROVED'
          ? 'Device approved! You can now install your authorized modules.'
          : 'Registration submitted. Waiting for Administrator approval.'
    });
  } catch (err) {
    console.error('[/api/v1/auth/register] Error:', err);
    return sendClientResponse(req, res, { ok: false, error: err.message });
  }
}

app.post('/api/v1/auth/register', handleRegisterRequest);
app.post('/api/v1/device/register', handleRegisterRequest);

app.post('/api/v1/auth/login', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const {
      identifier,
      username,
      email,
      password,
      hardwareFingerprint,
      socSerial,
      androidId,
      storageCid,
      deviceModel
    } = body;

    const lookup = String(identifier || username || email || '').trim();
    const pwHash = hashPassword(password);
    const hw = String(hardwareFingerprint || req.headers['x-hollow-hw'] || '').trim();

    if (!lookup && !hw) {
      return sendClientResponse(req, res, {
        ok: false,
        error: 'Please enter your username or email.'
      });
    }

    const allDevices = await listAllDevicesMerged();
    let matched = allDevices.find(
      (d) =>
        (lookup && d.username && d.username.toLowerCase() === lookup.toLowerCase()) ||
        (lookup && d.email && d.email.toLowerCase() === lookup.toLowerCase()) ||
        (hw && d.hardwareFingerprint === hw)
    );

    // If user signs in on a fresh device not yet in DB, auto-register as PENDING_APPROVAL so it shows in Admin Console!
    if (!matched && hw) {
      matched = await upsertDeviceRecord(hw, {
        username: lookup || 'user',
        email: lookup.includes('@') ? lookup.toLowerCase() : '',
        passwordHash: password ? pwHash : '',
        socSerial: socSerial || '',
        androidId: androidId || '',
        storageCid: storageCid || '',
        deviceModel: deviceModel || 'Android Device',
        status: 'PENDING_APPROVAL',
        allowedModules: []
      });
      await recordAudit(
        'REGISTER_VIA_LOGIN',
        hw,
        matched.username,
        `New device signed in & queued for approval (${matched.deviceModel})`,
        req
      );
    }

    if (!matched) {
      return sendClientResponse(req, res, {
        ok: false,
        error: 'Account not found. Please use Create Account to register this device.'
      });
    }

    if (matched.passwordHash && password && matched.passwordHash !== pwHash) {
      return sendClientResponse(req, res, {
        ok: false,
        error: 'Invalid password for this account.'
      });
    }

    const targetFp = hw || matched.hardwareFingerprint;
    const record = await upsertDeviceRecord(targetFp, {
      username: matched.username,
      email: matched.email,
      passwordHash: matched.passwordHash || (password ? pwHash : ''),
      deviceModel: deviceModel || matched.deviceModel,
      status: matched.status,
      allowedModules: matched.allowedModules,
      banReason: matched.banReason
    });

    let activeLicense = memoryStore.licenses.get(targetFp);
    if (isDbLive()) {
      try {
        const dbLic = await License.findOne({ hardwareFingerprint: targetFp, revoked: false })
          .sort({ issuedAtMs: -1 })
          .lean();
        if (dbLic) activeLicense = dbLic;
      } catch (_e) {}
    }

    await recordAudit(
      'USER_SIGN_IN',
      record.hardwareFingerprint,
      record.username,
      `Signed in on ${record.deviceModel}`,
      req
    );

    return sendClientResponse(req, res, {
      ok: true,
      status: record.status,
      username: record.username,
      email: record.email,
      hardwareFingerprint: record.hardwareFingerprint,
      allowedModules: record.status === 'APPROVED' ? record.allowedModules : [],
      licenseToken: activeLicense ? activeLicense.licenseToken : '',
      validUntilMs: activeLicense ? activeLicense.validUntilMs : 0,
      banReason: record.banReason || '',
      serverTimeMs: Date.now(),
      message:
        record.status === 'APPROVED'
          ? 'Signed in! Your authorized modules are being synchronized.'
          : 'Signed in. Waiting for Administrator approval.'
    });
  } catch (err) {
    return sendClientResponse(req, res, { ok: false, error: err.message });
  }
});

async function handleStatusRequest(req, res) {
  try {
    const body = decryptEnvelope(req.body);
    const hardwareFingerprint = String(
      body.hardwareFingerprint || req.headers['x-hollow-hw'] || ''
    ).trim();
    const username = String(body.username || '').trim();
    const email = String(body.email || '').trim().toLowerCase();
    const socSerial = body.socSerial || '';
    const androidId = body.androidId || '';
    const storageCid = body.storageCid || '';
    const deviceModel = body.deviceModel || 'Android Device';
    const elapsedRealtimeMs = Number(body.elapsedRealtimeMs || 0);

    let record = await findDeviceRecord(hardwareFingerprint);

    // Auto-register any pending device that polls status so it ALWAYS appears in Admin Console
    if (!record && hardwareFingerprint && username) {
      record = await upsertDeviceRecord(hardwareFingerprint, {
        username,
        email,
        socSerial,
        androidId,
        storageCid,
        deviceModel,
        status: 'PENDING_APPROVAL',
        allowedModules: [],
        highWaterWallClockMs: Date.now(),
        lastElapsedRealtimeMs: elapsedRealtimeMs
      });
      await recordAudit(
        'REGISTER_PENDING',
        hardwareFingerprint,
        username,
        `Synced pending device registration (${record.deviceModel})`,
        req
      );
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
    record = await upsertDeviceRecord(hardwareFingerprint, {
      highWaterWallClockMs: Math.max(nowMs, record.highWaterWallClockMs || 0),
      lastElapsedRealtimeMs: elapsedRealtimeMs
    });

    let activeLicense = memoryStore.licenses.get(hardwareFingerprint);
    if (isDbLive()) {
      try {
        const dbLic = await License.findOne({ hardwareFingerprint, revoked: false })
          .sort({ issuedAtMs: -1 })
          .lean();
        if (dbLic) activeLicense = dbLic;
      } catch (_e) {}
    }

    return sendClientResponse(req, res, {
      ok: true,
      status: record.status,
      username: record.username,
      email: record.email || '',
      hardwareFingerprint: record.hardwareFingerprint,
      allowedModules: record.status === 'APPROVED' ? record.allowedModules : [],
      licenseToken: activeLicense ? activeLicense.licenseToken : '',
      issuedAtMs: activeLicense ? activeLicense.issuedAtMs : nowMs,
      validUntilMs: activeLicense ? activeLicense.validUntilMs : 0,
      banReason: record.banReason || '',
      serverTimeMs: nowMs
    });
  } catch (err) {
    return sendClientResponse(req, res, { ok: false, error: err.message });
  }
}

app.post('/api/v1/auth/status', handleStatusRequest);
app.post('/api/v1/license/verify', handleStatusRequest);
app.post('/api/v1/device/heartbeat', handleStatusRequest);

async function handleModuleDownload(req, res, hardwareFingerprint, moduleId) {
  const record = await findDeviceRecord(hardwareFingerprint);

  if (!record || record.status !== 'APPROVED') {
    return res.status(403).json({
      ok: false,
      error:
        record?.status === 'BANNED'
          ? `Device is restricted: ${record.banReason}`
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
    return res.status(404).json({ ok: false, error: `Unknown or unbuilt moduleId: ${moduleId}` });
  }

  await recordAudit(
    'MODULE_DOWNLOADED',
    hardwareFingerprint,
    record.username,
    `Downloaded & Ed25519-signed ${moduleId} (v${mod.version}, sha256=${mod.sha256Checksum.slice(0, 12)}...)`,
    req
  );

  return sendClientResponse(req, res, {
    ok: true,
    ...mod,
    hardwareBoundTo: hardwareFingerprint,
    issuedAtMs: Date.now()
  });
}

app.post('/api/v1/modules/download', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const hardwareFingerprint = body.hardwareFingerprint || req.headers['x-hollow-hw'] || '';
    const moduleId = body.moduleId || '';
    return await handleModuleDownload(req, res, hardwareFingerprint, moduleId);
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.get('/api/v1/modules/download/:moduleId', async (req, res) => {
  try {
    const hardwareFingerprint = req.headers['x-hollow-hw'] || req.query.hw || '';
    const moduleId = req.params.moduleId || '';
    return await handleModuleDownload(req, res, hardwareFingerprint, moduleId);
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/security/incident', async (req, res) => {
  try {
    const body = decryptEnvelope(req.body);
    const hardwareFingerprint = body.hardwareFingerprint || req.headers['x-hollow-hw'] || 'UNKNOWN_HW';
    const triggerVector = body.triggerVector || 'MODULE_SIGNATURE_OR_CHECKSUM_FAILURE';
    const moduleId = body.moduleId || '';
    const details = body.details || '';

    const incidentDoc = {
      hardwareFingerprint,
      ipAddress: req.ip || req.connection?.remoteAddress || '127.0.0.1',
      triggerVector: `${triggerVector}${moduleId ? ` (${moduleId})` : ''}`,
      details: details || triggerVector,
      actionTaken: 'REJECTED_AND_PURGED_MODULE_FROM_VAULT',
      timestamp: new Date()
    };

    memoryStore.securityIncidents.unshift(incidentDoc);
    if (memoryStore.securityIncidents.length > 100) memoryStore.securityIncidents.pop();

    if (isDbLive()) {
      await SecurityIncident.create(incidentDoc).catch(() => {});
    }

    await recordAudit(
      'SECURITY_INCIDENT',
      hardwareFingerprint,
      '',
      `[${triggerVector}] ${moduleId}: ${details}`,
      req
    );

    return sendClientResponse(req, res, {
      ok: true,
      recorded: true,
      actionTaken: incidentDoc.actionTaken
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
    const token = jwt.sign({ username, role: 'SUPER_ADMIN' }, JWT_SECRET, { expiresIn: '24h' });
    await recordAudit('ADMIN_LOGIN', 'ADMIN_CONSOLE', username, 'Administrator signed in to Web Portal', req);
    return res.json({ ok: true, token, username });
  }
  return res.status(401).json({ ok: false, error: 'Invalid administrator credentials' });
});

app.get('/api/v1/admin/overview', requireAdminAuth, async (_req, res) => {
  try {
    const devicesList = await listAllDevicesMerged();
    let recentLogs = [...memoryStore.auditLogs];

    if (isDbLive()) {
      try {
        const dbLogs = await AuditLog.find().sort({ timestamp: -1 }).limit(40).lean();
        if (dbLogs && dbLogs.length > 0) {
          recentLogs = dbLogs;
        }
      } catch (_e) {}
    }

    const catalog = loadModulesFromRepo();
    const modulesList = Object.values(catalog).map(({ dexPayloadBase64, ...rest }) => rest);

    res.json({
      ok: true,
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

app.get('/api/v1/admin/devices', requireAdminAuth, async (_req, res) => {
  try {
    const devices = await listAllDevicesMerged();
    res.json({ ok: true, devices });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/approve', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, allowedModules, validityDays } = req.body || {};
    const catalog = loadModulesFromRepo();
    const defaultMods = Object.keys(catalog);
    const days = Number(validityDays || 365);
    const nowMs = Date.now();
    const validUntilMs = nowMs + days * 86400000;
    const mods =
      Array.isArray(allowedModules) && allowedModules.length > 0 ? allowedModules : defaultMods;

    const existing = await findDeviceRecord(hardwareFingerprint);
    if (!existing) {
      return res.status(404).json({ ok: false, error: 'Device not found' });
    }

    const licensePayload = {
      hardwareFingerprint,
      allowedModules: mods,
      issuedAtMs: nowMs,
      validUntilMs
    };
    const signature = signJsonPayload(licensePayload);
    const licenseToken = jwt.sign(licensePayload, JWT_SECRET, { expiresIn: `${days}d` });

    const record = await upsertDeviceRecord(hardwareFingerprint, {
      status: 'APPROVED',
      allowedModules: mods,
      banReason: '',
      licenseValidUntil: new Date(validUntilMs)
    });

    const licObj = {
      hardwareFingerprint,
      licenseToken,
      ed25519Signature: signature,
      allowedModules: mods,
      issuedAtMs: nowMs,
      validUntilMs,
      revoked: false
    };
    memoryStore.licenses.set(hardwareFingerprint, licObj);

    if (isDbLive()) {
      try {
        await License.updateMany({ hardwareFingerprint }, { $set: { revoked: true } });
        await License.create(licObj);
      } catch (_e) {}
    }

    await recordAudit(
      'ADMIN_APPROVE',
      hardwareFingerprint,
      record.username,
      `Approved for [${mods.join(', ')}] (${days} days)`,
      req
    );
    return res.json({ ok: true, device: record, licenseToken });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/update-modules', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, allowedModules } = req.body || {};
    const mods = Array.isArray(allowedModules) ? allowedModules : [];

    const existing = await findDeviceRecord(hardwareFingerprint);
    if (!existing) {
      return res.status(404).json({ ok: false, error: 'Device not found' });
    }

    const record = await upsertDeviceRecord(hardwareFingerprint, {
      allowedModules: mods
    });

    await recordAudit(
      'ADMIN_UPDATE_MODULES',
      hardwareFingerprint,
      record.username,
      `Updated modules -> [${mods.join(', ')}]`,
      req
    );
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/ban', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint, banReason } = req.body || {};
    const existing = await findDeviceRecord(hardwareFingerprint);
    if (!existing) {
      return res.status(404).json({ ok: false, error: 'Device not found' });
    }

    const record = await upsertDeviceRecord(hardwareFingerprint, {
      status: 'BANNED',
      allowedModules: [],
      banReason: banReason || 'Access restricted by Administrator.'
    });

    memoryStore.licenses.delete(hardwareFingerprint);
    if (isDbLive()) {
      try {
        await License.updateMany({ hardwareFingerprint }, { $set: { revoked: true } });
      } catch (_e) {}
    }

    await recordAudit(
      'ADMIN_BAN_DEVICE',
      hardwareFingerprint,
      record.username,
      `RESTRICTED: ${record.banReason}`,
      req
    );
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

app.post('/api/v1/admin/devices/unban', requireAdminAuth, async (req, res) => {
  try {
    const { hardwareFingerprint } = req.body || {};
    const existing = await findDeviceRecord(hardwareFingerprint);
    if (!existing) {
      return res.status(404).json({ ok: false, error: 'Device not found' });
    }

    const record = await upsertDeviceRecord(hardwareFingerprint, {
      status: 'PENDING_APPROVAL',
      banReason: ''
    });

    await recordAudit(
      'ADMIN_UNBAN_DEVICE',
      hardwareFingerprint,
      record.username,
      'Cleared restriction -> moved to PENDING_APPROVAL',
      req
    );
    return res.json({ ok: true, device: record });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message });
  }
});

// Backwards-compatible route aliases
app.post('/api/v1/admin/license/issue', requireAdminAuth, (req, res, next) => {
  req.url = '/api/v1/admin/devices/approve';
  if (req.body && req.body.durationDays && !req.body.validityDays) {
    req.body.validityDays = req.body.durationDays;
  }
  app._router.handle(req, res, next);
});

app.post('/api/v1/admin/license/revoke', requireAdminAuth, (req, res, next) => {
  req.url = '/api/v1/admin/devices/ban';
  if (req.body && req.body.reason && !req.body.banReason) {
    req.body.banReason = req.body.reason;
  }
  app._router.handle(req, res, next);
});

app.listen(PORT, () => {
  console.log(`Hollow Web Platform & Admin Console listening on http://0.0.0.0:${PORT}`);
});
