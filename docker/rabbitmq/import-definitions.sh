#!/bin/sh
# Nạp topology RabbitMQ (exchange, queue, binding, policy) từ definitions.json qua API quản trị của broker.
#
# Chạy trong container rabbitmq-init (image curlimages/curl) mỗi lần docker compose up, sau khi rabbitmq healthy.
# Nạp lại nhiều lần không sao (idempotent): thành phần đã có thì giữ nguyên, thành phần mới thì được tạo thêm.
# Không nạp lúc broker khởi động (definitions.import_backend) vì khi đó RabbitMQ bỏ qua việc tạo tài khoản mặc định
# từ RABBITMQ_DEFAULT_USER / RABBITMQ_DEFAULT_PASS, volume mới sẽ không có user nào để service kết nối.
#
# Biến môi trường:
#   RABBITMQ_USERNAME, RABBITMQ_PASSWORD  tài khoản quản trị của broker (mặc định guest/guest)
#   RABBITMQ_API                          mặc định http://rabbitmq:15672/api
#   DEFINITIONS_FILE                      mặc định /definitions.json
#   MAX_TRIES                             số lần thử khi API quản trị chưa sẵn sàng (mặc định 30, cách nhau 3 giây)
set -u

API="${RABBITMQ_API:-http://rabbitmq:15672/api}"
FILE="${DEFINITIONS_FILE:-/definitions.json}"
USERNAME="${RABBITMQ_USERNAME:-guest}"
PASSWORD="${RABBITMQ_PASSWORD:-guest}"
MAX_TRIES="${MAX_TRIES:-30}"

if [ ! -r "$FILE" ]; then
  echo "Không đọc được file định nghĩa $FILE" >&2
  exit 1
fi

i=1
while [ "$i" -le "$MAX_TRIES" ]; do
  response="$(curl -s -w '\n%{http_code}' -u "$USERNAME:$PASSWORD" -H 'Content-Type: application/json' \
    -X POST "$API/definitions" --data-binary "@$FILE" 2>/dev/null || printf '\n000')"
  code="$(printf '%s\n' "$response" | tail -n 1)"
  body="$(printf '%s\n' "$response" | sed '$d' | head -c 300)"
  case "$code" in
    200|201|204)
      queues="$(curl -s -u "$USERNAME:$PASSWORD" "$API/queues?columns=name" 2>/dev/null | grep -o '"name"' | wc -l | tr -d ' ')"
      echo "Đã nạp topology RabbitMQ từ $FILE (HTTP $code). Số queue hiện có trên broker: ${queues:-?}"
      exit 0
      ;;
    401|403)
      echo "Broker từ chối tài khoản quản trị '$USERNAME' (HTTP $code). Kiểm tra RABBITMQ_USERNAME, RABBITMQ_PASSWORD trong .env;" >&2
      echo "với volume đã có dữ liệu phải tạo tài khoản bằng rabbitmqctl trước, xem docs/MESSAGING.md mục 6." >&2
      exit 1
      ;;
    400)
      echo "File định nghĩa bị broker từ chối (HTTP 400): $body" >&2
      exit 1
      ;;
    *)
      echo "Lần $i/$MAX_TRIES: API quản trị chưa sẵn sàng (HTTP ${code:-000}). Thử lại sau 3 giây." >&2
      i=$((i + 1))
      sleep 3
      ;;
  esac
done

echo "Không nạp được topology RabbitMQ sau $MAX_TRIES lần. Xem docker compose logs rabbitmq." >&2
exit 1
