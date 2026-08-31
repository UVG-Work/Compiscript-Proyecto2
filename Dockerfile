FROM eclipse-temurin:17-jdk-jammy

# graphviz para renderizar a PNG el .dot que emite --dot.
RUN apt-get update \
    && apt-get install -y --no-install-recommends graphviz \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

CMD ["sleep", "infinity"]
