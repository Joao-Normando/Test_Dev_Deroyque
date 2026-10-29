# Activation Service

API REST para registrar solicitações de ativação de produtos, consultar seu estado e chamar um provedor externo simulado. O projeto foi desenvolvido para o desafio Java Pleno/Sênior e prioriza uma implementação simples, com validações, persistência, segurança e testes.

## Tecnologias

- Java 21 e Spring Boot 3
- Spring MVC, Spring Data JPA e Hibernate
- PostgreSQL e Flyway
- Spring Security Resource Server e JWT
- Maven, JUnit 5, Mockito, AssertJ e MockMvc
- Docker Compose e WireMock

## Como o projeto funciona

1. O controller recebe e valida o DTO de entrada.
2. O serviço procura um `externalId` existente e aplica a regra de idempotência.
3. Uma solicitação nova é persistida com uma restrição `UNIQUE` no banco.
4. O serviço chama o provedor de forma síncrona e atualiza o estado para `COMPLETED` ou `FAILED`.
5. A API devolve a solicitação, sem expor a entidade JPA diretamente.

O estado inicial é `PENDING`; durante o processamento ele passa por `PROCESSING` e termina em `COMPLETED` ou `FAILED`. A entidade controla as transições e usa `@Version` para concorrência otimista.

### Organização do código

| Pacote/pasta | Responsabilidade |
|---|---|
| `controller` | Endpoints REST, DTOs e tratamento de erros HTTP |
| `service` | Regras de aplicação e coordenação do caso de uso |
| `domain` | Entidade, estados e transições permitidas |
| `persistence` | Repositórios Spring Data JPA |
| `integration` | Cliente e contrato do provedor externo |
| `config` | Segurança, cliente HTTP, OpenAPI e correlação |
| `src/main/resources/db/migration` | Migrações versionadas do Flyway |
| `src/test` | Testes unitários e de integração |
| `wiremock` | Mapeamentos do provedor simulado |

## Executar a partir de um clone

### Pré-requisitos

- Git para clonar o repositório.
- Docker Desktop com Docker Compose para executar a aplicação, PostgreSQL e WireMock.
- Java 21 e Maven para executar os testes localmente. O repositório não inclui Maven Wrapper.

### Subir a aplicação

Na raiz do repositório, execute:

```powershell
docker compose up --build
```

O primeiro início pode levar alguns minutos para baixar as imagens e compilar a aplicação. Aguarde o serviço `app` iniciar. O Compose sobe:

| Serviço | Endereço | Uso |
|---|---|---|
| API | `http://localhost:8080` | Endpoints de negócio |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` | Explorar e chamar a API |
| OpenAPI | `http://localhost:8080/v3/api-docs` | Especificação da API |
| PostgreSQL | `localhost:5432` | Persistência local |
| WireMock | `http://localhost:8090` | Provedor de ativação simulado |
| Jaeger UI | `http://localhost:16686` | Consultar traces distribuídos |
| Health | `http://localhost:8080/actuator/health` | Estado geral da aplicação |
| Liveness | `http://localhost:8080/actuator/health/liveness` | Verificar se a aplicação está viva |
| Readiness | `http://localhost:8080/actuator/health/readiness` | Verificar se pode receber tráfego |

O Flyway cria/atualiza o schema ao iniciar a aplicação. O mapeamento local do WireMock responde `200` a `POST /provider/v1/activate`. O Jaeger recebe traces por OTLP/HTTP na rede Docker.

### Gerar um token local

Todos os endpoints de negócio exigem JWT. Em um segundo terminal PowerShell, na raiz do repositório, gere um token local:

```powershell
$token = & .\scripts\New-DevelopmentJwt.ps1
```

O script cria um token HS256 válido por 60 minutos, com `requests:read`, `requests:write` e `metrics:read`. Ele usa a chave local configurada no Compose. Essa chave é somente para desenvolvimento e não deve ser usada em produção.

Se o PowerShell bloquear scripts neste terminal, permita scripts locais apenas na sessão atual e tente novamente:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
$token = & .\scripts\New-DevelopmentJwt.ps1
```

Essa configuração termina ao fechar o terminal; não altera a política permanente do computador. Em um computador gerenciado, siga a política da organização.

Também é possível usar a API pelo Swagger UI: abra `http://localhost:8080/swagger-ui/index.html`, clique em **Authorize** e cole o token sem o prefixo `Bearer`.

Para simular consumidores diferentes e testar o isolamento, gere tokens com sujeitos distintos:

```powershell
$tokenA = & .\scripts\New-DevelopmentJwt.ps1 -Subject "consumer-a"
$tokenB = & .\scripts\New-DevelopmentJwt.ps1 -Subject "consumer-b"
```

### Criar uma solicitação e conferir o WireMock

Envie um `externalId` que ainda não tenha usado:

```powershell
$body = @{
    externalId = "EXT-TEST-001"
    customerId = "CUST-10001"
    productCode = "INTERNET-500"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/v1/requests" `
    -Headers @{ Authorization = "Bearer $token" } `
    -ContentType "application/json" `
    -Body $body
```

O POST retorna `201 Created` e um header `Location`. Como o processamento é síncrono, a resposta normalmente já mostra o estado `COMPLETED`. A API envia ao WireMock o payload da solicitação e o header `Idempotency-Key`, com o valor de `externalId`.

Para consultar o histórico recebido pelo WireMock:

```powershell
$journal = Invoke-RestMethod -Uri "http://localhost:8090/__admin/requests"
$journal.meta
$journal.requests | ForEach-Object {
    $_.request | Select-Object method, url, body
}
```

`meta.total` informa quantas chamadas foram registradas desde que o WireMock iniciou; esse total pode incluir execuções anteriores. `wasMatched: true` no detalhe da solicitação significa que ela correspondeu ao mapeamento local. A consulta ao journal apenas inspeciona chamadas — ela não cria uma ativação. Para testar de novo, use outro `externalId`: repetir o mesmo ID e conteúdo retorna o registro existente e não chama o provedor novamente.

### Métricas e traces

O endpoint Prometheus exige um token com `metrics:read`:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/actuator/prometheus" `
    -Headers @{ Authorization = "Bearer $token" }
```

O endpoint expõe métricas JVM e HTTP, entre outras fornecidas pelo Actuator/Micrometer. Para analisar uma chamada distribuída, abra `http://localhost:16686`, selecione o serviço `activation-service` e procure traces recentes. A instrumentação acompanha a requisição HTTP e a chamada HTTP ao provedor; o cliente propaga o contexto W3C (`traceparent`). A amostragem local é configurada em 100% para facilitar a inspeção. Em produção, ajuste `TRACING_SAMPLING_PROBABILITY` e o destino OTLP para o backend de observabilidade da plataforma.

### Consultar solicitações

Use um token com o escopo `requests:read`:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20" `
    -Headers @{ Authorization = "Bearer $token" }
```

Para consultar uma solicitação específica, use o UUID retornado no campo `id`:

```powershell
$id = "<UUID retornado pela API>"
Invoke-RestMethod `
    -Uri "http://localhost:8080/api/v1/requests/$id" `
    -Headers @{ Authorization = "Bearer $token" }
```

Para parar os serviços, pressione `Ctrl+C` no terminal em que `docker compose up --build` está rodando. Também é possível parar com `docker compose down`.

### Configuração por ambiente

As configurações externas são lidas de variáveis de ambiente. Os valores padrão abaixo são para desenvolvimento local; o Compose configura a URL interna do banco e do provedor para a rede Docker.

| Variável | Padrão da aplicação | Finalidade |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/activation` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | `activation` | Usuário do banco |
| `DB_PASSWORD` | `activation` | Senha local do banco |
| `PROVIDER_BASE_URL` | `http://localhost:8090` | URL base do provedor |
| `PROVIDER_CONNECT_TIMEOUT` | `2s` | Timeout para estabelecer conexão |
| `PROVIDER_READ_TIMEOUT` | `5s` | Timeout para leitura da resposta |
| `JWT_SECRET` | Chave local definida na configuração | Segredo HS256 usado no ambiente local |
| `JWT_ISSUER` | `https://identity.example.com` | Emissor esperado no JWT |
| `JWT_AUDIENCE` | `activation-service` | Audiência esperada no JWT |
| `OTEL_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Endpoint OTLP/HTTP para exportar traces |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Proporção de traces amostrados |
| `OTEL_SERVICE_NAME` | `activation-service` | Nome do serviço nos traces |

## API e validações

| Método e rota | Acesso | Descrição |
|---|---|---|
| `POST /api/v1/requests` | `requests:write` | Cria ou retorna uma solicitação idempotente |
| `GET /api/v1/requests/{id}` | `requests:read` | Consulta por UUID |
| `GET /api/v1/requests?status=&customerId=&page=&size=` | `requests:read` | Lista com filtros e paginação |

Exemplo de corpo para criação:

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

Os três campos são obrigatórios, aceitam até 64 caracteres e devem corresponder ao padrão `[A-Za-z0-9._-]+`. As datas são `Instant` serializadas em ISO-8601 UTC. As respostas de erro usam `application/problem+json`; erros inesperados não incluem stack trace ou detalhes internos.

## Decisões técnicas

### Duplicidade e concorrência

O `externalId` tem uma restrição `UNIQUE` no PostgreSQL. A consulta prévia evita uma tentativa de gravação desnecessária, mas não é suficiente sozinha: duas requisições simultâneas podem passar pela consulta ao mesmo tempo. Por isso, a restrição do banco é a garantia final e o serviço trata a colisão de inserção.

- Mesmo `externalId` e mesmos dados: retorna o registro existente sem chamar o provedor novamente. O endpoint mantém `201 Created`.
- Mesmo `externalId` com dados diferentes: retorna `409 Conflict`.
- Um consumidor diferente tentando reutilizar o mesmo ID recebe `409` genérico; a resposta não revela nem retorna os dados do proprietário original.
- Requisições simultâneas iguais: a restrição de unicidade permite um único registro e o serviço retorna o registro já existente para as demais.


### Segurança

A aplicação atua como OAuth 2.0 Resource Server e valida JWT HS256, assinatura, emissor, audiência, validade temporal e presença do `sub`. O `sub` identifica o consumidor e é obtido exclusivamente do token validado, nunca do corpo ou de um parâmetro enviado pelo cliente. Cada solicitação é persistida com esse proprietário; buscas por UUID e listagens sempre filtram pelo proprietário. Assim, um UUID de outro consumidor responde `404`, como se não existisse. `externalId` continua globalmente único; uma tentativa incompatível responde conflito sem retornar dados de outro consumidor.

O claim `scope` é convertido para authorities `SCOPE_...`: `requests:write` permite criação, `requests:read` permite consulta e `metrics:read` permite scrape de Prometheus. Health, liveness e readiness são públicos para permitir que o orquestrador verifique a aplicação; o endpoint de métricas exige autenticação. Swagger UI e documentos OpenAPI também são públicos. Não há servidor de identidade nem endpoint de emissão de token. Em produção, usaria OAuth 2.0 Client Credentials com um provedor de identidade, chaves assimétricas/JWKS e segredos de integração em um secret manager com rotação. Veja [ADR-0003](docs/adr/0003-autenticacao-e-autorizacao.md) e [ADR-0004](docs/adr/0004-isolamento-por-consumidor.md).

## Banco e índices

O Flyway cria a tabela `activation_requests`, a restrição de unicidade em `external_id` e a restrição dos estados válidos. A migração V2 adiciona `owner_subject` e índices que apoiam consultas isoladas por consumidor, filtros por cliente, estado e data. Os registros que já existiam quando V2 for aplicada recebem o proprietário técnico `legacy` para não ficarem visíveis a nenhum consumidor normal; antes de preservar dados existentes em um ambiente real, faça o backfill para os `sub` correspondentes por um processo administrativo seguro. O impacto dos índices deve ser confirmado com dados e `EXPLAIN ANALYZE`. Migrações aplicadas não devem ser editadas; mudanças futuras de schema devem usar uma nova versão.

## Testes

Execute a suíte na raiz do projeto:

```powershell
mvn verify
```

Java 21, Maven e Docker em execução são necessários. A maioria dos testes usa H2 em memória e simula o provedor com Mockito. O teste concorrente usa PostgreSQL real via Testcontainers; se Docker não estiver disponível para Testcontainers, configure um PostgreSQL de teste com as variáveis abaixo. O teste concorrente gera um `externalId` aleatório e remove somente o registro criado por ele.

```powershell
docker compose up -d postgres
$env:TEST_POSTGRES_URL = "jdbc:postgresql://localhost:5432/activation"
$env:TEST_POSTGRES_USERNAME = "activation"
$env:TEST_POSTGRES_PASSWORD = "activation"
mvn verify
```

Para executar somente o teste concorrente:

```powershell
mvn -Dtest=ActivationRequestConcurrencyIntegrationTest test
```

### O que cada teste verifica

| Classe/teste | Tipo | O que verifica |
|---|---|---|
| `ActivationRequestTest.validTransition` | Unitário de domínio | Permite `PENDING -> PROCESSING -> COMPLETED`. |
| `ActivationRequestTest.rejectsInvalidTransition` | Unitário de domínio | Rejeita concluir uma solicitação sem antes processá-la. |
| `ActivationRequestServiceTest.reusedExternalIdWithDifferentPayloadConflicts` | Unitário de serviço | Dados divergentes para um ID existente geram conflito e não chamam o provedor. |
| `ActivationRequestServiceTest.doesNotReturnAnotherConsumersRequestForAnIdempotentRetry` | Unitário de serviço | Um consumidor não recebe solicitação de outro ao repetir o mesmo `externalId`. |
| `ActivationRequestControllerIntegrationTest.noToken401` | Integração MVC | Requisição sem token recebe `401`. |
| `ActivationRequestControllerIntegrationTest.wrongScope403` | Integração MVC | Token de leitura não autoriza criação e recebe `403`. |
| `ActivationRequestControllerIntegrationTest.createsAndCompletes` | Integração MVC | Criação válida retorna `201`, `Location` e estado concluído quando o provedor mockado tem sucesso; também verifica que a chamada ocorre sem transação de banco ativa. |
| `ActivationRequestControllerIntegrationTest.validatesInput` | Integração MVC | Campo obrigatório inválido retorna `400` em formato Problem Details. |
| `ActivationRequestControllerIntegrationTest.malformedJsonReturnsBadRequest` | Integração MVC | JSON malformado retorna `400` em formato Problem Details. |
| `ActivationRequestControllerIntegrationTest.duplicateRetryDoesNotCallProviderAgain` | Integração MVC | Repetir a solicitação idêntica mantém um registro e aciona o provedor uma única vez. |
| `ActivationRequestControllerIntegrationTest.duplicateExternalIdWithDifferentContentReturnsConflict` | Integração MVC | Reutilizar o ID com conteúdo divergente retorna `409` sem uma segunda chamada ao provedor. |
| `ActivationRequestControllerIntegrationTest.consumersCanOnlyReadTheirOwnRequests` | Integração MVC | Consulta por outro consumidor retorna `404` e sua listagem não revela registros alheios. |
| `ActivationRequestControllerIntegrationTest.anotherConsumerCannotReuseAnExistingExternalId` | Integração MVC | Reutilização cross-tenant retorna conflito genérico sem chamar novamente o provedor. |
| `ActivationRequestControllerIntegrationTest.getsExistingRequest` | Integração MVC | Consulta por UUID retorna a solicitação e seu estado atual. |
| `ActivationRequestControllerIntegrationTest.missingRequestReturnsNotFound` | Integração MVC | UUID inexistente retorna `404` em formato Problem Details. |
| `ActivationRequestControllerIntegrationTest.providerErrorMarksFailed` | Integração MVC | Exceção simulada do provedor faz a solicitação terminar como `FAILED`. |
| `ActivationRequestControllerIntegrationTest.swaggerDocsArePublicAndDescribeBearerAuthentication` | Integração MVC | OpenAPI e Swagger UI são públicos e documentam autenticação Bearer. |
| `ActivationRequestControllerIntegrationTest.healthProbesArePublicAndPrometheusRequiresMetricsScope` | Integração MVC | Liveness/readiness são públicos; Prometheus exige `metrics:read` e expõe métricas HTTP. |
| `ActivationRequestConcurrencyIntegrationTest.concurrentRequestsWithSameExternalIdCreateOnlyOneActivation` | Integração com PostgreSQL | Dez chamadas simultâneas com o mesmo ID resultam em um registro e uma chamada ao provedor. |

Os testes MVC usam H2 e um mock de `ProviderActivationClient`; portanto, não exercitam o WireMock local. A conferência do WireMock descrita na seção de execução é um teste manual ponta a ponta da integração HTTP.

## Produção, observabilidade e análise de lentidão

O Dockerfile usa uma etapa Maven para compilar e uma imagem JRE para executar o JAR. O Compose é destinado ao desenvolvimento local, não à produção: usa credenciais e chave de desenvolvimento e não configura um volume PostgreSQL nomeado. Actuator fornece health, liveness, readiness e métricas Prometheus; Micrometer Tracing exporta traces via OTLP ao Jaeger local. O client HTTP propaga o contexto W3C para o provedor. Health endpoints são destinados a probes; proteja também o acesso operacional à aplicação e ao backend de observabilidade em produção. O CI em `.github/workflows/ci.yml` executa `mvn --batch-mode verify` em pushes e pull requests.

O filtro aceita ou gera `X-Correlation-Id`, devolve o identificador na resposta e o inclui no MDC. O `traceId` do tracing distribuído é registrado separadamente; não sobrescreve o ID de correlação. Métricas Micrometer incluem JVM e tráfego HTTP. Logs estruturados ainda não estão implementados. Não registre tokens, credenciais ou payloads sensíveis.

Se a latência média da API subir de 300 ms para 2,5 s enquanto CPU e memória permanecerem moderadas, eu começaria comparando p50/p95/p99 por endpoint e os tempos de banco e provedor. Verificaria traces e logs pelo `X-Correlation-Id`, consultas lentas, plano de execução, pool de conexões e latência do serviço externo. Como as medições apontam também aumento no banco e no provedor, investigaria ambos; uma chamada externa lenta aumenta o tempo total do POST, mas não mantém uma transação nem conexão de banco aberta durante a chamada. Confirmaria a hipótese com tracing, métricas do pool e testes de carga, em vez de assumir uma causa.

Após a correção, compararia as mesmas métricas antes e depois, incluindo latência percentil, taxa de erros, throughput e saturação de conexões, primeiro em canário ou carga controlada. Melhorias possíveis incluem otimizar consultas comprovadamente lentas, ajustar pools/índices com evidência, melhorar a capacidade do provedor ou desacoplar a integração com Outbox e processamento assíncrono.

## Pendências e evolução

- Retry automático, backoff, limite de tentativas e respeito a `Retry-After`.
- Outbox transacional e reconciliação de solicitações que permaneçam em `PROCESSING`.
- Limites de tamanho de corpo e taxa de chamadas.
- Testes automatizados de timeout/latência contra o cliente HTTP.
- Testes automatizados que validem a exportação OTLP e a propagação de contexto até um backend de tracing.

Em uma evolução para múltiplas instâncias, a unicidade no banco continuaria sendo a proteção contra duplicidade local. Uma Outbox permitiria publicar o trabalho após a gravação e processá-lo de forma resiliente; eventos de conclusão poderiam ser enviados a uma fila ou tópico. O pipeline de entrega validaria, testaria e construiria a imagem antes de publicá-la e implantá-la. Esses mecanismos são possibilidades futuras, não capacidades já implementadas.

## Registros de decisão

As decisões e seus motivos estão registrados em [`docs/adr/`](docs/adr/):

- [ADR-0001: unicidade e idempotência por `externalId`](docs/adr/0001-unicidade-e-idempotencia.md)
- [ADR-0002: integração síncrona e tratamento de falhas](docs/adr/0002-integracao-sincrona-e-falhas.md)
- [ADR-0003: JWT e autorização por escopo](docs/adr/0003-autenticacao-e-autorizacao.md)
- [ADR-0004: isolamento de solicitações por consumidor](docs/adr/0004-isolamento-por-consumidor.md)

## Uso de IA

Ferramentas de IA foram usadas como apoio à estrutura inicial, implementação de trechos, testes e documentação. As decisões e os resultados devem ser revisados pelo responsável pelo projeto.
