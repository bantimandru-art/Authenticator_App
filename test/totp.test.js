const test = require("node:test");
const assert = require("node:assert");
const { createHmac } = require("node:crypto");
const { base32Encode, base32Decode, hmacSha1, totp } = require("../js/totp.js");

// RFC 6238 Appendix B, SHA-1 seed "12345678901234567890", 8 digits.
const seed = new TextEncoder().encode("12345678901234567890");
const vectors = [
  [59, "94287082"], [1111111109, "07081804"], [1111111111, "14050471"],
  [1234567890, "89005924"], [2000000000, "69279037"], [20000000000, "65353130"],
];

test("matches RFC 6238 SHA-1 test vectors", () => {
  for (const [t, code] of vectors) assert.strictEqual(totp(seed, t, 8), code, `t=${t}`);
});

test("HMAC-SHA1 matches Node crypto", () => {
  for (const keyLen of [0, 20, 64, 65, 100]) {
    const key = Uint8Array.from({ length: keyLen }, (_, i) => (i * 37) & 255);
    const msg = Uint8Array.from({ length: 70 }, (_, i) => i);
    const expected = createHmac("sha1", key).update(msg).digest("hex");
    assert.strictEqual(Buffer.from(hmacSha1(key, msg)).toString("hex"), expected, `keyLen=${keyLen}`);
  }
});

test("Base32 round-trips", () => {
  const bytes = Uint8Array.from({ length: 20 }, (_, i) => i * 13);
  assert.deepStrictEqual(base32Decode(base32Encode(bytes)), bytes);
  assert.strictEqual(base32Encode(seed), "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
});
