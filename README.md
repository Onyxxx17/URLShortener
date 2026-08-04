# Onyx URL Shortener

A high-throughput, fully-featured URL shortening application with a Spring Boot REST API and a modern React frontend.

## Features

- **Shortcode Generation:** Collision-free shortcodes generated using a PostgreSQL sequence-backed Sqids encoding algorithm.
- **Frontend Dashboard:** A sleek, responsive single-page application built with React, Vite, and vanilla CSS for managing your links.
- **Advanced Authentication:** Secure JWT-based auth via `HttpOnly` cookies. Features an asynchronous SMTP email verification flow, resend verification, and a secure password reset flow.
- **Analytics Engine:** Real-time tracking of HTTP headers (`Referer`, `User-Agent`) for every redirect click event, visualized in a CSS-only bar chart on the frontend.
- **QR Codes:** Instant, dynamic QR code generation for short URLs.
- **High Performance:** Sub-millisecond read latency via Redis cache-aside architectures for URL redirects and password reset tokens.
- **Resilience:** Token-bucket rate limiting using Bucket4j via Spring Interceptors to mitigate API abuse (rate limits applied per-user, per-IP, and per-endpoint).

## Tech Stack

### Backend
- **Framework:** Java 21, Spring Boot 3
- **Database:** PostgreSQL (Spring Data JPA / Hibernate)
- **Caching:** Redis (Spring Data Redis)
- **Security:** Spring Security, JWT
- **Utilities:** Sqids (ID encoding), ZXing (QR Codes), Bucket4j (Rate Limiting)

### Frontend
- **Framework:** React 18, Vite
- **Routing:** React Router DOM
- **HTTP Client:** Axios
- **Styling:** Vanilla CSS (CSS Variables, Flexbox/Grid, zero external UI libraries)

## Prerequisites

- Java 21
- Node.js 18+ and Bun (or npm/yarn)
- PostgreSQL running on `localhost:5432`
- Redis running on `localhost:6379` (e.g., `docker run -d --name redis -p 6379:6379 redis:alpine`)
- Environment variables or `application.properties` configured with your SMTP credentials

## Running the Application

### 1. Start the Backend
Navigate to the root directory and run the Spring Boot app using the Maven wrapper:
```bash
./mvnw spring-boot:run
```
The API will be available at `http://localhost:8080`.
Access the Swagger UI documentation at `http://localhost:8080/swagger-ui.html`.

### 2. Start the Frontend
In a separate terminal, navigate to the `frontend` directory, install dependencies, and start the Vite dev server:
```bash
cd frontend
bun install
bun run dev
```
The web UI will be accessible at `http://localhost:5173`.

## API Endpoints Overview

- `POST /register` - Register a new user
- `POST /login` - Authenticate and receive an HttpOnly JWT cookie
- `POST /forgot-password` / `POST /reset-password` - Secure password reset flow
- `GET /verify-email` / `POST /resend-verification` - Email verification flows
- `POST /create` - Create a new short URL (Requires JWT)
- `GET /urls/my-urls` - List all URLs for the logged-in user
- `DELETE /urls/{shortCode}` - Delete a short URL
- `GET /urls/{shortCode}/stats` - View analytics and click events for a specific URL
- `GET /{shortCode}/qr` - Generate a PNG QR code for a short URL
- `GET /{shortCode}` - Public redirect to the original URL
