# =============================================================================
# Image cho ai-service (cổng 8086) - build đa giai đoạn.
# Build context là thư mục backend/ để dùng chung module common. Ví dụ chạy từ thư mục gốc repo:
#   docker build -f backend/ai-service/Dockerfile -t wms-ai:dev backend/
# Khóa AI (ANTHROPIC_API_KEY) KHÔNG được đưa vào image: chỉ truyền qua biến môi trường lúc chạy container.
# =============================================================================

# ---- Giai đoạn 1: build file jar bằng Gradle + JDK 17 ----
FROM gradle:8.10-jdk17 AS build
# Chép mã nguồn (đã lọc bằng .dockerignore) với quyền của user gradle để Gradle ghi được thư mục build
COPY --chown=gradle:gradle . /home/gradle/project
WORKDIR /home/gradle/project
RUN gradle :ai-service:bootJar -x test --no-daemon

# ---- Giai đoạn 2: runtime nhỏ gọn chỉ có JRE 17 ----
FROM eclipse-temurin:17-jre-alpine
# Chạy bằng user không phải root
RUN addgroup -S wms && adduser -S -G wms -H -s /sbin/nologin wms
WORKDIR /app
COPY --from=build --chown=wms:wms /home/gradle/project/ai-service/build/libs/ai-service.jar /app/app.jar
USER wms
EXPOSE 8086
# Tham số JVM có thể ghi đè khi chạy: docker run -e JAVA_OPTS="-Xmx512m" ...
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
# Dùng liveness (không phụ thuộc Redis) để container không bị đánh dấu unhealthy chỉ vì hạ tầng chưa sẵn sàng
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8086/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
