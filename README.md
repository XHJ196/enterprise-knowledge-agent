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

```bash
cd infrastructure
docker compose up -d postgres
docker compose port postgres 5432
```

The current project database is enterprise_knowledge, the local database account is MicroJason, and the published address is 127.0.0.1:5333. PostgreSQL still listens on port 5432 inside the container. Port 5332 belongs to a different local container and must not be used for this project. The project container is enterprise-postgres and retains its named data volume.

Start Spring Boot

Spring now requires a running PostgreSQL database and DB_PASSWORD in its process environment. Docker Compose reads infrastructure/.env, but Spring does not automatically import that file. The following Git Bash commands, starting from the repository root, load the resolved Compose password without displaying it, then start Spring:

```bash
cd backend/spring-boot
export DB_URL="jdbc:postgresql://127.0.0.1:5333/enterprise_knowledge"
export DB_USERNAME="MicroJason"
unset DB_PASSWORD

if DB_PASSWORD="$(docker compose \
    --env-file ../../infrastructure/.env \
    -f ../../infrastructure/docker-compose.yml \
    config --format json |
    python -c 'import json, sys; value = json.load(sys.stdin)["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"]; assert isinstance(value, str) and value, "Database password is empty"; sys.stdout.write(value)')"; then
    export DB_PASSWORD
    ./mvnw spring-boot:run
else
    printf 'Could not load the local database configuration.\n'
fi
```

The loader uses the existing local Python installation. Unsetting DB_PASSWORD first prevents an older terminal value from overriding infrastructure/.env during Compose interpolation. Do not print the password or commit it. Changing .env does not change a password already stored in an initialized PostgreSQL data volume.

To start from IntelliJ IDEA instead, configure DB_URL, DB_USERNAME, and DB_PASSWORD locally in the Application run configuration. An already running IDEA process does not automatically inherit environment variables exported in Git Bash.

Run the complete Spring Boot test suite from the same directory and terminal after loading the environment variables. If the server is running in that terminal, stop it first with Ctrl+C:

```bash
./mvnw test
```

Flyway is the only schema migration tool. It tracks V1__create_users.sql and V2__create_knowledge_bases.sql in core.flyway_schema_history. Hibernate uses ddl-auto=validate, and Spring SQL initialization is disabled. Successful application startup applies pending migrations; subsequent startups validate existing migrations without repeating them. Preserve applied files and add a new version for later schema changes.

Run the Day 12 database constraint acceptance script from infrastructure:

```bash
docker compose exec -T postgres psql \
  -U MicroJason -d enterprise_knowledge -v ON_ERROR_STOP=1 \
  < ../backend/spring-boot/day12-constraints.sql
```

It checks the generated name key, case-insensitive name uniqueness, and the creator foreign key. Expected output includes two PASS notices and zero remaining test rows after ROLLBACK.

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
- [x] Day 10: request Bean Validation, name normalization, and Unicode code point length boundaries
- [x] Day 11: knowledge base GET lists, pagination, stable ordering, and query/body boundary checks
- [x] Day 12: PostgreSQL connection, Flyway V1/V2 migrations, and database constraint acceptance

Day 9 was accepted on 2026-11-23: all 14 Spring Boot tests passed, and the learner confirmed the expected 201/409/422 responses, Location header, and request ID consistency in Postman. The exception-handler tests use a test controller; the real creation path was checked manually.

Day 10 was accepted on 2026-11-24: all 34 Spring Boot tests passed. The learner ran the four requests and response assertions in [day10-validation.http](backend/spring-boot/day10-validation.http) using IntelliJ IDEA HTTP Client and confirmed 422/422/201/409 for overlong names, overlong descriptions, valid limits, and duplicate names. Name normalization, code point limits, Location, and request ID consistency were verified. Controller tests use a mock service; the HTTP file exercises the running application.

Day 11 was accepted on 2026-11-24: all 71 Spring Boot tests passed, with no failures, errors, or skipped tests. The learner ran all 12 requests and response assertions in [day11-pagination.http](backend/spring-boot/day11-pagination.http) using IntelliJ IDEA HTTP Client and confirmed empty lists, default parameters, three created records, stable ordering, multiple pages, pages beyond the last page, invalid pagination, unknown query parameters, and rejection of non-empty GET bodies. Request IDs were checked against response headers. Controller tests use a mock service; the HTTP file exercises the running application and in-memory repository.

The list endpoint returns items/page/pageSize/total/hasNext, defaults to page=1 and pageSize=20, accepts pageSize from 1 to 100, and orders by createdAt DESC then id ASC. It excludes DELETED knowledge bases and retains ACTIVE, DELETING, and DELETE_FAILED states in the explicitly labeled admin demo scope. Authentication and employee permission filtering are not implemented.

Day 12 was accepted on 2026-11-24: the learner completed the full test run, and the mentor checked the seven current reports totaling 71 tests with zero failures, errors, or skipped tests. PostgreSQL 16.16 and pgvector 0.8.7 were verified. V1 and V2 created core.users and core.knowledge_bases; two successful SQL migration records retain their checksums and installation times after repeated context startup. The mentor ran [day12-constraints.sql](backend/spring-boot/day12-constraints.sql), and the learner also confirmed its two PASS notices. The final database check found zero rows in both business tables. This verifies selected database constraints, not API persistence or the full database design.

Next: Day 13, the PostgreSQL repository adapter, Domain/Entity mapping, transactions, and database uniqueness conflict translation. The application currently uses a demo user and in-memory storage for knowledge base POST/GET, loses API-created records on restart, and performs filtering, sorting, and pagination in memory. JPA entities and the database adapter have not yet been implemented. Remaining API contract work is tracked in the learning log. RAG and Agent workflows follow the existing roadmap.

6. Documentation

See the "docs/" directory for product requirements, architecture, database design, and API specifications.

- [Current progress and acceptance records](docs/daily-log.md)
- [Day 9 learning notes and Git Bash commands](Study_note/Day9.txt)
- [Day 10 learning notes](Study_note/Day10.txt)
- [Day 11 learning notes](Study_note/Day11.txt)
- [Day 12 learning notes and database troubleshooting](Study_note/Day12.txt)

The original handoff manual is a Day 8 snapshot. Read the current progress log and actual code when continuing. Terminal examples use Git Bash on Windows; manual API checks use IntelliJ IDEA HTTP Client files. Restart the in-memory Spring Boot application before each complete HTTP acceptance run. For Day 10, execute requests 1–4 in order; for Day 11, execute requests 1–12 in order. Do not restart between requests or create unrelated records during a run.

7. IntelliJ IDEA module setup

This repository contains three independent applications. Only "backend/spring-boot" is a Java/Maven module; "frontend" is the React application, and "backend/ai-service" is the Python application. The repository root is a workspace, not a second Java module.

When opening the repository root in IntelliJ IDEA, add "backend/spring-boot/pom.xml" as a Maven project and reload Maven projects. In Project Structure > Modules, the Java module should be "spring-boot". Do not create a plain Java module named "enterprise-knowledge-agent" or mark the repository root as a Java source root. Let Maven configure src/main/java, src/test/java, resources, and dependencies.

The simplest alternative is to open "backend/spring-boot/pom.xml" directly as a project. Use the Project Files view when working from the repository root to browse the other applications. IDEA metadata is local and ignored by Git; pom.xml remains the source of truth for Java dependencies.
