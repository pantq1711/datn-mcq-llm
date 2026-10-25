#!/usr/bin/env bash
# Cập nhật phiên bản: sao lưu -> kéo image -> khởi động lại -> chờ app khỏe. Flyway tự migrate khi app khởi động.
#   update.sh              kéo lại tag hiện tại trong deploy/.env (ví dụ latest)
#   update.sh sha-1a2b3c4  đổi APP_TAG trong deploy/.env sang tag này rồi cập nhật
# Quay lại bản cũ: update.sh <tag cũ> chỉ an toàn nếu migration mới tương thích ngược; chắc chắn nhất là
# restore.sh từ bản sao lưu mà script này vừa tạo trước khi cập nhật (migration chỉ đi tiến).
set -euo pipefail
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f compose.prod.yaml)
[ -f .env ] || { echo "Thiếu deploy/.env (copy từ .env.example)" >&2; exit 1; }

previous_tag="$(grep -E '^APP_TAG=' .env | tail -1 | cut -d= -f2- || true)"
tag="${1:-}"
if [ -n "$tag" ]; then
  [[ "$tag" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Tag không hợp lệ: '$tag'" >&2; exit 2; }
  if grep -q '^APP_TAG=' .env; then
    sed -i "s|^APP_TAG=.*|APP_TAG=$tag|" .env
  else
    echo "APP_TAG=$tag" >> .env
  fi
fi
echo "Tag trước: ${previous_tag:-latest}; tag cập nhật: ${tag:-${previous_tag:-latest}}"

echo "== 1/4 Sao lưu trước khi cập nhật"
if "${COMPOSE[@]}" ps --status running -q db | grep -q .; then
  bash scripts/backup.sh
else
  echo "Container db chưa chạy (lần triển khai đầu?): bỏ qua sao lưu"
fi

echo "== 2/4 Kéo image"
"${COMPOSE[@]}" pull

echo "== 3/4 Khởi động lại (docker compose up -d)"
"${COMPOSE[@]}" up -d --remove-orphans

echo "== 4/4 Chờ app khỏe (tối đa 180 giây)"
cid="$("${COMPOSE[@]}" ps -q app)"
for _ in $(seq 1 36); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")"
  [ "$status" = "healthy" ] && break
  sleep 5
done
if [ "$status" != "healthy" ]; then
  echo "LỖI: app chưa khỏe (trạng thái: $status). Xem log: docker compose -f compose.prod.yaml logs --tail=100 app" >&2
  echo "Tag trước đó: ${previous_tag:-latest}" >&2
  exit 1
fi

docker image prune -f > /dev/null
"${COMPOSE[@]}" ps
echo "Cập nhật xong."
