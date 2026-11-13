#!/usr/bin/env bash
# ============================================================================
# deploy.sh - Triển khai hoặc cập nhật WMS Supply Chain trên VPS Ubuntu (Docker Compose + Caddy).
#
# Chạy bằng root trên máy chủ:
#   bash deploy/deploy.sh                # lấy mã nguồn mới nhất, build image, khởi động lại stack
#   bash deploy/deploy.sh --no-build     # bỏ qua bước build, dùng lại image đã có
#   BRANCH=main bash deploy/deploy.sh    # triển khai nhánh khác
#
# Các bước: kiểm tra Docker -> đồng bộ mã nguồn (git) -> kiểm tra .env -> build -> up -d
#           -> chờ container healthy -> kiểm tra gateway và làm nóng vài request đầu
#           -> kiểm tra HTTPS và API qua tên miền -> dọn image cũ.
#
# Biến môi trường (đều tùy chọn):
#   APP_DIR       thư mục chứa mã nguồn trên VPS            mặc định /opt/wms-supply-chain
#   BRANCH        nhánh Git cần triển khai                  mặc định develop
#   REPO_URL      địa chỉ repository Git                    mặc định https://github.com/letoan0902/wms-supply-chain.git
#   WAIT_TIMEOUT  số giây tối đa chờ container healthy      mặc định 600
#
# Lần chạy đầu tiên: nếu chưa có file .env, script dừng lại và hướng dẫn tạo từ deploy/.env.production.example.
# Lưu ý: script dùng git reset --hard nên mọi chỉnh sửa cục bộ trong file đã theo dõi của APP_DIR sẽ bị xóa
# (file .env không bị ảnh hưởng). Chi tiết xem deploy/README.md.
# ============================================================================

set -Eeuo pipefail

# --- Cấu hình ---------------------------------------------------------------
APP_DIR="${APP_DIR:-/opt/wms-supply-chain}"
BRANCH="${BRANCH:-develop}"
REPO_URL="${REPO_URL:-https://github.com/letoan0902/wms-supply-chain.git}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-600}"

# Phiên bản Docker Compose tối thiểu: deploy/docker-compose.prod.yml dùng thẻ !reset và !override
MIN_COMPOSE_VERSION="2.24.4"
# Không đặt tên biến là COMPOSE_FILE vì đó là biến môi trường có ý nghĩa riêng với docker compose
COMPOSE_ARGS=(-f docker-compose.yml -f deploy/docker-compose.prod.yml)
COMPOSE_HINT="docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml"

SKIP_BUILD=0
FAILED=0
DOMAIN=""
CADDYFILE_HASH_BEFORE=""

# --- Hàm tiện ích -----------------------------------------------------------
ts() { date '+%Y-%m-%d %H:%M:%S'; }
log() { printf '[%s] %s\n' "$(ts)" "$*"; }
warn() { printf '[%s] CẢNH BÁO: %s\n' "$(ts)" "$*" >&2; }
die() { printf '[%s] LỖI: %s\n' "$(ts)" "$*" >&2; exit 1; }
step() { printf '\n[%s] ===== %s =====\n' "$(ts)" "$*"; }

on_error() {
  printf '[%s] LỖI: lệnh thất bại (mã thoát %s) tại dòng %s của deploy.sh\n' "$(ts)" "$1" "$2" >&2
}
trap 'on_error $? $LINENO' ERR

usage() {
  cat <<'EOF'
Cách dùng: bash deploy/deploy.sh [--no-build]

  --no-build   bỏ qua bước build image, dùng lại image đã build ở lần trước
               (docker compose up vẫn tự build những image còn thiếu)
  -h, --help   hiển thị hướng dẫn này

Biến môi trường (đều tùy chọn):
  APP_DIR       thư mục chứa mã nguồn trên VPS          (mặc định /opt/wms-supply-chain)
  BRANCH        nhánh Git cần triển khai                (mặc định develop)
  REPO_URL      địa chỉ repository Git                  (mặc định https://github.com/letoan0902/wms-supply-chain.git)
  WAIT_TIMEOUT  số giây tối đa chờ container healthy    (mặc định 600)

Ví dụ:
  bash deploy/deploy.sh
  bash deploy/deploy.sh --no-build
  BRANCH=main bash deploy/deploy.sh
EOF
}

parse_args() {
  while (( $# > 0 )); do
    case "$1" in
      --no-build) SKIP_BUILD=1 ;;
      -h | --help) usage; exit 0 ;;
      *) usage >&2; die "Tham số không hợp lệ: $1" ;;
    esac
    shift
  done
  [[ "$WAIT_TIMEOUT" =~ ^[0-9]+$ ]] || die "WAIT_TIMEOUT phải là số giây nguyên dương, hiện là: $WAIT_TIMEOUT"
}

# Chạy docker compose với cả hai file (phải đứng trong thư mục gốc repository)
compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}

# So sánh phiên bản: thành công nếu $1 >= $2
version_ge() {
  local lowest
  lowest="$(printf '%s\n%s\n' "$1" "$2" | sort -V | sed -n '1p')"
  [[ "$lowest" == "$2" ]]
}

# Mã băm sha256 của một file (không in gì nếu file không tồn tại)
file_hash() {
  if [[ -f "$1" ]]; then
    sha256sum "$1" | cut -d ' ' -f 1
  fi
}

# --- Kiểm tra môi trường ----------------------------------------------------
check_root() {
  [[ "$(id -u)" -eq 0 ]] || die "Hãy chạy script bằng quyền root (ví dụ: sudo bash deploy/deploy.sh)."
}

# Không cho hai lần deploy chạy chồng nhau
acquire_lock() {
  local lock_file="/var/lock/wms-deploy.lock"
  if command -v flock > /dev/null 2>&1; then
    exec 9> "$lock_file"
    flock -n 9 || die "Đang có một tiến trình deploy khác chạy (khóa: $lock_file)."
  fi
}

check_tools() {
  local tool
  for tool in git curl docker; do
    command -v "$tool" > /dev/null 2>&1 \
      || die "Chưa cài '$tool'. Xem mục 3 trong deploy/README.md để chuẩn bị máy chủ."
  done
  docker compose version > /dev/null 2>&1 \
    || die "Chưa có Docker Compose v2 (gói docker-compose-plugin). Xem mục 3 trong deploy/README.md."
  docker info > /dev/null 2>&1 \
    || die "Không kết nối được Docker daemon. Kiểm tra bằng: systemctl status docker"

  local version
  version="$(docker compose version --short 2> /dev/null || true)"
  version="${version#v}"
  [[ -n "$version" ]] || die "Không đọc được phiên bản Docker Compose."
  version_ge "$version" "$MIN_COMPOSE_VERSION" \
    || die "Docker Compose $version quá cũ, cần $MIN_COMPOSE_VERSION trở lên (deploy/docker-compose.prod.yml dùng thẻ !reset và !override). Nâng cấp theo mục 3 trong deploy/README.md."
  log "Docker: $(docker --version | sed 's/,.*//'); Docker Compose: $version"

  local free_kb
  free_kb="$(df -Pk /var/lib/docker 2> /dev/null | awk 'NR == 2 { print $4 }' || true)"
  if [[ -n "$free_kb" ]] && (( free_kb < 10 * 1024 * 1024 )); then
    warn "Ổ đĩa chứa /var/lib/docker còn dưới 10 GB trống, việc build image có thể thất bại. Dọn dẹp bằng: docker builder prune -f"
  fi
}

# --- Mã nguồn ---------------------------------------------------------------
sync_source() {
  if [[ -d "$APP_DIR/.git" ]]; then
    log "Cập nhật mã nguồn trong $APP_DIR từ origin/$BRANCH"
    CADDYFILE_HASH_BEFORE="$(file_hash "$APP_DIR/deploy/Caddyfile")"
    git -C "$APP_DIR" fetch --prune origin
    git -C "$APP_DIR" rev-parse --verify --quiet "refs/remotes/origin/$BRANCH" > /dev/null \
      || die "Không tìm thấy nhánh origin/$BRANCH sau khi fetch. Kiểm tra lại biến BRANCH."
    if [[ "$(git -C "$APP_DIR" rev-parse --abbrev-ref HEAD)" != "$BRANCH" ]]; then
      git -C "$APP_DIR" checkout --force -B "$BRANCH" "origin/$BRANCH"
    fi
    # Bỏ mọi thay đổi cục bộ của file đã theo dõi; file .env (không theo dõi) được giữ nguyên
    git -C "$APP_DIR" reset --hard "origin/$BRANCH"
  else
    if [[ -e "$APP_DIR" ]] && { [[ ! -d "$APP_DIR" ]] || [[ -n "$(ls -A "$APP_DIR")" ]]; }; then
      die "$APP_DIR đã tồn tại nhưng không phải repository Git. Hãy dọn thư mục đó hoặc đổi biến APP_DIR."
    fi
    log "Chưa có mã nguồn, clone $REPO_URL (nhánh $BRANCH) vào $APP_DIR"
    mkdir -p "$(dirname "$APP_DIR")"
    git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
  fi
  log "Commit đang triển khai: $(git -C "$APP_DIR" log -1 --format='%H %s (%an, %ad)' --date=short)"
}

# Nhánh được triển khai phải có đủ các file của bộ triển khai (chưa merge Pull Request thì nhánh develop chưa có)
check_files() {
  local f
  for f in docker-compose.yml deploy/docker-compose.prod.yml deploy/Caddyfile; do
    [[ -f "$APP_DIR/$f" ]] \
      || die "Nhánh $BRANCH chưa có file $f. Hãy merge Pull Request chứa thư mục deploy/ vào nhánh này, hoặc chạy với BRANCH=<nhánh có deploy/>."
  done
}

# --- File .env --------------------------------------------------------------
# Đọc giá trị một khóa trong file .env mà không "source" file (tránh thực thi nội dung và lỗi với ký tự đặc biệt).
# Xử lý được: dòng CRLF, khoảng trắng thừa, giá trị trong nháy đơn hoặc nháy kép, chú thích cuối dòng.
env_get() {
  local key="$1" file="$2" line value
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$file" | tail -n 1 || true)"
  value="${line#*=}"
  value="${value%$'\r'}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
    *)
      value="${value%%[[:space:]]#*}"
      value="${value%"${value##*[![:space:]]}"}"
      ;;
  esac
  printf '%s' "$value"
}

check_env() {
  step "Bước 3/7: Kiểm tra file .env"
  local env_file="$APP_DIR/.env"

  if [[ ! -f "$env_file" ]]; then
    cat >&2 <<EOF
[LỖI] Chưa có file $env_file.

Tạo file từ mẫu, điền các giá trị còn trống rồi chạy lại script:

  cp $APP_DIR/deploy/.env.production.example $env_file
  chmod 600 $env_file
  nano $env_file

Các biến bắt buộc: ACME_EMAIL, MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET
(sinh mật khẩu ngẫu nhiên bằng: openssl rand -base64 32). Xem deploy/README.md, mục 5.
EOF
    exit 1
  fi

  local mode
  mode="$(stat -c '%a' "$env_file")"
  if [[ "${mode: -2}" != "00" ]]; then
    warn "$env_file đang có quyền $mode. Nên đặt chmod 600 để người dùng khác trên máy chủ không đọc được mật khẩu."
  fi

  local key value
  local -a missing=() placeholders=()
  for key in ACME_EMAIL MYSQL_ROOT_PASSWORD DB_PASSWORD JWT_SECRET; do
    value="$(env_get "$key" "$env_file")"
    if [[ -z "$value" ]]; then
      missing+=("$key")
    elif [[ "$value" == *change_me* ]]; then
      placeholders+=("$key")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    die "Các biến sau còn trống trong $env_file: ${missing[*]}. Điền giá trị rồi chạy lại (mật khẩu: openssl rand -base64 32)."
  fi
  if (( ${#placeholders[@]} > 0 )); then
    die "Các biến sau vẫn là giá trị mẫu change_me trong $env_file: ${placeholders[*]}. Hãy thay bằng giá trị ngẫu nhiên."
  fi

  value="$(env_get JWT_SECRET "$env_file")"
  if (( ${#value} < 32 )); then
    die "JWT_SECRET phải dài tối thiểu 32 ký tự (hiện có ${#value}). Sinh bằng: openssl rand -base64 48"
  fi

  value="$(env_get ACME_EMAIL "$env_file")"
  [[ "$value" == *@*.* ]] || die "ACME_EMAIL không phải địa chỉ email hợp lệ: $value"
  case "${value,,}" in
    *@example.com | *@example.org | *@example.net)
      warn "ACME_EMAIL thuộc tên miền ví dụ ($value): Let's Encrypt thường từ chối loại email này, hãy dùng email thật."
      ;;
  esac

  DOMAIN="$(env_get DOMAIN "$env_file")"
  DOMAIN="${DOMAIN:-wms.toanmarket.me}"
  [[ "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || die "DOMAIN không hợp lệ: $DOMAIN"
  log "Tên miền: $DOMAIN"

  # Hai cấu hình sai thường gặp nhất sau khi đổi tên miền hoặc khi chẩn đoán sự cố
  local cors jwt_flag
  cors="$(env_get CORS_ALLOWED_ORIGINS "$env_file")"
  cors="${cors//[[:space:]]/}"
  if [[ -n "$cors" && ",${cors}," != *",https://${DOMAIN},"* ]]; then
    warn "CORS_ALLOWED_ORIGINS=$cors không chứa https://${DOMAIN}: đăng nhập từ trình duyệt sẽ bị gateway từ chối (403)."
  fi
  jwt_flag="$(env_get GATEWAY_JWT_ENABLED "$env_file")"
  if [[ "${jwt_flag,,}" == "false" ]]; then
    warn "GATEWAY_JWT_ENABLED=false: mọi API nghiệp vụ truy cập được mà không cần đăng nhập. Chỉ nên dùng khi chẩn đoán sự cố."
  fi

  log "Kiểm tra cấu hình Docker Compose sau khi ghép hai file (docker compose config)"
  compose config --quiet || die "Cấu hình Docker Compose không hợp lệ, xem thông báo phía trên."
}

# --- Build và khởi động -----------------------------------------------------
build_images() {
  step "Bước 4/7: Build image"
  if (( SKIP_BUILD )); then
    log "Bỏ qua bước build theo tham số --no-build."
    return 0
  fi
  log "Build image với --pull. Lần đầu mất khá lâu vì phải biên dịch 9 service Java và frontend."
  compose build --pull
}

# In mỗi container một dòng: <service>|<trạng thái>|<health>|<mã thoát> (health rỗng nếu container không có healthcheck).
# Container chạy một lần rồi kết thúc (rabbitmq-init) được coi là ổn khi trạng thái exited và mã thoát 0.
container_states() {
  local -a ids=()
  mapfile -t ids < <(compose ps --all --quiet 2> /dev/null)
  (( ${#ids[@]} > 0 )) || return 0
  docker inspect \
    --format '{{index .Config.Labels "com.docker.compose.service"}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{.State.ExitCode}}' \
    "${ids[@]}" 2> /dev/null || true
}

show_diagnostics() {
  warn "Trạng thái các container:"
  compose ps --all || true
  local svc state health
  while IFS='|' read -r svc state health exit_code; do
    if [[ -n "$svc" ]] && ! [[ ( "$state" == "running" && ( "$health" == "healthy" || -z "$health" ) ) || ( "$state" == "exited" && "$exit_code" == "0" ) ]]; then
      warn "Log của $svc (40 dòng cuối):"
      compose logs --tail 40 "$svc" || true
    fi
  done < <(container_states)
}

start_stack() {
  step "Bước 5/7: Khởi động stack"
  if ! compose up -d --remove-orphans; then
    show_diagnostics
    die "docker compose up thất bại, xem trạng thái và log phía trên."
  fi

  # Caddyfile được mount dạng tệp đơn lẻ. Khi git thay file (đổi inode), container đang chạy vẫn thấy bản cũ,
  # nên phải khởi động lại caddy để nạp cấu hình mới.
  local hash_after
  hash_after="$(file_hash "$APP_DIR/deploy/Caddyfile")"
  if [[ -n "$CADDYFILE_HASH_BEFORE" && "$CADDYFILE_HASH_BEFORE" != "$hash_after" ]]; then
    log "Caddyfile có thay đổi, khởi động lại caddy để nạp cấu hình mới."
    compose restart caddy
  fi
}

wait_healthy() {
  step "Bước 6/7: Chờ các container sẵn sàng (tối đa ${WAIT_TIMEOUT} giây)"
  local deadline=$(( SECONDS + WAIT_TIMEOUT )) states pending
  while true; do
    states="$(container_states)"
    if [[ -z "$states" ]]; then
      pending="(chưa đọc được trạng thái container)"
    else
      pending="$(awk -F'|' 'NF > 0 && !(($2 == "running" && ($3 == "healthy" || $3 == "")) || ($2 == "exited" && $4 == "0")) { printf "%s%s", (n++ ? ", " : ""), $1 }' <<< "$states")"
    fi
    if [[ -z "$pending" ]]; then
      log "Tất cả container đã chạy và healthy."
      compose ps
      return 0
    fi
    if (( SECONDS >= deadline )); then
      warn "Quá thời gian chờ, các service chưa sẵn sàng: $pending"
      show_diagnostics
      return 1
    fi
    log "Đang chờ: $pending"
    sleep 10
  done
}

# --- Làm nóng sau khi khởi động --------------------------------------------
# Request đầu tiên sau khi JVM khởi động thường mất 10-20 giây: api-gateway nạp danh sách instance từ Eureka và mở
# kết nối Redis cho rate limiter, auth-service biên dịch JIT tầng web. Gửi trước vài request vô hại để người dùng
# đầu tiên và smoke test không bị timeout. Chỉ ghi log, không bao giờ coi là lỗi triển khai.
warm_up() {
  local base="http://127.0.0.1:8080" path result i
  log "Làm nóng api-gateway và auth-service bằng vài request vô hại"
  # POST JSON rỗng tới /api/auth/login: gateway chuyển tiếp sang auth-service, auth-service trả 400 (thiếu dữ liệu)
  # nên không bị tính là một lần đăng nhập sai của tài khoản nào.
  for i in 1 2; do
    result="$(curl -s -o /dev/null -w '%{http_code} sau %{time_total}s' --max-time 60 -X POST "$base/api/auth/login" \
      -H 'Content-Type: application/json' -d '{}' || true)"
    log "  POST /api/auth/login (JSON rỗng) lần $i: HTTP ${result:-000}"
  done
  # GET không token tới các route còn lại: gateway trả 401 nhưng bộ lọc JWT và rate limiter đã được làm nóng.
  for path in /api/products /api/inventory/warehouses /api/orders /api/notifications /api/ai/health; do
    result="$(curl -s -o /dev/null -w '%{http_code} sau %{time_total}s' --max-time 30 "$base$path" || true)"
    log "  GET $path (không token): HTTP ${result:-000}"
  done
}

# --- Kiểm tra sau triển khai ------------------------------------------------
check_health() {
  step "Bước 7/7: Kiểm tra sức khỏe và dọn dẹp"

  # 1. API Gateway lắng nghe trên loopback của máy chủ (bắt buộc phải đạt)
  local gateway_url="http://127.0.0.1:8080/actuator/health"
  if curl -fsS -o /dev/null --max-time 10 --retry 5 --retry-delay 3 --retry-connrefused "$gateway_url"; then
    log "API Gateway: OK ($gateway_url)"
    # 2. Làm nóng để request đầu tiên của người dùng (hoặc smoke test) không bị chậm hơn 10 giây
    warm_up
  else
    warn "API Gateway không phản hồi tại $gateway_url"
    FAILED=1
  fi

  # 3. HTTPS qua Caddy (chỉ cảnh báo: lần đầu có thể chưa trỏ DNS hoặc Caddy đang xin chứng chỉ)
  local url="https://${DOMAIN}/" attempt rc=0 err="" max_attempts=12
  for (( attempt = 1; attempt <= max_attempts; attempt++ )); do
    rc=0
    err="$(curl -fsS -o /dev/null --max-time 10 "$url" 2>&1)" || rc=$?
    if (( rc == 0 )); then
      log "HTTPS: OK ($url)"
      break
    fi
    # Mã 6: không phân giải được tên miền, chờ thêm cũng vô ích
    if (( rc == 6 )); then
      break
    fi
    log "HTTPS chưa sẵn sàng (lần $attempt/$max_attempts, curl mã $rc). Caddy có thể đang xin chứng chỉ, thử lại sau 5 giây."
    sleep 5
  done
  if (( rc != 0 )); then
    warn "Chưa truy cập được $url (curl mã $rc: $err)"
    if (( rc == 6 )); then
      warn "Tên miền $DOMAIN chưa phân giải được: tạo bản ghi DNS A trỏ về IP máy chủ này (DNS only, không bật proxy Cloudflare)."
    else
      warn "Kiểm tra: bản ghi DNS A của $DOMAIN, cổng 80 và 443 không bị firewall chặn, log của Caddy: $COMPOSE_HINT logs --tail 100 caddy"
    fi
    warn "Đây chỉ là cảnh báo. Sau khi sửa DNS hoặc firewall, chạy lệnh sau để Caddy xin chứng chỉ ngay: $COMPOSE_HINT restart caddy"
  fi

  # 4. API qua tên miền (Caddy -> nginx của frontend -> api-gateway): GET không token phải nhận 401, không phải 502/504
  if (( rc == 0 )); then
    local api_result
    api_result="$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "https://${DOMAIN}/api/products" || true)"
    if [[ "$api_result" == "401" ]]; then
      log "API qua tên miền: OK (GET /api/products không token nhận 401 như mong đợi)"
    else
      warn "API qua tên miền trả về HTTP ${api_result:-000} thay vì 401: kiểm tra nginx của frontend có proxy /api tới api-gateway không ($COMPOSE_HINT logs --tail 50 frontend api-gateway)"
    fi
  fi

  # 5. Dọn các image cũ không còn được dùng (chỉ image dangling)
  log "Dọn image cũ (docker image prune -f)"
  docker image prune -f
}

print_summary() {
  step "Tổng kết"
  cat <<EOF
Địa chỉ ứng dụng : https://${DOMAIN}/
Thư mục mã nguồn : ${APP_DIR}

Các lệnh xem trạng thái và log (chạy trong ${APP_DIR}):
  ${COMPOSE_HINT} ps
  ${COMPOSE_HINT} logs -f --tail 100 caddy
  ${COMPOSE_HINT} logs -f --tail 100 api-gateway
  ${COMPOSE_HINT} logs -f --tail 100 auth-service
  ${COMPOSE_HINT} logs --tail 200 <tên-service>
  ${COMPOSE_HINT} restart <tên-service>

Nếu .env có dòng COMPOSE_FILE như file mẫu thì có thể gõ ngắn, ví dụ: docker compose logs -f caddy
EOF
}

main() {
  parse_args "$@"
  check_root
  acquire_lock

  step "Bước 1/7: Kiểm tra Docker và công cụ"
  log "Triển khai nhánh $BRANCH vào $APP_DIR"
  check_tools

  step "Bước 2/7: Đồng bộ mã nguồn"
  sync_source
  check_files
  cd "$APP_DIR"

  check_env
  build_images
  start_stack
  wait_healthy || FAILED=1
  check_health
  print_summary

  if (( FAILED )); then
    die "Triển khai xong nhưng có thành phần chưa khỏe, xem các cảnh báo phía trên."
  fi
  log "Triển khai hoàn tất."
}

# Bọc trong khối lệnh và exit ngay để bash đọc trọn vẹn script trước khi chạy: an toàn khi git
# ghi đè chính file này lúc đang chạy.
{ main "$@"; exit $?; }
