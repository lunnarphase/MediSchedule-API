FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system medischedule && useradd --system --gid medischedule app
COPY --from=build /app/target/medischedule-1.0.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
