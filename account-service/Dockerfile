# syntax=docker/dockerfile:1

############################
# Build stage
############################
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy Maven descriptors first to improve dependency caching.
COPY pom.xml ./pom.xml
COPY account-service/pom.xml ./account-service/pom.xml
COPY gateway-service/pom.xml ./gateway-service/pom.xml

RUN mvn \
    -pl account-service \
    -am \
    dependency:go-offline \
    -DskipTests

# Copy only the Account Service source.
COPY account-service/src ./account-service/src

# Build the module and its required parent reactor projects.
RUN mvn \
    -pl account-service \
    -am \
    clean package \
    -DskipTests

# Copy the executable Spring Boot JAR to a stable location.
RUN find account-service/target \
    -maxdepth 1 \
    -type f \
    -name "account-service-*.jar" \
    ! -name "*.jar.original" \
    -exec cp {} /workspace/account-service.jar \;

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
    /workspace/account-service.jar \
    /app/application.jar

USER spring:spring

EXPOSE 8081

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]