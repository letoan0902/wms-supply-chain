#!/usr/bin/env node
// Kịch bản tải đồng thời và kiểm tra bất biến tồn kho (WMS-19.2). Không cần thư viện ngoài, Node 20+ (fetch có sẵn).
//
// Dùng: node qa/load/concurrency.mjs [--base-url http://localhost:8080] [--mode all|load|idempotency]
//                                    [--rps 40] [--duration 30] [--initial-stock 300] [--settle-timeout 90]
//                                    [--max-p95-ms 0] [--timeout-ms 30000] [--json <file>]
//
// Script tự đăng ký một doanh nghiệp mới (stack phải đặt REGISTRATION_REQUIRE_ACTIVATION=false, xem qa/README.md),
// chuẩn bị kho, sản phẩm và tồn đã biết, rồi:
//   - load: bắn đều --rps request/giây trong --duration giây, trộn lập đơn bán nháp, xác nhận đơn (cùng giữ một tồn),
//     lập phiếu xuất cho đơn đã xác nhận và các API đọc; chờ sự kiện xử lý xong rồi kiểm tra bất biến qua API.
//   - idempotency: gửi song song hai lần cùng một phiếu nhập, một lần xác nhận đơn, một phiếu xuất, một lần bấm xuất.
// Mã thoát: 0 mọi bất biến đạt, 1 có vi phạm, 2 bước chuẩn bị lỗi.
import { writeFileSync } from 'node:fs';
import { performance } from 'node:perf_hooks';

// ------------------------------------------------------------------------------------------------
// Tham số
// ------------------------------------------------------------------------------------------------
function parseArgs(argv) {
  const o = {
    baseUrl: 'http://localhost:8080', mode: 'all', rps: 40, duration: 30, initialStock: 300,
    settleTimeout: 90, maxP95Ms: 0, timeoutMs: 30000, json: ''
  };
  const num = (a, v) => {
    const n = Number(v);
    if (!Number.isFinite(n) || n < 0) throw new Error(`Giá trị không hợp lệ cho ${a}: ${v}`);
    return n;
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const v = () => {
      if (i + 1 >= argv.length) throw new Error(`Thiếu giá trị cho ${a}`);
      return argv[++i];
    };
    switch (a) {
      case '--base-url': o.baseUrl = v().replace(/\/+$/, ''); break;
      case '--mode': o.mode = v(); break;
      case '--rps': o.rps = num(a, v()); break;
      case '--duration': o.duration = num(a, v()); break;
      case '--initial-stock': o.initialStock = num(a, v()); break;
      case '--settle-timeout': o.settleTimeout = num(a, v()); break;
      case '--max-p95-ms': o.maxP95Ms = num(a, v()); break;
      case '--timeout-ms': o.timeoutMs = num(a, v()); break;
      case '--json': o.json = v(); break;
      case '-h':
      case '--help':
        console.log('node qa/load/concurrency.mjs [--base-url URL] [--mode all|load|idempotency] [--rps 40] [--duration 30] '
          + '[--initial-stock 300] [--settle-timeout 90] [--max-p95-ms 0] [--timeout-ms 30000] [--json file]');
        process.exit(0);
        break;
      default: throw new Error(`Tham số không hỗ trợ: ${a}`);
    }
  }
  if (!['all', 'load', 'idempotency'].includes(o.mode)) throw new Error('--mode chỉ nhận all, load, idempotency');
  if (o.rps <= 0 || o.duration <= 0) throw new Error('--rps và --duration phải lớn hơn 0');
  return o;
}

const opts = parseArgs(process.argv.slice(2));

// ------------------------------------------------------------------------------------------------
// Gọi HTTP và thống kê
// ------------------------------------------------------------------------------------------------
const stats = new Map(); // nhãn API -> { lat: [], outcomes: Map("status code" -> số lần) }
const serverErrors = []; // 5xx và lỗi mạng
const deadlocks = [];
const DEADLOCK = /deadlock|lock wait timeout|CannotAcquireLock|LockAcquisition/i;

class SetupError extends Error {}

function record(label, ms, status, code, text) {
  let s = stats.get(label);
  if (!s) { s = { lat: [], outcomes: new Map() }; stats.set(label, s); }
  s.lat.push(ms);
  const key = `${status} ${code || ''}`.trim();
  s.outcomes.set(key, (s.outcomes.get(key) || 0) + 1);
  if (status === 0 || status >= 500) serverErrors.push({ label, status, code, text: String(text || '').slice(0, 200) });
  if (DEADLOCK.test(String(text || ''))) deadlocks.push({ label, status, text: String(text || '').slice(0, 200) });
}

async function call(label, method, path, { token, body, apiKey, headers: extraHeaders } = {}) {
  const headers = { Accept: 'application/json', ...(extraHeaders || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (apiKey) headers['X-Api-Key'] = apiKey;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const started = performance.now();
  let status = 0;
  let text = '';
  try {
    const res = await fetch(opts.baseUrl + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(opts.timeoutMs)
    });
    status = res.status;
    text = await res.text();
  } catch (e) {
    text = `${e.name}: ${e.message}`;
  }
  const ms = performance.now() - started;
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  const code = json && typeof json.code === 'string' ? json.code : '';
  record(label, ms, status, code, text);
  return { status, code, json, data: json ? json.data : undefined, text, ms };
}

// Gọi trong bước chuẩn bị: sai mã HTTP là dừng script với mã thoát 2.
async function must(label, method, path, expected, init) {
  const r = await call(label, method, path, init);
  if (r.status !== expected) {
    throw new SetupError(`${label}: cần HTTP ${expected}, nhận ${r.status} ${r.text.slice(0, 300)}`);
  }
  return r.data;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const num = (x) => (x === null || x === undefined ? NaN : Number(x));
const eq = (a, b) => Math.abs(num(a) - num(b)) < 0.0005;
const vnDate = (offsetDays) => new Date(Date.now() + 7 * 3600 * 1000 + offsetDays * 86400000).toISOString().slice(0, 10);

// ------------------------------------------------------------------------------------------------
// Chuẩn bị doanh nghiệp, kho, sản phẩm, tồn
// ------------------------------------------------------------------------------------------------
const suffix = (Date.now().toString(36) + Math.random().toString(36).slice(2, 6)).toLowerCase();
const ctx = { token: '', whId: 0, locId: 0, supId: 0, cusId: 0, uomId: 0 };

async function prepareCompany() {
  const username = `qa_load_${suffix}`;
  const password = `QaLoad-${suffix}-Mk`;
  const reg = await must('POST /api/auth/companies/register', 'POST', '/api/auth/companies/register', 201, {
    body: { companyName: `QA tải ${suffix}`, fullName: 'Quản trị thử tải', username, email: `${username}@qa.example.com`, password }
  });
  if (reg.activationRequired) {
    throw new SetupError('Đăng ký trả activationRequired = true: đặt REGISTRATION_REQUIRE_ACTIVATION=false (qa/docker-compose.qa.yml).');
  }
  const login = await must('POST /api/auth/login', 'POST', '/api/auth/login', 200, { body: { username, password } });
  ctx.token = login.accessToken;
  const t = { token: ctx.token };
  ctx.uomId = (await must('POST /api/products/uoms', 'POST', '/api/products/uoms', 201, { ...t, body: { code: 'CAI', name: 'Cái' } })).id;
  ctx.whId = (await must('POST /api/inventory/warehouses', 'POST', '/api/inventory/warehouses', 201,
    { ...t, body: { code: 'LOAD-WH', name: 'Kho thử tải', capacity: 100 } })).id;
  ctx.locId = (await must('POST /api/inventory/locations', 'POST', '/api/inventory/locations', 201,
    { ...t, body: { warehouseId: ctx.whId, zone: 'A', rack: 'R01', bin: 'B01' } })).id;
  ctx.supId = (await must('POST /api/orders/suppliers', 'POST', '/api/orders/suppliers', 201,
    { ...t, body: { code: 'NCC-LOAD', name: 'Nhà cung cấp thử tải' } })).id;
  ctx.cusId = (await must('POST /api/orders/customers', 'POST', '/api/orders/customers', 201,
    { ...t, body: { code: 'KH-LOAD', name: 'Khách thử tải', customerType: 'RETAIL' } })).id;
  console.log(`Doanh nghiệp thử tải: ${username} (công ty ${reg.company && reg.company.code}), kho ${ctx.whId}`);
}

async function createProduct(sku) {
  const p = await must('POST /api/products', 'POST', '/api/products', 201, {
    token: ctx.token, body: { sku, name: `Hàng thử tải ${sku}`, uomId: ctx.uomId, purchasePrice: 1000, sellingPrice: 2000 }
  });
  return { id: p.id, sku: p.sku, name: p.name };
}

async function approvedPo(product, qty) {
  const po = await must('POST /api/orders/purchase', 'POST', '/api/orders/purchase', 201, {
    token: ctx.token,
    body: { supplierId: ctx.supId, warehouseId: ctx.whId, warehouseName: 'Kho thử tải',
      items: [{ productId: product.id, skuCode: product.sku, productName: product.name, uom: 'Cái', quantity: qty, unitPrice: 1000 }] }
  });
  await must('POST /api/orders/purchase/{id}/approve', 'POST', `/api/orders/purchase/${po.id}/approve`, 200, { token: ctx.token });
  return { poId: po.id, poItemId: po.items[0].id };
}

const grnBody = (po, qty) => ({ poId: po.poId, items: [{ poItemId: po.poItemId, acceptedQuantity: qty, locationId: ctx.locId }] });

async function atp(product) {
  const r = await call('GET /api/inventory/stock/atp', 'GET',
    `/api/inventory/stock/atp?productId=${product.id}&warehouseId=${ctx.whId}`, { token: ctx.token });
  return r.status === 200 ? { onHand: num(r.data.onHandQuantity), reserved: num(r.data.reservedQuantity) } : null;
}

async function waitFor(what, fn, timeoutSec) {
  const end = Date.now() + timeoutSec * 1000;
  let last;
  while (Date.now() < end) {
    last = await fn();
    if (last.ok) return last;
    await sleep(1000);
  }
  return last;
}

async function receive(product, qty) {
  const po = await approvedPo(product, qty);
  await must('POST /api/orders/goods-receipt-notes', 'POST', '/api/orders/goods-receipt-notes', 201, { token: ctx.token, body: grnBody(po, qty) });
  const r = await waitFor('nhập kho', async () => {
    const a = await atp(product);
    return { ok: !!a && eq(a.onHand, qty), a };
  }, opts.settleTimeout);
  if (!r.ok) throw new SetupError(`Tồn của ${product.sku} không đạt ${qty} sau ${opts.settleTimeout} giây: ${JSON.stringify(r.a)}`);
}

// Chờ tới khi tồn và giữ chỗ không đổi trong 3 lần đọc liên tiếp (sự kiện RabbitMQ đã xử lý xong).
async function settle(product) {
  const end = Date.now() + opts.settleTimeout * 1000;
  let prev = null;
  let stable = 0;
  let cur = null;
  while (Date.now() < end) {
    cur = await atp(product);
    if (cur && prev && eq(cur.onHand, prev.onHand) && eq(cur.reserved, prev.reserved)) stable++;
    else stable = 0;
    if (stable >= 2) return { settled: true, ...cur };
    prev = cur;
    await sleep(2000);
  }
  return { settled: false, ...(cur || {}) };
}

// ------------------------------------------------------------------------------------------------
// Kết quả kiểm tra
// ------------------------------------------------------------------------------------------------
const checks = [];
function check(id, title, ok, detail) {
  checks.push({ id, title, ok: !!ok, detail });
  console.log(`${ok ? 'ĐẠT     ' : 'VI PHẠM '} ${id} ${title}${detail ? ` (${detail})` : ''}`);
}

async function pages(label, path) {
  const out = [];
  for (let page = 0; page < 100; page++) {
    const r = await call(label, 'GET', `${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`, { token: ctx.token });
    if (r.status !== 200) throw new Error(`${label}: HTTP ${r.status} ${r.text.slice(0, 200)}`);
    out.push(...(r.data.content || []));
    if (r.data.last || !(r.data.content || []).length) break;
  }
  return out;
}

// ------------------------------------------------------------------------------------------------
// Chế độ load
// ------------------------------------------------------------------------------------------------
async function runLoad() {
  console.log(`\n=== Chế độ load: ${opts.rps} request/giây trong ${opts.duration} giây, tồn ban đầu ${opts.initialStock} ===`);
  const product = await createProduct('LOAD-01');
  await receive(product, opts.initialStock);
  const t = { token: ctx.token };
  const drafts = [];
  const confirmed = [];
  const counters = { soCreated: 0, soConfirmed: 0, soConfirmRejected: 0, gdnCreated: 0, gdnRejected: 0 };

  const ops = {
    async createSo() {
      const qty = 1 + Math.floor(Math.random() * 3);
      const r = await call('POST /api/orders/sales', 'POST', '/api/orders/sales', { ...t, body: {
        customerId: ctx.cusId, warehouseId: ctx.whId, status: 'DRAFT',
        items: [{ productId: product.id, skuCode: product.sku, productName: product.name, uom: 'Cái', quantity: qty, unitPrice: 2000 }] } });
      if (r.status === 201) { counters.soCreated++; drafts.push({ id: r.data.id, itemId: r.data.items[0].id, qty }); }
    },
    async confirmSo() {
      const so = drafts.shift();
      if (!so) return ops.createSo();
      const r = await call('POST /api/orders/sales/{id}/confirm', 'POST', `/api/orders/sales/${so.id}/confirm`, t);
      if (r.status === 200) { counters.soConfirmed++; confirmed.push(so); } else counters.soConfirmRejected++;
    },
    async createGdn() {
      const so = confirmed.shift();
      if (!so) return ops.read();
      const r = await call('POST /api/orders/goods-delivery-notes', 'POST', '/api/orders/goods-delivery-notes', { ...t, body: {
        soId: so.id, items: [{ soItemId: so.itemId, productId: product.id, deliveredQuantity: so.qty }] } });
      if (r.status === 201) counters.gdnCreated++; else counters.gdnRejected++;
    },
    async read() {
      const pick = Math.floor(Math.random() * 4);
      if (pick === 0) await atp(product);
      else if (pick === 1) await call('GET /api/orders/sales', 'GET', `/api/orders/sales?warehouseId=${ctx.whId}&size=20`, t);
      else if (pick === 2) await call('GET /api/inventory/lots', 'GET', `/api/inventory/lots?productId=${product.id}&warehouseId=${ctx.whId}`, t);
      else await call('GET /api/orders/goods-delivery-notes', 'GET', `/api/orders/goods-delivery-notes?warehouseId=${ctx.whId}&size=20`, t);
    }
  };
  const weighted = [['createSo', 30], ['confirmSo', 25], ['createGdn', 20], ['read', 25]];
  const totalWeight = weighted.reduce((s, [, w]) => s + w, 0);
  const choose = () => {
    let x = Math.random() * totalWeight;
    for (const [name, w] of weighted) { if ((x -= w) < 0) return name; }
    return 'read';
  };

  const total = Math.round(opts.rps * opts.duration);
  const interval = 1000 / opts.rps;
  const started = performance.now();
  const inflight = [];
  for (let i = 0; i < total; i++) {
    const due = started + i * interval;
    const wait = due - performance.now();
    if (wait > 0) await sleep(wait);
    inflight.push(ops[choose()]().catch((e) => serverErrors.push({ label: 'script', status: 0, text: e.message })));
  }
  await Promise.all(inflight);
  const elapsed = (performance.now() - started) / 1000;
  console.log(`Đã gửi ${total} thao tác trong ${elapsed.toFixed(1)} giây (${(total / elapsed).toFixed(1)}/giây). `
    + `Đơn bán: tạo ${counters.soCreated}, xác nhận ${counters.soConfirmed}, bị từ chối xác nhận ${counters.soConfirmRejected}; `
    + `phiếu xuất: tạo ${counters.gdnCreated}, bị từ chối ${counters.gdnRejected}`);

  console.log('Chờ sự kiện giữ chỗ, trừ tồn xử lý xong...');
  const s = await settle(product);
  if (!s.settled) console.log(`Cảnh báo: tồn chưa ổn định sau ${opts.settleTimeout} giây, kiểm tra trên số đọc cuối.`);
  const lots = await pages('GET /api/inventory/lots', `/api/inventory/lots?productId=${product.id}&warehouseId=${ctx.whId}`);
  const gdns = (await pages('GET /api/orders/goods-delivery-notes', `/api/orders/goods-delivery-notes?warehouseId=${ctx.whId}`))
    .filter((g) => g.status === 'COMPLETED');
  const delivered = gdns.reduce((sum, g) => sum + num(g.totalQuantity), 0);
  const confirmedOrders = await pages('GET /api/orders/sales', `/api/orders/sales?warehouseId=${ctx.whId}&status=CONFIRMED`);
  const committed = confirmedOrders.reduce((sum, so) => sum + (so.items || []).reduce((x, it) => x + num(it.quantity), 0), 0);
  const report = await call('GET /api/inventory/reports/in-out-balance', 'GET',
    `/api/inventory/reports/in-out-balance?warehouseId=${ctx.whId}&productId=${product.id}&fromDate=${vnDate(-1)}&toDate=${vnDate(0)}`, t);
  const totals = (report.data && report.data.totals) || {};

  console.log(`Sau tải: onHand ${s.onHand}, reserved ${s.reserved}, đã xuất ${delivered} (${gdns.length} phiếu), `
    + `đơn CONFIRMED ${confirmedOrders.length} với tổng ${committed}`);
  const badLots = lots.filter((l) => num(l.quantity) < 0 || num(l.reservedQuantity) < 0 || num(l.reservedQuantity) > num(l.quantity) + 0.0005);
  check('I1', 'Tồn thực tế và giữ chỗ không âm', s.onHand >= 0 && s.reserved >= 0
    && lots.every((l) => num(l.quantity) >= 0 && num(l.reservedQuantity) >= 0), `onHand ${s.onHand}, reserved ${s.reserved}`);
  check('I2', 'Giữ chỗ không vượt tồn (tổng và từng lô)', s.reserved <= s.onHand + 0.0005 && badLots.length === 0,
    badLots.length ? `lô sai: ${badLots.map((l) => `${l.lotNo} ${l.quantity}/${l.reservedQuantity}`).join(', ')}` : `reserved ${s.reserved} <= onHand ${s.onHand}`);
  check('I3', 'Tổng đã xuất không vượt tồn đã nhập', delivered <= opts.initialStock + 0.0005, `${delivered} <= ${opts.initialStock}`);
  check('I4', 'Mỗi phiếu xuất trừ tồn đúng một lần (onHand = đã nhập - đã xuất)', eq(s.onHand, opts.initialStock - delivered),
    `onHand ${s.onHand}, cần ${opts.initialStock - delivered}`);
  check('I5', 'Đơn đã xác nhận được giữ đủ hàng (reserved = tổng đơn CONFIRMED)', eq(s.reserved, committed),
    `reserved ${s.reserved}, cam kết ${committed}`);
  check('I6', 'Không bán vượt tồn (tổng cam kết <= onHand)', committed <= s.onHand + 0.0005, `cam kết ${committed}, onHand ${s.onHand}`);
  check('I7', 'Sổ kho khớp tồn (nhập, xuất, tồn cuối)', report.status === 200 && eq(totals.quantityIn, opts.initialStock)
    && eq(totals.quantityOut, delivered) && eq(totals.closingQuantity, s.onHand),
    `HTTP ${report.status}, nhập ${totals.quantityIn}, xuất ${totals.quantityOut}, tồn cuối ${totals.closingQuantity}`);
  return { counters, onHand: s.onHand, reserved: s.reserved, delivered, committed, gdnCount: gdns.length };
}

// ------------------------------------------------------------------------------------------------
// Chế độ idempotency
// ------------------------------------------------------------------------------------------------
async function twice(fn) {
  return Promise.all([fn(), fn()]);
}

const statuses = (rs) => rs.map((r) => r.status).sort((a, b) => a - b);

async function stableAfter(product, expectOnHand, expectReserved, label) {
  const r = await waitFor(label, async () => {
    const a = await atp(product);
    return { ok: !!a && eq(a.onHand, expectOnHand) && (expectReserved === undefined || eq(a.reserved, expectReserved)), a };
  }, opts.settleTimeout);
  await sleep(5000);
  const later = await atp(product);
  return { reached: r.ok, first: r.a, later };
}

async function runIdempotency() {
  console.log('\n=== Chế độ idempotency: gửi song song hai lần cùng một thao tác ===');
  const t = { token: ctx.token };
  const grnProduct = await createProduct('IDEM-GRN');

  // G1: hai phiếu nhập giống nhau nhận đủ phần còn lại.
  const po1 = await approvedPo(grnProduct, 50);
  const g1 = await twice(() => call('POST /api/orders/goods-receipt-notes', 'POST', '/api/orders/goods-receipt-notes', { ...t, body: grnBody(po1, 50) }));
  const g1s = await stableAfter(grnProduct, 50, undefined, 'G1');
  check('G1', 'Hai phiếu nhập trùng nhận đủ: một 201, một 400, tồn tăng đúng 50',
    JSON.stringify(statuses(g1)) === '[201,400]' && g1s.reached && g1s.later && eq(g1s.later.onHand, 50),
    `HTTP ${statuses(g1).join(', ')}, onHand ${g1s.later && g1s.later.onHand}`);

  // G2: hai phiếu nhập từng phần giống nhau, gửi cùng một Idempotency-Key như form nhập kho làm khi người dùng bấm
  // đúp hoặc gửi lại sau khi quá giờ (docs/api/CHUNG_TU_TON_KHO.md): yêu cầu thứ hai nhận lại phiếu đầu (HTTP 200).
  const po2 = await approvedPo(grnProduct, 100);
  const g2Key = { 'Idempotency-Key': `qa-load-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` };
  const g2 = await twice(() => call('POST /api/orders/goods-receipt-notes', 'POST', '/api/orders/goods-receipt-notes', { ...t, headers: g2Key, body: grnBody(po2, 30) }));
  const created = g2.filter((r) => r.status === 201).length;
  const g2s = await stableAfter(grnProduct, 50 + 30 * Math.max(created, 1), undefined, 'G2');
  check('G2', 'Hai phiếu nhập từng phần trùng: chỉ ghi một lần, tồn tăng đúng 30',
    created === 1 && g2s.later && eq(g2s.later.onHand, 80),
    `HTTP ${statuses(g2).join(', ')}, onHand ${g2s.later && g2s.later.onHand} (cần 80)`);

  // G3 đến G5 trên một sản phẩm có 100 tồn.
  const soProduct = await createProduct('IDEM-SO');
  await receive(soProduct, 100);
  const newDraft = async (qty) => {
    const so = await must('POST /api/orders/sales', 'POST', '/api/orders/sales', 201, { ...t, body: {
      customerId: ctx.cusId, warehouseId: ctx.whId, status: 'DRAFT',
      items: [{ productId: soProduct.id, skuCode: soProduct.sku, productName: soProduct.name, uom: 'Cái', quantity: qty, unitPrice: 2000 }] } });
    return { id: so.id, itemId: so.items[0].id };
  };

  const so1 = await newDraft(20);
  const g3 = await twice(() => call('POST /api/orders/sales/{id}/confirm', 'POST', `/api/orders/sales/${so1.id}/confirm`, t));
  const g3s = await stableAfter(soProduct, 100, 20, 'G3');
  check('G3', 'Xác nhận đồng thời một đơn bán: không lỗi máy chủ, giữ chỗ đúng 20 (không giữ hai lần)',
    g3.every((r) => r.status < 500) && g3s.later && eq(g3s.later.reserved, 20),
    `HTTP ${statuses(g3).join(', ')}, reserved ${g3s.later && g3s.later.reserved}`);
  check('G3b', 'Xác nhận đồng thời một đơn bán: chỉ một lần thành công (lần sau 400), không phát hai sự kiện order.created',
    JSON.stringify(statuses(g3)) === '[200,400]', `HTTP ${statuses(g3).join(', ')}`);

  const gdnBody = (so, qty, status) => ({ soId: so.id, ...(status ? { status } : {}),
    items: [{ soItemId: so.itemId, productId: soProduct.id, deliveredQuantity: qty }] });
  const g4 = await twice(() => call('POST /api/orders/goods-delivery-notes', 'POST', '/api/orders/goods-delivery-notes', { ...t, body: gdnBody(so1, 20) }));
  const g4s = await stableAfter(soProduct, 80, 0, 'G4');
  check('G4', 'Hai phiếu xuất trùng đủ số lượng: một 201, một 400, tồn giảm đúng 20, giữ chỗ về 0',
    JSON.stringify(statuses(g4)) === '[201,400]' && g4s.later && eq(g4s.later.onHand, 80) && eq(g4s.later.reserved, 0),
    `HTTP ${statuses(g4).join(', ')}, onHand ${g4s.later && g4s.later.onHand}, reserved ${g4s.later && g4s.later.reserved}`);

  const so2 = await newDraft(10);
  await must('POST /api/orders/sales/{id}/confirm', 'POST', `/api/orders/sales/${so2.id}/confirm`, 200, t);
  await stableAfter(soProduct, 80, 10, 'G5 giữ chỗ');
  const draftGdn = await must('POST /api/orders/goods-delivery-notes', 'POST', '/api/orders/goods-delivery-notes', 201, { ...t, body: gdnBody(so2, 10, 'DRAFT') });
  const g5 = await twice(() => call('POST /api/orders/goods-delivery-notes/{id}/dispatch', 'POST', `/api/orders/goods-delivery-notes/${draftGdn.id}/dispatch`, t));
  const g5s = await stableAfter(soProduct, 70, 0, 'G5');
  check('G5', 'Bấm xác nhận xuất hai lần cùng lúc: tồn chỉ giảm 10',
    g5.every((r) => r.status === 200) && g5s.later && eq(g5s.later.onHand, 70),
    `HTTP ${statuses(g5).join(', ')}, onHand ${g5s.later && g5s.later.onHand} (cần 70)`);
}

// ------------------------------------------------------------------------------------------------
// Báo cáo
// ------------------------------------------------------------------------------------------------
function percentile(sorted, p) {
  if (!sorted.length) return NaN;
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1))];
}

function printTables() {
  const rows = [...stats.entries()].map(([label, s]) => {
    const lat = [...s.lat].sort((a, b) => a - b);
    return { label, n: lat.length, p50: percentile(lat, 50), p95: percentile(lat, 95), p99: percentile(lat, 99), max: lat[lat.length - 1] };
  }).sort((a, b) => a.label.localeCompare(b.label));
  const w = Math.max(...rows.map((r) => r.label.length), 10);
  const f = (x) => (Number.isFinite(x) ? x.toFixed(0) : '-').padStart(7);
  console.log('\n=== Độ trễ theo API (ms) ===');
  console.log(`${'API'.padEnd(w)}  ${'số lần'.padStart(7)} ${'p50'.padStart(7)} ${'p95'.padStart(7)} ${'p99'.padStart(7)} ${'lớn nhất'.padStart(8)}  NFR 500 ms`);
  for (const r of rows) {
    console.log(`${r.label.padEnd(w)}  ${String(r.n).padStart(7)} ${f(r.p50)} ${f(r.p95)} ${f(r.p99)} ${f(r.max).padStart(8)}  ${r.p95 > 500 ? 'vượt' : 'đạt'}`);
  }
  console.log('\n=== Kết quả theo API, mã HTTP và code ===');
  for (const [label, s] of [...stats.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    const parts = [...s.outcomes.entries()].sort().map(([k, v]) => `${k}: ${v}`);
    console.log(`${label.padEnd(w)}  ${parts.join(', ')}`);
  }
  return rows;
}

// ------------------------------------------------------------------------------------------------
async function main() {
  console.log(`Gateway ${opts.baseUrl}, chế độ ${opts.mode}`);
  let loadResult = null;
  try {
    await prepareCompany();
  } catch (e) {
    console.error(`Chuẩn bị thất bại: ${e.message}`);
    process.exit(2);
  }
  try {
    if (opts.mode === 'all' || opts.mode === 'load') loadResult = await runLoad();
    if (opts.mode === 'all' || opts.mode === 'idempotency') await runIdempotency();
  } catch (e) {
    console.error(`Lỗi khi chạy: ${e.message}`);
    if (e instanceof SetupError) {
      printTables();
      process.exit(2);
    }
    check('RUN', 'Kịch bản chạy hết không lỗi', false, e.message);
  }
  const rows = printTables();
  check('I8', 'Không có HTTP 5xx hoặc lỗi mạng', serverErrors.length === 0,
    serverErrors.length ? serverErrors.slice(0, 5).map((x) => `${x.label} ${x.status} ${x.code || ''} ${x.text}`).join(' | ') : '0 lỗi');
  check('I9', 'Không có phản hồi nhắc tới deadlock hoặc chờ khóa quá hạn', deadlocks.length === 0,
    deadlocks.length ? deadlocks.slice(0, 3).map((x) => `${x.label} ${x.text}`).join(' | ') : '0 lần');
  if (opts.maxP95Ms > 0) {
    const slow = rows.filter((r) => r.p95 > opts.maxP95Ms);
    check('P95', `p95 mọi API <= ${opts.maxP95Ms} ms`, slow.length === 0, slow.map((r) => `${r.label} ${r.p95.toFixed(0)} ms`).join(', '));
  }
  const failed = checks.filter((c) => !c.ok);
  console.log(`\n${failed.length ? `CÓ ${failed.length} VI PHẠM: ${failed.map((c) => c.id).join(', ')}` : 'MỌI BẤT BIẾN ĐẠT'}`);
  if (opts.json) {
    writeFileSync(opts.json, JSON.stringify({
      options: opts, load: loadResult, checks, serverErrors, deadlocks,
      latency: rows, outcomes: Object.fromEntries([...stats.entries()].map(([k, s]) => [k, Object.fromEntries(s.outcomes)]))
    }, null, 2));
    console.log(`Đã ghi kết quả vào ${opts.json}`);
  }
  process.exit(failed.length ? 1 : 0);
}

main();
