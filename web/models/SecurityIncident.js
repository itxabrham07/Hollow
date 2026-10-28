const mongoose = require('mongoose');

const SecurityIncidentSchema = new mongoose.Schema({
  hardwareFingerprint: { type: String, required: true, index: true },
  username: { type: String, default: '' },
  ipAddress: { type: String, default: '127.0.0.1' },
  triggerVector: { type: String, required: true },
  details: { type: String, default: '' },
  actionTaken: { type: String, default: 'REJECTED_AND_PURGED_MODULE_FROM_VAULT' },
  timestamp: { type: Date, default: Date.now }
});

module.exports = mongoose.model('SecurityIncident', SecurityIncidentSchema);
