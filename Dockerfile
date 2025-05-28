# Use uma imagem base com Java e ffmpeg
FROM openjdk:21-jdk-slim

# Instala o ffmpeg e outras dependências necessárias
RUN apt-get update && apt-get install -y ffmpeg && apt-get clean

# Diretório da aplicação no container
WORKDIR /app

# Copia o jar compilado da aplicação para dentro do container
COPY target/my-audio-to-text-backend-0.0.1-SNAPSHOT.jar app.jar

# Expõe a porta que seu app usa (mude se necessário)
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java","-jar","app.jar"]
