# ── Etapa 1: compilar el proyecto con Maven ──────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos primero solo el pom.xml para aprovechar el cache de dependencias
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# ── Etapa 2: imagen final, más liviana, solo con el JRE ──────────────────
FROM eclipse-temurin:21-jre-jammy

# Cliente de MySQL: provee los binarios mysqldump/mysql que usa el módulo
# de Respaldo y Recuperación (BackupService). Sin esto, ese módulo falla
# en el contenedor aunque funcione perfecto en local con XAMPP.
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Carpeta donde el backend guarda los .sql/.sql.enc de respaldo
RUN mkdir -p /app/backups

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
