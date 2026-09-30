# Image generique pour tous les modules Spring Boot du projet.
# Prerequis : jars construits sur l'hote (mvn package -DskipTests).
# Usage : docker build --build-arg MODULE=class-service -t fitconnect/class-service .
FROM eclipse-temurin:17-jre

ARG MODULE
ENV TZ=Europe/Paris
WORKDIR /app

COPY ${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
