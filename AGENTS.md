# Instrucoes para agentes

## Contexto do projeto

Servico REST de ativacao de produtos, desenvolvido em Java 21 e Spring Boot 3. Usa Spring MVC, Spring Data
JPA/Hibernate, PostgreSQL, Flyway, Spring Security Resource Server, springdoc-openapi, Maven e WireMock.

## Estrutura

- `src/main/java/br/com/example/activation/api`: controllers, DTOs e tratamento de erros.
- `src/main/java/br/com/example/activation/service`: casos de uso e regras da aplicacao.
- `src/main/java/br/com/example/activation/domain`: entidade e regras/transicoes de estado.
- `src/main/java/br/com/example/activation/persistence`: repositorios Spring Data JPA.
- `src/main/java/br/com/example/activation/integration`: cliente e contratos do provedor externo.
- `src/main/java/br/com/example/activation/config`: configuracao de seguranca, cliente HTTP, OpenAPI e correlacao.
- `src/main/resources/db/migration`: migracoes versionadas do Flyway.
- `src/test`: testes unitarios e testes de integracao MVC.
- `wiremock`: mapeamentos locais do provedor simulado.

## Convencoes e regras importantes

- Preserve a separacao entre API, servico, dominio, persistencia e integracao. Nao exponha entidades JPA diretamente:
  use os DTOs existentes.
- Validacoes de entrada ficam nos DTOs da API; transicoes de estado devem ser controladas pela entidade de dominio.
- Mantenha a unicidade de `externalId` garantida no banco, alem da verificacao previa na aplicacao. Repetir o mesmo ID
  com o mesmo conteudo retorna o registro existente; conteudo divergente deve resultar em conflito HTTP 409.
- Toda rota de negocio exige JWT. `requests:write` autoriza criacao e `requests:read` autoriza consulta. Swagger UI e
  documentos OpenAPI sao publicos; nao torne endpoints de negocio publicos.
- Nao exponha detalhes internos, credenciais, tokens ou stack traces em respostas e logs.
- Configuracoes externas devem continuar parametrizadas por propriedades/variaveis de ambiente, sem adicionar segredos
  ao repositorio.
- Nunca edite uma migracao Flyway ja aplicada para mudar o schema; crie uma nova migracao versionada.
- O processamento atual e sincrono. Retry automatico, Outbox, reconciliacao e isolamento BOLA ainda nao estao
  implementados; nao os descreva como existentes.

## Testes e comandos

- Execute a suite antes de concluir mudancas: `mvn verify`.
- A maioria dos testes usa H2 em memoria e simula o provedor por mock; `ActivationRequestConcurrencyIntegrationTest` usa
  PostgreSQL real via Testcontainers, ou um banco de teste configurado por `TEST_POSTGRES_URL`, `TEST_POSTGRES_USERNAME`
  e `TEST_POSTGRES_PASSWORD`.
- Para subir a aplicacao, PostgreSQL e WireMock localmente: `docker compose up --build`.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`; OpenAPI: `http://localhost:8080/v3/api-docs`.
- A API requer token JWT valido mesmo ao ser chamada pelo Swagger. A configuracao local usa HS256; nao reutilize a chave
  de desenvolvimento em producao.
- Para executar somente o teste concorrente com PostgreSQL/Testcontainers:
  `mvn -Dtest=ActivationRequestConcurrencyIntegrationTest test`. Alternativamente, defina `TEST_POSTGRES_URL`,
  `TEST_POSTGRES_USERNAME` e `TEST_POSTGRES_PASSWORD` para apontar a um PostgreSQL de teste existente.

## Documentacao

Atualize o `README.md` quando uma mudanca alterar endpoints, configuracao, comportamento de seguranca, execucao, testes
ou limitacoes conhecidas.
