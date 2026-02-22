FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app

COPY --from=build /workspace/target/kitchen-simulator.jar /app/kitchen-simulator.jar

# Configure via env vars:
#   ORDERS_URL, SOLVE_URL
# Optional:
#   GET_ONLY=true
ENTRYPOINT ["java","-jar","/app/kitchen-simulator.jar"]

