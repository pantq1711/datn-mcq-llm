#!/usr/bin/env bash
# Hàm dùng chung cho backup.sh và check-disk.sh (VH-02, VH-04). File này được `source`, không chạy trực tiếp.
# Cần biến DEPLOY_DIR (đường dẫn thư mục deploy/) đã đặt trước khi source.

# load_env_var TÊN
#   Nếu biến TÊN chưa có (hoặc rỗng) trong môi trường thì đọc dòng TÊN=giá_trị từ deploy/.env rồi export.
#   Không `source` file .env (giá trị có thể chứa ký tự đặc biệt); chỉ lấy đúng một dòng, dòng cuối thắng.
load_env_var() {
  local name="$1" line value
  [ -z "${!name:-}" ] || return 0
  [ -r "$DEPLOY_DIR/.env" ] || return 0
  line="$(grep -E "^${name}=" "$DEPLOY_DIR/.env" | tail -n 1 || true)"
  [ -n "$line" ] || return 0
  value="${line#*=}"
  value="${value%$'\r'}"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
    *) value="${value%% #*}"; value="${value%"${value##*[![:space:]]}"}" ;;  # bỏ chú thích cuối dòng " # ..."
  esac
  printf -v "$name" '%s' "$value"
  export "${name?}"
}

# ping_url URL [HẬU_TỐ] [NỘI_DUNG]
#   Gọi URL kiểm tra sống (healthchecks.io: URL, URL/start, URL/fail). URL rỗng thì không làm gì.
#   Lỗi mạng chỉ in cảnh báo (không in URL, vì URL là bí mật nhỏ), KHÔNG làm hỏng script gọi nó.
ping_url() {
  local url="${1:-}" suffix="${2:-}" body="${3:-}"
  [ -n "$url" ] || return 0
  local args=(-fsS -m 10 -o /dev/null)
  [ -z "$body" ] || args+=(--data-raw "$body")
  curl "${args[@]}" "${url%/}${suffix}" || echo "Cảnh báo: không ping được dịch vụ kiểm tra (${suffix:-báo thành công})" >&2
  return 0
}
