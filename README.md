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

Health check:

"http://localhost:8080/api/v1/health"

Start FastAPI

cd backend/ai-service
.\.venv\Scripts\Activate.ps1
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

Further business features, RAG capabilities, and Agent workflows will be implemented in subsequent development phases.

6. Documentation

See the "docs/" directory for product requirements, architecture, database design, and API specifications.