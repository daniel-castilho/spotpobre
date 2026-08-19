# Spotpobre API

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

Spotpobre API is a music streaming backend service built with **Java 21**, **Spring Boot 3** and a strict
**Clean Architecture**. Its business core is 100% framework-free and 100% framework-free of code-generation
tools: the `domain` layer holds plain Java entities (no Lombok, no annotation processors), rich business
rules and outbound port interfaces, which keeps the application scalable, testable and independent of
external technologies.

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Commands](#commands)
- [Testing](#testing)
- [API & Documentation](#api--documentation)
- [Current State](#current-state)
- [Roadmap](#roadmap)

## Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language & Framework** | ![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=spring&logoColor=white) |
| **Build & Dependencies** | ![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white) (with Maven Wrapper) |
| **Security** | ![Spring Security](https://img.shields.io/badge/Spring_Security-6-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white) ![JWT](https://img.shields.io/badge/JWT-JSON_Web_Tokens-000000?style=for-the-badge&logo=json-web-tokens&logoColor=white) |
| **Database** | ![Amazon DynamoDB](https://img.shields.io/badge/Amazon_DynamoDB-4053D6?style=for-the-badge&logo=amazon-dynamodb&logoColor=white) |
| **Storage & Cache** | ![Amazon S3](https://img.shields.io/badge/Amazon_S3-569A31?style=for-the-badge&logo=amazon-s3&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white) |
| **Documentation & Mapping** | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) ![MapStruct](https://img.shields.io/badge/MapStruct-333333?style=for-the-badge&logo=mapstruct&logoColor=white) |
| **Testing** | ![JUnit 5](https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=junit5&logoColor=white) ![Mockito](https://img.shields.io/badge/Mockito-D43A2A?style=for-the-badge&logo=mockito&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-262261?style=for-the-badge&logo=testcontainers&logoColor=white) ![RestAssured](https://img.shields.io/badge/REST_Assured-000000?style=for-the-badge&logo=rest-assured&logoColor=white) |
| **Local Dev** | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) ![LocalStack](https://img.shields.io/badge/LocalStack-4A90E2?style=for-the-badge&logo=localstack&logoColor=white) |

- **Cloud:** `spring-cloud-aws` 3.4 (DynamoDB + S3 starters) and `aws-sdk` DynamoDB Enhanced Client.
- **Auth:** Spring Security 6 with `jjwt` 0.12 JWT bearer tokens (1h default expiry, `JwtProperties`).
- **API docs:** `springdoc-openapi` (Swagger UI) + **Actuator** (`health`, `info`, `metrics`).
- **Logging:** `logstash-logback-encoder` for structured (JSON) logs.

## Architecture

The application follows the principles of Clean Architecture, split into three layers with a strict
dependency rule that always points inward:

```
src/main/java/com/spotpobre/backend/
├── domain/           Entities, value objects & outbound port interfaces (zero framework imports)
│   ├── album/
│   ├── artist/
│   ├── like/         Like entity + Strategy-friendly design (EntityType enum)
│   ├── playlist/
│   ├── song/
│   └── user/
├── application/      Use-case orchestration (pure Java, depends only on domain ports)
│   ├── album/
│   ├── artist/
│   ├── like/         LikeStrategy + LikeStrategyFactory (song / artist / playlist)
│   ├── playlist/
│   ├── song/
│   └── user/
└── infrastructure/   Adapters: Spring Web controllers, DynamoDB persistence, S3 storage, JWT
    ├── config/       Spring beans, security, cache, AWS & properties configuration
    ├── persistence/  DynamoDB repository adapters (Enhanced Client), entities & mappers
    ├── security/     JWT filter, UserDetailsService, SecurityConfig
    ├── storage/      S3 adapter + CDN storage adapter (SongStoragePort)
    └── web/          REST controllers, DTOs and MapStruct mappers
```

**Boundary rules:**

- `domain/` and `application/` never import `infrastructure` code or cloud/framework adapters
  (no `infrastructure.*`, `software.amazon.*`, `io.awspring.*`, `org.mapstruct.*`,
  `org.springdoc.*`, `org.springframework.web.*`).
- `application/` implements use cases against the domain's outbound port interfaces; it stays
  ignorant of `infrastructure/`.
- `infrastructure/` implements the domain ports and depends on `application/` — nothing ever points
  outward.
- Persistence (DynamoDB), storage (S3) and streaming are swappable behind ports
  (`AlbumRepository`, `SongStoragePort`, ...).

## Requirements

- JDK 21
- Maven 3.8+ (or use the bundled `./mvnw` wrapper)
- Docker and Docker Compose
- AWS CLI v2 (optional, only to run the LocalStack setup commands)

## Getting Started

### 1. Start the external services

With Docker running, start LocalStack (to simulate DynamoDB and S3) and Redis via Docker Compose:

```sh
docker-compose up -d
```

### 2. Configure LocalStack

In a **new terminal**, run the following commands to create the DynamoDB tables and the S3 bucket
the application expects.

```sh
# (Optional) alias to keep the commands short
alias awslocal='aws --endpoint-url=http://localhost:4566'

# 1. Create the S3 bucket
awslocal s3 mb s3://spotpobre-songs

# 2. Create the DynamoDB tables with their Global Secondary Indexes (GSIs)
# Users table (GSI on profile.email)
awslocal dynamodb create-table \
    --table-name Users \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=profile.email,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"email-index\",
                \"KeySchema\": [{\"AttributeName\":\"profile.email\",\"KeyType\":\"HASH\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# UserEmails table (email uniqueness sentinel used during registration)
awslocal dynamodb create-table \
    --table-name UserEmails \
    --attribute-definitions AttributeName=email,AttributeType=S \
    --key-schema AttributeName=email,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

# Playlists table (GSI on ownerId)
awslocal dynamodb create-table \
    --table-name Playlists \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=ownerId,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"ownerId-index\",
                \"KeySchema\": [{\"AttributeName\":\"ownerId\",\"KeyType\":\"HASH\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Songs table (GSIs for title search and album lookup; searchPartition is a constant "SONG";
# searchTitle is the write-time lowercased title used as the title-search-index sort key)
awslocal dynamodb create-table \
    --table-name Songs \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchTitle,AttributeType=S AttributeName=albumId,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"title-search-index\",
                \"KeySchema\": [{\"AttributeName\":\"searchPartition\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"searchTitle\",\"KeyType\":\"RANGE\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            },
            {
                \"IndexName\": \"albumId-index\",
                \"KeySchema\": [{\"AttributeName\":\"albumId\",\"KeyType\":\"HASH\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Artists table (GSI for name search; searchPartition is a constant "ARTIST";
# searchName is the write-time lowercased name used as the name-search-index sort key)
awslocal dynamodb create-table \
    --table-name Artists \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=searchName,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"name-search-index\",
                \"KeySchema\": [{\"AttributeName\":\"searchPartition\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"searchName\",\"KeyType\":\"RANGE\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Albums table (GSI on artistId)
awslocal dynamodb create-table \
    --table-name Albums \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=artistId,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"artistId-index\",
                \"KeySchema\": [{\"AttributeName\":\"artistId\",\"KeyType\":\"HASH\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Likes table (Adjacency List with reverse GSI)
awslocal dynamodb create-table \
    --table-name Likes \
    --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=entityCompositeKey,AttributeType=S \
    --key-schema AttributeName=userId,KeyType=HASH AttributeName=entityCompositeKey,KeyType=RANGE \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"entityId-index\",
                \"KeySchema\": [{\"AttributeName\":\"entityCompositeKey\",\"KeyType\":\"HASH\"}, {\"AttributeName\":\"userId\",\"KeyType\":\"RANGE\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "LocalStack environment configured successfully!"
```

> Note: the local `application.yaml` already points `aws.dynamodb.endpoint`, `aws.s3.endpoint` and
> `spring.data.redis` at localhost (`4566` / `6379`), and the `jwt.secret` shown there is a dev-only
> example. Override it via environment variables in production.

### 3. Run the application

```sh
./mvnw spring-boot:run
```

The application will be available at `http://localhost:8080`.

## Commands

| Purpose | Command |
| :--- | :--- |
| Run the dev server | `./mvnw spring-boot:run` |
| Run pure unit tests (no Docker needed) | `./mvnw test` |
| Run slice + E2E tests (needs Docker + LocalStack) | `./mvnw test -Dtest='*IT'` |
| Production build | `./mvnw clean package` |
| Start external services (LocalStack + Redis) | `docker-compose up -d` |
| Stop external services | `docker-compose down` |

## Testing

The project follows a comprehensive test strategy to guarantee quality and robustness:

- **Unit tests** — focus on the `domain` and `application` layers. They use **JUnit 5** and
  **Mockito** to exercise business rules and use cases in isolation, with no Spring context or I/O.
  Run them with `./mvnw test` (no Docker required).
- **Slice integration tests** — use **Testcontainers** with **LocalStack** to boot a real AWS
  environment locally and validate the persistence and storage layers against DynamoDB/S3
  (`DynamoDbPlaylistRepositoryAdapterIT`, `S3SongStorageAdapterIT`). They are named `*IT` so they
  do **not** run in the default `./mvnw test`; execute them explicitly with
  `./mvnw test -Dtest='*IT'` (requires Docker).
- **End-to-end (E2E) tests** — use **RestAssured** against the full application on a random port
  (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) with Testcontainers. They validate complete
  user flows from controller to database (`AuthenticationFlowIT`, `ArtistSongFlowIT`,
  `PlaylistFlowIT`). Same `*IT` naming convention — run explicitly with `./mvnw test -Dtest='*IT'`.

## API & Documentation

### Interactive documentation (Swagger UI)

With the application running, open the interactive API docs:

- **URL:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Browse every endpoint and DTO and try them out directly, including JWT authentication.

### Endpoint summary

| Entity | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/v1/auth/register` | Register a new user. |
| | `POST` | `/api/v1/auth/authenticate` | Authenticate a user and return a JWT. |
| **Users** | `GET` | `/api/v1/users/me` | Return the authenticated user's profile. |
| **Artists** | `POST` | `/api/v1/artists` | Create a new artist (requires `ROLE_ADMIN`). |
| | `GET` | `/api/v1/artists/search?query={q}&limit={n}&cursor={token}` | Search artists by name (case-insensitive, cursor-paginated; `limit` max 50). |
| **Albums** | `POST` | `/api/v1/albums` | Create a new album for an artist. |
| | `POST` | `/api/v1/albums/{albumId}/songs` | Initiate a song upload (`ROLE_ARTIST`): validates type/size and returns short-lived presigned PUT URL(s). Files over 100 MB get S3 multipart part URLs. The API never accepts file bytes. |
| | `POST` | `/api/v1/albums/{albumId}/songs/{songId}/confirm` | Confirm a completed direct-to-S3 upload (`ROLE_ARTIST`); completes multipart when needed. |
| **Songs** | `GET` | `/api/v1/songs/{songId}` | Return a song's metadata and streaming URL. |
| | `GET` | `/api/v1/songs/search?query={q}&limit={n}&cursor={token}` | Search songs by title (case-insensitive, cursor-paginated; `limit` max 50). |
| **Playlists** | `POST` | `/api/v1/playlists` | Create a new playlist. |
| | `GET` | `/api/v1/me/playlists` | List the authenticated user's playlists (paginated). |
| | `GET` | `/api/v1/playlists/{playlistId}` | Return a playlist's details. |
| | `PATCH` | `/api/v1/playlists/{playlistId}` | Rename a playlist. |
| | `DELETE` | `/api/v1/playlists/{playlistId}` | Delete a playlist. |
| | `POST` | `/api/v1/playlists/{playlistId}/songs/{songId}` | Add a song to a playlist. |
| | `DELETE` | `/api/v1/playlists/{playlistId}/songs/{songId}` | Remove a song from a playlist. |
| **Likes** | `POST` | `/api/v1/likes/toggle` | Like or unlike a song, artist or playlist. |

### Monitoring endpoints (Actuator)

- **Health:** `GET /actuator/health`
- **Metrics:** `GET /actuator/metrics`
- **Info:** `GET /actuator/info`

## Current State

The project is an early-stage backend (`0.0.1-SNAPSHOT`) with the following already implemented on
`main`:

- **Auth & users** — JWT registration/authentication, `ROLE_ADMIN` / `ROLE_ARTIST` / `ROLE_USER`,
  profile endpoint. Passwords are hashed with **Argon2id** behind the domain `PasswordHasher` port
  (adapter `SpringSecurityPasswordHasher`), so the hashing library is swappable without touching
  the application layer.
- **Catalog** — artists, albums and songs aggregates with rich domain models (`Album` aggregate,
  `Song`, `SongMetadata`).
- **Song upload** — direct-to-S3 via presigned URLs. `POST /albums/{id}/songs` authorizes the
  upload (content type, max 500 MB) and returns 10-minute presigned PUT URL(s); the client PUTs
  the audio to S3; `POST .../songs/{songId}/confirm` verifies the object (or completes multipart).
  No `byte[]` / `MultipartFile` on the API.
- **Playlists** — full CRUD with owner authorization (IDOR fixed: authenticated users can only mutate playlists they own; 403 returned for unauthorized access), paginated listing and song membership.
- **Likes** — adjacency-list persistence with a reverse GSI; implemented as a Strategy family
  (`SongLikeStrategy`, `ArtistLikeStrategy`, `PlaylistLikeStrategy`).
- **Search** — songs by title and artists by name via DynamoDB GSIs, case-insensitive (write-time
  normalized `searchTitle`/`searchName` sort keys) and cursor-paginated (`ExclusiveStartKey` +
  `nextPageToken`/`hasNext`; `limit` capped at 50).
- **Storage & streaming** — S3-backed `SongStoragePort` with a CDN storage adapter and Redis-backed
  caching.
- **Hardening** — global exception handling with structured validation errors, correct DynamoDB
  pagination (cursor-based, no silent data leaks), and now enforced architectural boundaries.
  `PlaylistController` and `LikeController` depend only on application inbound ports (`*UseCase`),
  with direct `UserRepository` calls replaced by the `GetCurrentUserUseCase` application service.
- **CI/CD** — GitHub Actions workflow (`.github/workflows/ci.yml`) runs pure unit tests, then the
  `*IT` slice + E2E suite (Testcontainers), then `./mvnw clean package` on every push/PR.
  `DynamoDbConfig` / `S3Config` build AWS clients with `StaticCredentialsProvider` from
  `AwsProperties` (env-overridable), so tests work on clean runners with LocalStack.
- **Data consistency & modelling** — every relationship has a single source of truth:
  playlists live only in the `Playlists` table (`ownerId-index`), album songs only in the `Songs`
  table (`albumId-index`); the `Users` / `Albums` aggregates no longer embed collections.
  `MAX_PLAYLISTS_PER_USER = 10` is enforced against persistent state, user registration is
  atomic against duplicate emails (`TransactWriteItems` + `UserEmails` uniqueness table), playlist
  mutations use optimistic locking (`version` + conditional writes), and a failed metadata save
  after a multipart S3 upload aborts the orphan upload. Decisions are recorded in
  `docs/data-model-decisions.md`.

## Roadmap

Deliberately not implemented yet (candidate backlog):

- Pagination on more list endpoints (artists, albums)
- Rate limiting and per-user quotas
- Email verification and password recovery
- Production deployment runtime shape (how the artifact is shipped/run — the env-var contract is
  defined in `application-prod.yaml`, but the container image / platform is not yet documented)
- Additional quality gates (JaCoCo coverage, SpotBugs, dependency/security scanning)

## Documentation

| Document | Purpose |
| :--- | :--- |
| `README.md` | This file — overview, architecture, setup and testing |
| `CHANGELOG.md` | Release history (Keep a Changelog); update policy in `AGENTS.md` rule 8 |
| `AGENTS.md` | Rules for AI agents and human contributors |
| `docs/coding-standards.md` | Day-to-day coding standards (Java / Spring Boot / Maven) |
| `docs/testing-playbook.md` | Testing pyramid, patterns, regression checklist & smoke |
| `docs/lessons.md` | Durable lessons learned from debugging and design decisions |
| `docs/twelve-factor.md` | Twelve-Factor App reference & compliance matrix |
| `docker-compose.yaml` | LocalStack (DynamoDB, S3) + Redis for local development |
| `pom.xml` | Dependency, build and annotation-processor configuration |