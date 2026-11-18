#!/usr/bin/env bash
# Kiểm tra dung lượng đĩa và báo qua healthchecks.io (VH-04). Dùng quá ngưỡng thì ping URL/fail, còn lại ping URL.
# Cron (root hoặc user bất kỳ), mỗi giờ; dùng `bash` để không phụ thuộc quyền thực thi của file:
#   0 * * * * bash /opt/datn/deploy/scripts/check-disk.sh >> /var/log/datn-check-disk.log 2>&1
# Biến tùy chọn:
#   HEALTHCHECK_DISK_URL   URL ping của check "đĩa" (healthchecks.io). Chưa có trong môi trường thì đọc từ deploy/.env.
#                          Không đặt thì script chỉ in kết quả và thoát (mã 1 nếu vượt ngưỡng).
#   DISK_THRESHOLD_PCT     ngưỡng phần trăm (mặc định 80): dùng quá ngưỡng này (lớn hơn, không phải bằng) là báo lỗi
#   DISK_PATH              đường dẫn cần đo (mặc định /)
# Check của healthchecks.io nên đặt chu kỳ 1 giờ, ân hạn 15 phút: cron chết hẳn cũng bị phát hiện vì vắng tín hiệu.
set -euo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=lib-alert.sh
source "$DEPLOY_DIR/scripts/lib-alert.sh"
load_env_var HEALTHCHECK_DISK_URL

THRESHOLD="${DISK_THRESHOLD_PCT:-80}"
DISK_PATH="${DISK_PATH:-/}"
[[ "$THRESHOLD" =~ ^[0-9]+$ ]] || { echo "DISK_THRESHOLD_PCT không hợp lệ: '$THRESHOLD'" >&2; exit 2; }

# df -P: định dạng POSIX, một dòng dữ liệu; cột 5 là phần trăm đã dùng (ví dụ "63%")
used="$(df -P "$DISK_PATH" | awk 'NR==2 {gsub(/%/, "", $5); print $5}')"
[[ "$used" =~ ^[0-9]+$ ]] || { echo "LỖI: không đọc được dung lượng đĩa của $DISK_PATH" >&2; ping_url "${HEALTHCHECK_DISK_URL:-}" /fail "Không đọc được df $DISK_PATH"; exit 1; }

msg="Đĩa $DISK_PATH dùng ${used}% (ngưỡng ${THRESHOLD}%)"
if [ "$used" -gt "$THRESHOLD" ]; then
  echo "[$(date '+%F %T')] CẢNH BÁO: $msg"
  ping_url "${HEALTHCHECK_DISK_URL:-}" /fail "$msg"
  exit 1
fi
echo "[$(date '+%F %T')] OK: $msg"
ping_url "${HEALTHCHECK_DISK_URL:-}" "" "$msg"
