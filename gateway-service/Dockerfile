# syntax=docker/dockerfile:1

############################
# Build stage
############################
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy Maven descriptors first to improve Docker layer caching.
COPY pom.xml ./pom.xml
COPY account-service/pom.xml ./account-service/pom.xml
COPY gateway-service/pom.xml ./gateway-service/pom.xml

RUN mvn \
    -pl gateway-service \
    -am \
    dependency:go-offline \
    -DskipTests

# Copy only the Gateway source.
COPY gateway-service/src ./gateway-service/src

RUN mvn \
    -pl gateway-service \
    -am \
    clean package \
    -DskipTests

RUN find gateway-service/target \
    -maxdepth 1 \
    -type f \
    -name "gateway-service-*.jar" \
    ! -name "*.jar.original" \
    -exec cp {} /workspace/gateway-service.jar \;

############################
# Runtime stage
############################
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd \
        --system \
        --gid spring \
        --create-home \
        spring

WORKDIR /app

COPY --from=builder \
    /workspace/gateway-service.jar \
    /app/application.jar

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]