FROM eclipse-temurin:17-jdk-jammy

# Solo el JDK: Maven lo descarga el wrapper (./mvnw) y sus dependencias quedan
# en el volumen maven-repo de docker-compose.yml.
WORKDIR /app

CMD ["sleep", "infinity"]
