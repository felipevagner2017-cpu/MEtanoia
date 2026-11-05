# MEtanoia — Fase 3: continuidade técnica

**Atualizado:** 05/11/2026  
**Repositório:** https://github.com/felipevagner2017-cpu/MEtanoia  
**Branch:** `work/phase1-integrated-main`  
**PR:** #3 — https://github.com/felipevagner2017-cpu/MEtanoia/pull/3

## Homologação do commit 0eb5f3c

Execução GitHub Actions `42028926794`, concluída em 05/11/2026 12:42:42 UTC, resultado geral `success`.

- `build`: success — assembleDebug, unit tests, PWA JavaScript, Wrangler dry-run, packaging e upload.
- `room-integration`: success — três testes instrumentados executados no emulador Android API 34, sem falhas.
- O acesso indexado `onAllNodesWithText(...)[0]` resolveu a falha anterior de navegação.

A checagem externa Cloudflare Workers Builds, para o mesmo SHA, terminou `failure`. Portanto, a PR não está apta a merge enquanto essa verificação obrigatória permanecer vermelha.

## Artefatos verificados

Run: https://github.com/felipevagner2017-cpu/MEtanoia/actions/runs/42028926794

- APK artifact ID `13415715684`, ZIP de 16.624.592 bytes; APK interno 17.106.316 bytes. Teste ZIP sem erros.
- Source artifact ID `13415690698`, ZIP de 119.603 bytes; contém arquivo-fonte ZIP de 129.886 bytes. Integridade ZIP confirmada.
- APK Gradle metadata: Android Gradle Plugin 8.7.3.
- Gradle `app/build.gradle.kts`: applicationId `br.com.metanoia`, versionCode 5, versionName 0.4.1, minSdk 26, targetSdk 35.
- Manifest declara INTERNET, POST_NOTIFICATIONS, SET_WALLPAPER, RECEIVE_BOOT_COMPLETED e SCHEDULE_EXACT_ALARM.
- Assinatura APK v2 verificada estruturalmente; certificado encontrado: Android Debug, SHA-256 `47:E7:66:E7:62:4C:F3:C2:F7:53:59:64:6D:45:25:A4:B6:3E:21:24:8E:12:D9:12:B0:1F:F5:BD:DE:E1:93:71`.
- É uma assinatura debug, não uma chave de distribuição persistente. Não prometer atualização sobre instalação existente até validar assinatura do APK instalado e configurar chave de release estável.
- O source ZIP original não continha Gradle Wrapper. O workflow foi corrigido para gerar Gradle Wrapper 8.9 e incluí-lo no próximo artefato. Também foi adicionado teste automatizado para validar as migrations D1 com SQLite.
- A PWA foi atualizada para proteger o cofre matrimonial com AES-256-GCM e PBKDF2-SHA256 (310.000 iterações), senha digitada localmente e chave mantida somente em memória durante a sessão. A migração de registros antigos em plaintext é não destrutiva; o app preserva o original se a criptografia falhar. Senhas incorretas não desbloqueiam nem alteram o ciphertext. Formatos antigos não reconhecidos são preservados, nunca apagados automaticamente.
- Foi adicionado teste automatizado que extrai os helpers reais do app e verifica criptografia, ausência de plaintext, rejeição de senha incorreta, desbloqueio e migração não destrutiva. O teste aguarda o CI do HEAD atual.

## Arquitetura de dados — preparação

Situação atual comprovada:
- Android: Room local, schema versão 3, entidades agenda, diário, revisões, confissões e análise diária.
- PWA: `localStorage`, chave `metonoia-pwa-v1`.
- Sincronização central e autenticação de usuário ainda não existem.
- Não usar o projeto Supabase ativo do Restaura Lar. Não ativar o projeto Supabase inativo sem comprovar sua finalidade.
- Não foi provisionado D1 nem outro banco central.

Foi criada a migration D1 `migrations/0001_initial.sql` para usuários, sessões, dispositivos, registros criptografados, operações idempotentes de sincronização, consentimentos, conversas/mensagens IA, limites diários e backups. Ela é preparação de schema, não significa que exista banco provisionado ou API de sincronização em produção.

Princípios da migration:
- Conteúdo pessoal persistido somente em ciphertext.
- Controle de revisão e operações idempotentes para retomar sincronização.
- Conflitos explícitos via base_revision; nunca sobrescrever silenciosamente.
- Dados matrimoniais exigem consentimento independente entre usuários; não importar nem sincronizar esse domínio sem consentimento de Ingrid.
- A chave de criptografia deve ser segredo de runtime no Worker; não armazenar em código, D1 plaintext ou cliente.

## Cloudflare

O Wrangler dry-run no CI foi aprovado: leu 10 arquivos de `web/`, empacotou Worker de 6,38 KiB e reconheceu bindings AI e ASSETS. Isso valida configuração local do Wrangler, não autenticação nem deploy.

Foi adicionado um diagnóstico de leitura à CI usando os secrets já configurados no GitHub. Resultado real em 05/11/2026:
- O secret de API Token e o Account ID existem e são aceitos para listar Workers (HTTP 200).
- A conta contém três Workers: `igrejaparacidade`, `metanoia` (tag `641780bf8f00438e9772d7481d53b6d4`) e `metonoia` (tag `91b542bb719049e39309be258c1122ce`). Portanto, há dois Workers com grafias distintas; nenhum foi excluído ou alterado.
- Listagem de Worker Domains respondeu HTTP 200, sem domínios customizados retornados.
- A API workers/subdomain respondeu HTTP 200 e confirmou o subdomínio da conta como `restaura-lar`. O endereço correspondente ao Worker `metanoia` é `https://metanoia.restaura-lar.workers.dev`. O Worker `metonoia` permanece preservado como recurso separado; não foi excluído.
- A allowlist CORS secundária foi corrigida de `metonoia.felipevagner2017.workers.dev` para `metonoia.restaura-lar.workers.dev`, alinhando-a ao subdomínio atual da conta. O domínio oficial do Worker principal permaneceu inalterado.
- A API de builds respondeu HTTP 403 para os dois Workers e para os builds conhecidos (`acb6fba7-4e02-4a40-991d-873faabe8eba`, `99a49e20-ae4d-424d-8bb2-2a328fbd31fc)). A credencial atual não tem permissão Workers CI Read.
- A API D1 respondeu HTTP 401; a credencial não possui acesso D1 suficiente para inventariar/provisionar banco.
- O token atual, portanto, permite inventário de scripts, mas não permite obter os logs de build nem consultar bancos D1.

A CI foi preparada para consultar também o subdomínio workers.dev da conta. O domínio oficial ainda não foi identificado com evidência suficiente; não deduzir apenas pela grafia do Worker.

**Permissões que faltam para concluir a auditoria e infraestrutura:** atualizar o secret GitHub `CLOUDFLARE_API_TOKEN` com Workers CI Read, D1 Read e, para provisionar o banco, D1 Edit. Para corrigir as configurações Workers Builds, também é necessária Workers Builds Configuration Edit. Manter Workers Scripts Edit para publicação. Não enviar token pelo chat; atualizar o secret diretamente no GitHub.

Link do build que falhou: https://dash.cloudflare.com/69cf119d00a74c4fe5cd6960d2d9e5f9/workers/services/view/metanoia/production/builds/acb6fba7-4e02-4a40-991d-873faabe8eba

## Bloqueios e limites

1. Acesso autenticado Cloudflare ausente: necessário para ler logs completos, comparar Workers e ajustar Build settings/preview e provisionar D1.
2. Nenhum banco central MEtanoia provisionado.
3. IA atual ainda exige `METANOIA_AI_TOKEN` no cliente e não persiste histórico; substituir somente após implementar auth e armazenamento seguro.
4. Cofre matrimonial da PWA agora tem criptografia local AES-GCM; ainda não existe sincronização, consentimento autenticado de Ingrid nem criptografia ponta a ponta multi-dispositivo. Não sincronizar esse domínio até implementar e validar essas camadas.
5. APK atual usa assinatura debug; compatibilidade de atualização com o aplicativo já instalado não foi verificada. O source ZIP passará a incluir Gradle Wrapper gerado no CI.

## Próximas ações

1. Confirmar CI após commit de empacotamento do Gradle Wrapper.
2. Completar a API D1 e testes de autenticação/sincronização antes de provisionar.
3. Obter logs Workers Builds e validar preview com acesso Cloudflare.
4. Implementar armazenamento seguro de IA, consentimento e limites de uso.
5. Criar testes de migração/importação idempotente Room e PWA sem apagar os dados locais.
6. Validar APK assinado com a mesma chave do instalado antes de atualização.
7. Só mesclar/publicar quando verificações obrigatórias estiverem verdes e o endpoint estiver validado.

**Não declarar sincronização, autenticação, cofre seguro, IA persistente, preview ou produção concluídos sem testes e evidência.**
