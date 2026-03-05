FROM gradle:8.14.3-jdk17 AS build
WORKDIR /workspace

COPY . .
RUN ./gradlew :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/server/build/install/server /app

EXPOSE 8080

ENTRYPOINT ["./bin/server"]
