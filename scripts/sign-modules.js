#!/usr/bin/env node
/**
 * Signs compiled feature module DEX binaries (`modules-src/<id>/build/module.dex.bin`)
 * using Ed25519 (`MODULE_ED25519_PRIVATE_KEY_B64` env secret in CI or local gitignored
 * `certs/module_ed25519_private.pem`), syncs `module.dex.bin` and updated `manifest.json`
 * into `modules/<id>/`, and writes debug-only test bundles to
 * `app/src/debug/assets/debug_modules/<id>.json` for offline Dev Full Build / JVM tests.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const repoRoot = path.resolve(__dirname, '..');
const modulesSrcRoot = path.join(repoRoot, 'modules-src');
const modulesOutRoot = path.join(repoRoot, 'modules');
const debugAssetsDir = path.join(repoRoot, 'app', 'src', 'debug', 'assets', 'debug_modules');
const privKeyPath = path.join(repoRoot, 'certs', 'module_ed25519_private.pem');
const pubKeyPath = path.join(repoRoot, 'certs', 'module_ed25519_public.pem');

function resolvePrivateKey() {
  const envB64 = (process.env.MODULE_ED25519_PRIVATE_KEY_B64 || '').trim();
  if (envB64.length > 0) {
    const pem = Buffer.from(envB64, 'base64').toString('utf8');
    return crypto.createPrivateKey(pem);
  }
  const envPem = (process.env.MODULE_ED25519_PRIVATE_KEY_PEM || '').trim();
  if (envPem.length > 0) {
    return crypto.createPrivateKey(envPem);
  }
  if (fs.existsSync(privKeyPath)) {
    return crypto.createPrivateKey(fs.readFileSync(privKeyPath, 'utf8'));
  }
  throw new Error(
    'Missing Ed25519 private key: provide MODULE_ED25519_PRIVATE_KEY_B64 env secret or local gitignored certs/module_ed25519_private.pem'
  );
}

const privateKey = resolvePrivateKey();
const publicKey = fs.existsSync(pubKeyPath)
  ? crypto.createPublicKey(fs.readFileSync(pubKeyPath, 'utf8'))
  : crypto.createPublicKey(privateKey);

fs.mkdirSync(debugAssetsDir, { recursive: true });

const moduleDirs = fs.readdirSync(modulesSrcRoot, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => d.name)
  .sort();

for (const moduleId of moduleDirs) {
  const builtDexPath = path.join(modulesSrcRoot, moduleId, 'build', 'module.dex.bin');
  if (!fs.existsSync(builtDexPath)) {
    throw new Error(`Compiled DEX not found at ${builtDexPath}`);
  }

  const dexBytes = fs.readFileSync(builtDexPath);
  if (dexBytes.length < 8 || dexBytes.subarray(0, 4).toString('ascii') !== 'dex\n') {
    throw new Error(`Invalid DEX header in ${builtDexPath}`);
  }

  const sha256Checksum = crypto.createHash('sha256').update(dexBytes).digest('hex');
  const signatureBytes = crypto.sign(null, Buffer.from(sha256Checksum, 'utf8'), privateKey);
  const signatureB64 = signatureBytes.toString('base64');

  const verified = crypto.verify(
    null,
    Buffer.from(sha256Checksum, 'utf8'),
    publicKey,
    signatureBytes
  );
  if (!verified) {
    throw new Error(
      `Ed25519 self-verification failed for ${moduleId} (private key does not match certs/module_ed25519_public.pem)`
    );
  }

  const outDir = path.join(modulesOutRoot, moduleId);
  fs.mkdirSync(outDir, { recursive: true });
  const targetDexPath = path.join(outDir, 'module.dex.bin');
  fs.copyFileSync(builtDexPath, targetDexPath);

  const manifestPath = path.join(outDir, 'manifest.json');
  const manifest = fs.existsSync(manifestPath)
    ? JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
    : { moduleId, version: 1, minCoreVersion: 1 };

  manifest.moduleId = moduleId;
  manifest.sha256 = sha256Checksum;
  manifest.sha256Checksum = sha256Checksum;
  manifest.signatureB64 = signatureB64;
  manifest.sizeKb = Math.max(1, Math.round(dexBytes.length / 1024));

  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8');

  const debugBundle = {
    ...manifest,
    dexHeaderMagic: 'dex\n039\u0000',
    dexPayloadBase64: dexBytes.toString('base64')
  };
  fs.writeFileSync(
    path.join(debugAssetsDir, `${moduleId}.json`),
    JSON.stringify(debugBundle),
    'utf8'
  );

  console.log(
    `[Signed Module] ${moduleId}: ${dexBytes.length} bytes | sha256=${sha256Checksum.slice(0, 16)}... | sig=${signatureB64.slice(0, 20)}...`
  );
}
