# syntax=docker/dockerfile:1

# =====================================================================
# Stage 1 - build: kompilasi dan packaging bootJar (build/libs/app.jar)
# =====================================================================
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Hanya file yang dibutuhkan build yang di-copy (.env, cred_db.md, .git, dsb. tidak masuk image).
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# Distribusi Gradle dan dependency disimpan di BuildKit cache mount agar build berikutnya lebih cepat.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# =====================================================================
# Stage 2 - runtime: hanya JRE 25 + app.jar
# =====================================================================
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=build /workspace/build/libs/app.jar ./app.jar

USER spring
EXPOSE 8080

# Image JRE tidak menyertakan curl/wget; healthcheck memakai bash /dev/tcp.
# /actuator/health mengembalikan HTTP 200 saat UP dan 503 saat DOWN (termasuk jika database tidak terjangkau).
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8080} && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n 1 <&3 | grep -q "^HTTP/1\.[01] 200"'

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
