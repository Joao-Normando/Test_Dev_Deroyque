# Activation Service

API de ativação de produtos usando Java 21, Spring Boot 3, Maven, PostgreSQL, Flyway e Spring Security Resource Server.

## Executar

Requisitos: Docker + Docker Compose para o ambiente completo; Java 21 e Maven para testes.

```bash
docker compose up --build
mvn verify
```

API: `http://localhost:8080`; Swagger UI: `http://localhost:8080/swagger-ui/index.html`; especificação OpenAPI: `http://localhost:8080/v3/api-docs`; PostgreSQL: `localhost:5432`; WireMock: `localhost:8090`. O mock responde HTTP 200 a `POST /provider/v1/activate`.

No Swagger UI, clique em **Authorize** e informe um JWT emitido pelo seu provedor de identidade com `requests:write` para criar solicitações ou `requests:read` para consultar. A interface e a especificação OpenAPI são públicas; os endpoints da API continuam protegidos.

## Segurança

Todos os endpoints exigem JWT. O Resource Server valida assinatura HS256, issuer, audience e expiração. O claim `scope` é convertido para authorities `SCOPE_...`. `requests:write` permite POST e `requests:read` permite GET. Sem token: 401; token válido sem escopo: 403.

Não há servidor de identidade nem endpoint para emitir tokens. A chave default serve apenas para desenvolvimento; não a use em produção. Em produção usaria OAuth 2.0 Client Credentials com IdP corporativo, JWT assimétrico e JWKS; validaria assinatura, `iss`, `aud`, `exp` e `nbf`, além de escopos. Segredos externos devem ficar em secret manager com rotação. Para evitar BOLA, associaria cada registro ao consumidor autenticado e verificaria o proprietário em cada leitura.

## Endpoints

- `POST /api/v1/requests` — cria solicitação e retorna 201 com `Location`.
- `GET /api/v1/requests/{id}` — consulta por UUID.
- `GET /api/v1/requests?status=PENDING&customerId=CUST-1&page=0&size=20` — listagem paginada e filtrada.

Exemplo de POST:

```json
{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}
```

Campos obrigatórios, máximo de 64 caracteres e padrão `[A-Za-z0-9._-]+`. Datas são `Instant` e serializadas em ISO-8601 UTC.

## Idempotência e concorrência

`external_id` tem restrição `UNIQUE` no PostgreSQL. A consulta prévia melhora a resposta, mas a restrição do banco é a garantia para corridas concorrentes. Mesmo `externalId` e mesmo conteúdo retorna a solicitação existente sem chamar o provedor outra vez. Mesmo ID com conteúdo divergente resulta em 409 Conflict. A integração envia `Idempotency-Key: externalId`; isso só evita duplicação remota se o provedor implementar essa semântica.

## Estados e integração síncrona

Transições de domínio: `PENDING -> PROCESSING -> COMPLETED|FAILED`. A entidade usa `@Version` para lock otimista.

| Resposta externa | Estado | Retry nesta versão |
|---|---|---|
| 2xx | COMPLETED | Não |
| 400 | FAILED | Não, erro permanente |
| 429 / 5xx / 503 | FAILED | Não implementado; classificado como temporário |
| Timeout/conexão | FAILED | Não implementado; resultado remoto pode ser ambíguo |

Timeouts de conexão e leitura são configuráveis em `PROVIDER_CONNECT_TIMEOUT` e `PROVIDER_READ_TIMEOUT`. O registro é persistido antes da chamada remota e a chamada ocorre fora de uma transação de banco aberta. O POST é síncrono e pode demorar até os limites de timeout.

Limitação: se o processo cair após persistir `PROCESSING`, o registro pode ficar preso; esta versão não tem retry automático nem reconciliação. Em produção implementaria Outbox transacional, worker, backoff exponencial com jitter, limite de tentativas, respeito a `Retry-After` e reconciliação. Timeout é ambíguo: a chave de idempotência precisa ser suportada pelo provedor para evitar ativação duplicada.

## Erros

`ProblemDetail` (application/problem+json) para validação (400), não encontrado (404), conflito (409) e falha inesperada (500). Nunca se devolve stack trace. `X-Correlation-Id` é propagado/gerado e colocado no MDC dos logs.

## Banco e índices

Flyway cria o schema. Há índice em `customer_id` e composto em `(status, created_at)` para filtros e paginação por status/ordenação temporal. Validaria índices reais com `EXPLAIN ANALYZE`.

## Testes

Testes unitários cobrem transições e regra de conflito; testes MVC de integração cobrem criação, validação, JSON malformado, duplicidade com mesmo conteúdo e conteúdo divergente, consulta existente/inexistente, falha do provedor, documentação Swagger e segurança 401/403. Usam H2 para execução local rápida. Testcontainers/PostgreSQL real para concorrência, timeout ponta a ponta, isolamento BOLA e teste concorrente de lock otimista ainda não foram implementados.

## Uso de IA

IA foi usada como apoio para gerar estrutura inicial, trechos de implementação, testes e documentação. Revise e execute a suíte antes de uso em produção.
