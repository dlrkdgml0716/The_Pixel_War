# 1. Java 17 이미지를 기반으로 함
FROM amazoncorretto:17

# 2. 컨테이너 내부 작업 폴더 설정
WORKDIR /app

# 3. 빌드된 Jar 파일을 컨테이너로 복사
# (주의: 로컬에서 ./gradlew build를 먼저 해야 이 파일이 생깁니다)
COPY pixel-service/build/libs/*-SNAPSHOT.jar app.jar

# 4. 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]