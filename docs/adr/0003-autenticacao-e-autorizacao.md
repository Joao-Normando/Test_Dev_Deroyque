# ADR-0003: JWT e autorização por escopo

- **Status:** Aceita para o escopo do desafio
- **Data:** 2026-10-29

## Contexto

A API é consumida por outros sistemas e precisa proteger os endpoints de negócio, distinguindo chamadas de escrita das de leitura sem criar um servidor de identidade como parte do desafio.

## Decisão

A aplicação usa Spring Security Resource Server para validar JWT HS256. Valida assinatura, emissor, audiência, validade temporal e presença de `sub`. O claim `scope` vira authorities com prefixo `SCOPE_`: `requests:write` autoriza criação, `requests:read` autoriza consultas e `metrics:read` autoriza acesso ao endpoint Prometheus. Swagger UI, OpenAPI e health probes são públicos; os endpoints de negócio exigem token.

Um script local gera tokens de desenvolvimento com a chave definida no Compose. Essa chave não é apropriada para produção.

## Consequências

É possível exercitar autenticação e autorização localmente e testar respostas 401/403 sem um IdP real. O `sub` validado identifica o consumidor e é usado como proprietário das solicitações; leituras e listagens são sempre filtradas por ele. Em produção, usar OAuth 2.0 Client Credentials com um IdP corporativo, assinatura assimétrica e validação por JWKS; armazenar e rotacionar credenciais em um secret manager. Métricas são protegidas por escopo; o acesso ao backend de tracing e aos health probes deve ser restringido pela rede/plataforma de produção.
