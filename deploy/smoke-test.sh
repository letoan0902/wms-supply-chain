#!/usr/bin/env bash
# ============================================================================
# smoke-test.sh - Kiểm tra nhanh (smoke test) một bản triển khai WMS Supply Chain.
#
# Cách dùng:
#   bash deploy/smoke-test.sh [BASE_URL] [USERNAME] [PASSWORD]
#
#   BASE_URL  mặc định https://wms.toanmarket.me (hoặc biến môi trường BASE_URL)
#               https://...             đi đúng đường của người dùng: Caddy -> nginx của frontend -> api-gateway
#               http://127.0.0.1:8080   kiểm tra nội bộ trên máy chủ: gọi thẳng api-gateway
#   USERNAME  mặc định admin (hoặc biến môi trường SMOKE_USERNAME)
#   PASSWORD  mặc định lấy từ biến môi trường SMOKE_PASSWORD; nếu trống và đang chạy trong terminal thì script hỏi.
#             Truyền mật khẩu qua tham số sẽ lộ trong lịch sử lệnh và danh sách tiến trình.
#
# Biến môi trường tùy chọn:
#   SMOKE_TIMEOUT   số giây tối đa cho mỗi request (mặc định 10)
#   SMOKE_INSECURE  đặt 1 để bỏ kiểm tra chứng chỉ TLS (chỉ dùng khi Caddy đang lấy chứng chỉ staging của Let's Encrypt)
#
# Các bước, mỗi bước in PASS, FAIL hoặc SKIP kèm mã HTTP và thời gian phản hồi:
#   GET  /                             200, trang React (chỉ khi BASE_URL là https://)
#   POST /api/auth/login               đăng nhập đúng: 200 và có data.accessToken
#   POST /api/auth/login               sai mật khẩu: 401 (chỉ gọi 1 lần)
#   GET  /api/auth/me                  200 với Bearer token
#   GET  /api/inventory/warehouses     200 với Bearer token
#   GET  /api/products?page=0&size=1   200 với Bearer token
#   GET  /actuator/health              200 và status UP (chỉ khi BASE_URL là http://, tức kiểm tra nội bộ)
#
# auth-service khóa tài khoản và IP 30 phút sau 5 lần đăng nhập sai. Vì vậy bước sai mật khẩu chỉ chạy khi bước
# đăng nhập đúng đã thành công, và ngay sau đó script đăng nhập đúng thêm một lần để xóa bộ đếm lần sai.
#
# Yêu cầu: bash, curl, và python3 (ưu tiên) hoặc jq để đọc JSON.
# Mã thoát: 0 khi không có bước FAIL, 1 khi có bước FAIL, 2 khi thiếu công cụ hoặc tham số không hợp lệ.
# Hướng dẫn chi tiết: deploy/SMOKE_TEST.md
# ============================================================================

# Dừng sớm khi bị gọi bằng sh hoặc dash: phần còn lại dùng cú pháp riêng của bash
if [ -z "${BASH_VERSION:-}" ]; then
  echo "LỖI: hãy chạy bằng bash, ví dụ: bash deploy/smoke-test.sh" >&2
  exit 2
fi

set -uo pipefail

# --- Cấu hình ---------------------------------------------------------------
DEFAULT_BASE_URL="https://wms.toanmarket.me"
DEFAULT_USERNAME="admin"
USER_AGENT="wms-smoke-test"
TIMEOUT="${SMOKE_TIMEOUT:-10}"
CURL_INSECURE=""
if [[ "${SMOKE_INSECURE:-}" == 1 ]]; then
  CURL_INSECURE=1
fi

# --- Trạng thái -------------------------------------------------------------
MODE=""            # public: BASE_URL https:// (qua Caddy); internal: BASE_URL http:// (thẳng tới api-gateway)
ORIGIN=""          # origin của BASE_URL, gửi kèm request đăng nhập ở chế độ public giống trình duyệt
LOGIN_USER=""
LOGIN_PASS=""
WRONG_PASS=""
JSON_TOOL=""       # python3 hoặc jq
TMP_DIR=""
BODY_FILE=""
ERR_FILE=""
ACCESS_TOKEN=""
TOKEN_USER=""
UNREACHABLE=0      # 1 khi không kết nối được tới BASE_URL: các bước sau được bỏ qua thay vì chờ hết timeout

HTTP_CODE="---"
HTTP_MS="-"
CURL_RC=0
CURL_MSG=""
REDIRECT_URL=""

STEP_NO=0
STEP_TOTAL=6
STEP_DESC=""
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

# --- Hàm tiện ích chung -----------------------------------------------------
die() {
  printf 'LỖI: %s\n' "$*" >&2
  exit 2
}

usage() {
  cat <<'EOF'
Cách dùng: bash deploy/smoke-test.sh [BASE_URL] [USERNAME] [PASSWORD]

  BASE_URL   mặc định https://wms.toanmarket.me (hoặc biến môi trường BASE_URL)
               https://...            kiểm tra qua tên miền: Caddy -> nginx -> api-gateway, có bước trang chủ
               http://127.0.0.1:8080  kiểm tra nội bộ trên máy chủ: gọi thẳng api-gateway, có bước /actuator/health
  USERNAME   mặc định admin (hoặc biến môi trường SMOKE_USERNAME)
  PASSWORD   mặc định lấy từ biến môi trường SMOKE_PASSWORD; trống thì script hỏi khi chạy trong terminal

Biến môi trường tùy chọn:
  SMOKE_TIMEOUT=10   số giây tối đa cho mỗi request
  SMOKE_INSECURE=1   bỏ kiểm tra chứng chỉ TLS (chỉ khi Caddy dùng CA staging của Let's Encrypt)

Ví dụ:
  bash deploy/smoke-test.sh                                    # hỏi mật khẩu của admin
  bash deploy/smoke-test.sh http://127.0.0.1:8080              # kiểm tra nội bộ trên máy chủ
  SMOKE_PASSWORD='<mật khẩu>' bash deploy/smoke-test.sh https://wms.toanmarket.me manager

Mã thoát: 0 khi không có bước FAIL, 1 khi có bước FAIL, 2 khi thiếu công cụ hoặc tham số không hợp lệ.
EOF
}

cleanup() {
  if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then
    rm -rf "$TMP_DIR"
  fi
}

parse_args() {
  case "${1:-}" in
    -h | --help) usage; exit 0 ;;
    -*) usage >&2; die "Tham số không hợp lệ: $1" ;;
  esac
  if (( $# > 3 )); then
    usage >&2
    die "Quá nhiều tham số (tối đa 3: BASE_URL USERNAME PASSWORD)."
  fi

  BASE_URL="${1:-${BASE_URL:-$DEFAULT_BASE_URL}}"
  LOGIN_USER="${2:-${SMOKE_USERNAME:-$DEFAULT_USERNAME}}"
  LOGIN_PASS="${3:-${SMOKE_PASSWORD:-}}"

  while [[ "$BASE_URL" == */ ]]; do
    BASE_URL="${BASE_URL%/}"
  done
  case "$BASE_URL" in
    https://?*) MODE=public ;;
    http://?*) MODE=internal ;;
    *) die "BASE_URL phải bắt đầu bằng https:// hoặc http:// (hiện là: '$BASE_URL')." ;;
  esac
  local rest="${BASE_URL#*://}"
  ORIGIN="${BASE_URL%%://*}://${rest%%/*}"

  [[ "$TIMEOUT" =~ ^[1-9][0-9]*$ ]] || die "SMOKE_TIMEOUT phải là số giây nguyên dương (hiện là: '$TIMEOUT')."
  [[ -n "$LOGIN_USER" ]] || die "USERNAME không được để trống."
}

check_tools() {
  command -v curl > /dev/null 2>&1 || die "Chưa cài curl. Trên Ubuntu: sudo apt-get install -y curl"
  # Chạy thử python3 thay vì chỉ kiểm tra tên lệnh (trên Windows, python3 có thể chỉ là lối tắt tới Microsoft Store)
  if command -v python3 > /dev/null 2>&1 \
    && python3 -I -c 'import json, sys; sys.exit(0 if sys.version_info >= (3, 6) else 1)' > /dev/null 2>&1; then
    JSON_TOOL=python3
  elif command -v jq > /dev/null 2>&1; then
    JSON_TOOL=jq
  else
    die "Cần python3 (3.6 trở lên) hoặc jq để đọc phản hồi JSON nhưng không tìm thấy. Trên Ubuntu: sudo apt-get install -y python3 (hoặc: sudo apt-get install -y jq)"
  fi
}

read_password() {
  if [[ -z "$LOGIN_PASS" && -t 0 ]]; then
    read -r -s -p "Mật khẩu của tài khoản $LOGIN_USER: " LOGIN_PASS
    printf '\n' >&2
  fi
  [[ -n "$LOGIN_PASS" ]] \
    || die "Chưa có mật khẩu: đặt biến môi trường SMOKE_PASSWORD hoặc truyền tham số thứ ba (xem deploy/SMOKE_TEST.md, mục 1)."
  WRONG_PASS="smoke-test-wrong-password-$$"
  if [[ "$WRONG_PASS" == "$LOGIN_PASS" ]]; then
    WRONG_PASS="${WRONG_PASS}-x"
  fi
}

make_tmp() {
  TMP_DIR="$(mktemp -d 2> /dev/null || mktemp -d -t wms-smoke 2> /dev/null)" || TMP_DIR=""
  [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]] || die "Không tạo được thư mục tạm bằng mktemp."
  BODY_FILE="$TMP_DIR/body"
  ERR_FILE="$TMP_DIR/stderr"
}

# Đổi thời gian của curl (giây, ví dụ 0.123456; dấu thập phân có thể là dấu phẩy tùy locale) sang mili giây
to_ms() {
  local t="${1:-}" sec frac
  if [[ ! "$t" =~ ^[0-9]+([.,][0-9]+)?$ ]]; then
    printf '?'
    return
  fi
  sec="${t%%[.,]*}"
  frac="${t#"$sec"}"
  frac="${frac#[.,]}000"
  printf '%d' "$(( 10#$sec * 1000 + 10#${frac:0:3} ))"
}

# Thành công nếu thời gian bằng 0, nghĩa là curl chưa mở được kết nối TCP
is_zero_time() {
  [[ "${1:-}" =~ ^0*([.,]0*)?$ ]]
}

# --- Gửi request ------------------------------------------------------------
# http_request <đường dẫn> <tham số curl...>
# Gọi ${BASE_URL}<đường dẫn>, ghi thân phản hồi vào BODY_FILE và gán HTTP_CODE, HTTP_MS, CURL_RC, CURL_MSG, REDIRECT_URL.
# Mật khẩu và token được đưa qua stdin (@-) để không xuất hiện trong danh sách tiến trình.
http_request() {
  local path="$1" out="" rc=0 time_total="" time_connect=""
  shift
  : > "$BODY_FILE"
  : > "$ERR_FILE"
  out="$(curl -sS -o "$BODY_FILE" -w '%{http_code}|%{time_total}|%{time_connect}|%{redirect_url}' \
    --max-time "$TIMEOUT" -A "$USER_AGENT" ${CURL_INSECURE:+--insecure} "$@" "${BASE_URL}${path}" 2> "$ERR_FILE")" || rc=$?
  IFS='|' read -r HTTP_CODE time_total time_connect REDIRECT_URL <<< "$out"
  HTTP_CODE="${HTTP_CODE:-000}"
  HTTP_MS="$(to_ms "$time_total")"
  CURL_RC=$rc
  CURL_MSG=""
  if (( rc != 0 )); then
    CURL_MSG="$(head -n 1 "$ERR_FILE")"
    CURL_MSG="${CURL_MSG:-curl kết thúc với mã $rc}"
    case "$rc" in
      6 | 7 | 35 | 51 | 58 | 60) UNREACHABLE=1 ;;
      28) if is_zero_time "$time_connect"; then UNREACHABLE=1; fi ;;
    esac
  fi
  return 0
}

# --- Đọc JSON (python3, hoặc jq khi không có python3) -----------------------
# json_query <get|len|down> [đường dẫn dạng a.b.c] < tệp JSON
#   get   giá trị tại đường dẫn: chuỗi và số in nguyên dạng, boolean in true/false, mảng nối bằng dấu phẩy
#   len   số phần tử của mảng hoặc object tại đường dẫn
#   down  các thành phần health có status khác UP, dạng ten=STATUS (đường dẫn trỏ tới object components)
# Đầu vào không phải JSON thì không in gì.
PY_JSON_QUERY='
import json, sys
mode = sys.argv[1]
path = sys.argv[2] if len(sys.argv) > 2 else ""
try:
    value = json.load(sys.stdin.buffer)
except Exception:
    sys.exit(3)
for key in [k for k in path.split(".") if k]:
    value = value.get(key) if isinstance(value, dict) else None
if mode == "len":
    value = len(value) if isinstance(value, (list, dict)) else None
elif mode == "down":
    if isinstance(value, dict):
        value = ["%s=%s" % (k, v.get("status")) for k, v in sorted(value.items())
                 if isinstance(v, dict) and v.get("status") != "UP"]
    else:
        value = None
if value is None:
    text = ""
elif isinstance(value, bool):
    text = "true" if value else "false"
elif isinstance(value, list):
    text = ",".join(str(item) for item in value)
elif isinstance(value, dict):
    text = json.dumps(value, ensure_ascii=False)
else:
    text = str(value)
sys.stdout.buffer.write(text.encode("utf-8"))
'

JQ_JSON_QUERY='
(try getpath($p) catch null) as $v
| (if $m == "len" then
     (if ($v | type) == "array" or ($v | type) == "object" then ($v | length) else null end)
   elif $m == "down" then
     (if ($v | type) == "object"
      then [$v | to_entries[] | select((.value | type) == "object" and .value.status != "UP") | "\(.key)=\(.value.status)"]
      else null end)
   else $v end)
| if . == null then ""
  elif type == "array" then map(tostring) | join(",")
  elif type == "object" then tojson
  else tostring end
'

json_query() {
  local mode="$1" path="${2:-}" q='"'
  if [[ "$JSON_TOOL" == python3 ]]; then
    python3 -I -c "$PY_JSON_QUERY" "$mode" "$path"
  elif [[ -n "$path" ]]; then
    jq -r --arg m "$mode" --argjson p "[${q}${path//./${q},${q}}${q}]" "$JQ_JSON_QUERY"
  else
    jq -r --arg m "$mode" --argjson p '[]' "$JQ_JSON_QUERY"
  fi
}

# Đọc một trường hoặc đếm phần tử trong phản hồi vừa nhận
json_field() { json_query get "$1" < "$BODY_FILE" 2> /dev/null; }
json_count() { json_query len "$1" < "$BODY_FILE" 2> /dev/null; }

# Thân request đăng nhập, khớp record LoginRequest(username, password) của auth-service.
# Mật khẩu đi qua biến môi trường của tiến trình con, không nằm trong tham số dòng lệnh.
PY_LOGIN_BODY='
import json, os, sys
body = {"username": os.environ.get("SMOKE_LOGIN_USER", ""), "password": os.environ.get("SMOKE_LOGIN_PASS", "")}
sys.stdout.buffer.write(json.dumps(body).encode("utf-8"))
'

login_body() {
  if [[ "$JSON_TOOL" == python3 ]]; then
    SMOKE_LOGIN_USER="$1" SMOKE_LOGIN_PASS="$2" python3 -I -c "$PY_LOGIN_BODY"
  else
    SMOKE_LOGIN_USER="$1" SMOKE_LOGIN_PASS="$2" jq -cn '{username: env.SMOKE_LOGIN_USER, password: env.SMOKE_LOGIN_PASS}'
  fi
}

# login_request <mật khẩu>: POST /api/auth/login với tài khoản LOGIN_USER
login_request() {
  local body
  body="$(login_body "$LOGIN_USER" "$1")"
  if [[ "$MODE" == public ]]; then
    # Trình duyệt luôn gửi Origin với request POST; api-gateway so khớp với CORS_ALLOWED_ORIGINS, sai thì trả 403
    http_request /api/auth/login -H 'Content-Type: application/json' -H 'Accept: application/json' \
      -H "Origin: $ORIGIN" --data @- <<< "$body"
  else
    http_request /api/auth/login -H 'Content-Type: application/json' -H 'Accept: application/json' \
      --data @- <<< "$body"
  fi
}

# api_get <đường dẫn>: GET kèm header Authorization: Bearer <token>
api_get() {
  http_request "$1" -H 'Accept: application/json' -H @- <<< "Authorization: Bearer $ACCESS_TOKEN"
}

# --- Diễn giải phản hồi -----------------------------------------------------
body_snippet() {
  if [[ ! -s "$BODY_FILE" ]]; then
    printf 'Phản hồi rỗng.'
    return
  fi
  local text
  text="$(head -c 600 "$BODY_FILE" | LC_ALL=C tr -d '\000-\010\013\014\016-\037' | LC_ALL=C tr '\t\r\n' '   ' | tr -s ' ')"
  text="${text:0:300}"
  printf 'Phản hồi: %s' "${text% }"
}

# Thông báo lỗi trong phản hồi: message (ApiResponse) hoặc error (lỗi mặc định của Spring), nếu không thì đoạn đầu thân phản hồi
response_summary() {
  local message code error cr=$'\r' nl=$'\n'
  message="$(json_field message)"
  if [[ -n "$message" ]]; then
    code="$(json_field code)"
    message="${message//$cr/ }"
    printf 'Phản hồi: %s%s' "${code:+$code: }" "${message//$nl/ }"
    return
  fi
  error="$(json_field error)"
  if [[ -n "$error" ]]; then
    printf 'Phản hồi: %s' "$error"
    return
  fi
  body_snippet
}

curl_hint() {
  case "$1" in
    6) printf 'không phân giải được tên miền: kiểm tra bản ghi DNS A hoặc lỗi chính tả trong BASE_URL.' ;;
    7) printf 'không kết nối được: dịch vụ chưa chạy, sai cổng hoặc firewall chặn. Trên máy chủ: docker compose ps' ;;
    28) printf 'quá %s giây không có phản hồi: firewall chặn gói tin hoặc dịch vụ quá tải (có thể tăng SMOKE_TIMEOUT).' "$TIMEOUT" ;;
    35 | 51 | 58 | 60) printf 'lỗi TLS: Caddy chưa có chứng chỉ hợp lệ cho tên miền (đang xin, đang dùng CA staging hoặc đã hết hạn). Xem: docker compose logs --tail 100 caddy' ;;
    52) printf 'máy chủ đóng kết nối mà không trả dữ liệu.' ;;
    56) printf 'kết nối bị ngắt khi đang nhận dữ liệu.' ;;
    *) printf 'curl lỗi mã %s (xem mục EXIT CODES trong man curl).' "$1" ;;
  esac
}

# http_hint <mã HTTP> <service phía sau>
http_hint() {
  local service="${2:-api-gateway}"
  case "$1" in
    200) printf 'HTTP 200 nhưng nội dung không đúng khung ApiResponse (cần success=true và data).' ;;
    301 | 302 | 303 | 307 | 308) printf 'bị chuyển hướng tới %s: kiểm tra BASE_URL (tên miền công khai chỉ phục vụ https://).' "${REDIRECT_URL:-?}" ;;
    400) printf 'yêu cầu không hợp lệ, xem thông báo phía trên.' ;;
    401) printf 'token bị từ chối. Thông báo "Token không hợp lệ hoặc đã hết hạn" hay "Thiếu token xác thực" đến từ api-gateway: JWT_SECRET của api-gateway và auth-service phải giống nhau (sau khi đổi .env chạy: docker compose up -d api-gateway auth-service).' ;;
    403) printf 'tài khoản không đủ quyền gọi API này.' ;;
    404) printf 'không có đường dẫn này: kiểm tra BASE_URL và các route trong application.yml của api-gateway.' ;;
    429) printf 'vượt giới hạn tốc độ của api-gateway: đợi vài giây rồi chạy lại, hoặc tăng RATE_LIMIT_REPLENISH_RATE và RATE_LIMIT_BURST_CAPACITY trong .env.' ;;
    500) printf 'lỗi bên trong %s, thường do MySQL hoặc Redis. Xem: docker compose logs --tail 100 %s' "$service" "$service" ;;
    502) printf 'proxy không gọi được tầng phía sau: Caddy không tới được frontend, hoặc nginx của frontend không tới được api-gateway. Xem: docker compose ps' ;;
    503) printf '%s chưa đăng ký với Eureka (đang khởi động, đang khởi động lại hoặc đã dừng) nên api-gateway không có nơi chuyển request. Đợi 30 đến 60 giây rồi chạy lại; xem: docker compose ps %s' "$service" "$service" ;;
    504) printf '%s phản hồi quá chậm. Xem: docker compose logs --tail 100 %s' "$service" "$service" ;;
    *) printf 'mã HTTP ngoài dự kiến, xem log của %s và api-gateway.' "$service" ;;
  esac
}

# Gợi ý cho 400 khi đăng nhập: bị khóa tạm thời (BAD_REQUEST) hoặc dữ liệu không hợp lệ (VALIDATION_ERROR)
bad_request_hint() {
  if [[ "$(json_field code)" == VALIDATION_ERROR ]]; then
    printf 'Gợi ý: dữ liệu đăng nhập không hợp lệ (tên đăng nhập tối đa 50 ký tự, mật khẩu tối đa 100 ký tự).'
  else
    printf 'Gợi ý: tài khoản hoặc IP đang bị khóa 30 phút sau 5 lần đăng nhập sai. Chờ hết thời gian khóa, hoặc mở khóa trên máy chủ: docker compose exec redis redis-cli --scan --pattern %s rồi docker compose exec redis redis-cli DEL <khóa>' "'login:lock:*'"
  fi
}

login_forbidden_hint() {
  if [[ "$MODE" == public ]]; then
    printf 'Gợi ý: request đăng nhập gửi kèm Origin: %s giống trình duyệt và bị api-gateway từ chối. Thêm origin này vào CORS_ALLOWED_ORIGINS trong .env rồi chạy: docker compose up -d api-gateway' "$ORIGIN"
  else
    printf 'Gợi ý: %s' "$(http_hint 403 auth-service)"
  fi
}

# --- In kết quả từng bước ---------------------------------------------------
begin_step() {
  STEP_NO=$(( STEP_NO + 1 ))
  STEP_DESC="$1"
  HTTP_CODE="---"
  HTTP_MS="-"
}

# print_result <PASS|FAIL|SKIP> [dòng chi tiết...]
print_result() {
  local status="$1" line
  shift
  case "$status" in
    PASS) PASS_COUNT=$(( PASS_COUNT + 1 )) ;;
    FAIL) FAIL_COUNT=$(( FAIL_COUNT + 1 )) ;;
    *) SKIP_COUNT=$(( SKIP_COUNT + 1 )) ;;
  esac
  if [[ "$status" == SKIP ]]; then
    printf '%s  [%d/%d] %s\n' "$status" "$STEP_NO" "$STEP_TOTAL" "$STEP_DESC"
  else
    printf '%s  [%d/%d] %s (HTTP %s, %s ms)\n' "$status" "$STEP_NO" "$STEP_TOTAL" "$STEP_DESC" "$HTTP_CODE" "$HTTP_MS"
  fi
  if (( $# > 0 )); then
    for line in "$@"; do
      if [[ -n "$line" ]]; then
        printf '      %s\n' "$line"
      fi
    done
  fi
}

pass() { print_result PASS "$@"; }
fail() { print_result FAIL "$@"; }
skip() { print_result SKIP "$@"; }

skip_if_unreachable() {
  (( UNREACHABLE )) || return 1
  skip "Bỏ qua: không kết nối được tới $BASE_URL (xem lỗi ở bước trước)."
  return 0
}

skip_if_no_token() {
  [[ -z "$ACCESS_TOKEN" ]] || return 1
  skip "Bỏ qua: chưa có access token vì bước đăng nhập đúng không thành công."
  return 0
}

# In FAIL và trả 0 nếu không nhận được phản hồi HTTP đầy đủ (lỗi DNS, kết nối, TLS, quá thời gian chờ...)
fail_if_transport_error() {
  (( CURL_RC != 0 )) || return 1
  fail "$CURL_MSG" "Gợi ý: $(curl_hint "$CURL_RC")"
  return 0
}

# --- Các bước kiểm tra ------------------------------------------------------
step_home_page() {
  begin_step "Trang chủ giao diện web: GET /"
  skip_if_unreachable && return
  http_request / -H 'Accept: text/html'
  fail_if_transport_error && return
  if [[ "$HTTP_CODE" == 200 ]] && LC_ALL=C grep -qiE '<div id="root"|<!doctype html' "$BODY_FILE"; then
    pass
  elif [[ "$HTTP_CODE" == 200 ]]; then
    fail 'HTTP 200 nhưng nội dung không phải trang React (không có <div id="root" hoặc <!doctype html>).' "$(body_snippet)"
  else
    fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" frontend)"
  fi
}

step_login() {
  begin_step "Đăng nhập đúng bằng tài khoản $LOGIN_USER: POST /api/auth/login"
  skip_if_unreachable && return
  login_request "$LOGIN_PASS"
  fail_if_transport_error && return

  local success token roles expires
  if [[ "$HTTP_CODE" == 200 ]]; then
    success="$(json_field success)"
    token="$(json_field data.accessToken)"
    if [[ "$success" == true && -n "$token" ]]; then
      ACCESS_TOKEN="$token"
      TOKEN_USER="$(json_field data.username)"
      roles="$(json_field data.roles)"
      expires="$(json_field data.expiresIn)"
      pass "Nhận accessToken của ${TOKEN_USER:-?} (vai trò ${roles:-?}, hết hạn sau ${expires:-?} giây)."
    elif [[ -n "$token" ]]; then
      fail "HTTP 200 có accessToken nhưng success=${success:-?} (cần true)."
    else
      fail "HTTP 200 nhưng phản hồi không có data.accessToken." "$(body_snippet)"
    fi
    return
  fi

  case "$HTTP_CODE" in
    401)
      fail "$(response_summary)" \
        "Gợi ý: sai tên đăng nhập hoặc mật khẩu. Kiểm tra SMOKE_PASSWORD (mật khẩu có thể đã được đổi so với mật khẩu seed)." \
        "Cảnh báo: mỗi lần chạy với mật khẩu sai cộng thêm 1 lần sai; đủ 5 lần trong 30 phút thì tài khoản và IP bị khóa 30 phút."
      ;;
    400) fail "$(response_summary)" "$(bad_request_hint)" ;;
    403) fail "$(response_summary)" "$(login_forbidden_hint)" ;;
    *) fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" auth-service)" ;;
  esac
}

step_wrong_password() {
  begin_step "Đăng nhập sai mật khẩu bị từ chối: POST /api/auth/login"
  skip_if_unreachable && return
  if [[ -z "$ACCESS_TOKEN" ]]; then
    skip "Bỏ qua: chỉ chạy khi bước đăng nhập đúng thành công, để không cộng thêm lần sai vào bộ đếm chống dò mật khẩu."
    return
  fi
  login_request "$WRONG_PASS"
  fail_if_transport_error && return
  case "$HTTP_CODE" in
    401) pass "$(response_summary)" ;;
    200) fail "Mật khẩu sai vẫn đăng nhập được: lỗi bảo mật nghiêm trọng, kiểm tra auth-service ngay." ;;
    400) fail "$(response_summary)" "$(bad_request_hint)" ;;
    403) fail "$(response_summary)" "$(login_forbidden_hint)" ;;
    *) fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" auth-service)" ;;
  esac
  if [[ "$HTTP_CODE" == 401 ]]; then
    reset_failed_attempts
  fi
}

# Đăng nhập đúng thêm một lần: auth-service xóa bộ đếm lần sai của tài khoản và IP khi đăng nhập thành công
reset_failed_attempts() {
  login_request "$LOGIN_PASS"
  if (( CURL_RC != 0 )) || [[ "$HTTP_CODE" != 200 ]]; then
    printf '      Lưu ý: đăng nhập lại để xóa bộ đếm lần sai không thành công (HTTP %s); bộ đếm tự hết hạn sau 30 phút.\n' "$HTTP_CODE"
  fi
}

step_me() {
  begin_step "Thông tin người dùng hiện tại: GET /api/auth/me"
  skip_if_unreachable && return
  skip_if_no_token && return
  api_get /api/auth/me
  fail_if_transport_error && return
  local user roles
  if [[ "$HTTP_CODE" == 200 && "$(json_field success)" == true ]]; then
    user="$(json_field data.username)"
    roles="$(json_field data.roles)"
    if [[ -n "$TOKEN_USER" && "$user" != "$TOKEN_USER" ]]; then
      fail "Tên người dùng trả về (${user:-trống}) khác tài khoản vừa đăng nhập ($TOKEN_USER)."
    else
      pass "Người dùng ${user:-?}, vai trò ${roles:-?}."
    fi
  else
    fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" auth-service)"
  fi
}

step_warehouses() {
  begin_step "Danh sách kho (inventory-service): GET /api/inventory/warehouses"
  skip_if_unreachable && return
  skip_if_no_token && return
  api_get /api/inventory/warehouses
  fail_if_transport_error && return
  if [[ "$HTTP_CODE" == 200 && "$(json_field success)" == true ]]; then
    pass "Số kho: $(json_count data)."
  else
    fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" inventory-service)"
  fi
}

step_products() {
  begin_step "Danh sách sản phẩm (product-service): GET /api/products?page=0&size=1"
  skip_if_unreachable && return
  skip_if_no_token && return
  api_get "/api/products?page=0&size=1"
  fail_if_transport_error && return
  if [[ "$HTTP_CODE" == 200 && "$(json_field success)" == true ]]; then
    pass "Tổng số sản phẩm: $(json_field data.totalElements)."
  else
    fail "$(response_summary)" "Gợi ý: $(http_hint "$HTTP_CODE" product-service)"
  fi
}

step_gateway_health() {
  begin_step "Sức khỏe api-gateway: GET /actuator/health"
  skip_if_unreachable && return
  http_request /actuator/health -H 'Accept: application/json'
  fail_if_transport_error && return
  local status down
  status="$(json_field status)"
  if [[ "$HTTP_CODE" == 200 && "$status" == UP ]]; then
    pass "status=UP"
  elif [[ -n "$status" ]]; then
    down="$(json_query down components < "$BODY_FILE" 2> /dev/null)"
    fail "status=${status}${down:+, thành phần chưa UP: $down}" \
      "Gợi ý: xem log: docker compose logs --tail 100 api-gateway (Redis, Eureka hoặc Config Server có thể đang lỗi)."
  else
    fail "Phản hồi không phải JSON health của Spring Boot: BASE_URL có thể không trỏ tới api-gateway." "$(body_snippet)"
  fi
}

# --- Chương trình chính -----------------------------------------------------
print_header() {
  local route
  if [[ "$MODE" == public ]]; then
    route="qua tên miền: Caddy -> nginx -> api-gateway; không kiểm tra /actuator/health vì cổng 8080 chỉ mở trên máy chủ"
  else
    route="nội bộ: gọi thẳng api-gateway; không kiểm tra trang chủ"
  fi
  printf 'Smoke test WMS Supply Chain - %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf '  Địa chỉ   : %s (%s)\n' "$BASE_URL" "$route"
  printf '  Tài khoản : %s\n' "$LOGIN_USER"
  printf '  Timeout   : %s giây mỗi request; đọc JSON bằng %s\n' "$TIMEOUT" "$JSON_TOOL"
  if [[ -n "$CURL_INSECURE" ]]; then
    printf '  Cảnh báo  : SMOKE_INSECURE=1, không kiểm tra chứng chỉ TLS\n'
  fi
  printf '\n'
}

print_summary() {
  printf '\nKết quả: %d PASS, %d FAIL, %d SKIP (%d bước)\n' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT" "$STEP_TOTAL"
  if (( FAIL_COUNT > 0 )); then
    printf 'Có bước thất bại: xem gợi ý ngay dưới từng dòng FAIL và mục 4 của deploy/SMOKE_TEST.md.\n'
  fi
}

main() {
  parse_args "$@"
  check_tools
  read_password
  make_tmp

  print_header
  if [[ "$MODE" == public ]]; then
    step_home_page
  fi
  step_login
  step_wrong_password
  step_me
  step_warehouses
  step_products
  if [[ "$MODE" == internal ]]; then
    step_gateway_health
  fi
  print_summary

  (( FAIL_COUNT == 0 ))
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

main "$@"
exit $?
