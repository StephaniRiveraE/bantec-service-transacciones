# -------------------------------------------------------------------
# ETAPA 1: Construcción (Build)
# Usamos una imagen con Maven y JDK 21 para compilar
# -------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiamos primero el pom.xml y descargamos dependencias
# Esto aprovecha la caché de Docker para que no descargue internet cada vez
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiamos el código fuente
COPY src ./src

# Compilamos el proyecto (saltando tests para agilizar builds de dev)
RUN mvn clean package -DskipTests

# -------------------------------------------------------------------
# ETAPA 2: Ejecución (Runtime)
# Usamos una imagen ligera solo con JRE 21 (sin Maven)
# -------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Instalar certificados CA actualizados para evitar errores de SSL con Let's Encrypt
RUN apk add --no-cache ca-certificates

WORKDIR /app

# Copiamos solo el JAR generado en la etapa anterior
# El *.jar busca cualquier nombre, asegurando que funcione aunque cambies la versión
COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto
EXPOSE 8080

# Comando de arranque
ENTRYPOINT ["java", "-jar", "app.jar"]