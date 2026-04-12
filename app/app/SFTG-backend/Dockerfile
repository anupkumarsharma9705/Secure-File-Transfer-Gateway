# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
# Install Maven, bash, git
RUN apk add --no-cache maven bash git
# Copy pom and source code
COPY pom.xml .
COPY src ./src
# Build JAR
RUN mvn clean package -DskipTests
# Runtime stage
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
RUN mkdir -p /app/uploads
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8000
CMD ["java", "-jar", "app.jar"]