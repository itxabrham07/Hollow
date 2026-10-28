const mongoose = require('mongoose');

const AuditLogSchema = new mongoose.Schema({
  event: { type: String, required: true },
  hardwareFingerprint: { type: String, default: '', index: true },
  username: { type: String, default: '' },
  details: { type: String, default: '' },
  ipAddress: { type: String, default: '127.0.0.1' },
  timestamp: { type: Date, default: Date.now, index: true }
});

module.exports = mongoose.model('AuditLog', AuditLogSchema);
