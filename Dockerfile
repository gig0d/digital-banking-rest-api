FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system banking && useradd --system --gid banking banking
WORKDIR /app
COPY --from=build /workspace/target/digital-banking-rest-api-1.0.0-SNAPSHOT.jar app.jar
USER banking:banking
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
