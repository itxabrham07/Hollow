const mongoose = require('mongoose');

const DeviceSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: false },
  username: { type: String, required: true, trim: true },
  email: { type: String, required: true, lowercase: true, trim: true },
  hardwareFingerprint: { type: String, required: true, unique: true, index: true },
  socSerial: { type: String, default: '' },
  androidId: { type: String, default: '' },
  storageCid: { type: String, default: '' },
  deviceModel: { type: String, default: 'Android Device' },
  status: {
    type: String,
    enum: ['pending', 'approved', 'revoked', 'banned'],
    default: 'pending',
    index: true
  },
  allowedModules: {
    type: [String],
    default: []
  },
  licenseValidUntil: { type: Date, default: null },
  monotonicUptimeTick: { type: Number, default: 0 },
  lastHeartbeatAt: { type: Date, default: Date.now },
  lastIp: { type: String, default: '' },
  clientVersion: { type: String, default: '1.0.0' },
  banReason: { type: String, default: '' },
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Device', DeviceSchema);
