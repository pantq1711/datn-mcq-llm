#!/usr/bin/env bash
# Khôi phục PostgreSQL từ file do backup.sh tạo.
#
#   restore.sh <file.dump> --into <tên_db_tạm>   Thử khôi phục vào DB tạm (không đụng DB đang chạy). Dùng kiểm tra định kỳ.
#   restore.sh <file.dump> --yes                 Khôi phục ĐÈ lên DB đang chạy: dừng app, xóa DB, nạp lại, bật app.
#
# Migration Flyway chỉ đi tiến: file sao lưu phải do cùng phiên bản app hoặc bản cũ hơn tạo ra.
#
# File .age (backup.sh với BACKUP_AGE_RECIPIENT): đặt AGE_IDENTITY=<đường dẫn file khóa riêng age> rồi chạy như bình thường,
# ví dụ: AGE_IDENTITY=~/datn-age.key restore.sh datn-20261101-020000.dump.age --into datn_restore_test
# (cần cài age; khóa riêng lấy từ nơi lưu ngoài máy chủ, xóa khỏi máy chủ sau khi khôi phục xong).
set -euo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

usage() { sed -n '2,11p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 2; }

[ $# -ge 2 ] || usage
FILE="$1"; MODE="$2"; TARGET="${3:-}"
[ -s "$FILE" ] || { echo "Không thấy file hoặc file rỗng: $FILE" >&2; exit 1; }

# Ghi nội dung dump (đã giải mã nếu là .age) ra stdout để chuyển cho pg_restore; pipefail bắt lỗi giải mã.
if [[ "$FILE" == *.age ]]; then
  command -v age > /dev/null || { echo "Cần cài age để giải mã $FILE" >&2; exit 1; }
  [ -r "${AGE_IDENTITY:-}" ] || { echo "File .age cần AGE_IDENTITY=<file khóa riêng age> (đọc được)" >&2; exit 1; }
  dump_stream() { age -d -i "$AGE_IDENTITY" "$FILE"; }
else
  dump_stream() { cat "$FILE"; }
fi

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f compose.prod.yaml)

# Chạy một lệnh psql ở database "postgres" trong container db; $1 là câu SQL
admin_sql() {
  "${COMPOSE[@]}" exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -c "$1"' sh "$1"
}

case "$MODE" in
  --into)
    [[ "$TARGET" =~ ^[a-z_][a-z0-9_]*$ ]] || { echo "Tên DB tạm không hợp lệ: '$TARGET'" >&2; exit 2; }
    live="$("${COMPOSE[@]}" exec -T db sh -c 'printf %s "$POSTGRES_DB"')"
    [ "$TARGET" != "$live" ] || { echo "--into không được trùng DB đang chạy ($live)" >&2; exit 2; }
    echo "Khôi phục $FILE vào DB tạm '$TARGET' (DB đang chạy '$live' không bị đụng)"
    admin_sql "DROP DATABASE IF EXISTS \"$TARGET\" WITH (FORCE)"
    admin_sql "CREATE DATABASE \"$TARGET\""
    dump_stream | "${COMPOSE[@]}" exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error' sh "$TARGET"
    echo "Số phiên bản migration Flyway đã nạp:"
    "${COMPOSE[@]}" exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$1" -Atc "SELECT count(*) FROM flyway_schema_history"' sh "$TARGET"
    echo "Xong. Xóa DB tạm khi không cần: docker compose -f compose.prod.yaml exec db sh -c 'dropdb -U \"\$POSTGRES_USER\" $TARGET'"
    ;;
  --yes)
    echo "CẢNH BÁO: sẽ xóa toàn bộ dữ liệu hiện tại và nạp lại từ $FILE"
    # Kiểm đọc/giải mã được TRƯỚC khi xóa gì (khóa sai hoặc file hỏng thì dừng ở đây, DB còn nguyên)
    dump_stream | "${COMPOSE[@]}" exec -T db pg_restore -l > /dev/null || { echo "LỖI: không đọc được $FILE (sai khóa age hoặc file hỏng); chưa xóa gì" >&2; exit 1; }
    "${COMPOSE[@]}" stop app
    "${COMPOSE[@]}" exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" -c "CREATE DATABASE \"$POSTGRES_DB\""'
    dump_stream | "${COMPOSE[@]}" exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error'
    "${COMPOSE[@]}" start app
    echo "Đã khôi phục. Theo dõi: docker compose -f compose.prod.yaml logs -f --tail=100 app"
    ;;
  *) usage ;;
esac
