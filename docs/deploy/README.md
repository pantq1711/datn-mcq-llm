# Triển khai lên máy chủ (W1-5)

Mục tiêu: một VPS Linux có tên miền và HTTPS để giáo viên dùng thử; chi phí thấp; vận hành bằng Docker Compose.

> **Trạng thái kiểm chứng (25/10/2026, Docker Desktop trên Windows, Gemini giả):**
>
> | Phần | Kết quả |
> |---|---|
> | Image backend (`back_end/Dockerfile`): build, user non-root, pandoc 3.12.1, font tiếng Việt, múi giờ | Đã chạy, đạt |
> | Xuất Word trong container (pandoc + `reference.docx`): 10 equation OMML, ĐÁP ÁN, LỜI GIẢI | Đã chạy, đạt |
> | `caddy validate` Caddyfile; định tuyến, chặn `/actuator` `/swagger-ui` `/v3/api-docs`, proxy `/api`, cookie `Secure; HttpOnly; SameSite=Strict`, khóa đăng nhập theo IP qua `X-Forwarded-For` | Đã chạy, đạt |
> | Luồng tạo ma trận → tạo đề → Giữ / Sửa / Đổi → xuất Word, bằng Gemini giả qua `LLM_BASE_URL` | Đã chạy, đạt |
> | Đo bộ nhớ khi worker chạy và khi xuất Word đồng thời (mục 2) | Đã chạy |
> | Sao lưu, khôi phục vào DB tạm (so số bản ghi), khôi phục đè (`restore.sh --yes`) | Đã chạy, đạt |
> | **Image `web` (frontend thật)** | **Chưa build được:** `npm run build` (`tsc -b`) báo 3 lỗi TypeScript có sẵn trong mã frontend (`Header.tsx`, `Sidebar.tsx`, `ReviewQuestionForm.tsx`). Chờ người làm frontend sửa. |
> | **CI trên GitHub (D7)** | **Chưa chạy.** Job `frontend` sẽ đỏ vì lỗi TypeScript trên; chờ frontend sửa rồi mở PR để kích hoạt. |
> | Gemini **thật** trong container (`LLM_API_KEY` đặt qua `deploy/.env`): đề 3 câu (1 mcq, 1 tf, 1 sa) | Đã chạy, đạt: 3/3 câu `ready` sau ~2 phút, xuất Word 200. Câu tf có cờ "cần xem kỹ" (`solver_inconsistent`): Solver thật lệch nhãn, đúng thiết kế, không phải lỗi triển khai |
> | `update.sh`, kéo image từ GHCR, Let's Encrypt trên tên miền thật | Chưa chạy (cần image đã đẩy lên GHCR, máy chủ thật, tên miền) |
>
> Lần kiểm chứng dùng một trang tĩnh giả thay cho frontend (Caddyfile là bản thật của repo).

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

Cấu hình đề xuất cho giai đoạn dùng thử: **VPS Ubuntu 24.04 LTS, x86_64, 2 vCPU, 2 GB RAM + 2 GB swap, ≥ 40 GB SSD**, có
IPv4 công khai. Nâng lên **4 GB RAM** nếu có nhiều người dùng đồng thời (xem số đo bên dưới). Nhà cung cấp chọn sau; cả hai
hướng dưới đây đều phù hợp:

| Tiêu chí | VPS nước ngoài gần VN (ví dụ vùng Singapore) | VPS trong nước |
|---|---|---|
| Độ trễ tới giáo viên | khoảng vài chục ms | thấp nhất |
| Thanh toán | thẻ quốc tế | VND, chuyển khoản |
| Ghi chú | thường tính theo giờ, dễ nâng/hạ cấu hình | nên hỏi rõ băng thông, snapshot, hỗ trợ IPv4 |

Cần kiểm trước khi mua: x86_64 (CI build image `linux/amd64`; ARM cần build thêm `arm64`), có snapshot hoặc backup của
nhà cung cấp, cho mở cổng 80 và 443 (Let's Encrypt cần cả hai), tên miền trỏ được về IP.

**Bộ nhớ đo thực tế** (`docker stats` và cgroup, Gemini giả, dữ liệu rất nhỏ, giới hạn `app` 1 GiB):

| Thành phần | Đo được |
|---|---|
| app, nghỉ sau khi khởi động | ~390 MiB |
| app, worker đang sinh 8 câu | ~400 MiB (đỉnh 409 MiB) |
| app, xuất Word 6 yêu cầu đồng thời × 4 đợt (24 lần, không lỗi) | đỉnh 466 MiB; không OOM |
| db (PostgreSQL) | ~70 MiB |
| web (Caddy) | ~20 MiB |

- Tổng ba container khoảng 0,5 GiB, thấp hơn nhiều so với ước tính ban đầu (~1,3–1,6 GB). Đây là tải nhẹ và dữ liệu nhỏ:
  khi có nhiều giáo viên và dữ liệu lớn hơn, PostgreSQL và heap sẽ tăng. Đo lại bằng `docker stats` sau vài ngày dùng thật.
- **2 vCPU / 2 GB + swap 2 GB: đủ cho giai đoạn dùng thử.** Ba container dùng khoảng 0,5 GiB, còn lại cho hệ điều hành
  (~0,3 GiB) và dư địa. Swap 2 GB là bắt buộc (mục 4.3) để chịu được đỉnh bất thường. Không build image trên máy này.
- **4 GB:** chọn khi nhiều người dùng đồng thời (nhiều đề sinh song song, xuất Word cùng lúc), dữ liệu lớn lên, hoặc muốn
  build image tại chỗ. **Cấu hình 4 GB (D5):** `compose.prod.yaml` đặt `mem_limit` của `app` là **1536m** (heap JVM 55% ≈ 845 MB;
  đổi bằng `APP_MEM_LIMIT` trong `deploy/.env`), `db` 768m, `web` 128m: tổng giới hạn 2,4 GB, còn lại cho hệ điều hành,
  pandoc (tiến trình con nằm trong cgroup của `app`) và page cache. Máy 2 GB **phải** đặt `APP_MEM_LIMIT=1g` (1536m + 768m + 128m
  vượt RAM thật). Không đổi `db`/`web` khi nâng RAM nếu chưa đo.

## 3. Chuẩn bị trước

1. **Tên miền** và bản ghi DNS `A` (và `AAAA` nếu có IPv6) trỏ về IP máy chủ. Caddy chỉ xin được chứng chỉ khi DNS đã đúng
   và cổng 80/443 thông từ Internet.
2. **Mọi thay đổi đã nằm trên `main`** và workflow CI của lần push đó đã xanh (đã đẩy image `sha-…` và `latest`).
3. **Khóa Gemini riêng cho môi trường thật**, nên đặt hạn mức chi tiêu/cảnh báo ngân sách ở phía Google.
4. **Giá trị `VITE_API_BASE_URL`**: image `web` do CI build với mặc định `/api`. Giá trị này được ghi cứng vào bản build
   frontend; **phải thống nhất với người làm frontend** cách họ viết đường dẫn gọi API (backend phục vụ ở `/api/...`). Nếu
   khác, build lại image với `--build-arg VITE_API_BASE_URL=...` (xem mục 8).

### Dữ liệu và khóa Gemini

- **Dùng Project / API key riêng:** Khuyến nghị tạo Google Cloud Project và API key riêng dành cho hệ thống này trên môi trường thật (không dùng chung key với các dự án khác vì sẽ bị dùng chung hạn mức gọi API).
- **Cân nhắc gói trả phí:** Với gói miễn phí của Gemini API, Google có thể sử dụng dữ liệu gửi lên để cải thiện sản phẩm (theo điều khoản của Google tại thời điểm 10/2026); khi triển khai hệ thống cho giáo viên dùng thật nên cân nhắc chuyển sang gói trả phí và thiết lập cảnh báo ngân sách.
- **Dẫn chiếu chính sách dữ liệu:** Xem chi tiết về dữ liệu gửi tới dịch vụ AI, trách nhiệm của giáo viên và ô xác nhận ở frontend tại [`docs/api/README.md` mục 9.5](../api/README.md#95-dữ-liệu-gửi-tới-dịch-vụ-ai).

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

# 4.3 Swap 2 GB (bắt buộc với máy 2 GB, nên có cả khi 4 GB)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 4.4 Docker Engine + plugin compose: làm theo https://docs.docker.com/engine/install/ubuntu/
usermod -aG docker deploy      # đăng nhập lại để có hiệu lực
```

> Lưu ý: cổng mà Docker `publish` **bỏ qua ufw**. Vì vậy compose chỉ publish đúng 80/443 của `web`; không thêm `ports:` cho
> `db` hay `app`.

```bash
# 4.5 Lấy file triển khai (chỉ cần thư mục deploy/; repo public nên clone không cần khóa)
sudo mkdir -p /opt/datn && sudo chown deploy: /opt/datn
git clone https://github.com/pantq1711/datn-mcq-llm.git /opt/datn
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

Repo `pantq1711/datn-mcq-llm` là **public**. Sau lần CI đầu tiên đẩy image, vào trang package trên GitHub (Your profile >
Packages > chọn package > Package settings > Change visibility) và đặt **Public** cho cả hai package `backend` và `web`.
Máy chủ kéo được mà không cần đăng nhập. Image không chứa bí mật (bí mật nằm ở `deploy/.env` trên máy chủ).

> Nếu sau này repo hoặc package chuyển sang private: tạo Personal access token (classic) chỉ với scope `read:packages`,
> rồi trên máy chủ chạy `echo '<TOKEN>' | docker login ghcr.io -u <tài-khoản-GitHub> --password-stdin`.

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

Kiểm tra quyền ghi thư mục tạm `/tmp` trên volume đĩa:
```bash
docker compose exec app sh -c 'touch /tmp/.w && rm /tmp/.w && echo ok'
```
Lệnh phải in ra `ok`. Nếu báo `Permission denied` (do Docker tạo volume với user `root` trong khi container `app` chạy user non-root `app` có UID/GID `10001:10001` theo `back_end/Dockerfile`): sửa quyền bằng `docker compose exec -u 0 app chown -R 10001:10001 /tmp` (hoặc `chmod 1777 /tmp`), sau đó chạy lại lệnh kiểm tra trên.

### 4.9 Tạo admin đầu tiên và tài khoản giáo viên

1. `ADMIN_EMAIL` và `ADMIN_PASSWORD` trong `deploy/.env` được dùng khi `app` khởi động và **chưa có admin nào**. Không ghi
   đè tài khoản có sẵn. Mật khẩu từ 12 ký tự, tối đa 72 byte; sai thì `app` không khởi động và log nêu lý do.
2. Khi log có dòng "Đã tạo tài khoản admin đầu tiên", **xóa `ADMIN_PASSWORD` khỏi `deploy/.env`** rồi `docker compose up -d`
   (Compose tạo lại container `app` với cấu hình mới).
3. Đăng nhập bằng tài khoản admin, tạo tài khoản giáo viên qua API `POST /api/admin/users` (xem `docs/api/README.md`);
   đặt lại mật khẩu bằng `POST /api/admin/users/{id}/reset-password`.

> Môn học: migration `V17` tự thêm hai môn **Toán** và **Giáo dục công dân** khi `app` khởi động (không thêm bản trùng nếu
> môn đã có, không phân biệt hoa thường), nên giáo viên tạo ma trận bằng JSON được ngay, không cần bước thủ công.
> **Không chạy `db/dev-seed/demo_base.sql` trên máy chủ thật** (nó tạo user `demo@example.com`).

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
- **JVM:** heap = 55% bộ nhớ container (`mem_limit` của `app` mặc định 1536m, mục 2 "Cấu hình 4 GB"; máy 2 GB đặt `APP_MEM_LIMIT=1g`). Để chỉnh, đặt `JAVA_TOOL_OPTIONS` trong
  `deploy/.env` (thay **toàn bộ** cờ trong image, nhớ giữ `-Djava.awt.headless=true` và `-Duser.timezone=Asia/Bangkok`).
- **Upload:** hiện chưa có endpoint upload; giới hạn 10 MB/tệp, 12 MB/yêu cầu (backend) và 12 MB thân `/api/*` (Caddy)
  chỉ là chuẩn bị. Muốn đổi thì sửa cả `application-prod.yaml` và `deploy/web/Caddyfile`.
- **Health:** `GET /actuator/health` chỉ trả trạng thái (UP/DOWN), chi tiết chỉ cho người đã đăng nhập. Caddy chặn
  `/actuator` từ bên ngoài; healthcheck của Docker gọi nội bộ trong container.
- **Tài nguyên Pandoc & trích tài liệu:**
  - `MATERIAL_MAX_CONCURRENT` (mặc định prod: `1`): số tác vụ trích tài liệu (`docx`/`pdf`) và xuất Word (`pandoc`) được chạy cùng một lúc. Cả hai dùng chung một Semaphore có trần permit để tránh OOM khi máy chủ VPS chỉ có 2 GB RAM (container `app` giới hạn 1 GB).
  - `MATERIAL_QUEUE_WAIT` (mặc định: `30s`): thời gian chờ xếp hàng lấy permit; nếu quá thời gian này sẽ trả lời ngay `503 Service Unavailable` (`EXTRACTOR_UNAVAILABLE` hoặc `PANDOC_UNAVAILABLE`) kèm header `Retry-After: 30`.
  - `MATERIAL_PANDOC_HEAP_MB` (mặc định prod: `256`): Pandoc được chạy kèm cờ Haskell RTS `+RTS -M<MB>m -RTS` để khống chế heap tối đa, tránh tràn RAM container.
  - **Thư mục tạm `/tmp`:** Chuyển từ `tmpfs: 128m` sang Docker volume trên đĩa `app_tmp:/tmp`. Thay đổi này giúp tiết kiệm 128 MB RAM cho container và đảm bảo sẵn sàng tương thích nếu sau này bật `read_only: true` (volume ghi được mount vào `/tmp`, không làm mất cờ read-only của container filesystem).
- **Giới hạn sinh câu theo từng giáo viên (D1):** để một người không làm tắc hàng đợi và cạn quota Gemini dùng chung
  (480 lượt/ngày cho cả hệ thống). Chỉ câu hệ thống phải sinh mới bị tính; câu lấy từ ngân hàng thì không. Đặt trong
  `deploy/.env`, đổi xong khởi động lại `app`:
  - `GENERATION_MAX_QUESTIONS_PER_EXAM` (mặc định `60`): tổng số câu của một ma trận hoặc một đề sinh theo bài; vượt thì 400.
  - `GENERATION_MAX_PENDING_JOBS` (mặc định `60`): số job sinh câu đang chờ/đang chạy của một giáo viên; vượt thì 429
    `GENERATION_QUEUE_LIMIT`.
  - `GENERATION_MAX_DAILY_QUESTIONS` (mặc định `120`): số câu yêu cầu sinh mỗi ngày (theo giờ Việt Nam, đặt lại lúc 0 giờ),
    tính cả Đổi câu bằng sinh mới; vượt thì 429 `DAILY_GENERATION_LIMIT` kèm `Retry-After`.
  - D2, độ dài trường ô ma trận đi vào prompt: `GENERATION_CELL_CONTENT_MAX_CHARS` (`1000`),
    `GENERATION_CELL_OUTCOME_MAX_CHARS` (`500`), `GENERATION_CELL_OUTCOMES_MAX_ITEMS` (`10`). Chỉ hạ được, không nới quá giá trị
    mặc định (trần cứng của prompt; đặt cao hơn thì app không khởi động).
  Migration V24 thêm bảng `generation_quota` (một dòng mỗi giáo viên). Mã lỗi và thông báo: `docs/api/README.md` mục 7.2b.
  Cỡ các trần nên đối chiếu với `LLM_DAILY_QUOTA` (480): 120 câu/ngày/giáo viên × số giáo viên dùng cùng lúc không được vượt quá
  quota Gemini của cả hệ thống (mỗi câu tốn ít nhất một lượt sinh, thường thêm lượt Solver).
- **Giới hạn đăng nhập theo IP (D3):** `LOGIN_IP_MAX_ATTEMPTS` (mặc định `20`) lần mỗi `LOGIN_IP_WINDOW` (mặc định `1m`) cho
  mỗi IP (IPv6 gom /64), chạy trước bcrypt, độc lập với `LOGIN_MAX_FAILURES`/`LOGIN_FAILURE_WINDOW`/`LOGIN_LOCK_DURATION` (khóa
  theo email + IP). Phụ thuộc `FORWARD_HEADERS_STRATEGY=native` để thấy IP thật sau Caddy; nếu không, mọi người cùng chung IP
  của proxy và dùng chung 20 lần/phút. Vượt thì 429 `TOO_MANY_ATTEMPTS` kèm `Retry-After`. Bộ đếm trong bộ nhớ, mất khi khởi
  động lại.
- **Cửa sổ ân hạn refresh (D6):** `REFRESH_REUSE_GRACE` (mặc định `30s`, `0` là tắt): token vừa bị thay trong khoảng này mà bị
  gửi lại (tab thứ hai) trả 401 `REFRESH_RACE` thay vì thu hồi cả chuỗi. Chuỗi đã bị thu hồi vì lý do khác (đăng xuất, đổi mật
  khẩu, khóa) thì vẫn là dùng lại. Frontend phải xử lý `REFRESH_RACE` (chờ 1 giây, thử lại một lần): `docs/api/README.md` mục 8.
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
COMPOSE_FILE=compose.prod.yaml:compose.build.yaml   # Windows: dấu ;
APP_TAG=local
SITE_ADDRESS=localhost              # KHÔNG ghi cổng ở đây (xem lưu ý dưới); Caddy dùng chứng chỉ tự ký (curl -k)
HTTPS_PORT=8443                     # chỉ là cổng phía máy host, ánh xạ vào cổng 443 của container
HTTP_PORT=8080
HSTS=max-age=0                      # tránh trình duyệt nhớ HSTS cho localhost
CORS_ALLOWED_ORIGINS=https://localhost:8443
# DB_PASSWORD, JWT_SECRET, ADMIN_*: giá trị thử nghiệm
```

> `SITE_ADDRESS=localhost:8443` **không chạy được**: Caddy sẽ lắng nghe cổng 8443 *trong* container, còn compose chỉ ánh xạ
> cổng host vào cổng 443 của container, nên kết nối bị ngắt. Chỉ đổi cổng phía host bằng `HTTPS_PORT`/`HTTP_PORT`. Hệ quả:
> chuyển hướng http→https của Caddy trỏ tới `https://localhost/` (không kèm cổng); khi chạy thử hãy mở thẳng
> `https://localhost:8443`.

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

## 11. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Job tạo đề đứng ở `pending` | `WORKER_ENABLED` không phải `true` (kiểm `docker compose exec app printenv WORKER_ENABLED`); hoặc hết quota/khóa Gemini sai (xem log `app`) |
| Trình duyệt báo không an toàn / không xin được chứng chỉ | DNS chưa trỏ đúng, cổng 80/443 bị chặn, hoặc đã xin quá nhiều lần (Let's Encrypt giới hạn); xem `docker compose logs web` |
| Đăng nhập được nhưng bị đăng xuất sau ít phút | cookie không lưu: `COOKIE_SECURE=true` mà truy cập qua http, hoặc frontend gọi API khác domain |
| API trả 403 "Invalid CORS request" | `CORS_ALLOWED_ORIGINS` không khớp `https://<tên miền>` |
| Tải tài liệu docx báo 503 `EXTRACTOR_UNAVAILABLE` hoặc container `OOMKilled` khi tải docx/pdf lớn | docx cần pandoc (cùng bản dùng xuất Word): `docker compose exec app pandoc --version`. OOM: hạ `MATERIAL_PANDOC_HEAP_MB` hoặc tăng `mem_limit` của `app`; pdf độc có thể ăn nhiều heap, xem `docs/api/README.md` mục 9.4 |
| Xuất Word báo 503/500 | 503 `PANDOC_UNAVAILABLE`: hết slot hoặc quá thời gian xếp hàng `MATERIAL_QUEUE_WAIT` (đang trích tài liệu hoặc xuất Word khác); thử lại sau thời gian trong header `Retry-After`. 500: kiểm tra pandoc trong container (`docker compose exec app pandoc --version`), xem log `app`; nếu container bị OOM (`docker inspect` ghi `OOMKilled`) → tăng `mem_limit` của `app` hoặc hạ `MATERIAL_PANDOC_HEAP_MB` |
| `app` khởi động lại liên tục | `docker compose logs app`: thường là `JWT_SECRET` thiếu/ngắn, `ADMIN_PASSWORD` dưới 12 ký tự, hoặc sai mật khẩu DB (đổi `DB_PASSWORD` sau khi volume `pgdata` đã tạo không có tác dụng) |
| Đĩa đầy | `docker system df`; `docker image prune -f`; kiểm thư mục sao lưu |

## 12. Việc còn mở

- **Môn học "Giáo dục công dân":** V17 chỉ thêm tên môn. Các prompt sinh câu hiện viết cho giáo viên Toán THCS (một số
  prompt còn ghi cứng "Toán" khi không có tên môn), nên chất lượng sinh câu cho môn khác chưa được kiểm.
- **Image `web` và CI (D7):** chờ người làm frontend sửa 3 lỗi TypeScript (xem "Trạng thái kiểm chứng").
- **Trích PDF của tài liệu bài học (W2-3):** PDFBox không có trần bộ nhớ cho luồng nén; một PDF độc có thể làm `app` hết heap và thoát (`ExitOnOutOfMemoryError`). Chi tiết và cách đã giảm rủi ro: `docs/api/README.md` mục 9.4.
- **Kích thước image backend ~742 MB** (chưa tối ưu): chưa rõ phần nào chiếm nhiều (lớp Ubuntu, font, pandoc, JRE).
  Layered jar (Spring Boot `jarmode=tools`) có thể giảm lượng phải kéo mỗi lần cập nhật — chưa kiểm với Boot 4.1.1.
- JLaTeXMath: image đã có font và `java.awt.headless=true`; cần smoke test vẽ ảnh công thức khi thêm dependency.
- `update.sh` và kéo image từ GHCR: chưa chạy được vì chưa có image trên GHCR.
- Tự động deploy sau khi merge — chưa làm, quyết định deploy thủ công trong giai đoạn dùng thử.
