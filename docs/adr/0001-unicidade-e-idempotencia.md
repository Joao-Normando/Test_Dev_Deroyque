# ADR-0001: Unicidade e idempotência por `externalId`

- **Data:** 2026-10-29

## Contexto

Sistemas clientes podem repetir uma solicitação após timeout ou enviar solicitações simultâneas. Uma consulta prévia ao banco não impede sozinha que duas requisições insiram o mesmo identificador.

## Decisão

`external_id` tem restrição `UNIQUE` no PostgreSQL. O serviço também consulta o identificador antes da inserção para decidir se os dados repetidos são idênticos ou conflitantes e trata a colisão de inserção concorrente.

- Mesmo ID e mesmo conteúdo: retornar a solicitação existente sem chamar o provedor novamente.
- Mesmo ID e conteúdo divergente: retornar `409 Conflict`.
- Requisições simultâneas idênticas: manter um único registro e retornar esse registro para as demais.

A resposta HTTP atual para o retry com conteúdo igual permanece `201 Created`, igual à criação inicial.

## Consequências

A restrição do banco é a garantia de unicidade mesmo com múltiplas instâncias. A semântica de retry fica explícita e evita uma nova chamada externa para um registro já existente. A proteção contra duplicidade no provedor depende de ele respeitar o header `Idempotency-Key` enviado pela aplicação.
