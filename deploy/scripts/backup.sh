#!/usr/bin/env bash
# Sao lưu PostgreSQL bằng pg_dump (định dạng custom, đã nén). Chạy từ bất kỳ đâu; cần quyền dùng docker.
# Cron (root hoặc user thuộc nhóm docker), 02:00 hằng ngày:
#   0 2 * * * /opt/datn/deploy/scripts/backup.sh >> /var/log/datn-backup.log 2>&1
# Biến tùy chọn:
#   BACKUP_DIR    thư mục lưu (mặc định /var/backups/datn)
#   KEEP_DAYS     số ngày giữ (mặc định 14)
#   RCLONE_REMOTE nếu đặt (ví dụ "gdrive:datn-backup") thì chép thêm bản sao ra ngoài máy chủ bằng rclone, rồi xóa
#                 bản ở xa (chỉ file datn-*.dump*) cũ hơn KEEP_DAYS ngày. Lỗi chép hoặc lỗi xóa là lỗi sao lưu.
#   HEALTHCHECK_URL nếu đặt (healthchecks.io) thì ping URL/start lúc bắt đầu, URL khi xong, URL/fail khi lỗi.
#                 Lỗi khi ping không làm hỏng sao lưu. Vắng tín hiệu quá hạn thì dịch vụ gửi thư báo.
#   BACKUP_AGE_RECIPIENT nếu đặt (khóa công khai age1... ) thì mã hóa bản dump bằng `age` (cần cài age); file thành
#                 datn-*.dump.age, KHÔNG để lại bản rõ. Khóa riêng giữ ngoài máy chủ; khôi phục xem restore.sh.
# Ba biến RCLONE_REMOTE, HEALTHCHECK_URL, BACKUP_AGE_RECIPIENT nếu chưa có trong môi trường thì được đọc từ deploy/.env.
# Không đặt biến nào thì script chạy như trước đây.
# Bản sao lưu nằm cùng ổ đĩa với DB KHÔNG phải là sao lưu đủ an toàn: hãy có bản ngoài máy chủ (docs/deploy/README.md mục 7).
set -euo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f compose.prod.yaml)

# shellcheck source=lib-alert.sh
source "$DEPLOY_DIR/scripts/lib-alert.sh"
load_env_var HEALTHCHECK_URL
load_env_var RCLONE_REMOTE
load_env_var BACKUP_AGE_RECIPIENT

BACKUP_DIR="${BACKUP_DIR:-/var/backups/datn}"
KEEP_DAYS="${KEEP_DAYS:-14}"

umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
out="$BACKUP_DIR/datn-$stamp.dump"
tmp="$BACKUP_DIR/.datn-$stamp.dump.partial"
if [ -n "${BACKUP_AGE_RECIPIENT:-}" ]; then
  out="$out.age"
fi
tmp_enc="$BACKUP_DIR/.datn-$stamp.dump.age.partial"

# Dọn file tạm; thoát với mã khác 0 thì báo /fail. ping_url không bao giờ làm hỏng script.
finish() {
  local rc=$?
  rm -f "$tmp" "$tmp_enc"
  if [ "$rc" -ne 0 ]; then
    ping_url "${HEALTHCHECK_URL:-}" /fail "Sao lưu lỗi (mã thoát $rc), xem /var/log/datn-backup.log"
  fi
}
trap finish EXIT

ping_url "${HEALTHCHECK_URL:-}" /start
# Cấu hình mã hóa sai thì dừng ngay, trước khi dump (không để lại bản rõ, không âm thầm bỏ mã hóa)
if [ -n "${BACKUP_AGE_RECIPIENT:-}" ]; then
  command -v age > /dev/null || { echo "LỖI: đặt BACKUP_AGE_RECIPIENT nhưng chưa cài age (apt install age)" >&2; exit 1; }
fi
echo "[$(date '+%F %T')] Bắt đầu sao lưu -> $out"
# Tên DB/user lấy từ biến của chính container db, không cần đọc deploy/.env
"${COMPOSE[@]}" exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp"

[ -s "$tmp" ] || { echo "LỖI: bản sao lưu rỗng" >&2; exit 1; }
# pg_restore -l đọc được toàn bộ mục lục thì file không bị cắt cụt
"${COMPOSE[@]}" exec -T db pg_restore -l < "$tmp" > /dev/null || { echo "LỖI: bản sao lưu không đọc được" >&2; exit 1; }

if [ -n "${BACKUP_AGE_RECIPIENT:-}" ]; then
  age -r "$BACKUP_AGE_RECIPIENT" -o "$tmp_enc" "$tmp" || { echo "LỖI: mã hóa bằng age thất bại" >&2; exit 1; }
  [ -s "$tmp_enc" ] || { echo "LỖI: file đã mã hóa rỗng" >&2; exit 1; }
  rm -f "$tmp"          # không để bản rõ lại trên đĩa
  mv "$tmp_enc" "$out"
  echo "Đã mã hóa bằng age (khóa riêng giữ ngoài máy chủ)"
else
  mv "$tmp" "$out"
fi
echo "[$(date '+%F %T')] Xong: $(du -h "$out" | cut -f1)"

find "$BACKUP_DIR" -maxdepth 1 \( -name 'datn-*.dump' -o -name 'datn-*.dump.age' \) -mtime +"$KEEP_DAYS" -print -delete

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy "$out" "$RCLONE_REMOTE" || { echo "LỖI: rclone copy ra $RCLONE_REMOTE thất bại" >&2; exit 1; }
  echo "Đã chép ra $RCLONE_REMOTE"
  # Chỉ xóa file sao lưu của mình (bộ lọc --include), phòng khi remote là thư mục dùng chung.
  rclone delete --min-age "${KEEP_DAYS}d" --include 'datn-*.dump*' "$RCLONE_REMOTE" \
    || { echo "LỖI: rclone delete bản cũ ở $RCLONE_REMOTE thất bại" >&2; exit 1; }
  echo "Đã xóa bản ở xa cũ hơn ${KEEP_DAYS} ngày"
fi

ping_url "${HEALTHCHECK_URL:-}"
