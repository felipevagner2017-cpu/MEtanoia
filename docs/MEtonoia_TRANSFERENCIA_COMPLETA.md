# MEtanoia — transferência técnica e continuidade do projeto

**Data do registro:** 04/11/2026  
**Repositório:** https://github.com/felipevagner2017-cpu/MEtanoia  
**Branch de trabalho:** `work/master-restructure-phase1`  
**PR principal:** https://github.com/felipevagner2017-cpu/MEtanoia/pull/2  
**Versão em preparação:** Android `0.4.0` / `versionCode 4`

> Este documento registra o estado técnico conhecido e as solicitações de produto. Não declara homologação, publicação ou teste físico que ainda não tenham sido comprovados.

## 1. Direção do produto

MEtanoia é um aplicativo pessoal de vida intencional, independente do Restaura Lar. A proposta é reunir a vida em quatro dimensões integradas, com uma agenda transversal:

1. **Devocional / Espiritual** — estudos, reflexões, perguntas, contexto bíblico e aplicação das Escrituras.
2. **Matrimônio / Ingrid** — cuidado, presença, escuta, desafios práticos, atitudes e histórico.
3. **Profissional** — metas, projetos, competências, responsabilidades e resultados.
4. **Cotidiano** — agenda, calendário, tarefas, hábitos, diário, revisões e alertas.

Frase institucional: **“Uma mente renovada. Uma vida intencional.”** — Romanos 12:2.

Princípios: prioridade offline; dados pessoais locais; linguagem em português brasileiro; Escrituras como autoridade; não transformar ausência de registros em julgamento de negligência ou maturidade; não apresentar a aplicação como chatbot nem como substituta de aconselhamento pastoral, psicológico ou profissional.

## 2. Experiência e navegação solicitadas

A navegação principal deve funcionar como uma única jornada horizontal, em padrão premium inspirado em aplicativos bancários:

- **Mensagens e desafios**
- **Devocional**
- **Matrimônio**
- **Profissional**
- **Cotidiano**

A pessoa deve poder deslizar horizontalmente para a direita/esquerda entre as áreas, sem depender de cinco abas principais independentes. O primeiro espaço concentra visão do dia, mensagens/desafios e atalhos para as dimensões. Os atalhos devem abrir a página correspondente.

Dentro de **Cotidiano**, manter os subtópicos:
- Agenda
- Diário
- Revisões
- Alertas

Identidade visual aprovada para o refinamento: violeta profundo/azul-marinho, dourado champanhe e branco marfim; visual sóbrio, premium, legível e consistente. Referência de capa já aprovada: cérebro verde luminoso sobre fundo escuro, com acabamento dourado. Não inventar a aparência de Ingrid nem redesenhar o símbolo oficial sem a referência final aprovada.

## 3. Estado da alteração de navegação desta entrega

Foi alterado o código Android na branch de trabalho para substituir a navegação superior por um pager horizontal com cinco páginas: Mensagens e desafios, Devocional, Matrimônio, Profissional e Cotidiano. O primeiro painel mantém o resumo diário e atalhos para as dimensões. Cotidiano preserva suas quatro áreas internas.

Também foi atualizado o texto de apresentação do primeiro painel, o README e a versão Android para 0.4.0 / versionCode 4.

**Estado:** alteração registrada na branch; depende da execução completa do CI desta revisão. Não considerar homologada até a conclusão do build, testes unitários e testes instrumentados.

## 4. Arquitetura técnica conhecida

- Android nativo, Kotlin e Jetpack Compose.
- Namespace/applicationId: `br.com.metanoia`.
- compileSdk 35; minSdk 26; targetSdk 35.
- Java 17; Android Gradle Plugin 8.7.3; Kotlin 2.0.21; Gradle 8.9 no workflow.
- Jetpack Compose BOM 2024.12.01.
- Room 2.6.1 como persistência local.
- WorkManager 2.10.0 para trabalho agendado.
- Módulo Android: `:app`.
- Banco Room: `MetanoiaDatabase`, versão 3; migrações conhecidas 1→2 e 2→3.
- Não há Gradle Wrapper versionado no estado auditado; o CI instala Gradle 8.9.

## 5. Dados e privacidade

O projeto utiliza Room para agenda, diário, revisões, confissões opcionais e análises diárias. Os registros pessoais locais ficam no armazenamento privado do aplicativo no dispositivo. O código-fonte e os artefatos do GitHub **não contêm automaticamente o banco de dados privado do aparelho**.

Não apagar, recriar ou migrar o banco sem validar uma migração compatível e preservar os dados existentes. A versão 3 do Room é a última versão identificada no histórico auditado. Não há snapshot de schema exportado no estado conhecido.

A recuperação de dados privados de um dispositivo não é possível apenas baixando o repositório. Backup e restauração de dados precisam de implementação e validação próprias.

## 6. Agenda, calendário e notificações

Funcionalidades identificadas no código:
- cadastro de tarefas, compromissos e hábitos;
- calendário mensal navegável e seleção de datas;
- edição, conclusão e exclusão de itens;
- recorrências diária, dias úteis, semanal e mensal;
- lembretes individuais e notificações diárias configuráveis;
- atualização da agenda de tela de bloqueio a partir do Room;
- reagendamento após reinício, atualização do aplicativo, mudança de horário e fuso.

O código de cadastro de agenda cria lembrete padrão de 30 minutos antes quando a opção de lembrete está ativa. O scheduler também trabalha com lembretes individuais e notificações diárias configuráveis (07h, 12h e 18h, conforme preferências). Alarmes exatos dependem das permissões da versão Android; existe fallback inexato.

**Tela de bloqueio:** a implementação atual gera uma imagem de papel de parede estática com compromissos de hoje e próximos dias. Não é um widget interativo de tela de bloqueio. A aparência e atualização precisam de teste em aparelho físico.

**Recorrência:** hábitos recorrentes são materializados em ocorrências futuras; a edição/exclusão em série completa ainda precisa ser avaliada. Não presumir que alterar uma ocorrência altera toda a série.

## 7. Diário e inteligência contextual

- Diário livre por dia, com campo separado e opcional para confissão.
- Uma confissão não deve ser interpretada automaticamente como pecado; a palavra “confesso” isolada não basta para inferir culpa.
- Análise diária local e determinística, baseada em regras e palavras-chave.
- Temas reconhecidos incluem emoções, finanças, casamento/família, profissional, espiritual, erro explícito/arrependimento, medo, gratidão e perguntas.
- A análise considera texto do diário, confissão e títulos de compromissos pendentes do dia.
- Não existe integração com modelo generativo/IA externa no estado auditado. Não prometer aconselhamento automatizado ou exegese completa.

## 8. Dimensões já identificadas

**Devocional:** formulário para passagem/tema, observações, perguntas e aplicação; registros armazenados no diário/Room com dimensão espiritual.

**Matrimônio:** desafio prático rotativo por dia do ano, campo para registrar atitude e histórico. O avatar exibido no Android ainda é um marcador temporário com a letra “I”; a imagem 3D final depende da referência aprovada.

**Profissional:** registro de meta/projeto e histórico em formulário genérico. Indicadores estruturados de progresso, marcos, métricas e acompanhamento ainda precisam ser implementados.

**Cotidiano:** agenda, diário, revisões semanais/mensais e preferências de notificações.

## 9. Conta e acesso da Ingrid

No código auditado não foi identificada uma camada de autenticação/contas de usuários com redefinição de senha. O aplicativo nativo é centrado em dados locais no dispositivo. Portanto, não existe fluxo comprovado de “esqueci minha senha” da Ingrid nesta versão. Não armazenar senha em texto puro nem tentar recuperar uma senha antiga. Uma eventual autenticação multiusuário e redefinição segura precisam ser desenhadas como funcionalidade separada, preservando os dados locais existentes.

## 10. PWA e Cloudflare

O repositório contém uma estrutura web/PWA em `web/`, manifest, service worker, ícones, página de downloads e configuração Wrangler. O workflow `.github/workflows/deploy-cloudflare.yml` foi configurado para executar a partir da branch `main` e depende dos secrets de repositório `CLOUDFLARE_API_TOKEN` e `CLOUDFLARE_ACCOUNT_ID`.

URL pretendida anteriormente: `https://metonoia.felipevagner2017.workers.dev`. **Publicação atual não confirmada.** Não declarar a PWA implantada sem uma execução de deploy bem-sucedida e verificação da URL.

A compilação Android e o deploy Cloudflare são fluxos separados. A branch de trabalho não deve ser considerada publicada automaticamente.

## 11. Histórico GitHub conhecido

- Branch principal: `main`.
- Branch de trabalho: `work/master-restructure-phase1`.
- Branch adicional: `feature/lockscreen-agenda-polish`.
- Backups: `backup/pre-master-restructure-2026-11-04` e `backup/pre-lockscreen-delivery-2026-11-04`.
- PR #1: agenda premium na tela de bloqueio e entrega de arquivos.
- PR #2: quatro dimensões e identidade violeta premium.
- Commit-base da primeira etapa da PR #2 auditada: `c1291a344c220a699228c8998b3438e3dc2cc31d`.
- As alterações de pager, versão e documentação desta entrega foram registradas posteriormente na branch de trabalho.

Não mesclar a PR na `main` sem verificar o CI completo, os arquivos alterados e a preservação do histórico.

## 12. Validação e artefatos

Workflow Android: `.github/workflows/android.yml`.

No run anterior `41845634725`:
- Build APK: sucesso.
- Testes unitários: sucesso.
- Empacotamento de código-fonte e APK: sucesso.
- Testes instrumentados Room no emulador: cancelados; portanto a validação total não foi concluída.
- O job instrumentado foi solicitado para nova execução; verificar o resultado atualizado antes de declarar homologação.

Os artefatos gerados por aquele run pertencem ao commit anterior e **não incluem as alterações posteriores de navegação/versão**. Não apresentar o APK antigo como se fosse a versão 0.4.0.

Não foi realizado teste físico em aparelho Android. A instalação, permissões de notificação, alarmes e papel de parede da tela de bloqueio precisam de homologação em dispositivo.

## 13. Pendências priorizadas

1. Concluir CI da versão 0.4.0: assembleDebug, testes unitários e instrumentados.
2. Corrigir qualquer falha e repetir CI até sucesso completo.
3. Conferir navegação horizontal e layout em diferentes tamanhos de tela.
4. Refinar a primeira página premium (Mensagens e desafios) e validar gestos/atalhos.
5. Validar lembretes de todos os compromissos, permissões e reagendamento.
6. Homologar papel de parede da tela de bloqueio em aparelho físico; não chamar de widget.
7. Implementar backup/restauração do banco privado sem perda de dados.
8. Avaliar edição/exclusão de séries recorrentes.
9. Definir autenticação e recuperação segura para Ingrid, caso multiusuário seja requisito confirmado.
10. Aplicar o avatar final apenas com referência aprovada.
11. Estruturar metas e métricas profissionais.
12. Revisar PWA offline e configurar/verificar secrets Cloudflare; publicar somente após deploy confirmado.
13. Gerar APK, ZIP de fonte e relatório de hashes a partir do commit efetivamente homologado.
14. Manter `main` protegida até aprovação da revisão.

## 14. Critério de entrega

Uma versão só pode ser declarada entregue quando:
- o commit e a branch exatos estiverem identificados;
- o build APK tiver concluído com sucesso;
- os testes unitários e instrumentados tiverem concluído com sucesso;
- o APK e o ZIP-fonte forem gerados a partir do mesmo commit;
- os hashes forem registrados;
- as limitações de teste físico e deploy forem declaradas;
- a publicação Cloudflare, se solicitada, tiver execução e URL verificadas;
- os dados locais existentes forem preservados.

**Regra de integridade:** não declarar como realizado algo que esteja apenas planejado, em execução, cancelado ou não verificado.
