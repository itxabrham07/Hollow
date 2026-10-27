const mongoose = require('mongoose');

const ModuleSchema = new mongoose.Schema({
  moduleId: { type: String, required: true, unique: true, index: true },
  name: { type: String, required: true },
  description: { type: String, required: true },
  version: { type: Number, default: 1 },
  minCoreVersion: { type: Number, default: 1 },
  payloadUrl: { type: String, required: true },
  sha256Checksum: { type: String, required: true },
  signatureB64: { type: String, required: true },
  isEnabled: { type: Boolean, default: true },
  updatedAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Module', ModuleSchema);
