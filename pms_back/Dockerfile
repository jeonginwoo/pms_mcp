# ── 빌드 스테이지: Gradle 래퍼로 bootJar 생성 ──
# 테스트는 Testcontainers(도커 안 도커) 문제로 이미지 빌드에서 제외 — scripts/verify.sh가 담당
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ── 실행 스테이지 ──
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
