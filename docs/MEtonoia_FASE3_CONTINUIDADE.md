# MEtanoia — Fase 3: continuidade técnica

**Atualizado:** 05/11/2026  
**Repositório:** https://github.com/felipevagner2017-cpu/MEtanoia  
**Branch:** `work/phase1-integrated-main`  
**PR:** #3 — https://github.com/felipevagner2017-cpu/MEtanoia/pull/3

## Homologação mais recente

Execução GitHub Actions `42031050218`, commit `a02071a5e7ba2339e0b38d3ab36760cf8c291c9c`, concluída com resultado geral `success`.

- `build`: success — assembleDebug, testes unitários, validação das migrations D1, teste de criptografia do cofre, validação JavaScript da PWA, Wrangler dry-run, geração do Gradle Wrapper, empacotamento e upload.
- `room-integration`: success — testes instrumentados Room e navegação no emulador Android API 34.
- O seletor Compose corrigido com acesso indexado passou.
- O Wrangler dry-run passou, mas isso não substitui o check externo Cloudflare Workers Builds.

A PR #3 continua aberta. O check externo `Workers Builds: metanoia` falhou para o commit atual, build ID `d08878b1-c4bf-4e7a-9c58-f745332e23cd`. Não mesclar enquanto essa verificação obrigatória estiver vermelha.

## Artefatos verificados

Run: https://github.com/felipevagner2017-cpu/MEtanoia/actions/runs/42031050218

- APK artifact ID `13416646689`, ZIP externo de 16.624.592 bytes; APK interno 17.106.320 bytes. Integridade ZIP do pacote e APK confirmada.
- Source artifact ID `13416895313`, ZIP externo de 175.355 bytes; arquivo-fonte interno 186.375 bytes, 64 arquivos. Integridade ZIP confirmada.
- Source inclui `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` e `gradle-wrapper.properties`; JAR contém 33 entradas e wrapper aponta para Gradle 8.9.
- APK manifest: applicationId `br.com.metanoia`, versionCode 5, versionName 0.4.1, minSdk 26, targetSdk 35.
- Permissões: INTERNET, POST_NOTIFICATIONS, SET_WALLPAPER, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, WAKE_LOCK, ACCESS_NETWORK_STATE e FOREGROUND_SERVICE, além da permissão interna dinâmica do AndroidX.
- APK possui bloco de assinatura v2 e certificado Android Debug SHA-256 `6A:4D:C7:58:DD:3D:FA:8D:F6:80:D0:A9:8C:FF:BE:AD:8F:DD:91:6F:4C:F7:51:08:35:D3:BF:00:CB:55:7E:27`. O manifest indica `debuggable=true` e `allowBackup=false`.
- A assinatura muda entre execuções CI (a execução anterior usou SHA-256 `02:23:7D:58:06:3F:19:EA:28:40:D6:0E:10:7D:51:1D:6D:96:B2:4C:96:36:0D:E4:B0:CB:4D:0D:9E:EF:4C:87`). Portanto, este APK não pode ser tratado como atualização segura da instalação existente. Não desinstalar o app nem sobrescrever a instalação até configurar uma chave de assinatura persistente e comparar com o certificado instalado.
- O backup automático Android foi desativado enquanto Room não tiver criptografia de banco comprovada; isso reduz backup automático e deve ser substituído por backup cifrado próprio antes de considerar essa frente concluída.

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
