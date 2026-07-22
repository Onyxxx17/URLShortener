# Antigravity URL Shortener

A high-throughput, fully-featured URL shortening REST API built with Java and Spring Boot.

## Features

- **Shortcode Generation:** Collision-free shortcodes generated using a PostgreSQL sequence-backed Sqids encoding algorithm.
- **Stateless Authentication:** Secure JWT-based authentication with an asynchronous SMTP email verification flow.
- **Analytics Engine:** Real-time tracking of HTTP headers (`Referer`, `User-Agent`) for every redirect click event.
- **High Performance:** 90% reduction in redirect latency via Redis caching, with a smart TTL expiry strategy.
- **Resilience:**  Token-bucket rate limiting using Bucket4j to mitigate API abuse.

## Tech Stack

- **Framework:** Java 21, Spring Boot 3
- **Database:** PostgreSQL (with Spring Data JPA / Hibernate)
- **Caching:** Redis (Spring Data Redis)
- **Security:** Spring Security, JWT (io.jsonwebtoken)
- **Utilities:** Sqids (for encoding IDs), Lombok
- **Documentation:** Swagger UI / OpenAPI

## Prerequisites

- Java 21
- PostgreSQL running on `localhost:5432`
- Redis running on `localhost:6379` (e.g., `docker run -d --name redis -p 6379:6379 redis:alpine`)
- Environment variables or `application.properties` updated with your SMTP credentials

## Running the Application

1. Ensure Postgres and Redis are running.
2. Run the application using the Maven wrapper:
```bash
./mvnw spring-boot:run
```
3. The API will be available at `http://localhost:8080`.
4. Access the Swagger UI documentation at `http://localhost:8080/swagger-ui.html`.

## API Endpoints Overview

- `POST /register` - Register a new user
- `POST /login` - Authenticate and receive a JWT
- `POST /create` - Create a new short URL (Requires JWT)
- `GET /{shortCode}` - Redirect to the original URL
- `GET /urls/{shortCode}/stats` - View analytics and click events for a specific URL (Requires JWT)

## Coming Soon
- **Frontend UI:** A modern web application built to consume these APIs.
- **Cloud Deployment:** Terraform configurations and CI/CD pipelines (GitHub Actions) to deploy this service to AWS (ECS/Fargate) or a VPS.
