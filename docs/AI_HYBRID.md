# Inteligência contextual do MEtanoia

## Arquitetura em evolução

- O Worker Cloudflare executa o modelo Workers AI pelo binding `AI`.
- O backend agora possui endpoints de conta e sincronização D1 em `worker/d1-api.ts`.
- Cadastro por convite, login, sessão opaca armazenada somente como hash, vínculo de sessão/dispositivo e revogação individual.
- O endpoint de IA exige sessão autenticada e limita a 30 solicitações por usuário/dia.
- Registros sincronizados são recebidos como ciphertext; o Worker não precisa conhecer o conteúdo pessoal em texto claro.
- O contexto enviado à IA continua limitado ao que o cliente seleciona explicitamente.

## Endpoints adicionados

- `POST /api/auth/register`: cadastro protegido por `X-Registration-Key`; exige senha com no mínimo 12 caracteres.
- `POST /api/auth/login`: autenticação por e-mail e senha com PBKDF2-SHA256 (310.000 iterações).
- `POST /api/auth/logout`: revogação da sessão atual.
- `GET /api/devices/list` e `DELETE /api/devices/{id}`: gerenciamento de dispositivos.
- `POST /api/sync/push`: lote de até 50 operações, idempotência por operationId e controle otimista de revisão.
- `GET /api/sync/pull?since={timestamp}&afterId={recordId}`: busca incremental paginada de alterações e tombstones, com cursor estável.
- `POST /api/backups/create`, `GET /api/backups/list`, `GET /api/backups/{id}` e `DELETE /api/backups/{id}`: backups cifrados, verificação de integridade e recuperação.
- `GET /api/consents/list`, `POST /api/consents/grant` e `POST /api/consents/revoke`: registro de consentimento concedido e revogável pelo participante.
- `POST /api/ai/chat`: exige sessão e aplica limite diário.

## Provisionamento ainda necessário

O Worker exige um binding D1 chamado `DB`, conectado exclusivamente ao banco do MEtanoia. O banco remoto ainda não foi criado nem associado: a migration local não é evidência de provisionamento. O cadastro exige o secret `METANOIA_REGISTRATION_KEY`. Nenhum token de IA deve ser digitado ou armazenado no aplicativo; a chamada Workers AI é feita pelo próprio Worker.

## Limites de integração atuais

- A API D1 está implementada no código, mas não pode funcionar até existir o binding remoto e as migrations serem aplicadas.
- As telas Android e PWA ainda não foram conectadas ao fluxo compartilhado de conta/sincronização; Room e localStorage continuam sendo os repositórios locais.
- O histórico contextual da IA ainda não é persistido no D1. A tabela de conversas usa ciphertext e precisa da integração criptográfica cliente-servidor antes de armazenar conteúdo.
- A cota diária é aplicada por usuário. A medição de tokens e os limites de custo monetário ainda precisam ser conectados ao retorno de uso do modelo.
- O uso da IA requer internet. A análise local permanece disponível offline.

## Segurança

- Não grave credenciais em código, logs ou variáveis públicas.
- Não envie o cofre matrimonial de Ingrid como contexto implícito.
- O cliente deve selecionar e autorizar os registros antes do envio.
- O servidor trata registros como dados não confiáveis, nunca como instruções.
- A IA não substitui acompanhamento pastoral, médico, psicológico, jurídico ou financeiro.
