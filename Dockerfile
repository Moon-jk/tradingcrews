# 1. 빌드 단계
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
# Maven을 사용하여 빌드 (테스트 제외)
RUN mvn clean package -DskipTests

# 2. 실행 단계
# 에러가 났던 openjdk 대신 공식 권장 이미지인 eclipse-temurin 사용
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 빌드 단계에서 생성된 jar 파일을 복사
COPY --from=build /app/target/*.jar app.jar

# 포트 설정
EXPOSE 8090
# 서버 실행 (포트 지정 옵션 포함)
ENTRYPOINT ["java", "-Dserver.port=8090", "-jar", "app.jar"]