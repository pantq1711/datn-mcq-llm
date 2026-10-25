# syntax=docker/dockerfile:1
# Backend Spring Boot (W1-5). Build context: thư mục back_end/.
#   docker build -t datn-backend back_end

# ---- Tầng build: Maven wrapper + JDK 21 ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# `sh mvnw` để không phụ thuộc quyền thực thi của file; cache ~/.m2 giữa các lần build (BuildKit)
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp dependency:go-offline
COPY src src
# Test đã chạy ở CI (PostgreSQL thật), image chỉ đóng gói
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -ntp -DskipTests package \
    && cp target/*.jar /app.jar

# ---- Tầng chạy: JRE 21 (glibc), pandoc, font, user không phải root ----
FROM eclipse-temurin:21-jre-noble

# Phiên bản pandoc phải khớp bản dùng để tạo/kiểm tra reference.docx (docs/api/README.md mục 7.6)
ARG PANDOC_VERSION=3.12.1
ARG PANDOC_SHA256=b4217ac7c54067c442f9d4cd38bcd0c184a43f3e7053431a12263a6f92a33ba2

# curl: healthcheck. tzdata: múi giờ Asia/Bangkok. fontconfig + libfreetype6 + font: AWT headless cho JLaTeXMath
# (task sau), font có tiếng Việt: DejaVu và Liberation (tương đương metric Times New Roman).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl ca-certificates tzdata fontconfig libfreetype6 fonts-dejavu-core fonts-liberation2 \
    && curl -fsSL -o /tmp/pandoc.deb \
        "https://github.com/jgm/pandoc/releases/download/${PANDOC_VERSION}/pandoc-${PANDOC_VERSION}-1-amd64.deb" \
    && echo "${PANDOC_SHA256}  /tmp/pandoc.deb" | sha256sum -c - \
    && apt-get install -y --no-install-recommends /tmp/pandoc.deb \
    && rm -f /tmp/pandoc.deb \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*

# uid cố định 10001; HOME riêng để fontconfig/pandoc có chỗ ghi cache nếu cần
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --create-home --home-dir /home/app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app

# Heap = 55% bộ nhớ container; phần còn lại cho metaspace, luồng, pandoc (tiến trình con cùng cgroup) và AWT.
# Giá trị chưa đo: chỉnh sau khi đo `docker stats` khi xuất đề (docs/deploy/README.md).
ENV TZ=Asia/Bangkok \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Duser.timezone=Asia/Bangkok -Dfile.encoding=UTF-8"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
