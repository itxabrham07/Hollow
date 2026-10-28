const mongoose = require('mongoose');

const LicenseSchema = new mongoose.Schema({
  hardwareFingerprint: { type: String, required: true, index: true },
  licenseToken: { type: String, required: true },
  ed25519Signature: { type: String, default: '' },
  allowedModules: { type: [String], default: [] },
  issuedAtMs: { type: Number, required: true },
  validUntilMs: { type: Number, required: true },
  revoked: { type: Boolean, default: false, index: true },
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('License', LicenseSchema);
