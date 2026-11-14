# Etapa 1: compila o projeto com Maven
# (os testes rodam no GitHub Actions a cada envio; aqui pulamos para o deploy ser rápido)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etapa 2: imagem final, só com o Java e o .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/aloseguro-api-*.jar app.jar

# O plano gratuito do Render tem 512 MB: limita o Java a 75% disso
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
