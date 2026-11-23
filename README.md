Enterprise Knowledge & Agent Platform

An enterprise knowledge management and AI assistant platform built with React, Spring Boot, FastAPI, PostgreSQL, and pgvector.

1. Project Overview

This project aims to build an enterprise knowledge platform that supports document management, knowledge retrieval, and AI-powered question answering.

The system separates business logic from AI capabilities:

- Frontend: React + TypeScript
- Business Backend: Java + Spring Boot
- AI Backend: Python + FastAPI
- Database: PostgreSQL + pgvector
- Infrastructure: Docker Compose

2. Project Structure

enterprise-knowledge-agent/
├── docs/
├── frontend/
├── backend/
│   ├── spring-boot/
│   └── ai-service/
├── infrastructure/
│   └── docker-compose.yml
├── scripts/
├── .gitignore
└── README.md

3. Development Environment

- JDK 25
- Maven
- Node.js and npm
- Python
- Docker Desktop
- PostgreSQL 16 with pgvector

4. Local Development

Start PostgreSQL

Configure your local database password in "infrastructure/.env", using "infrastructure/.env.example" as a reference.

cd infrastructure
docker compose up -d

Start Spring Boot

Open "backend/spring-boot" in IntelliJ IDEA and run the Spring Boot application.

Alternatively, run from the repository root in Git Bash:

```bash
cd backend/spring-boot
./mvnw spring-boot:run
```

Run the complete Spring Boot test suite from the same directory:

```bash
./mvnw test
```

Health check:

"http://localhost:8080/api/v1/health"

Start FastAPI

cd backend/ai-service
source .venv/Scripts/activate
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000

Health check:

"http://localhost:8000/health"

API documentation:

"http://localhost:8000/docs"

Start Frontend

cd frontend
npm install
npm run dev

Frontend:

"http://localhost:5173"

5. Project Status

- [x] Product requirements and architecture design
- [x] Database design
- [x] API design
- [x] Spring Boot initialization and health check
- [x] FastAPI initialization and health check
- [x] React + TypeScript initialization
- [x] PostgreSQL + pgvector setup
- [x] Day 8: knowledge base creation vertical slice with an in-memory repository
- [x] Day 9: unified JSON responses, request IDs, and 409/422/500 exception mapping

Day 9 was accepted on 2026-11-23: all 14 Spring Boot tests passed, and the learner confirmed the expected 201/409/422 responses, Location header, and request ID consistency in Postman. The exception-handler tests use a test controller; the real creation path was checked manually.

Next: Day 10, Bean Validation and request boundaries. The creation endpoint currently uses a demo user and in-memory storage. Authentication, authorization, list pagination, Flyway, database persistence, and remaining API contract details are tracked in the learning log. RAG and Agent workflows follow the existing roadmap.

6. Documentation

See the "docs/" directory for product requirements, architecture, database design, and API specifications.

- [Current progress and Day 9 acceptance record](docs/daily-log.md)
- [Day 9 learning notes and Git Bash commands](Study_note/Day9.txt)

The original handoff manual is a Day 8 snapshot. Read the current progress log and actual code when continuing. Terminal examples use Git Bash on Windows.

7. IntelliJ IDEA module setup

This repository contains three independent applications. Only "backend/spring-boot" is a Java/Maven module; "frontend" is the React application, and "backend/ai-service" is the Python application. The repository root is a workspace, not a second Java module.

When opening the repository root in IntelliJ IDEA, add "backend/spring-boot/pom.xml" as a Maven project and reload Maven projects. In Project Structure > Modules, the Java module should be "spring-boot". Do not create a plain Java module named "enterprise-knowledge-agent" or mark the repository root as a Java source root. Let Maven configure src/main/java, src/test/java, resources, and dependencies.

The simplest alternative is to open "backend/spring-boot/pom.xml" directly as a project. Use the Project Files view when working from the repository root to browse the other applications. IDEA metadata is local and ignored by Git; pom.xml remains the source of truth for Java dependencies.
