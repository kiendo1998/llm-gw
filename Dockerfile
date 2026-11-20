FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/llm-gw-*.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
