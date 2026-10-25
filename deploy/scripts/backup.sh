#!/usr/bin/env bash
# Sao lưu PostgreSQL bằng pg_dump (định dạng custom, đã nén). Chạy từ bất kỳ đâu; cần quyền dùng docker.
# Cron (root hoặc user thuộc nhóm docker), 02:00 hằng ngày:
#   0 2 * * * /opt/datn/deploy/scripts/backup.sh >> /var/log/datn-backup.log 2>&1
# Biến tùy chọn:
#   BACKUP_DIR    thư mục lưu (mặc định /var/backups/datn)
#   KEEP_DAYS     số ngày giữ (mặc định 14)
#   RCLONE_REMOTE nếu đặt (ví dụ "gdrive:datn-backup") thì chép thêm bản sao ra ngoài máy chủ bằng rclone
# Bản sao lưu nằm cùng ổ đĩa với DB KHÔNG phải là sao lưu đủ an toàn: hãy có bản ngoài máy chủ (docs/deploy/README.md mục 7).
set -euo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f compose.prod.yaml)

BACKUP_DIR="${BACKUP_DIR:-/var/backups/datn}"
KEEP_DAYS="${KEEP_DAYS:-14}"

umask 077
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
out="$BACKUP_DIR/datn-$stamp.dump"
tmp="$BACKUP_DIR/.datn-$stamp.dump.partial"
trap 'rm -f "$tmp"' EXIT

echo "[$(date '+%F %T')] Bắt đầu sao lưu -> $out"
# Tên DB/user lấy từ biến của chính container db, không cần đọc deploy/.env
"${COMPOSE[@]}" exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$tmp"

[ -s "$tmp" ] || { echo "LỖI: bản sao lưu rỗng" >&2; exit 1; }
# pg_restore -l đọc được toàn bộ mục lục thì file không bị cắt cụt
"${COMPOSE[@]}" exec -T db pg_restore -l < "$tmp" > /dev/null || { echo "LỖI: bản sao lưu không đọc được" >&2; exit 1; }

mv "$tmp" "$out"
echo "[$(date '+%F %T')] Xong: $(du -h "$out" | cut -f1)"

find "$BACKUP_DIR" -maxdepth 1 -name 'datn-*.dump' -mtime +"$KEEP_DAYS" -print -delete

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy "$out" "$RCLONE_REMOTE" && echo "Đã chép ra $RCLONE_REMOTE"
fi
