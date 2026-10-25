#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# 1. Bật DB nếu chưa chạy và chờ sẵn sàng
docker start datn-mcq-db >/dev/null
until docker exec datn-mcq-db pg_isready -U datn >/dev/null 2>&1; do sleep 1; done

# 2. Nạp biến từ .env ở gốc repo (bỏ ký tự \r nếu file sửa trên Windows)
set -a
source <(sed 's/\r$//' ../.env)
set +a

# 3. Kiểm tra pandoc rồi chạy app
pandoc --version | head -1
exec ./mvnw spring-boot:run