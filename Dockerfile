# Imagen para desplegar la aplicación (Render, Railway, Fly.io o cualquier servicio que acepte Docker).
# Etapa 1: compila el proyecto con Maven y ejecuta las pruebas.
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package

# Etapa 2: solo el JRE, el JAR y el corpus (PDF, abstracts, metadatos y embeddings ya generados).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/target/bibliometria.jar ./bibliometria.jar
COPY corpus ./corpus
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-Xmx384m", "-jar", "bibliometria.jar", "servidor", "corpus"]
