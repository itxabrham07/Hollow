# Sensor & Mic Hardware Firewall Module (`module_sensor_firewall`)

Zero-touch server-shipped module that blanks camera/microphone buffers, zeroes gyroscope/accelerometer telemetry, and blocks background ultrasonic tracking beacons.

## Architecture
- **Contract**: Implements `HollowModule` from `:hollow-sdk`.
- **Target Hooks**: Intercepts `AudioRecord`, `CameraManager`, and `SystemSensorManager`.
- **Storage**: Module-scoped preferences via `HollowModuleContext.preferences`.
