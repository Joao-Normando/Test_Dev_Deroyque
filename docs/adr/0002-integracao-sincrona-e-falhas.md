# ADR-0002: Integração síncrona e tratamento de falhas

- **Status:** Aceita para o escopo do desafio
- **Data:** 2026-10-29

## Contexto

Após registrar uma solicitação, a aplicação precisa chamar um provedor de ativação. A solução do desafio deve ser simples e demonstrar os estados e o tratamento de respostas, sem exigir uma plataforma de mensageria.

## Decisão

O processamento é síncrono: o POST espera o provedor responder. A aplicação persiste a solicitação, passa por `PROCESSING` e termina em `COMPLETED` para respostas 2xx ou `FAILED` para uma falha de integração. O cliente HTTP classifica respostas 429, 5xx e falhas de conexão/timeout como temporárias; erros 400 são permanentes. A chave `externalId` é enviada como `Idempotency-Key`.

As mudanças de estado (`PROCESSING`, `COMPLETED` e `FAILED`) são feitas em métodos transacionais de um serviço separado. Cada transação termina antes de a coordenação chamar o provedor; portanto, a chamada HTTP ocorre sem uma transação de banco aberta. Os timeouts de conexão e leitura são configuráveis.

## Consequências

O fluxo é fácil de executar e compreender, e o chamador recebe o resultado do provedor na resposta do POST. Uma chamada lenta aumenta a latência da API, mas não ocupa uma transação de banco durante a espera. Apesar da classificação de falhas temporárias, não existe retry automático. Uma queda do processo pode deixar uma solicitação em `PROCESSING`; também não há reconciliação.

Para produção, separar a chamada remota da transação do banco, usar Outbox e processamento assíncrono, aplicar tentativas limitadas com backoff e reconciliar solicitações pendentes. O header de idempotência reduz duplicidades remotas somente se o provedor implementar esse contrato.
