// TOTP (RFC 6238) with a self-contained SHA-1/HMAC so it works without crypto.subtle.
const B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function base32Encode(bytes) {
  let out = "", buffer = 0, bits = 0;
  for (const b of bytes) {
    buffer = (buffer << 8) | b; bits += 8;
    while (bits >= 5) { bits -= 5; out += B32[(buffer >> bits) & 31]; }
    buffer &= (1 << bits) - 1;
  }
  if (bits > 0) out += B32[(buffer << (5 - bits)) & 31];
  return out;
}

function base32Decode(text) {
  const clean = text.replace(/[\s=-]/g, "").toUpperCase();
  const out = []; let buffer = 0, bits = 0;
  for (const ch of clean) {
    const v = B32.indexOf(ch);
    if (v < 0) throw new Error("Invalid Base32");
    buffer = (buffer << 5) | v; bits += 5;
    if (bits >= 8) { bits -= 8; out.push((buffer >> bits) & 255); buffer &= (1 << bits) - 1; }
  }
  return Uint8Array.from(out);
}

// Plain SHA-1 so the page doesn't depend on crypto.subtle (not available in every sandboxed frame).
const rol = (n, s) => ((n << s) | (n >>> (32 - s))) >>> 0;
function sha1(bytes) {
  const len = bytes.length, blocks = Math.floor((len + 8) / 64) + 1;
  const w = new Uint32Array(blocks * 16);
  for (let i = 0; i < len; i++) w[i >> 2] |= bytes[i] << (24 - (i % 4) * 8);
  w[len >> 2] |= 0x80 << (24 - (len % 4) * 8);
  w[blocks * 16 - 1] = len * 8;
  let h = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0];
  const x = new Uint32Array(80);
  for (let b = 0; b < w.length; b += 16) {
    for (let t = 0; t < 80; t++) x[t] = t < 16 ? w[b + t] : rol(x[t - 3] ^ x[t - 8] ^ x[t - 14] ^ x[t - 16], 1);
    let [a, bb, c, d, e] = h;
    for (let t = 0; t < 80; t++) {
      let f, k;
      if (t < 20) { f = (bb & c) | (~bb & d); k = 0x5a827999; }
      else if (t < 40) { f = bb ^ c ^ d; k = 0x6ed9eba1; }
      else if (t < 60) { f = (bb & c) | (bb & d) | (c & d); k = 0x8f1bbcdc; }
      else { f = bb ^ c ^ d; k = 0xca62c1d6; }
      const tmp = (rol(a, 5) + f + e + k + x[t]) >>> 0;
      e = d; d = c; c = rol(bb, 30); bb = a; a = tmp;
    }
    h = [h[0] + a, h[1] + bb, h[2] + c, h[3] + d, h[4] + e].map(v => v >>> 0);
  }
  const out = new Uint8Array(20);
  h.forEach((v, i) => { out[i * 4] = v >>> 24; out[i * 4 + 1] = v >>> 16; out[i * 4 + 2] = v >>> 8; out[i * 4 + 3] = v; });
  return out;
}

function hmacSha1(key, msg) {
  if (key.length > 64) key = sha1(key);
  const k = new Uint8Array(64); k.set(key);
  const inner = new Uint8Array(64 + msg.length), outer = new Uint8Array(84);
  for (let i = 0; i < 64; i++) { inner[i] = k[i] ^ 0x36; outer[i] = k[i] ^ 0x5c; }
  inner.set(msg, 64);
  outer.set(sha1(inner), 64);
  return sha1(outer);
}

function totp(secretBytes, unixSeconds, digits = 6, period = 30) {
  let counter = Math.floor(unixSeconds / period);
  const msg = new Uint8Array(8);
  for (let i = 7; i >= 0; i--) { msg[i] = counter & 255; counter = Math.floor(counter / 256); }
  const hash = hmacSha1(secretBytes, msg);
  const off = hash[19] & 15;
  const bin = ((hash[off] & 127) << 24) | (hash[off + 1] << 16) | (hash[off + 2] << 8) | hash[off + 3];
  return String(bin % 10 ** digits).padStart(digits, "0");
}

// Expose for Node tests; in the browser these are plain globals.
if (typeof module !== "undefined") module.exports = { base32Encode, base32Decode, sha1, hmacSha1, totp };
