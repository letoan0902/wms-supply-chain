#!/usr/bin/env node
// Chạy lần lượt các collection Postman của qa/postman bằng Postman CLI với cùng một runId (qa/README.md, mục 4.1).
// Dùng: node qa/run-all.mjs [--base-url URL] [--run-id ID] [--only 00,10,20,30] [--output DIR]
//                           [--poll-max N] [--poll-delay-ms MS] [--load]
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const qaDir = dirname(fileURLToPath(import.meta.url));
const postmanDir = join(qaDir, 'postman');
const repoRoot = dirname(qaDir);

function parseArgs(argv) {
  const opts = { baseUrl: 'http://localhost:8080', runId: '', only: null, output: '', pollMax: '', pollDelayMs: '', load: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) throw new Error(`Thiếu giá trị cho ${a}`);
      return argv[++i];
    };
    if (a === '--base-url') opts.baseUrl = next();
    else if (a === '--run-id') opts.runId = next();
    else if (a === '--only') opts.only = next().split(',').map((s) => s.trim()).filter(Boolean);
    else if (a === '--output') opts.output = next();
    else if (a === '--poll-max') opts.pollMax = next();
    else if (a === '--poll-delay-ms') opts.pollDelayMs = next();
    else if (a === '--load') opts.load = true;
    else if (a === '--help' || a === '-h') {
      console.log('node qa/run-all.mjs [--base-url URL] [--run-id ID] [--only 00,10,20,30] [--output DIR] [--poll-max N] [--poll-delay-ms MS] [--load]');
      process.exit(0);
    } else throw new Error(`Tham số không hỗ trợ: ${a}`);
  }
  return opts;
}

const opts = parseArgs(process.argv.slice(2));
const runId = (opts.runId || `qa${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`)
  .toLowerCase().replace(/[^a-z0-9]/g, '');
if (runId.length < 4 || runId.length > 20) {
  console.error('runId phải gồm 4 đến 20 chữ thường hoặc chữ số');
  process.exit(2);
}

const collections = readdirSync(postmanDir)
  .filter((f) => /^\d\d-.*\.postman_collection\.json$/.test(f))
  .sort()
  .filter((f) => !opts.only || opts.only.includes(f.slice(0, 2)));
if (!collections.length) {
  console.error('Không tìm thấy collection nào trong qa/postman');
  process.exit(2);
}

console.log(`runId của lượt chạy: ${runId}`);
console.log(`Gateway: ${opts.baseUrl}`);
const results = [];
for (const file of collections) {
  const args = [
    'collection', 'run', join(postmanDir, file),
    '-e', join(postmanDir, 'wms-qa.postman_environment.json'),
    '--env-var', `runId=${runId}`,
    '--env-var', `baseUrl=${opts.baseUrl}`,
    '--working-dir', postmanDir,
    '--timeout-request', '180000'
  ];
  if (opts.pollMax) args.push('--env-var', `pollMax=${opts.pollMax}`);
  if (opts.pollDelayMs) args.push('--env-var', `pollDelayMs=${opts.pollDelayMs}`);
  if (opts.output) args.push('--output', opts.output);
  console.log(`\n===== ${file} =====`);
  const started = Date.now();
  const r = spawnSync('postman', args, { stdio: 'inherit', cwd: repoRoot });
  if (r.error) {
    console.error(`Không chạy được Postman CLI: ${r.error.message}. Cài Postman CLI và kiểm tra lệnh "postman --version".`);
    process.exit(2);
  }
  results.push({ name: file, code: r.status, seconds: Math.round((Date.now() - started) / 1000) });
  if (file.startsWith('00-') && r.status !== 0) {
    console.error('\n00-setup thất bại: dừng, các collection sau cần doanh nghiệp A, B của bước này.');
    break;
  }
}

if (opts.load) {
  const script = join(qaDir, 'load', 'concurrency.mjs');
  if (!existsSync(script)) {
    console.error('Chưa có qa/load/concurrency.mjs');
  } else {
    console.log('\n===== load/concurrency.mjs =====');
    const started = Date.now();
    const r = spawnSync(process.execPath, [script, '--base-url', opts.baseUrl], { stdio: 'inherit', cwd: repoRoot });
    results.push({ name: relative(qaDir, script), code: r.status, seconds: Math.round((Date.now() - started) / 1000) });
  }
}

console.log('\n===== Tổng hợp =====');
console.log(`runId: ${runId}`);
for (const r of results) {
  console.log(`${r.code === 0 ? 'ĐẠT     ' : 'CÓ LỖI  '} ${r.name} (mã thoát ${r.code}, ${r.seconds} giây)`);
}
process.exit(results.every((r) => r.code === 0) ? 0 : 1);
