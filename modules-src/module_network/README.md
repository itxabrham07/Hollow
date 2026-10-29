# Proxy & V2Ray Suite Module (`module_network`)

Per-app and global VPN/Proxy routing supporting VLESS XTLS Reality, VMess WS/TLS, Shadowsocks AEAD, Trojan TLS, and SOCKS5/HTTP proxies with Kill Switch protection.

## Architecture
- **Contract**: Implements `HollowModule` from `:hollow-sdk`.
- **Target Hooks**: Uses Android VpnService & network interception.
- **Storage**: Independent scoped storage (`HollowModuleContext.preferences`).
