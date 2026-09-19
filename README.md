# Bankomunal

Sistema web de gestión de fondos comunitarios (bancos comunales) que permite a un grupo de socios administrar aportes, préstamos solidarios, metas de ahorro, encuestas de grupo, reuniones y comunicación, con un panel administrativo completo.

# Bankomunal — Backend

API REST del sistema de gestión de fondos comunitarios **Bankomunal**, desarrollada con Spring Boot.

> El frontend de este proyecto vive en un repositorio aparte: [bankomunal-frontend](https://github.com/hyashley0620/bankomunal-frontend).

## Tecnologías

- Java 21
- Spring Boot 3.3.0 (Web, Data JPA, Security, Validation, WebSocket, Mail)
- MySQL 8
- JWT (JJWT 0.12.5) para autenticación
- Apache POI (exportación a Excel)
- WebSocket / STOMP (notificaciones y chat en tiempo real)
- AES-256-GCM (cifrado de respaldos de base de datos)
- Maven

## Estructura del proyecto

```
backend/
├── src/main/java/com/bankomunal/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── security/
│   ├── config/
│   ├── exception/
│   └── util/
├── src/main/resources/
│   └── application.properties
└── pom.xml
```

## Funcionalidades expuestas por la API

- Autenticación y autorización con JWT, roles y permisos granulares
- Gestión de socios, grupos y roles (presidente, tesorero, secretario)
- Fondo común, aportes, beneficios y capitalización
- Préstamos solidarios (simulación, solicitud, pagos, gestión)
- Metas de ahorro individuales
- Transferencias entre cuentas y pago de servicios, con comprobantes reales
- Encuestas y votaciones de grupo
- Reuniones con actas
- Educación financiera con cursos y certificados
- Reportes financieros exportables y plantillas de filtros guardadas
- Notificaciones y chat de soporte en tiempo real (WebSocket)
- Auditoría de acciones del sistema
- Respaldo y recuperación real de la base de datos (mysqldump, cifrado y verificación de integridad)
- Administración (usuarios, roles, límites de transacción, salud financiera)

## Requisitos previos

- JDK 21
- Maven 3.9+
- MySQL 8 (o MariaDB compatible)
- Los binarios `mysqldump` / `mysql` instalados localmente (necesarios para el módulo de Respaldo y Recuperación)

## Instalación y ejecución local

### 1. Clonar el repositorio

```bash
git clone https://github.com/hyashley0620/bankomunal-backend.git
cd bankomunal-backend
```

### 2. Crear la base de datos

El script `bankomunal.sql` crea todas las tablas necesarias:

```bash
mysql -u root -p -e "CREATE DATABASE bankomunal;"
mysql -u root -p bankomunal < bankomunal.sql
```

### 3. Configurar `application.properties`

Edita `src/main/resources/application.properties` y ajusta según tu entorno:

| Propiedad | Descripción |
|---|---|
| `spring.datasource.password` | Contraseña de tu usuario de MySQL |
| `backup.mysqldump-path` / `backup.mysql-path` | Ruta de tus binarios de MySQL (respaldo) |
| `backup.encryption-key` | Clave usada para cifrar los respaldos (AES-256-GCM) |
| `spring.mail.username` / `spring.mail.password` | Cuenta de Gmail con contraseña de aplicación (opcional, para recuperación de contraseña) |


### 4. Ejecutar

```bash
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

> CORS ya está habilitado para `http://localhost:5500` y `http://127.0.0.1:5500` (frontend con Live Server).

## Autor

** Wendy Hyashley Duarte Contreras **
Tecnología en Análisis y Desarrollo de Software (ADSO) — SENA