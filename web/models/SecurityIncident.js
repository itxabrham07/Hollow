const mongoose = require('mongoose');

const SecurityIncidentSchema = new mongoose.Schema({
  hardwareFingerprint: { type: String, required: true, index: true },
  username: { type: String, default: '' },
  triggerVector: { type: String, required: true },
  details: { type: String, required: true },
  timestamp: { type: Date, default: Date.now }
});

module.exports = mongoose.model('SecurityIncident', SecurityIncidentSchema);
