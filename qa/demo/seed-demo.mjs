#!/usr/bin/env node
// Tạo dữ liệu demo cho các doanh nghiệp mẫu qua API công khai của api-gateway (qa/demo/README.md).
// Không dùng thư viện ngoài (Node 20+, fetch có sẵn). Không gọi SQL, không gọi endpoint nội bộ.
//
//   node qa/demo/seed-demo.mjs register  [--base-url URL] [--only k1,k2] [--email-template mẫu] [--suffix hậu-tố]
//   node qa/demo/seed-demo.mjs populate  [--base-url URL] [--only k1,k2] [--suffix hậu-tố]
//   node qa/demo/seed-demo.mjs status    [--base-url URL] [--only k1,k2] [--suffix hậu-tố]
//   node qa/demo/seed-demo.mjs all       (register rồi populate nếu máy chủ không yêu cầu kích hoạt qua email)
//
// Mật khẩu của mọi tài khoản demo lấy từ biến môi trường DEMO_PASSWORD; công cụ không in, không ghi nó ra đĩa.
import { readFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(HERE, 'data');
const COMPANY_ORDER = ['trai-cay', 'kho-van', 'dien-may', 'my-pham', 'van-phong-pham'];
// Tiền tố dành cho dữ liệu nhập trực tiếp bằng AI trong buổi demo: dữ liệu nền không được dùng
const RESERVED_PREFIX = /^(TCM|KVM|DMM|NCC-TCM|NCC-KVM|NCC-DMM|KH-TCM|KH-KVM|KH-DMM)-/i;
const MIN_INTERVAL_MS = 70; // khoảng 14 request mỗi giây, dưới giới hạn 20/giây của gateway
const STOCK_WAIT_SECONDS = 90;

// ------------------------------------------------------------------------------------------------
// Tham số dòng lệnh
// ------------------------------------------------------------------------------------------------
function usage() {
  return [
    'Dùng: node qa/demo/seed-demo.mjs <register|populate|status|all> [tùy chọn]',
    '  --base-url URL          địa chỉ api-gateway (mặc định http://127.0.0.1:18080)',
    '  --only k1,k2            chỉ xử lý các doanh nghiệp này (khóa: ' + COMPANY_ORDER.join(', ') + ')',
    '  --email-template MẪU    email quản trị khi đăng ký, {key} được thay bằng khóa doanh nghiệp',
    '                          (mặc định demo+{key}@example.com; trên máy chủ thật dùng hộp thư của bạn)',
    '  --suffix HẬU_TỐ         thêm vào tên đăng nhập, tên doanh nghiệp, email (chạy lại trên CSDL đã có dữ liệu cũ)',
    '  --skip-alert-scan       populate không gọi quét cảnh báo (lần quét gửi thông báo và email cho quản trị, quản lý kho)',
    'Biến môi trường bắt buộc: DEMO_PASSWORD (mật khẩu chung của mọi tài khoản demo, tối thiểu 8 ký tự).'
  ].join('\n');
}

function parseArgs(argv) {
  const opts = { command: '', baseUrl: 'http://127.0.0.1:18080', only: null, emailTemplate: 'demo+{key}@example.com', suffix: '', alertScan: true };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) throw new Error(`Thiếu giá trị cho ${a}`);
      return argv[++i];
    };
    if (a === '--base-url') opts.baseUrl = next().replace(/\/+$/, '');
    else if (a === '--only') opts.only = next().split(',').map((s) => s.trim()).filter(Boolean);
    else if (a === '--email-template') opts.emailTemplate = next();
    else if (a === '--suffix') opts.suffix = next().trim();
    else if (a === '--skip-alert-scan') opts.alertScan = false;
    else if (a === '--help' || a === '-h') { console.log(usage()); process.exit(0); }
    else if (!a.startsWith('-') && !opts.command) opts.command = a;
    else throw new Error(`Tham số không hỗ trợ: ${a}`);
  }
  if (!['register', 'populate', 'status', 'all'].includes(opts.command)) throw new Error('Thiếu lệnh hoặc lệnh không hợp lệ.\n' + usage());
  if (opts.suffix && !/^[a-z0-9._-]{1,12}$/i.test(opts.suffix)) {
    throw new Error('--suffix chỉ gồm chữ, số, dấu chấm, gạch ngang, gạch dưới, tối đa 12 ký tự');
  }
  if (!opts.emailTemplate.includes('{key}')) throw new Error('--email-template phải chứa {key}');
  return opts;
}

// ------------------------------------------------------------------------------------------------
// Tiện ích
// ------------------------------------------------------------------------------------------------
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const num = (x) => (x === null || x === undefined || x === '' ? 0 : Number(x));
const money = (x) => Math.round(num(x));
const fmtMoney = (x) => money(x).toLocaleString('vi-VN');
// Ngày theo giờ Việt Nam (UTC+7), lệch offset ngày so với hôm nay
const vnDate = (offsetDays = 0) => new Date(Date.now() + 7 * 3600e3 + offsetDays * 86400e3).toISOString().slice(0, 10);
const vnInstant = (offsetDays = 0) => new Date(Date.now() + offsetDays * 86400e3).toISOString();
const upper = (s) => String(s || '').trim().toUpperCase();
const pad = (s, n) => { s = String(s); return s.length >= n ? s : s + ' '.repeat(n - s.length); };
const padL = (s, n) => { s = String(s); return s.length >= n ? s : ' '.repeat(n - s.length) + s; };

class ApiError extends Error {
  constructor(method, path, status, code, message, data) {
    super(`${method} ${path} -> HTTP ${status}${code ? ' ' + code : ''}: ${message}`);
    this.method = method; this.path = path; this.status = status; this.code = code; this.apiMessage = message; this.data = data;
  }
}
class SeedError extends Error {}
// Lỗi tạm thời của máy chủ (quá tải, hết thời gian chờ giữa các dịch vụ, rớt mạng): chạy lại là qua
const isTransient = (e) => e instanceof ApiError && [0, 502, 503, 504].includes(e.status);

// ------------------------------------------------------------------------------------------------
// Gọi HTTP: giãn nhịp, thử lại khi 429 theo Retry-After, đăng nhập lại khi token hết hạn
// ------------------------------------------------------------------------------------------------
class Http {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.last = 0;
    this.count = 0;
  }

  async raw(method, path, { token, body, headers } = {}) {
    for (let attempt = 1; ; attempt++) {
      const wait = this.last + MIN_INTERVAL_MS - Date.now();
      if (wait > 0) await sleep(wait);
      this.last = Date.now();
      this.count++;
      const h = { Accept: 'application/json', ...(headers || {}) };
      if (token) h.Authorization = `Bearer ${token}`;
      if (body !== undefined) h['Content-Type'] = 'application/json';
      let res;
      let text = '';
      try {
        res = await fetch(this.baseUrl + path, {
          method, headers: h, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(120000)
        });
        text = await res.text();
      } catch (e) {
        // Lỗi mạng: chỉ thử lại request đọc, request ghi có thể đã được xử lý
        if (method === 'GET' && attempt < 4) { await sleep(1500 * attempt); continue; }
        throw new ApiError(method, path, 0, 'NETWORK', `${e.name}: ${e.message}`);
      }
      if (res.status === 429 && attempt < 8) {
        const ra = Number(res.headers.get('retry-after'));
        await sleep((Number.isFinite(ra) && ra > 0 ? ra : attempt) * 1000);
        continue;
      }
      if (method === 'GET' && [502, 503, 504].includes(res.status) && attempt < 4) { await sleep(2000 * attempt); continue; }
      let json = null;
      try { json = text ? JSON.parse(text) : null; } catch { json = null; }
      return { status: res.status, headers: res.headers, json, data: json ? json.data : undefined, text };
    }
  }
}

class Session {
  constructor(http, username, password, label) {
    this.http = http; this.username = username; this.password = password; this.label = label; this.token = null;
  }

  async login() {
    const r = await this.http.raw('POST', '/api/auth/login', { body: { username: this.username, password: this.password } });
    if (r.status === 200 && r.data && r.data.accessToken) { this.token = r.data.accessToken; this.loginData = r.data; return r.data; }
    const code = r.json && r.json.code;
    const msg = (r.json && r.json.message) || r.text.slice(0, 300);
    if (code === 'COMPANY_INACTIVE' && r.json.data && r.json.data.companyStatus === 'PENDING') {
      throw new SeedError(`Doanh nghiệp của tài khoản ${this.username} chưa được kích hoạt. Mở email kích hoạt, bấm liên kết rồi chạy lại populate.`);
    }
    if (r.status === 401) {
      throw new SeedError(`Không đăng nhập được ${this.username} (${this.label}): sai mật khẩu hoặc tài khoản chưa có. `
        + 'Kiểm tra DEMO_PASSWORD, --suffix, và đã chạy register chưa. Lưu ý: sai mật khẩu 5 lần trong 30 phút sẽ bị khóa đăng nhập theo IP.');
    }
    throw new ApiError('POST', '/api/auth/login', r.status, code, msg);
  }

  // Gọi API; trạng thái nằm trong allow thì trả về kết quả, còn lại không phải 2xx thì ném ApiError
  async call(method, path, { body, headers, allow = [] } = {}) {
    if (!this.token) await this.login();
    let r = await this.http.raw(method, path, { token: this.token, body, headers });
    if (r.status === 401) {
      await this.login();
      r = await this.http.raw(method, path, { token: this.token, body, headers });
    }
    if ((r.status >= 200 && r.status < 300) || allow.includes(r.status)) return r;
    const code = r.json && r.json.code;
    const msg = (r.json && r.json.message) || r.text.slice(0, 300);
    throw new ApiError(method, path, r.status, code, msg, r.json && r.json.data);
  }

  async get(path, opts) { return (await this.call('GET', path, opts)).data; }
  async post(path, body, opts = {}) { return (await this.call('POST', path, { ...opts, body })).data; }
  async put(path, body, opts = {}) { return (await this.call('PUT', path, { ...opts, body })).data; }

  // Đọc hết các trang của một API PageResponse/Page
  async all(path, size = 200) {
    const out = [];
    for (let page = 0; page < 50; page++) {
      const d = await this.get(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=${size}`);
      const content = (d && d.content) || [];
      out.push(...content);
      if (d.last === true || content.length < size) break;
    }
    return out;
  }

  async total(path) {
    const d = await this.get(`${path}${path.includes('?') ? '&' : '?'}page=0&size=1`);
    return num(d.totalElements);
  }
}

// ------------------------------------------------------------------------------------------------
// Dữ liệu khai báo và kiểm tra trước khi chạy
// ------------------------------------------------------------------------------------------------
function loadCompanies(only) {
  const files = readdirSync(DATA_DIR).filter((f) => f.endsWith('.json'));
  const companies = files.map((f) => JSON.parse(readFileSync(join(DATA_DIR, f), 'utf8')));
  companies.sort((a, b) => COMPANY_ORDER.indexOf(a.key) - COMPANY_ORDER.indexOf(b.key));
  if (only) {
    const unknown = only.filter((k) => !companies.some((c) => c.key === k));
    if (unknown.length) throw new Error(`Không có dữ liệu cho doanh nghiệp: ${unknown.join(', ')}`);
    return companies.filter((c) => only.includes(c.key));
  }
  return companies;
}

function validateData(c) {
  const errs = [];
  const has = (arr, key, v) => (arr || []).some((x) => upper(x[key]) === upper(v));
  const checkCode = (kind, code) => { if (RESERVED_PREFIX.test(code)) errs.push(`${kind} ${code} dùng tiền tố dành cho dữ liệu demo trực tiếp`); };
  for (const p of c.products || []) {
    checkCode('Hàng hóa', p.sku);
    if (!has(c.uoms, 'code', p.uom)) errs.push(`Hàng ${p.sku}: đơn vị ${p.uom} chưa khai báo`);
    if (p.category && !has(c.categories, 'code', p.category)) errs.push(`Hàng ${p.sku}: danh mục ${p.category} chưa khai báo`);
  }
  for (const s of c.suppliers || []) checkCode('Nhà cung cấp', s.code);
  for (const k of c.customers || []) checkCode('Khách hàng', k.code);
  const wh = (code) => { if (!has(c.warehouses, 'code', code)) errs.push(`Kho ${code} chưa khai báo`); };
  const sku = (s) => { if (!has(c.products, 'sku', s)) errs.push(`Mã hàng ${s} chưa khai báo`); };
  for (const po of c.purchaseOrders || []) {
    if (!has(c.suppliers, 'code', po.supplier)) errs.push(`Đơn mua ${po.number}: nhà cung cấp ${po.supplier} chưa khai báo`);
    wh(po.warehouse);
    (po.items || []).forEach((it) => sku(it.sku));
    for (const r of po.receipts || []) for (const l of r.lines) {
      if (!(po.items || []).some((it) => upper(it.sku) === upper(l.sku))) errs.push(`Đơn mua ${po.number}: phiếu nhập có ${l.sku} không thuộc đơn`);
    }
  }
  for (const so of c.salesOrders || []) {
    if (!has(c.customers, 'code', so.customer)) errs.push(`Đơn bán ${so.number}: khách hàng ${so.customer} chưa khai báo`);
    wh(so.warehouse);
    (so.items || []).forEach((it) => sku(it.sku));
  }
  for (const t of c.transfers || []) { wh(t.from); wh(t.to); (t.items || []).forEach((it) => sku(it.sku)); }
  for (const s of c.stocktakes || []) { wh(s.warehouse); (s.adjust || []).forEach((a) => sku(a.sku)); }
  for (const u of c.users || []) for (const w of (Array.isArray(u.warehouses) ? u.warehouses : [])) wh(w);
  if (errs.length) throw new SeedError(`Dữ liệu ${c.key} có lỗi:\n  - ${errs.join('\n  - ')}`);
}

function checkTrialLimits(c, trial) {
  if (!trial) return;
  const users = 1 + (c.users || []).length;
  const warehouses = (c.warehouses || []).length;
  const skus = (c.products || []).length;
  const over = [];
  if (trial.maxUsers >= 0 && users > trial.maxUsers) over.push(`người dùng ${users}/${trial.maxUsers}`);
  if (trial.maxWarehouses >= 0 && warehouses > trial.maxWarehouses) over.push(`kho ${warehouses}/${trial.maxWarehouses}`);
  if (trial.maxSkus >= 0 && skus > trial.maxSkus) over.push(`SKU ${skus}/${trial.maxSkus}`);
  if (over.length) throw new SeedError(`Dữ liệu ${c.key} vượt giới hạn gói TRIAL: ${over.join(', ')}`);
}

function names(c, opts) {
  const sfx = opts.suffix;
  const adminUsername = c.company.admin.username + (sfx ? `-${sfx}` : '');
  const userName = (u) => u.username + (sfx ? `-${sfx}` : '');
  const companyName = c.company.name + (sfx ? ` ${sfx}` : '');
  // Mã số thuế duy nhất toàn hệ thống: chạy với hậu tố thì dùng dạng mã chi nhánh 10 số + 3 số
  const taxCode = sfx ? `${c.company.taxCode}-${String(parseInt(createHash('sha1').update(sfx).digest('hex').slice(0, 6), 16) % 900 + 100)}` : c.company.taxCode;
  const adminEmail = opts.emailTemplate.replace('{key}', c.key + (sfx ? `.${sfx}` : ''));
  const userEmail = (u) => `${userName(u)}@${c.emailDomain || c.key + '.example'}`;
  return { adminUsername, userName, companyName, taxCode, adminEmail, userEmail };
}

// ------------------------------------------------------------------------------------------------
// Ghi nhận kết quả cho bảng cuối
// ------------------------------------------------------------------------------------------------
class Tally {
  constructor() { this.rows = new Map(); }
  add(kind, what) {
    if (!this.rows.has(kind)) this.rows.set(kind, { created: 0, existing: 0, updated: 0 });
    this.rows.get(kind)[what]++;
  }
  created(kind) { this.add(kind, 'created'); }
  existing(kind) { this.add(kind, 'existing'); }
  updated(kind) { this.add(kind, 'updated'); }
  totalCreated() { let n = 0; for (const r of this.rows.values()) n += r.created + r.updated; return n; }
  print(title) {
    console.log(`\n  ${title}`);
    console.log(`  ${pad('Loại', 34)}${padL('Tạo mới', 9)}${padL('Đã có', 8)}${padL('Cập nhật', 10)}`);
    for (const [k, r] of this.rows) console.log(`  ${pad(k, 34)}${padL(r.created, 9)}${padL(r.existing, 8)}${padL(r.updated, 10)}`);
  }
}

function step(c, msg) { console.log(`[${c.key}] ${msg}`); }

// ------------------------------------------------------------------------------------------------
// register
// ------------------------------------------------------------------------------------------------
async function register(http, companies, opts, password) {
  const pending = [];
  for (const c of companies) {
    const n = names(c, opts);
    const body = {
      companyName: n.companyName, taxCode: n.taxCode, phone: c.company.phone, fullName: c.company.admin.fullName,
      username: n.adminUsername, email: n.adminEmail, password
    };
    const r = await http.raw('POST', '/api/auth/companies/register', { body });
    if (r.status === 201) {
      const d = r.data || {};
      const st = d.company && d.company.status;
      step(c, `đã đăng ký "${n.companyName}" (mã ${d.company && d.company.code}, trạng thái ${st}, gói ${d.subscription && d.subscription.plan}), quản trị ${n.adminUsername}, email ${n.adminEmail}`);
      if (d.activationRequired) pending.push({ c, n });
      continue;
    }
    const msg = (r.json && r.json.message) || r.text.slice(0, 300);
    if (r.status === 409 && /Tên đăng nhập đã tồn tại/i.test(msg)) {
      // Đã đăng ký ở lần chạy trước: thử đăng nhập để chắc tài khoản là của mình
      const s = new Session(http, n.adminUsername, password, 'quản trị');
      try {
        await s.login();
        step(c, `đã có từ trước: ${n.adminUsername} đăng nhập được, bỏ qua`);
      } catch (e) {
        if (e instanceof SeedError && /chưa được kích hoạt/.test(e.message)) {
          step(c, `đã đăng ký từ trước nhưng chưa kích hoạt qua email (${n.adminEmail})`);
          pending.push({ c, n });
        } else {
          throw new SeedError(`Tên đăng nhập ${n.adminUsername} đã có nhưng không đăng nhập được bằng DEMO_PASSWORD hiện tại. `
            + 'Có thể do lần chạy cũ dùng mật khẩu khác: chạy lại với --suffix mới.');
        }
      }
      continue;
    }
    throw new ApiError('POST', '/api/auth/companies/register', r.status, r.json && r.json.code, msg);
  }
  if (pending.length) {
    console.log('\nMáy chủ yêu cầu kích hoạt doanh nghiệp qua email. Việc cần làm tiếp:');
    for (const { c, n } of pending) console.log(`  - ${c.company.name}: mở hộp thư ${n.adminEmail}, bấm liên kết kích hoạt`);
    console.log('Sau khi kích hoạt đủ, chạy lại cùng tham số với lệnh populate, ví dụ:');
    console.log(`  node qa/demo/seed-demo.mjs populate --base-url ${opts.baseUrl}${opts.only ? ' --only ' + opts.only.join(',') : ''}${opts.suffix ? ' --suffix ' + opts.suffix : ''}`);
  }
  return pending.length;
}

// ------------------------------------------------------------------------------------------------
// populate
// ------------------------------------------------------------------------------------------------
class Populator {
  constructor(http, c, opts, password) {
    this.http = http; this.c = c; this.opts = opts; this.password = password;
    this.n = names(c, opts);
    this.admin = new Session(http, this.n.adminUsername, password, 'quản trị');
    this.t = new Tally();
    this.uoms = new Map(); this.categories = new Map(); this.warehouses = new Map(); this.locations = new Map();
    this.products = new Map(); this.suppliers = new Map(); this.customers = new Map(); this.users = new Map();
    this.custTx = new Map();
    this.stockExpect = new Map(); // "productId:warehouseId" -> { baseline, added }
  }

  log(msg) { step(this.c, msg); }

  async run() {
    const s = this.admin;
    const me = await s.login();
    this.log(`đăng nhập ${this.n.adminUsername} (doanh nghiệp ${me.company ? me.company.name + ' ' + me.company.code : '?'})`);
    await this.uomsAndCategories();
    await this.warehousesAndLocations();
    await this.ensureUsers();
    await this.ensureProducts();
    await this.ensurePartners();
    await this.purchaseOrders();
    await this.waitStock();
    await this.stocktakes();
    await this.transfers();
    await this.salesOrders();
    if (this.opts.alertScan) await this.alertScan();
    this.t.print(`Kết quả ${this.c.key} (${this.n.companyName}):`);
    return this.t;
  }

  // ---------- danh mục nền ----------
  async uomsAndCategories() {
    const s = this.admin;
    for (const u of await s.get('/api/products/uoms')) this.uoms.set(upper(u.code), u);
    for (const u of this.c.uoms || []) {
      if (this.uoms.has(upper(u.code))) { this.t.existing('Đơn vị tính'); continue; }
      const d = await s.post('/api/products/uoms', { code: u.code, name: u.name });
      this.uoms.set(upper(d.code), d); this.t.created('Đơn vị tính');
    }
    for (const x of await s.get('/api/products/categories')) this.categories.set(upper(x.code), x);
    for (const x of this.c.categories || []) {
      if (this.categories.has(upper(x.code))) { this.t.existing('Danh mục'); continue; }
      const d = await s.post('/api/products/categories', { code: x.code, name: x.name, description: x.description });
      this.categories.set(upper(d.code), d); this.t.created('Danh mục');
    }
    this.log(`đơn vị tính ${this.uoms.size}, danh mục ${this.categories.size}`);
  }

  async warehousesAndLocations() {
    const s = this.admin;
    for (const w of await s.get('/api/inventory/warehouses')) this.warehouses.set(upper(w.code), w);
    for (const w of this.c.warehouses) {
      let wh = this.warehouses.get(upper(w.code));
      if (wh) this.t.existing('Kho');
      else {
        wh = await s.post('/api/inventory/warehouses', {
          code: w.code, name: w.name, address: w.address, managerName: w.managerName, contactPhone: w.contactPhone,
          capacity: w.capacity, active: true
        });
        this.warehouses.set(upper(wh.code), wh); this.t.created('Kho');
      }
      let locs = await s.get(`/api/inventory/locations?warehouseId=${wh.id}`);
      const have = new Set(locs.map((l) => `${upper(l.zone)}-${upper(l.rack)}-${upper(l.bin)}`));
      for (const z of w.locations || []) {
        let missing = 0;
        for (let r = z.racks[0]; r <= z.racks[1]; r++) for (let b = z.bins[0]; b <= z.bins[1]; b++) {
          if (!have.has(`${upper(z.zone)}-R${String(r).padStart(2, '0')}-B${String(b).padStart(2, '0')}`)) missing++;
        }
        const total = (z.racks[1] - z.racks[0] + 1) * (z.bins[1] - z.bins[0] + 1);
        if (!missing) { for (let i = 0; i < total; i++) this.t.existing('Vị trí'); continue; }
        const created = await s.post('/api/inventory/locations/bulk', {
          warehouseId: wh.id, zone: z.zone, rackPrefix: 'R', rackFrom: z.racks[0], rackTo: z.racks[1],
          binPrefix: 'B', binFrom: z.bins[0], binTo: z.bins[1], maxWeight: z.maxWeight
        });
        for (let i = 0; i < created.length; i++) this.t.created('Vị trí');
        for (let i = created.length; i < total; i++) this.t.existing('Vị trí');
      }
      locs = await s.get(`/api/inventory/locations?warehouseId=${wh.id}`);
      const m = new Map();
      for (const l of locs) m.set(`${upper(l.zone)}-${upper(l.rack)}-${upper(l.bin)}`, l);
      this.locations.set(wh.id, m);
    }
    this.log(`kho ${this.c.warehouses.length}, vị trí ${[...this.locations.values()].reduce((a, m) => a + m.size, 0)}`);
  }

  wh(code) {
    const w = this.warehouses.get(upper(code));
    if (!w) throw new SeedError(`Không thấy kho ${code}`);
    return w;
  }

  loc(warehouseCode, spec) {
    const w = this.wh(warehouseCode);
    const m = this.locations.get(w.id);
    const parts = String(spec).toUpperCase().split('-');
    const key = parts.length === 3 ? `${parts[0]}-${parts[1]}-${parts[2]}` : null;
    const l = key && m.get(key);
    if (!l) throw new SeedError(`Không thấy vị trí ${spec} trong kho ${warehouseCode}`);
    return l;
  }

  async ensureUsers() {
    const s = this.admin;
    for (const u of this.c.users || []) {
      const username = this.n.userName(u);
      const found = await s.get(`/api/auth/users?keyword=${encodeURIComponent(username)}&size=20`);
      let user = ((found && found.content) || []).find((x) => x.username === username);
      const whIds = u.warehouses === 'all' ? this.c.warehouses.map((w) => this.wh(w.code).id)
        : (u.warehouses || []).map((w) => this.wh(w).id);
      if (user) { this.t.existing('Người dùng'); }
      else {
        user = await s.post('/api/auth/users', {
          username, email: this.n.userEmail(u), password: this.password, fullName: u.fullName, roles: u.roles, warehouseIds: whIds
        });
        this.t.created('Người dùng');
      }
      this.users.set(u.username, { ...user, roles: u.roles, session: new Session(this.http, username, this.password, u.roles.join(',')) });
    }
    this.log(`người dùng thêm ${this.users.size} (ngoài quản trị)`);
  }

  async ensureProducts() {
    const s = this.admin;
    for (const p of await s.all('/api/products')) this.products.set(upper(p.sku), p);
    for (const p of this.c.products) {
      if (this.products.has(upper(p.sku))) { this.t.existing('Hàng hóa'); continue; }
      const uom = this.uoms.get(upper(p.uom));
      const cat = p.category ? this.categories.get(upper(p.category)) : null;
      const d = await s.post('/api/products', {
        sku: p.sku, barcode: p.barcode, name: p.name, description: p.description, categoryId: cat ? cat.id : null, uomId: uom.id,
        purchasePrice: p.purchasePrice, sellingPrice: p.sellingPrice, vatRate: p.vatRate ?? 8,
        manageByLot: !!p.lot, manageByExpiration: !!p.expiry,
        safetyStock: p.safetyStock ?? 0, reorderPoint: p.reorderPoint ?? 0, maxStock: p.maxStock
      });
      this.products.set(upper(d.sku), d); this.t.created('Hàng hóa');
    }
    this.log(`hàng hóa ${this.c.products.length}`);
  }

  prod(sku) {
    const p = this.products.get(upper(sku));
    if (!p) throw new SeedError(`Không thấy hàng hóa ${sku}`);
    return p;
  }

  async ensurePartners() {
    const s = this.admin;
    for (const x of await s.all('/api/orders/suppliers')) this.suppliers.set(upper(x.code), x);
    for (const x of this.c.suppliers || []) {
      if (this.suppliers.has(upper(x.code))) { this.t.existing('Nhà cung cấp'); continue; }
      const d = await s.post('/api/orders/suppliers', {
        code: x.code, name: x.name, taxCode: x.taxCode, contactName: x.contactName, email: x.email, phone: x.phone,
        address: x.address, bankAccount: x.bankAccount, bankName: x.bankName, paymentTerms: x.paymentTerms,
        creditLimit: x.creditLimit, initialDebt: x.initialDebt
      });
      this.suppliers.set(upper(d.code), d); this.t.created('Nhà cung cấp');
    }
    for (const x of await s.all('/api/orders/customers')) this.customers.set(upper(x.code), x);
    for (const x of this.c.customers || []) {
      if (this.customers.has(upper(x.code))) { this.t.existing('Khách hàng'); continue; }
      const d = await s.post('/api/orders/customers', {
        code: x.code, name: x.name, customerType: x.customerType || 'RETAIL', taxCode: x.taxCode, contactName: x.contactName,
        email: x.email, phone: x.phone, address: x.address, paymentTerms: x.paymentTerms, creditLimit: x.creditLimit,
        initialDebt: x.initialDebt, notes: x.notes
      });
      this.customers.set(upper(d.code), d); this.t.created('Khách hàng');
    }
    this.log(`nhà cung cấp ${(this.c.suppliers || []).length}, khách hàng ${(this.c.customers || []).length}`);
  }

  // ---------- mua hàng, nhập kho theo lô, hóa đơn mua ----------
  async onHand(productId, warehouseId) {
    const d = await this.admin.get(`/api/inventory/stock/atp?productId=${productId}&warehouseId=${warehouseId}`);
    return num(d.onHandQuantity);
  }

  async expectStock(productId, warehouseId, qty) {
    const k = `${productId}:${warehouseId}`;
    if (!this.stockExpect.has(k)) this.stockExpect.set(k, { productId, warehouseId, baseline: await this.onHand(productId, warehouseId), added: 0 });
    this.stockExpect.get(k).added += qty;
  }

  async purchaseOrders() {
    const s = this.admin;
    for (const po of this.c.purchaseOrders || []) {
      const wh = this.wh(po.warehouse);
      const sup = this.suppliers.get(upper(po.supplier));
      const r = await s.call('GET', `/api/orders/purchase/number/${encodeURIComponent(po.number)}`, { allow: [404] });
      let cur = r.status === 404 ? null : r.data;
      if (cur) this.t.existing('Đơn mua');
      else {
        cur = await s.post('/api/orders/purchase', {
          poNumber: po.number, supplierId: sup.id, warehouseId: wh.id, warehouseName: wh.name,
          orderDate: vnDate(-(po.daysAgo || 0)), expectedDate: po.expectedInDays !== undefined ? vnDate(po.expectedInDays) : undefined,
          note: po.note, status: po.state === 'draft' ? 'DRAFT' : 'PENDING',
          items: po.items.map((it) => {
            const p = this.prod(it.sku);
            return {
              productId: p.id, skuCode: p.sku, productName: p.name, uom: p.uomName, quantity: it.qty,
              unitPrice: it.price ?? p.purchasePrice, taxRate: it.taxRate ?? p.vatRate, discountPercent: it.discountPercent
            };
          })
        });
        this.t.created('Đơn mua');
      }
      const state = po.state || 'received';
      if (state === 'cancelled' && ['DRAFT', 'PENDING'].includes(cur.status)) {
        cur = await s.post(`/api/orders/purchase/${cur.id}/cancel`); this.t.updated('Đơn mua: hủy');
      } else if (state === 'rejected' && cur.status === 'PENDING') {
        cur = await s.post(`/api/orders/purchase/${cur.id}/reject`, { reason: po.rejectReason || 'Giá chưa phù hợp, đề nghị nhà cung cấp báo giá lại' });
        this.t.updated('Đơn mua: từ chối');
      } else if (['approved', 'partial', 'received'].includes(state) && ['DRAFT', 'PENDING'].includes(cur.status)) {
        cur = await s.post(`/api/orders/purchase/${cur.id}/approve`); this.t.updated('Đơn mua: duyệt');
      }
      if (['partial', 'received'].includes(state)) await this.receipts(po, cur, wh);
      if (po.payment) await this.payBills(po, cur);
      this.log(`đơn mua ${po.number} (${state})`);
    }
  }

  async receipts(po, cur, wh) {
    const s = this.admin;
    const itemBySku = new Map(cur.items.map((it) => [upper(it.skuCode), it]));
    for (let i = 0; i < (po.receipts || []).length; i++) {
      const rc = po.receipts[i];
      const receiptDate = vnDate(-(rc.daysAgo || 0));
      const lines = rc.lines.map((l) => {
        const it = itemBySku.get(upper(l.sku));
        const lot = l.lot ? l.lot.replace('{date}', receiptDate.slice(2).replace(/-/g, '')) : undefined;
        return {
          poItemId: it.id, acceptedQuantity: l.qty, rejectedQuantity: l.rejected || 0, rejectedReason: l.rejectedReason,
          batchNumber: lot, mfgDate: l.mfgDaysAgo !== undefined ? vnDate(-l.mfgDaysAgo) : undefined,
          expDate: l.expInDays !== undefined ? vnDate(l.expInDays) : undefined,
          locationId: this.loc(po.warehouse, l.location).id
        };
      });
      const key = `${po.number}-NK${i + 1}`;
      // Lô đã hết hạn không tính vào tồn dùng được nên không chờ nó
      const counted = rc.lines.filter((l) => !(l.expInDays !== undefined && l.expInDays <= 0));
      // Đọc tồn trước khi gửi phiếu: inventory-service có thể ghi tồn ngay sau khi phiếu được lưu
      for (const l of counted) await this.expectStock(this.prod(l.sku).id, wh.id, 0);
      const r = await s.call('POST', '/api/orders/goods-receipt-notes', {
        body: { poId: cur.id, receiptDate, note: rc.note, items: lines }, headers: { 'Idempotency-Key': key }
      });
      if (r.status === 201) {
        this.t.created('Phiếu nhập kho');
        for (const l of counted) await this.expectStock(this.prod(l.sku).id, wh.id, l.qty);
      } else this.t.existing('Phiếu nhập kho');
    }
  }

  async payBills(po, cur) {
    const s = this.admin;
    const bills = await s.get(`/api/orders/purchase-bills/po/${cur.id}`);
    if (!bills.length) return;
    const paid = bills.reduce((a, b) => a + num(b.paidAmount), 0);
    if (paid > 0) { this.t.existing('Thanh toán hóa đơn mua'); return; }
    const total = bills.reduce((a, b) => a + num(b.totalAmount), 0);
    let left = money(po.payment.amount ?? total * po.payment.ratio);
    let n = 0;
    for (const b of bills) {
      if (left <= 0) break;
      const amount = Math.min(left, money(b.remainingAmount));
      if (amount <= 0) continue;
      n++;
      await s.post(`/api/orders/purchase-bills/${b.id}/payments`, {
        amount, paymentMethod: po.payment.method || 'BANK_TRANSFER', referenceCode: `UNC-${po.number}-${n}`,
        note: po.payment.note || `Thanh toán cho đơn ${po.number}`, paymentDate: vnDate(-(po.payment.daysAgo || 0))
      });
      left -= amount;
      this.t.created('Thanh toán hóa đơn mua');
    }
  }

  async waitStock() {
    const pending = new Set([...this.stockExpect.keys()].filter((k) => this.stockExpect.get(k).added > 0));
    if (!pending.size) return;
    this.log(`chờ inventory-service ghi tồn của ${pending.size} cặp hàng-kho vừa nhập`);
    const end = Date.now() + STOCK_WAIT_SECONDS * 1000;
    while (pending.size && Date.now() < end) {
      for (const k of [...pending]) {
        const e = this.stockExpect.get(k);
        if (await this.onHand(e.productId, e.warehouseId) >= e.baseline + e.added - 0.0005) pending.delete(k);
      }
      if (pending.size) await sleep(2000);
    }
    if (pending.size) {
      const k = [...pending][0];
      const e = this.stockExpect.get(k);
      throw new SeedError(`Tồn chưa cập nhật sau ${STOCK_WAIT_SECONDS} giây (sản phẩm ${e.productId}, kho ${e.warehouseId}, cần ${e.baseline + e.added}). Kiểm tra RabbitMQ và inventory-service rồi chạy lại populate.`);
    }
  }

  // ---------- kiểm kê ----------
  userWithRole(role) {
    for (const u of this.users.values()) if (u.roles.includes(role)) return u;
    return null;
  }

  async stocktakes() {
    const s = this.admin;
    if (!(this.c.stocktakes || []).length) return;
    const existing = await s.all('/api/inventory/stocktakes');
    for (const st of this.c.stocktakes) {
      const wh = this.wh(st.warehouse);
      let cur = existing.find((x) => (x.notes || '').includes(st.ref));
      if (cur) this.t.existing('Đợt kiểm kê');
      else {
        cur = await s.post('/api/inventory/stocktakes', {
          warehouseId: wh.id, scopeType: 'ZONES', zones: st.zones,
          plannedDate: vnDate(st.plannedInDays !== undefined ? st.plannedInDays : -(st.plannedDaysAgo || 0)),
          notes: `${st.notes} (${st.ref})`
        });
        this.t.created('Đợt kiểm kê');
      }
      if (st.state === 'completed') await this.completeStocktake(st, cur);
      this.log(`kiểm kê ${st.ref} (${st.state})`);
    }
  }

  async countLines(id) { return this.admin.all(`/api/inventory/stocktakes/${id}/count-lines`, 500); }

  async completeStocktake(st, cur) {
    const s = this.admin;
    const id = cur.id;
    const intended = new Map(); // lineId -> số đếm mong muốn
    const want = async () => {
      // Số hệ thống của từng dòng lấy từ báo cáo chênh lệch (chỉ có sau khi kết thúc đếm)
      const v = await s.get(`/api/inventory/stocktakes/${id}/variances?page=0&size=500`);
      const lines = (v.lines && v.lines.content) || [];
      const used = new Set();
      for (const l of lines) {
        let q = num(l.systemQuantity);
        const adj = (st.adjust || []).find((a) => !used.has(a.sku) && this.prod(a.sku).id === l.productId && q + a.delta >= 0);
        if (adj) { q += adj.delta; used.add(adj.sku); }
        intended.set(l.lineId, { q, reason: adj ? adj.reason : null, line: l });
      }
    };
    if (cur.status === 'DRAFT') { cur = await s.post(`/api/inventory/stocktakes/${id}/start`); this.t.updated('Kiểm kê: bắt đầu đếm'); }
    if (cur.status === 'COUNTING') {
      // Lượt đếm đầu (đếm mù): nhân viên đếm theo số thực tế trên kệ, ở đây lấy số lượng của lô tại vị trí; dòng
      // không tra được để trống, kết thúc với dòng chưa đếm tính bằng 0, báo cáo chênh lệch sẽ cho đếm lại đúng dòng đó
      const lots = await s.all(`/api/inventory/lots?warehouseId=${cur.warehouseId}&hasStock=true`, 500);
      const qty = new Map(lots.map((l) => [`${l.binLocationId}:${l.productId}:${l.lotNo || ''}`, num(l.quantity)]));
      const used = new Set();
      for (const line of await this.countLines(id)) {
        if (line.countedQuantity !== null && line.countedQuantity !== undefined) continue;
        const q = qty.get(`${line.binLocationId}:${line.productId}:${line.lotNo || ''}`);
        if (q === undefined) continue;
        const adj = (st.adjust || []).find((a) => !used.has(a.sku) && this.prod(a.sku).id === line.productId && q + a.delta >= 0);
        if (adj) used.add(adj.sku);
        await s.post(`/api/inventory/stocktakes/${id}/counts`, { lineId: line.lineId, quantity: q + (adj ? adj.delta : 0), mode: 'SET' });
        this.t.created('Kiểm kê: dòng đếm');
      }
      cur = await s.post(`/api/inventory/stocktakes/${id}/finish`, { confirmUncountedAsZero: true });
    }
    if (cur.status === 'COUNTED') {
      await want();
      const wrong = [...intended.entries()].filter(([, x]) => Math.abs(num(x.line.countedQuantity) - x.q) > 0.0005).map(([lid]) => lid);
      if (wrong.length) {
        await s.post(`/api/inventory/stocktakes/${id}/recount`, { lineIds: wrong });
        for (const lid of wrong) {
          await s.post(`/api/inventory/stocktakes/${id}/counts`, { lineId: lid, quantity: intended.get(lid).q, mode: 'SET' });
          this.t.created('Kiểm kê: dòng đếm');
        }
        cur = await s.post(`/api/inventory/stocktakes/${id}/finish`, { confirmUncountedAsZero: true });
      }
      for (const [lid, x] of intended) {
        if (x.reason) await s.put(`/api/inventory/stocktakes/${id}/lines/${lid}/reason`, { reason: x.reason });
      }
      cur = await s.post(`/api/inventory/stocktakes/${id}/approve`, { notes: st.managerNote || 'Đồng ý kết quả kiểm kê' });
      this.t.updated('Kiểm kê: quản lý duyệt');
    }
    if (cur.status === 'PENDING_ACCOUNTING') {
      const acc = this.userWithRole('ACCOUNTANT');
      if (!acc) throw new SeedError(`Kiểm kê ${st.ref} cần tài khoản ACCOUNTANT để duyệt cấp kế toán (người duyệt hai cấp phải khác nhau)`);
      cur = (await acc.session.call('POST', `/api/inventory/stocktakes/${id}/adjustment/approve`)).data;
      this.t.updated('Kiểm kê: kế toán duyệt');
    }
    if (cur.status !== 'COMPLETED') throw new SeedError(`Kiểm kê ${st.ref} dừng ở trạng thái ${cur.status}`);
  }

  // ---------- điều chuyển ----------
  async transfers() {
    const s = this.admin;
    if (!(this.c.transfers || []).length) return;
    const existing = await s.all('/api/inventory/transfers');
    for (const t of this.c.transfers) {
      const from = this.wh(t.from);
      const to = this.wh(t.to);
      let cur = existing.find((x) => (x.notes || '').includes(`(${t.ref})`));
      if (cur) this.t.existing('Lệnh điều chuyển');
      else {
        cur = await s.post('/api/inventory/transfers', {
          sourceWarehouseId: from.id, destinationWarehouseId: to.id,
          expectedDeliveryDate: t.expectedInDays !== undefined ? vnInstant(t.expectedInDays) : undefined,
          notes: `${t.notes} (${t.ref})`,
          items: t.items.map((it) => {
            const p = this.prod(it.sku);
            return { productId: p.id, skuCode: p.sku, productName: p.name, uom: p.uomName, quantity: it.qty };
          })
        });
        this.t.created('Lệnh điều chuyển');
      }
      if (t.state === 'cancelled' && cur.status === 'DRAFT') {
        cur = await s.post(`/api/inventory/transfers/${cur.id}/cancel`, { reason: t.cancelReason || 'Kho đích đã tự nhập hàng từ nhà cung cấp' });
        this.t.updated('Điều chuyển: hủy');
      }
      if (['in_transit', 'completed', 'partial'].includes(t.state) && cur.status === 'DRAFT') {
        cur = await s.post(`/api/inventory/transfers/${cur.id}/dispatch`); this.t.updated('Điều chuyển: xuất');
      }
      if (['completed', 'partial'].includes(t.state) && cur.status === 'IN_TRANSIT') {
        const bin = this.loc(t.to, t.toLocation);
        cur = await s.post(`/api/inventory/transfers/${cur.id}/receive`, {
          notes: t.receiveNotes || 'Đã kiểm đếm khi nhận hàng',
          items: cur.items.map((it) => {
            const spec = t.items.find((x) => upper(x.sku) === upper(it.skuCode)) || {};
            const shipped = num(it.transferredQuantity || it.quantity);
            const received = spec.received ?? shipped;
            return {
              itemId: it.id, receivedQuantity: received, damagedQuantity: spec.damaged || 0,
              destinationBinLocationId: bin.id, notes: spec.note
            };
          })
        });
        this.t.updated('Điều chuyển: nhận');
      }
      this.log(`điều chuyển ${t.ref} ${t.from} -> ${t.to}: ${cur.status}`);
    }
  }

  // ---------- bán hàng, xuất kho, hóa đơn, thu tiền ----------
  async customerTx(customerId) {
    if (!this.custTx.has(customerId)) {
      this.custTx.set(customerId, await this.admin.all(`/api/orders/customers/${customerId}/transactions`));
    }
    return this.custTx.get(customerId);
  }

  async salesOrders() {
    const s = this.admin;
    for (const so of this.c.salesOrders || []) {
      const wh = this.wh(so.warehouse);
      const cus = this.customers.get(upper(so.customer));
      const spec = (this.c.customers || []).find((x) => upper(x.code) === upper(so.customer)) || {};
      const state = so.state;
      const r = await s.call('GET', `/api/orders/sales/number/${encodeURIComponent(so.number)}`, { allow: [404] });
      let cur = r.status === 404 ? null : r.data;
      if (cur) this.t.existing('Đơn bán');
      else {
        cur = await s.post('/api/orders/sales', {
          soNumber: so.number, customerId: cus.id, warehouseId: wh.id, warehouseName: wh.name,
          deliveryAddress: so.deliveryAddress || spec.address, recipientName: so.recipientName || spec.contactName,
          recipientPhone: so.recipientPhone || spec.phone, orderDate: vnDate(-(so.daysAgo || 0)),
          expectedDate: so.expectedInDays !== undefined ? vnDate(so.expectedInDays) : undefined, note: so.note,
          status: state === 'draft' ? 'DRAFT' : 'CONFIRMED', allowBackorder: state === 'backorder',
          items: so.items.map((it) => {
            const p = this.prod(it.sku);
            return {
              productId: p.id, skuCode: p.sku, productName: p.name, uom: p.uomName, quantity: it.qty,
              unitPrice: it.price ?? p.sellingPrice, taxRate: it.taxRate ?? p.vatRate, discountPercent: it.discountPercent
            };
          })
        });
        this.t.created('Đơn bán');
        if (state === 'backorder' && cur.status !== 'BACKORDER') {
          console.warn(`[${this.c.key}] Cảnh báo: đơn ${so.number} khai báo thiếu hàng nhưng máy chủ xác nhận ${cur.status}`);
        }
      }
      if (state === 'cancelled' && !['CANCELLED', 'COMPLETED'].includes(cur.status)) {
        cur = await s.post(`/api/orders/sales/${cur.id}/cancel`); this.t.updated('Đơn bán: hủy');
      }
      if (['completed', 'partial'].includes(state)) cur = await this.deliveries(so, cur);
      if (so.invoice) await this.invoice(so, cur, cus);
      this.log(`đơn bán ${so.number} (${state}): ${cur.status}`);
    }
  }

  async deliveries(so, cur) {
    const s = this.admin;
    const done = (await s.get(`/api/orders/goods-delivery-notes/so/${cur.id}`)).filter((g) => g.status !== 'CANCELLED');
    // Phiếu còn PICKING là phiếu của lần chạy trước mà order-service chưa xác nhận được việc trừ tồn: xuất lại đúng phiếu đó
    if (await this.redispatch(done)) cur = await s.get(`/api/orders/sales/${cur.id}`);
    const plan = so.deliveries && so.deliveries.length ? so.deliveries : [{ daysAgo: Math.max(0, (so.daysAgo || 0) - 1) }];
    for (let i = done.length; i < plan.length; i++) {
      if (cur.status === 'COMPLETED') break;
      const d = plan[i];
      const items = [];
      for (const it of cur.items) {
        const line = d.lines ? d.lines.find((l) => upper(l.sku) === upper(it.skuCode)) : null;
        if (d.lines && !line) continue;
        const qty = line ? line.qty : num(it.quantity) - (i === 0 ? 0 : plan.slice(0, i).reduce((a, p) => {
          const x = (p.lines || []).find((l) => upper(l.sku) === upper(it.skuCode));
          return a + (x ? x.qty : 0);
        }, 0));
        if (qty > 0) items.push({ soItemId: it.id, productId: it.productId, skuCode: it.skuCode, productName: it.productName, uom: it.uom, deliveredQuantity: qty });
      }
      if (!items.length) continue;
      try {
        await s.post('/api/orders/goods-delivery-notes', {
          soId: cur.id, deliveryDate: vnDate(-(d.daysAgo || 0)), note: d.note || `Giao hàng cho đơn ${so.number}`, items
        });
      } catch (e) {
        // 503: phiếu đã được lưu ở PICKING nhưng chưa xác nhận được việc trừ tồn (dịch vụ kho trả lời chậm).
        // Không lập phiếu mới, xuất lại đúng phiếu đó; thao tác này dùng chung một khóa nên không trừ tồn hai lần.
        if (!(e instanceof ApiError) || e.status !== 503) throw e;
        const open = (await s.get(`/api/orders/goods-delivery-notes/so/${cur.id}`)).filter((g) => g.status !== 'CANCELLED');
        if (!(await this.redispatch(open))) throw e;
      }
      this.t.created('Phiếu xuất kho');
      cur = await s.get(`/api/orders/sales/${cur.id}`);
    }
    return cur;
  }

  // Xuất lại các phiếu xuất kho đang PICKING, thử vài lần khi máy chủ còn chậm. Trả về số phiếu đã xuất lại.
  async redispatch(gdns) {
    let n = 0;
    for (const g of gdns.filter((x) => x.status === 'PICKING')) {
      for (let attempt = 1; ; attempt++) {
        try {
          await this.admin.post(`/api/orders/goods-delivery-notes/${g.id}/dispatch`);
          break;
        } catch (e) {
          if (!isTransient(e) || attempt >= 6) throw e;
          this.log(`phiếu xuất ${g.gdnNumber} chưa xác nhận được việc trừ tồn, thử lại lần ${attempt + 1}`);
          await sleep(5000 * attempt);
        }
      }
      this.t.updated('Phiếu xuất kho: xuất lại');
      this.log(`phiếu xuất ${g.gdnNumber} đã xuất lại xong`);
      n++;
    }
    return n;
  }

  async invoice(so, cur, cus) {
    const s = this.admin;
    const txs = await this.customerTx(cus.id);
    const ref = `INV-${so.number}`;
    let changed = false;
    if (txs.some((x) => x.transactionType === 'SALES_INVOICE' && upper(x.referenceCode) === ref)) this.t.existing('Hóa đơn bán');
    else {
      await s.post(`/api/orders/customers/${cus.id}/transactions`, {
        type: 'SALES_INVOICE', amount: money(cur.totalAmount), referenceCode: ref, note: `Xuất hóa đơn bán hàng cho đơn ${so.number}`
      });
      this.t.created('Hóa đơn bán'); changed = true;
    }
    for (let i = 0; i < (so.payments || []).length; i++) {
      const p = so.payments[i];
      const pref = `PT-${so.number}-${i + 1}`;
      if (txs.some((x) => x.transactionType === 'PAYMENT' && upper(x.referenceCode) === pref)) { this.t.existing('Phiếu thu'); continue; }
      await s.post(`/api/orders/customers/${cus.id}/transactions`, {
        type: 'PAYMENT', amount: money(p.amount ?? num(cur.totalAmount) * p.ratio), referenceCode: pref,
        note: p.note || `Thu tiền đơn ${so.number}${p.method ? ' (' + p.method + ')' : ''}`
      });
      this.t.created('Phiếu thu'); changed = true;
    }
    if (changed) this.custTx.delete(cus.id);
  }

  async alertScan() {
    const r = await this.admin.post('/api/inventory/alerts/scan');
    const parts = (r.results || []).map((k) => `${k.kind} ${k.status}${k.itemCount ? ' (' + k.itemCount + ' dòng)' : ''}`);
    this.log(`quét cảnh báo tồn kho: ${parts.join(', ') || JSON.stringify(r).slice(0, 200)}`);
  }
}

// ------------------------------------------------------------------------------------------------
// status
// ------------------------------------------------------------------------------------------------
const SO_STATUSES = ['DRAFT', 'CONFIRMED', 'BACKORDER', 'COMPLETED', 'CANCELLED'];

async function status(http, companies, opts, password) {
  const rows = [];
  for (const c of companies) {
    const n = names(c, opts);
    const s = new Session(http, n.adminUsername, password, 'quản trị');
    try { await s.login(); } catch (e) { rows.push({ key: c.key, error: e.message }); continue; }
    const whs = await s.get('/api/inventory/warehouses');
    let locs = 0;
    for (const w of whs) locs += (await s.get(`/api/inventory/locations?warehouseId=${w.id}`)).length;
    const so = {};
    for (const st of SO_STATUSES) so[st] = await s.total(`/api/orders/sales?status=${st}`);
    const alerts = await s.get('/api/inventory/alerts/summary');
    const tr = await s.get('/api/inventory/transfers?page=0&size=1');
    rows.push({
      key: c.key, company: n.companyName, users: await s.total('/api/auth/users'), warehouses: whs.length, locations: locs,
      products: await s.total('/api/products'), suppliers: await s.total('/api/orders/suppliers'),
      customers: await s.total('/api/orders/customers'), po: await s.total('/api/orders/purchase'), so,
      transfers: num(tr.totalElements), stocktakes: await s.total('/api/inventory/stocktakes'),
      alerts: num(alerts.total), alertKinds: alerts.countsByKind || {}
    });
  }
  console.log('');
  const head = ['Doanh nghiệp', 'ND', 'Kho', 'Vị trí', 'Hàng', 'NCC', 'KH', 'Đơn mua', 'Đơn bán (nháp/xác nhận/thiếu/xong/hủy)', 'Đ.chuyển', 'K.kê', 'Cảnh báo'];
  const w = [16, 4, 5, 7, 6, 5, 5, 8, 38, 9, 6, 9];
  console.log(head.map((h, i) => pad(h, w[i])).join(' '));
  for (const r of rows) {
    if (r.error) { console.log(`${pad(r.key, 16)} LỖI: ${r.error}`); continue; }
    const soStr = SO_STATUSES.map((x) => r.so[x]).join('/');
    console.log([r.key, r.users, r.warehouses, r.locations, r.products, r.suppliers, r.customers, r.po, soStr, r.transfers, r.stocktakes, r.alerts]
      .map((v, i) => pad(v, w[i])).join(' '));
  }
  for (const r of rows) {
    if (r.error) continue;
    const kinds = Object.entries(r.alertKinds).map(([k, v]) => `${k} ${v}`).join(', ');
    console.log(`  ${r.key}: cảnh báo theo loại: ${kinds || 'không có'}`);
  }
  return rows.some((r) => r.error) ? 1 : 0;
}

// ------------------------------------------------------------------------------------------------
// main
// ------------------------------------------------------------------------------------------------
async function main() {
  let opts;
  try { opts = parseArgs(process.argv.slice(2)); } catch (e) { console.error(e.message); return 2; }
  const password = process.env.DEMO_PASSWORD;
  if (!password) {
    console.error('Chưa đặt biến môi trường DEMO_PASSWORD (mật khẩu chung của các tài khoản demo). Xem qa/demo/README.md mục 3.');
    return 2;
  }
  if (password.length < 8 || password.length > 72) {
    console.error('DEMO_PASSWORD phải dài từ 8 đến 72 ký tự.');
    return 2;
  }
  const companies = loadCompanies(opts.only);
  for (const c of companies) validateData(c);

  const http = new Http(opts.baseUrl);
  console.log(`Máy chủ: ${opts.baseUrl}; doanh nghiệp: ${companies.map((c) => c.key).join(', ')}${opts.suffix ? `; hậu tố: ${opts.suffix}` : ''}`);
  const plans = await http.raw('GET', '/api/auth/plans');
  if (plans.status !== 200) throw new ApiError('GET', '/api/auth/plans', plans.status, plans.json && plans.json.code, 'không đọc được danh sách gói');
  const trial = (plans.data || []).find((p) => p.code === 'TRIAL');
  if (trial) console.log(`Giới hạn gói TRIAL: ${trial.maxUsers} người dùng, ${trial.maxWarehouses} kho, SKU ${trial.maxSkus < 0 ? 'không giới hạn' : trial.maxSkus}, ${trial.trialDays} ngày`);
  for (const c of companies) checkTrialLimits(c, trial);

  if (opts.command === 'status') return status(http, companies, opts, password);
  if (opts.command === 'register' || opts.command === 'all') {
    const pending = await register(http, companies, opts, password);
    if (opts.command === 'register' || pending) return 0;
  }
  const started = Date.now();
  let created = 0;
  for (const c of companies) {
    // Lỗi tạm thời thì chạy lại cả doanh nghiệp: mọi bước đều kiểm tra cái đã có nên không tạo trùng
    for (let attempt = 1; ; attempt++) {
      try {
        const t = await new Populator(http, c, opts, password).run();
        created += t.totalCreated();
        break;
      } catch (e) {
        if (!isTransient(e) || attempt >= 4) throw e;
        step(c, `máy chủ trả lỗi tạm thời (${e.method} ${e.path}, HTTP ${e.status}); chờ ${20 * attempt} giây rồi chạy lại doanh nghiệp này (lần ${attempt + 1})`);
        await sleep(20000 * attempt);
      }
    }
  }
  console.log(`\nXong populate sau ${Math.round((Date.now() - started) / 1000)} giây, ${http.count} request, ${created} bản ghi tạo mới hoặc chuyển trạng thái.`);
  return 0;
}

main().then((code) => process.exit(code), (e) => {
  if (e instanceof ApiError) {
    console.error(`\nLỖI API: ${e.method} ${e.path}\n  HTTP ${e.status}, mã ${e.code || '(không có)'}\n  ${e.apiMessage}`);
    if (e.data) console.error(`  dữ liệu kèm theo: ${JSON.stringify(e.data).slice(0, 600)}`);
  } else if (e instanceof SeedError) {
    console.error(`\nDỪNG: ${e.message}`);
  } else {
    console.error(e && e.stack ? e.stack : e);
  }
  process.exit(1);
});
