# ADR-0004: Isolamento de solicitações por consumidor

- **Data:** 2026-10-29

## Contexto

Consumidores autenticados não devem consultar solicitações criadas por outros consumidores (BOLA). O serviço já valida JWT, mas inicialmente não associava os registros a uma identidade.

## Decisão

O `sub` obrigatório do JWT validado identifica o consumidor. O valor é extraído do principal autenticado no controller, nunca de um campo do request, e persistido em `owner_subject`. Todas as consultas por UUID e listagens incluem `owner_subject` no filtro. Um UUID que pertence a outro consumidor responde `404`.

O `externalId` continua globalmente único para preservar a regra atual de idempotência e a garantia contra corrida no banco. Se outro consumidor tentar reutilizá-lo, recebe um conflito genérico sem conteúdo do registro, evitando revelar o proprietário ou os dados existentes.

Na migração V2, registros preexistentes recebem o proprietário técnico `legacy`, que não corresponde a consumidores normais. Uma implantação com dados reais precisa mapear esses registros aos sujeitos corretos por um processo administrativo antes de reabrir seu acesso.

## Consequências

O isolamento aplica-se a leitura individual, listagem filtrada e repetição idempotente. A escolha de `sub` pressupõe que o emissor de tokens atribua um sujeito estável e único por consumidor dentro do issuer validado. Para múltiplos issuers, a identidade persistida deve incluir também o issuer.
