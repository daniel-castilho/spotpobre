# Spotpobre API

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)

A Spotpobre API é um serviço de backend para streaming de música, construído com Java 21, Spring Boot 3 e uma rigorosa implementação de **Arquitetura Limpa (Clean Architecture)**. O projeto foi projetado para ser escalável, testável e independente de frameworks e tecnologias externas em seu núcleo de negócio.

## Tabela de Conteúdos

- [Core Architecture](#core-architecture)
- [Tech Stack](#tech-stack)
- [Test Strategy](#test-strategy)
- [Pré-requisitos](#pré-requisitos)
- [Como Rodar Localmente](#como-rodar-localmente)
- [Endpoints da API e Documentação](#endpoints-da-api-e-documentação)
- [Estrutura do Projeto](#estrutura-do-projeto)

## Core Architecture

A aplicação segue os princípios da Arquitetura Limpa, dividindo o código em três camadas principais, com uma regra de dependência estrita que aponta sempre para o centro:

1.  **`domain` (Camada de Domínio):** O núcleo da aplicação. Contém as entidades de negócio (`User`, `Song`, `Playlist`), a lógica de negócio rica e as interfaces das portas de saída (`UserRepository`, `SongStoragePort`). Esta camada é 100% Java puro, sem dependências de frameworks.

2.  **`application` (Camada de Aplicação):** Orquestra o domínio para executar os casos de uso específicos da aplicação (`CreatePlaylistUseCase`, `UploadSongUseCase`). Esta camada depende do `domain`, mas permanece ignorante da camada de `infrastructure`.

3.  **`infrastructure` (Camada de Infraestrutura):** A camada mais externa. Contém todos os detalhes tecnológicos: controladores Spring Web, adaptadores de persistência (DynamoDB), adaptadores de armazenamento (S3), configuração de segurança (JWT), etc. Esta camada implementa as portas do domínio e depende da camada de aplicação.

## Tech Stack

| Categoria | Tecnologia |
| :--- | :--- |
| **Linguagem & Framework** | ![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring](https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=for-the-badge&logo=spring&logoColor=white) |
| **Build & Dependências** | ![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white) |
| **Segurança** | ![Spring Security](https://img.shields.io/badge/Spring_Security-6-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white) ![JWT](https://img.shields.io/badge/JWT-JSON_Web_Tokens-000000?style=for-the-badge&logo=json-web-tokens&logoColor=white) |
| **Banco de Dados** | ![Amazon DynamoDB](https://img.shields.io/badge/Amazon_DynamoDB-4053D6?style=for-the-badge&logo=amazon-dynamodb&logoColor=white) |
| **Armazenamento & Cache** | ![Amazon S3](https://img.shields.io/badge/Amazon_S3-569A31?style=for-the-badge&logo=amazon-s3&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white) |
| **Documentação & Mapeamento** | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) ![MapStruct](https://img.shields.io/badge/MapStruct-333333?style=for-the-badge&logo=mapstruct&logoColor=white) |
| **Testes** | ![JUnit 5](https://img.shields.io/badge/JUnit5-25A162?style=for-the-badge&logo=junit5&logoColor=white) ![Mockito](https://img.shields.io/badge/Mockito-D43A2A?style=for-the-badge&logo=mockito&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-262261?style=for-the-badge&logo=testcontainers&logoColor=white) ![RestAssured](https://img.shields.io/badge/REST_Assured-000000?style=for-the-badge&logo=rest-assured&logoColor=white) |
| **Ambiente de Dev** | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white) ![LocalStack](https://img.shields.io/badge/LocalStack-4A90E2?style=for-the-badge&logo=localstack&logoColor=white) |

## Test Strategy

O projeto segue uma estratégia de testes abrangente para garantir a qualidade e a robustez do código:

-   **Testes Unitários:** Focados nas camadas de `domain` e `application`. Eles usam **JUnit 5** e **Mockito** para testar a lógica de negócio e os casos de uso de forma isolada, sem depender de contexto Spring ou I/O.
-   **Testes de Integração "Slice":** Usam **Testcontainers** com **LocalStack** para iniciar um ambiente AWS real (mas local) e testar a integração da camada de persistência com o DynamoDB.
-   **Testes de Ponta a Ponta (E2E):** Usam **RestAssured** para fazer chamadas HTTP reais à aplicação completa, que roda em uma porta aleatória com o Testcontainers. Esses testes validam fluxos de usuário completos, desde o controlador até o banco de dados.

## Pré-requisitos

Antes de começar, certifique-se de ter os seguintes softwares instalados:
- JDK 21
- Maven 3.8+
- Docker e Docker Compose
- AWS CLI v2

## Como Rodar Localmente

Siga estes passos para configurar e rodar a aplicação em seu ambiente de desenvolvimento.

### 1. Iniciar os Serviços Externos

Com o Docker rodando, inicie o LocalStack (para simular DynamoDB e S3) e o Redis usando o Docker Compose. Na raiz do projeto, execute:

```sh
docker-compose up -d
```

### 2. Configurar o Ambiente LocalStack

Com os contêineres rodando, abra um **novo terminal** e execute os seguintes comandos para criar as tabelas do DynamoDB e o bucket S3 necessários.

```sh
# (Opcional) Crie um alias para facilitar os comandos
alias awslocal='aws --endpoint-url=http://localhost:4566'

# 1. Criar o bucket S3
awslocal s3 mb s3://spotpobre-songs

# 2. Criar as tabelas do DynamoDB com seus Índices Secundários Globais (GSIs)
# Tabela de Usuários (GSI no email)
awslocal dynamodb create-table \
    --table-name Users \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=email,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"email-index\",
                \"KeySchema\": [{\"AttributeName\":\"email\",\"KeyType\":\"HASH\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Tabela de Playlists (GSI no ownerId)
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

# Tabela de Músicas (GSI para busca por título)
awslocal dynamodb create-table \
    --table-name Songs \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=title,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"title-search-index\",
                \"KeySchema\": [{\"AttributeName\":\"searchPartition\",\"KeyType\":\"HASH\"}, {\"AttributeName\":\"title\",\"KeyType\":\"RANGE\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Tabela de Artistas (GSI para busca por nome)
awslocal dynamodb create-table \
    --table-name Artists \
    --attribute-definitions AttributeName=id,AttributeType=S AttributeName=searchPartition,AttributeType=S AttributeName=name,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --global-secondary-indexes \
        "[
            {
                \"IndexName\": \"name-search-index\",
                \"KeySchema\": [{\"AttributeName\":\"searchPartition\",\"KeyType\":\"HASH\"}, {\"AttributeName\":\"name\",\"KeyType\":\"RANGE\"}],
                \"Projection\": {\"ProjectionType\":\"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

# Tabela de Likes (Adjacency List com GSI reverso)
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

echo "Ambiente Localstack configurado com sucesso!"
```

### 3. Rodar a Aplicação

Finalmente, você pode iniciar a aplicação Spring Boot. A forma mais simples é através do Maven:

```sh
mvn spring-boot:run
```

A aplicação estará disponível em `http://localhost:8080`.

## Endpoints da API e Documentação

### Documentação Interativa (Swagger UI)

Com a aplicação rodando, acesse a documentação interativa da API no seu navegador:

- **URL:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Lá você poderá ver todos os endpoints, seus DTOs, e testá-los diretamente, incluindo a autenticação JWT.

### Resumo dos Endpoints

| Entidade | Método | Endpoint | Descrição |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/v1/auth/register` | Registra um novo usuário. |
| | `POST` | `/api/v1/auth/authenticate` | Autentica um usuário e retorna um JWT. |
| **Users** | `GET` | `/api/v1/users/me` | Retorna o perfil do usuário autenticado. |
| **Playlists** | `POST` | `/api/v1/playlists` | Cria uma nova playlist. |
| | `GET` | `/api/v1/me/playlists` | Lista as playlists do usuário autenticado (paginado). |
| | `GET` | `/api/v1/playlists/{id}` | Retorna os detalhes de uma playlist. |
| | `PATCH` | `/api/v1/playlists/{id}` | Renomeia uma playlist. |
| | `DELETE` | `/api/v1/playlists/{id}` | Apaga uma playlist. |
| | `POST` | `/api/v1/playlists/{id}/songs/{songId}` | Adiciona uma música a uma playlist. |
| | `DELETE` | `/api/v1/playlists/{id}/songs/{songId}` | Remove uma música de uma playlist. |
| **Songs** | `POST` | `/api/v1/songs` | Faz o upload de uma nova música (requer `ROLE_ARTIST`). |
| | `GET` | `/api/v1/songs/{id}` | Retorna os metadados e a URL de streaming de uma música. |
| **Artists** | `POST` | `/api/v1/artists` | Cria um novo artista (requer `ROLE_ADMIN`). |
| **Search** | `GET` | `/api/v1/search/songs?query={q}` | Busca músicas por título. |
| | `GET` | `/api/v1/search/artists?query={q}` | Busca artistas por nome. |
| **Likes** | `POST` | `/api/v1/likes/toggle` | Curte ou descurte uma música, artista ou playlist. |

### Endpoints de Monitoramento (Actuator)

Os seguintes endpoints do Actuator estão expostos:

- **Saúde da Aplicação:** `GET /actuator/health`
- **Métricas:** `GET /actuator/metrics`
- **Informações:** `GET /actuator/info`

## Estrutura do Projeto

A estrutura de diretórios reflete a Arquitetura Limpa:

```
src/main/java/com/spotpobre/backend/
├── domain/
│   ├── artist/
│   │   ├── model/      # Entidades e Value Objects (Artist, ArtistId)
│   │   └── port/       # Interfaces de repositório (ArtistRepository)
│   ├── playlist/
│   └── user/
├── application/
│   ├── artist/
│   │   ├── port/in/    # Interfaces de Casos de Uso (CreateArtistUseCase)
│   │   └── service/    # Implementações dos Casos de Uso (CreateArtistService)
│   ├── playlist/
│   └── user/
└── infrastructure/
    ├── config/         # Configuração do Spring, Beans, Segurança, Propriedades
    ├── persistence/    # Adaptadores de persistência (DynamoDB)
    ├── security/       # Lógica de JWT, UserDetailsService
    ├── storage/        # Adaptadores de armazenamento (S3)
    └── web/            # Controladores, DTOs e Mappers da API
```
