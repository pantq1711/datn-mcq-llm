# Triển khai lên máy chủ (W1-5)

Mục tiêu: một VPS Linux có tên miền và HTTPS để giáo viên dùng thử; chi phí thấp; vận hành bằng Docker Compose.

> **Trạng thái kiểm chứng:** các file và lệnh trong tài liệu này đã được viết nhưng **chưa được chạy thử** (chưa build
> image, chưa chạy compose, chưa chạy CI). Sau khi chạy kế hoạch kiểm chứng, cập nhật mục này.

> **Trước khi triển khai, toàn bộ thay đổi sẽ được merge vào nhánh `main`.** CI chỉ đẩy image lên GHCR khi push vào
> `main` hoặc tag `v*`, nên máy chủ chỉ chạy được code đã nằm trên `main`.

## 1. Kiến trúc

```
Internet ──443/80──▶ web (Caddy) ──/api/*──▶ app (Spring Boot :8080) ──▶ db (PostgreSQL + pgvector)
                      │  HTTPS tự động            │  worker sinh câu, pandoc xuất Word      │  volume pgdata
                      └─ frontend tĩnh (/)        └─ gọi Gemini ra Internet                 └─ không mở cổng ra ngoài
```

- Frontend và API **cùng một domain** (cookie refresh `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`).
- Chỉ `web` mở cổng 80/443. `app` và `db` không có `ports:`; `db` còn nằm trên mạng nội bộ không ra Internet.
- **Chỉ chạy một bản `app`.** Giới hạn đăng nhập sai nằm trong bộ nhớ và worker chạy cùng tiến trình; không dùng `--scale`.
- Flyway tự migrate khi `app` khởi động.

## 2. Chọn máy chủ

Cấu hình đề xuất: **VPS Ubuntu 24.04 LTS, x86_64, 2 vCPU, 4 GB RAM, ≥ 40 GB SSD**, có IPv4 công khai. Nhà cung cấp chọn
sau; cả hai hướng dưới đây đều phù hợp:

| Tiêu chí | VPS nước ngoài gần VN (ví dụ vùng Singapore) | VPS trong nước |
|---|---|---|
| Độ trễ tới giáo viên | khoảng vài chục ms | thấp nhất |
| Thanh toán | thẻ quốc tế | VND, chuyển khoản |
| Ghi chú | thường tính theo giờ, dễ nâng/hạ cấu hình | nên hỏi rõ băng thông, snapshot, hỗ trợ IPv4 |

Cần kiểm trước khi mua: x86_64 (CI build image `linux/amd64`; ARM cần build thêm `arm64`), có snapshot hoặc backup của
nhà cung cấp, cho mở cổng 80 và 443 (Let's Encrypt cần cả hai), tên miền trỏ được về IP.

**Ước tính bộ nhớ (chưa đo, sẽ đo bằng `docker stats`):**

| Thành phần | Ước tính |
|---|---|
| app: heap ~55% của giới hạn 1 GB + metaspace, luồng | ~700–800 MB |
| pandoc khi xuất Word (tiến trình con, cùng giới hạn với app) | +100–200 MB thoáng qua |
| db: PostgreSQL (`shared_buffers` 256 MB) | ~300–400 MB |
| web: Caddy | ~30–50 MB |
| Hệ điều hành + Docker | ~300 MB |

- **4 GB:** thoải mái, khuyến nghị.
- **2 GB:** chạy được nhưng sát; bắt buộc có swap 2 GB và nên hạ `mem_limit` của `app`. Không build image trên máy này.

## 3. Chuẩn bị trước

1. **Tên miền** và bản ghi DNS `A` (và `AAAA` nếu có IPv6) trỏ về IP máy chủ. Caddy chỉ xin được chứng chỉ khi DNS đã đúng
   và cổng 80/443 thông từ Internet.
2. **Mọi thay đổi đã nằm trên `main`** và workflow CI của lần push đó đã xanh (đã đẩy image `sha-…` và `latest`).
3. **Khóa Gemini riêng cho môi trường thật**, nên đặt hạn mức chi tiêu/cảnh báo ngân sách ở phía Google.
4. **Giá trị `VITE_API_BASE_URL`**: image `web` do CI build với mặc định `/api`. Giá trị này được ghi cứng vào bản build
   frontend; **phải thống nhất với người làm frontend** cách họ viết đường dẫn gọi API (backend phục vụ ở `/api/...`). Nếu
   khác, build lại image với `--build-arg VITE_API_BASE_URL=...` (xem mục 8).

## 4. Dựng máy chủ từ đầu

```bash
# 4.1 Tài khoản và SSH (làm một lần). Chỉ đăng nhập bằng key; tắt đăng nhập root và mật khẩu.
adduser deploy && usermod -aG sudo deploy
#   chép public key vào /home/deploy/.ssh/authorized_keys; trong /etc/ssh/sshd_config đặt:
#   PasswordAuthentication no   và   PermitRootLogin no     rồi: systemctl restart ssh

# 4.2 Cập nhật, tường lửa, tự cập nhật bản vá
apt update && apt -y upgrade
apt -y install ufw fail2ban unattended-upgrades
ufw default deny incoming && ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp && ufw enable

# 4.3 Swap 2 GB (bắt buộc nếu máy 2 GB, nên có cả khi 4 GB)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 4.4 Docker Engine + plugin compose: làm theo https://docs.docker.com/engine/install/ubuntu/
usermod -aG docker deploy      # đăng nhập lại để có hiệu lực
```

> Lưu ý: cổng mà Docker `publish` **bỏ qua ufw**. Vì vậy compose chỉ publish đúng 80/443 của `web`; không thêm `ports:` cho
> `db` hay `app`.

```bash
# 4.5 Lấy file triển khai (chỉ cần thư mục deploy/). Repo public:
sudo mkdir -p /opt/datn && sudo chown deploy: /opt/datn
git clone https://github.com/pantq1711/datn-mcq-llm.git /opt/datn
#   Repo private: tạo "deploy key" chỉ đọc cho máy chủ (GitHub: Settings > Deploy keys) rồi clone qua SSH,
#   hoặc chép thư mục deploy/ bằng scp:  scp -r deploy deploy@<ip>:/opt/datn/
chmod +x /opt/datn/deploy/scripts/*.sh

# 4.6 Cấu hình
cd /opt/datn/deploy
cp .env.example .env && chmod 600 .env
openssl rand -base64 48        # -> JWT_SECRET
openssl rand -hex 24           # -> DB_PASSWORD
nano .env                      # điền: SITE_ADDRESS, CORS_ALLOWED_ORIGINS=https://<tên miền>, DB_PASSWORD, JWT_SECRET,
                               #       ADMIN_EMAIL, ADMIN_PASSWORD (>= 12 ký tự), LLM_API_KEY
```

### 4.7 Quyền kéo image từ GHCR

Image nằm ở `ghcr.io/pantq1711/datn-mcq-llm/backend` và `.../web` (tên đặt trong `IMAGE_PREFIX`).

- **Repo public:** vào trang package trên GitHub (Your profile > Packages > chọn package > Package settings > Change
  visibility) và đặt **Public**. Máy chủ kéo được mà không cần đăng nhập. Làm cho cả hai package `backend` và `web`. Image
  không chứa bí mật (bí mật nằm ở `deploy/.env` trên máy chủ).
- **Repo private:** package mặc định private. Tạo token để máy chủ kéo image:
  1. GitHub > Settings > Developer settings > Personal access tokens > **Tokens (classic)** > Generate new token.
     Chỉ chọn scope **`read:packages`**, đặt thời hạn, đặt tên rõ ràng (ví dụ `datn-server-pull`).
     (Fine-grained token chưa dùng được cho GHCR.)
  2. Trên máy chủ: `echo '<TOKEN>' | docker login ghcr.io -u <tên-tài-khoản-GitHub> --password-stdin`
     (thông tin đăng nhập được Docker lưu ở `~/.docker/config.json`; giữ file này riêng tư).
  3. Hết hạn token thì tạo token mới và đăng nhập lại.

### 4.8 Khởi động lần đầu

```bash
cd /opt/datn/deploy
docker compose pull
docker compose up -d
docker compose ps                         # db, app, web đều "healthy"/"running" (app mất ~1–2 phút)
docker compose logs -f --tail=100 app     # Flyway migrate lên V16; dòng "Đã tạo tài khoản admin đầu tiên: <email>"
```

Kiểm tra: mở `https://<tên miền>` (chứng chỉ do Caddy tự xin lần đầu, có thể mất vài chục giây); `curl -i
https://<tên miền>/actuator/health` phải trả **404** (đã bị chặn); `https://<tên miền>/swagger-ui/index.html` cũng 404.

### 4.9 Tạo admin đầu tiên và tài khoản giáo viên

1. `ADMIN_EMAIL` và `ADMIN_PASSWORD` trong `deploy/.env` được dùng khi `app` khởi động và **chưa có admin nào**. Không ghi
   đè tài khoản có sẵn. Mật khẩu từ 12 ký tự, tối đa 72 byte; sai thì `app` không khởi động và log nêu lý do.
2. Khi log có dòng "Đã tạo tài khoản admin đầu tiên", **xóa `ADMIN_PASSWORD` khỏi `deploy/.env`** rồi `docker compose up -d`
   (Compose tạo lại container `app` với cấu hình mới).
3. Đăng nhập bằng tài khoản admin, tạo tài khoản giáo viên qua API `POST /api/admin/users` (xem `docs/api/README.md`);
   đặt lại mật khẩu bằng `POST /api/admin/users/{id}/reset-password`.

> Môn học: cơ sở dữ liệu mới không có sẵn môn nào (môn `Toán` chỉ có sau khi tạo ma trận từ mẫu hoặc tạo bằng
> `db/dev-seed`). **Không chạy `db/dev-seed/demo_base.sql` trên máy chủ thật** (nó tạo user `demo@example.com`). Việc có
> cần thêm môn mặc định bằng migration hay không đang chờ kiểm chứng.

## 5. Cấu hình môi trường

Biến đặt trong `deploy/.env` (mẫu: `deploy/.env.example`). Compose **đè cố định** các giá trị dưới đây, không chỉnh được qua
`.env` (trừ `COOKIE_SECURE`):

| Biến | Giá trị trên máy chủ | Vì sao |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | tắt Swagger/OpenAPI, giới hạn upload 10 MB / 12 MB, tắt máy chủ nhẹ nhàng |
| `DB_HOST`, `DB_PORT` | `db`, `5432` | DB trong mạng nội bộ |
| `FORWARD_HEADERS_STRATEGY` | `native` | lấy IP thật từ `X-Forwarded-For`; để `none` thì mọi người chung IP của proxy và một kẻ xấu khóa được đăng nhập của bất kỳ giáo viên nào |
| `WORKER_ENABLED` | `true` | mặc định của app là `false`; để `false` thì job sinh câu nằm `pending` mãi |
| `COOKIE_SECURE` | `true` (mặc định) | cookie refresh chỉ đi qua HTTPS |

- **Gemini:** `LLM_API_KEY` (app cũng chấp nhận biến hệ thống `GEMINI_API_KEY` làm dự phòng). Chỉ đặt `LLM_BASE_URL` khi
  kiểm chứng bằng Gemini giả.
- **Múi giờ `Asia/Bangkok`:** code dùng `LocalDateTime.now()` và cột `TIMESTAMP` không múi giờ nên giờ phụ thuộc múi giờ
  JVM. Image đặt `-Duser.timezone=Asia/Bangkok` và `TZ`; PostgreSQL cũng đặt `timezone=Asia/Bangkok`. Đừng đổi giữa chừng
  trên dữ liệu đã có.
- **JVM:** heap = 55% bộ nhớ container (`mem_limit` của `app` là 1 GB). Để chỉnh, đặt `JAVA_TOOL_OPTIONS` trong
  `deploy/.env` (thay **toàn bộ** cờ trong image, nhớ giữ `-Djava.awt.headless=true` và `-Duser.timezone=Asia/Bangkok`).
- **Upload:** hiện chưa có endpoint upload; giới hạn 10 MB/tệp, 12 MB/yêu cầu (backend) và 12 MB thân `/api/*` (Caddy)
  chỉ là chuẩn bị. Muốn đổi thì sửa cả `application-prod.yaml` và `deploy/web/Caddyfile`.
- **Health:** `GET /actuator/health` chỉ trả trạng thái (UP/DOWN), chi tiết chỉ cho người đã đăng nhập. Caddy chặn
  `/actuator` từ bên ngoài; healthcheck của Docker gọi nội bộ trong container.
- **PostgreSQL:** `shared_buffers=256MB`, `max_connections=60`, `mem_limit` 768 MB.

## 6. Vận hành

Mọi lệnh chạy trong `/opt/datn/deploy` (Compose đọc `COMPOSE_FILE` từ `.env`).

### Xem log

```bash
docker compose logs -f --tail=200 app      # backend (Flyway, worker, lỗi)
docker compose logs -f --tail=100 web      # Caddy: chứng chỉ, truy cập
docker compose logs --since=1h db
docker stats                               # bộ nhớ/CPU từng container
```

Log lưu bằng `json-file`, xoay vòng 5 file × 10 MB mỗi container.

### Cập nhật phiên bản

```bash
bash scripts/update.sh                # kéo lại tag hiện tại trong .env (ví dụ latest)
bash scripts/update.sh sha-1a2b3c4    # hoặc chỉ định tag (lấy ở trang Packages hoặc tab Actions)
```

Script sao lưu DB, `pull`, `up -d`, rồi chờ `app` khỏe. Flyway tự migrate khi `app` khởi động. Trong lúc khởi động lại
(khoảng 1–2 phút) site tạm thời không dùng được; job đang chạy dở được `StaleJobRecovery` trả về hàng đợi sau
`WORKER_STALE_AFTER_SECONDS` (mặc định 300 giây).

**Quay lại bản cũ:** migration Flyway chỉ đi tiến. Đổi lại tag cũ chỉ an toàn nếu migration mới tương thích ngược. Cách chắc
chắn là khôi phục bản sao lưu mà `update.sh` tạo ngay trước khi cập nhật (mục dưới) rồi `update.sh <tag cũ>`.

### Sao lưu và khôi phục

```bash
# Cron (user trong nhóm docker hoặc root), 02:00 hằng ngày:
( crontab -l 2>/dev/null; echo '0 2 * * * /opt/datn/deploy/scripts/backup.sh >> /var/log/datn-backup.log 2>&1' ) | crontab -
#   cần quyền ghi /var/log/datn-backup.log và /var/backups/datn (hoặc đặt BACKUP_DIR trong lệnh cron)

bash scripts/backup.sh                                   # sao lưu ngay
ls -lh /var/backups/datn/
```

- File `datn-<ngày-giờ>.dump` (định dạng custom của `pg_dump`), giữ 14 ngày (`KEEP_DAYS`).
- **Bản sao lưu cùng ổ đĩa với DB không đủ an toàn.** Làm ít nhất một trong hai: bật snapshot/backup của nhà cung cấp,
  hoặc đặt `RCLONE_REMOTE` (ví dụ `gdrive:datn-backup`, cài và cấu hình `rclone` trước) để chép ra ngoài.
- **Thử khôi phục định kỳ (mỗi tháng và trước ngày giáo viên dùng thử)** vào DB tạm, không đụng DB đang chạy:

```bash
bash scripts/restore.sh /var/backups/datn/datn-20261101-020000.dump --into datn_restore_test
```

- **Khôi phục thật** (ghi đè toàn bộ dữ liệu hiện tại; dừng `app`, xóa DB, nạp lại, bật `app`):

```bash
bash scripts/restore.sh /var/backups/datn/datn-20261101-020000.dump --yes
```

### Đặt lại mật khẩu admin

Admin quên mật khẩu: một admin khác đặt lại qua `POST /api/admin/users/{id}/reset-password`. Nếu không còn admin nào, đặt
`ADMIN_EMAIL`/`ADMIN_PASSWORD` mới (email chưa thuộc tài khoản nào) và `docker compose up -d`.

## 7. CI/CD

Workflow `.github/workflows/ci.yml`:

| Sự kiện | Việc làm |
|---|---|
| Mọi pull request | test backend (~770 test, PostgreSQL pgvector thật, DB `datn_mcq_test`, LLM giả), build frontend, **build** image (không đẩy) |
| Push vào `main` | như trên, rồi đẩy image `backend` và `web` với tag `sha-<7 ký tự>` và `latest` |
| Push tag `v*` (ví dụ `v1.0.0`) | như trên, đẩy tag `sha-…` và `1.0.0` |

- CI **không cần khóa Gemini** và không dùng secret nào ngoài `GITHUB_TOKEN` tự có (quyền `packages: write` chỉ ở job
  build image).
- **Deploy làm thủ công** bằng `scripts/update.sh` trên máy chủ (mục 6), không tự động sau khi merge. Có thể thêm bước
  deploy bằng SSH sau.
- Lint frontend tạm thời không chặn CI (`continue-on-error`).
- Nên thêm branch protection cho `main`: bắt buộc PR và CI xanh trước khi merge.

## 8. Build image tại chỗ (kiểm chứng local, hoặc thay thế GHCR)

```bash
cd deploy
# trong .env: COMPOSE_FILE=compose.prod.yaml:compose.build.yaml   và   APP_TAG=local
docker compose build                                  # app (back_end/Dockerfile) + web (deploy/web/Dockerfile)
docker compose build --build-arg VITE_API_BASE_URL=/api web
```

Build backend cần khoảng 1–2 GB RAM trống cho Maven; không build trên VPS 2 GB.

Trên Windows dấu phân cách trong `COMPOSE_FILE` là `;` (`compose.prod.yaml;compose.build.yaml`), trên Linux/macOS là `:`.
Có thể thay bằng `docker compose -f compose.prod.yaml -f compose.build.yaml ...`.

## 9. Chạy thử local (không cần tên miền)

Dùng `deploy/.env` riêng; project Compose tên `datn` có volume riêng, **không đụng** container dev `datn-mcq-db`.

```ini
COMPOSE_FILE=compose.prod.yaml:compose.build.yaml
APP_TAG=local
SITE_ADDRESS=localhost:8443         # Caddy dùng chứng chỉ tự ký; trình duyệt sẽ cảnh báo (curl -k)
HTTPS_PORT=8443
HTTP_PORT=8080
HSTS=max-age=0                      # tránh trình duyệt nhớ HSTS cho localhost
CORS_ALLOWED_ORIGINS=https://localhost:8443
# DB_PASSWORD, JWT_SECRET, ADMIN_*: giá trị thử nghiệm
```

Chỉ chạy qua `http://localhost:8080` (không TLS): `SITE_ADDRESS=http://localhost`, và `COOKIE_SECURE=false` (Safari không
nhận cookie Secure qua http).

## 10. Checklist bảo mật

- [ ] SSH chỉ bằng key; tắt đăng nhập root và mật khẩu; fail2ban chạy.
- [ ] ufw chỉ mở 22, 80, 443 (tcp) và 443 (udp); `docker compose ps` không thấy cổng nào khác mở ra `0.0.0.0` (ngoài 80/443).
- [ ] `deploy/.env` quyền `600`, thuộc user triển khai, **không** nằm trong Git.
- [ ] `JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD` sinh ngẫu nhiên; `ADMIN_PASSWORD` đã xóa khỏi `.env` sau khi tạo admin.
- [ ] Khóa Gemini riêng cho môi trường thật, có hạn mức/cảnh báo chi tiêu.
- [ ] `https://<tên miền>/swagger-ui/index.html` và `/actuator/health` trả 404 từ bên ngoài.
- [ ] Đăng nhập: cookie `refresh_token` có `Secure; HttpOnly; SameSite=Strict`.
- [ ] `FORWARD_HEADERS_STRATEGY=native` (kiểm bằng cách đăng nhập sai nhiều lần từ hai IP khác nhau: chỉ IP đó bị khóa).
- [ ] Sao lưu chạy theo lịch, có bản **ngoài máy chủ**, đã thử khôi phục vào DB tạm ít nhất một lần.
- [ ] `unattended-upgrades` bật; theo dõi bản vá của image gốc (`eclipse-temurin`, `pgvector`, `caddy`) khi cập nhật.
- [ ] Token `read:packages` (nếu repo private) chỉ có scope đó và có thời hạn.

## 11. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Job tạo đề đứng ở `pending` | `WORKER_ENABLED` không phải `true` (kiểm `docker compose exec app printenv WORKER_ENABLED`); hoặc hết quota/khóa Gemini sai (xem log `app`) |
| Trình duyệt báo không an toàn / không xin được chứng chỉ | DNS chưa trỏ đúng, cổng 80/443 bị chặn, hoặc đã xin quá nhiều lần (Let's Encrypt giới hạn); xem `docker compose logs web` |
| Đăng nhập được nhưng bị đăng xuất sau ít phút | cookie không lưu: `COOKIE_SECURE=true` mà truy cập qua http, hoặc frontend gọi API khác domain |
| API trả 403 "Invalid CORS request" | `CORS_ALLOWED_ORIGINS` không khớp `https://<tên miền>` |
| Xuất Word báo 503/500 | `docker compose exec app pandoc --version`; xem log `app`; container có thể bị OOM (`docker inspect` ghi `OOMKilled`) → tăng `mem_limit` của `app` |
| `app` khởi động lại liên tục | `docker compose logs app`: thường là `JWT_SECRET` thiếu/ngắn, `ADMIN_PASSWORD` dưới 12 ký tự, hoặc sai mật khẩu DB (đổi `DB_PASSWORD` sau khi volume `pgdata` đã tạo không có tác dụng) |
| Đĩa đầy | `docker system df`; `docker image prune -f`; kiểm thư mục sao lưu |

## 12. Việc còn mở

- Môn học mặc định cho cơ sở dữ liệu mới (migration `V17` hay không) — chờ kết quả kiểm chứng.
- JLaTeXMath: image đã có font và `java.awt.headless=true`; cần smoke test vẽ ảnh công thức khi thêm dependency.
- Layered jar (Spring Boot `jarmode=tools`) để giảm kích thước kéo image mỗi lần cập nhật — chưa làm.
- Tự động deploy sau khi merge — chưa làm, quyết định deploy thủ công trong giai đoạn dùng thử.
