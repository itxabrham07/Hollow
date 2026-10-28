const mongoose = require('mongoose');

const ModuleSchema = new mongoose.Schema({
  moduleId: { type: String, required: true, unique: true, index: true },
  name: { type: String, default: '' },
  displayName: { type: String, default: '' },
  description: { type: String, default: '' },
  version: { type: Number, default: 1 },
  minCoreVersion: { type: Number, default: 1 },
  payloadUrl: { type: String, default: '' },
  sha256Checksum: { type: String, default: '' },
  signatureB64: { type: String, default: '' },
  isEnabled: { type: Boolean, default: true },
  updatedAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Module', ModuleSchema);
