FROM eclipse-temurin:21-jdk-alpine

# Definir o diretório de trabalho no container
WORKDIR /app

# Copiar o JAR gerado
COPY target/my-audio-to-text-backend-0.0.1-SNAPSHOT.jar app.jar

# Expor a porta
EXPOSE 8080

# Rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]