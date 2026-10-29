const mongoose = require('mongoose');

const DeviceSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: false },
  username: { type: String, required: true, trim: true },
  email: { type: String, required: false, default: '', lowercase: true, trim: true },
  passwordHash: { type: String, default: '' },
  hardwareFingerprint: { type: String, required: true, unique: true, index: true },
  socSerial: { type: String, default: '' },
  androidId: { type: String, default: '' },
  storageCid: { type: String, default: '' },
  deviceModel: { type: String, default: 'Android Device' },
  status: {
    type: String,
    enum: [
      'PENDING_APPROVAL',
      'APPROVED',
      'REVOKED',
      'BANNED',
      'pending',
      'approved',
      'revoked',
      'banned'
    ],
    default: 'PENDING_APPROVAL',
    index: true
  },
  allowedModules: {
    type: [String],
    default: []
  },
  maxDevices: {
    type: Number,
    default: 1
  },
  licenseValidUntil: { type: Date, default: null },
  highWaterWallClockMs: { type: Number, default: 0 },
  lastElapsedRealtimeMs: { type: Number, default: 0 },
  monotonicUptimeTick: { type: Number, default: 0 },
  lastHeartbeatAt: { type: Date, default: Date.now },
  lastIp: { type: String, default: '' },
  clientVersion: { type: String, default: '1.0.0' },
  banReason: { type: String, default: '' },
  registeredAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now },
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Device', DeviceSchema);
