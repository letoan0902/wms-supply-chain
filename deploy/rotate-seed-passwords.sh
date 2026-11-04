#!/usr/bin/env bash
# Đổi mật khẩu các tài khoản mẫu do Flyway tạo (admin, manager, staff, purchasing, sales, accountant) trên máy chủ.
#
# Mật khẩu mặc định của các tài khoản này nằm công khai trong repository (V2__seed_default_accounts.sql), nên ngay sau
# lần triển khai đầu phải đổi. Script sinh mật khẩu ngẫu nhiên cho từng tài khoản, băm BCrypt bằng container caddy
# (caddy hash-password), ghi vào CSDL auth_db và lưu danh sách mật khẩu mới vào một file chỉ root đọc được.
# Không in mật khẩu ra màn hình, không truyền mật khẩu MySQL trên dòng lệnh của máy chủ.
#
# Cách dùng (trong /opt/wms-supply-chain):
#   bash deploy/rotate-seed-passwords.sh                 # đổi cả 6 tài khoản
#   bash deploy/rotate-seed-passwords.sh admin manager   # chỉ đổi một số tài khoản
#   OUT_FILE=/root/khac.txt bash deploy/rotate-seed-passwords.sh   # đổi nơi lưu (mặc định /root/wms-seed-accounts.txt)
#
# Sau khi đổi: đăng nhập bằng mật khẩu mới, lưu vào trình quản lý mật khẩu rồi xóa file. Người dùng có thể tự đổi tiếp
# bằng API POST /api/auth/users/change-password (hoặc màn hình đổi mật khẩu khi giao diện có).
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

OUT_FILE="${OUT_FILE:-/root/wms-seed-accounts.txt}"
DEFAULT_ACCOUNTS=(admin manager staff purchasing sales accountant)
ACCOUNTS=("$@")
if [ ${#ACCOUNTS[@]} -eq 0 ]; then
  ACCOUNTS=("${DEFAULT_ACCOUNTS[@]}")
fi
for account in "${ACCOUNTS[@]}"; do
  if ! [[ "$account" =~ ^[a-z0-9_.-]+$ ]]; then
    echo "Tên đăng nhập không hợp lệ: $account" >&2
    exit 1
  fi
done

compose() {
  docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml "$@"
}

for service in mysql caddy; do
  if ! compose ps --status running --services 2>/dev/null | grep -qx "$service"; then
    echo "Container $service chưa chạy. Chạy deploy/deploy.sh trước." >&2
    exit 1
  fi
done

umask 077
tmp_list="$(mktemp)"
trap 'rm -f "$tmp_list"' EXIT

sql=""
for account in "${ACCOUNTS[@]}"; do
  password="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 20)"
  hash="$(compose exec -T caddy caddy hash-password --plaintext "$password" | tr -d '\r\n')"
  case "$hash" in
    '$2'*) ;;
    *) echo "Không tạo được hash BCrypt cho $account (caddy hash-password trả về: ${hash:0:20})" >&2; exit 1 ;;
  esac
  sql+="UPDATE users SET password_hash = '$hash', updated_at = UTC_TIMESTAMP(6), updated_by = 'rotate-seed-passwords' WHERE username = '$account';"$'\n'
  sql+="SELECT CONCAT('Đã đổi: ', '$account', ' (', ROW_COUNT(), ' dòng)');"$'\n'
  printf '%s\t%s\n' "$account" "$password" >> "$tmp_list"
done

# Biến $MYSQL_ROOT_PASSWORD nằm trong nháy đơn: do shell trong container mysql đọc, không lộ trên máy chủ.
printf '%s' "$sql" | compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N auth_db'

{
  echo "# Mật khẩu tài khoản mẫu WMS, đổi lúc $(date -Is) bởi deploy/rotate-seed-passwords.sh."
  echo "# Chỉ root đọc được. Lưu vào trình quản lý mật khẩu rồi xóa file này."
  echo "# tên đăng nhập<TAB>mật khẩu"
  cat "$tmp_list"
} > "$OUT_FILE"
chmod 600 "$OUT_FILE"

echo "Đã đổi mật khẩu ${#ACCOUNTS[@]} tài khoản: ${ACCOUNTS[*]}"
echo "Mật khẩu mới lưu tại $OUT_FILE (quyền 600). Mật khẩu cũ không còn dùng được."
