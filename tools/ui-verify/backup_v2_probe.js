// QA 备份文件独立探针：解密并检查载荷。仅用于假数据。
const fs = require('fs');
const crypto = require('crypto');
const [, , file, passphrase, payloadOut] = process.argv;
if (!file || !passphrase) { console.error('usage: backup_v2_probe.js <file> <passphrase>'); process.exit(2); }
function canon(v) {
  if (v === null) return 'null';
  if (typeof v === 'number') return JSON.stringify(v);
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (typeof v === 'string') return JSON.stringify(v);
  if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']';
  return '{' + Object.keys(v).sort().map(k => JSON.stringify(k) + ':' + canon(v[k])).join(',') + '}';
}
const outer = JSON.parse(fs.readFileSync(file, 'utf8'));
if (outer.format !== 'mengyalife-backup' || outer.backupFormatVersion !== 2) throw Error('format/version bad');
const enc = outer.encryption;
const salt = Buffer.from(enc.salt, 'base64');
const nonce = Buffer.from(enc.nonce, 'base64');
const aadObj = {
  backupFormatVersion: 2,
  encryption: {
    cipher: enc.cipher, iterations: enc.iterations, kdf: enc.kdf,
    nonce: enc.nonce, salt: enc.salt, tagBits: enc.tagBits,
  },
  format: 'mengyalife-backup',
  payloadSha256: outer.payloadSha256,
};
const aad = Buffer.from(canon(aadObj), 'utf8');
const key = crypto.pbkdf2Sync(passphrase, salt, enc.iterations, 32, 'sha256');
const ct = Buffer.from(outer.ciphertext, 'base64');
const tag = ct.subarray(ct.length - 16);
const body = ct.subarray(0, ct.length - 16);
const decipher = crypto.createDecipheriv('aes-256-gcm', key, nonce);
decipher.setAAD(aad);
decipher.setAuthTag(tag);
const plain = Buffer.concat([decipher.update(body), decipher.final()]);
const hash = crypto.createHash('sha256').update(plain).digest('hex');
if (hash !== outer.payloadSha256) throw Error('payload hash mismatch');
const payload = JSON.parse(plain.toString('utf8'));
if (payloadOut) fs.writeFileSync(payloadOut, plain);
if (payload.format !== 'mengyalife-backup' || payload.backupFormatVersion !== 2) throw Error('payload format bad');
const tables = payload.data || {};
const tableHashes = {};
const expected = ['journalEntries','goals','goalCheckins','advisorMessages','goalProposals','growthSignals','growthMemories','insightReports','weeklyReviews','weeklyAiReviews'];
for (const t of expected) {
  if (!Array.isArray(tables[t])) throw Error('missing table ' + t);
  if (payload.counts[t] !== tables[t].length) throw Error('count mismatch ' + t);
  const h = crypto.createHash('sha256').update(canon(tables[t]), 'utf8').digest('hex');
  if (payload.integrity.perTableSha256[t] !== h) throw Error('table hash mismatch ' + t);
  tableHashes[t] = h;
}
const forbidden = ['encrypted_api_key','use_fake_engine','mengya_ai_key','"granted"','consent'];
for (const f of forbidden) if (plain.toString('utf8').includes(f)) throw Error('forbidden field ' + f);
console.log(JSON.stringify({
  ok: true, fileBytes: fs.statSync(file).size, backupId: payload.backupId,
  createdAt: payload.createdAt, counts: payload.counts,
  qaBuild: payload.generator.qaBuild, versionName: payload.generator.versionName,
  payloadSha256: outer.payloadSha256, perTableSha256: tableHashes,
  sensitiveScan: true,
}));
