# 1) 빌드 단계
FROM gradle:8-jdk21 AS builder

WORKDIR /app
COPY . .

# 테스트는 일단 제외하고 빌드 (필요하면 -x test 빼면 됨)
RUN ./gradlew clean bootJar -x test

# 2) 실행 단계 (슬림한 JRE 이미지)
FROM eclipse-temurin:21-jre

WORKDIR /app

# bootJar 결과물 복사 (build/libs 안에 jar 하나 나온다고 가정)
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너가 뜰 때 실행할 명령
ENTRYPOINT ["java", "-jar", "/app/app.jar"]