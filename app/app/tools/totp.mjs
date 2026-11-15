#!/usr/bin/env node
// Prints the current 6-digit TOTP code for a Base32 secret (RFC 6238, SHA-1, 30 s) — the second factor of
// "Log In (Password + TOTP)" (Deliverable 1 Fig 4.3, NFR Security). For demos without an authenticator app.
//   node tools/totp.mjs COMETFITALICE234
import { createHmac } from 'node:crypto';

export function totp(secret, time = Date.now()) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of secret.replace(/=|\s/g, '').toUpperCase()) {
    const v = alphabet.indexOf(c);
    if (v < 0) throw new Error('Invalid Base32 secret');
    bits += v.toString(2).padStart(5, '0');
  }
  const key = Buffer.from((bits.match(/.{8}/g) ?? []).map((b) => parseInt(b, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(time / 1000 / 30)));
  const h = createHmac('sha1', key).update(counter).digest();
  const off = h[h.length - 1] & 0xf;
  const bin = ((h[off] & 0x7f) << 24) | (h[off + 1] << 16) | (h[off + 2] << 8) | h[off + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

if (import.meta.url === `file://${process.argv[1].replace(/\\/g, '/')}` || process.argv[1]?.endsWith('totp.mjs')) {
  const secret = process.argv[2];
  if (!secret) {
    console.error('Usage: node tools/totp.mjs <BASE32_SECRET>');
    process.exit(1);
  }
  const left = 30 - (Math.floor(Date.now() / 1000) % 30);
  console.log(`${totp(secret)}   (valid for ~${left}s)`);
}
