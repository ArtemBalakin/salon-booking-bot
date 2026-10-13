FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B --no-transfer-progress verify

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/bot-service.jar app.jar
ENV JAVA_OPTS="-Xmx320m -Xss256k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
