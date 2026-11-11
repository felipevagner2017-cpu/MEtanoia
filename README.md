# MEtanoia

**Uma mente renovada. Uma vida intencional.** (Romanos 12:2)

Aplicativo Android nativo, Kotlin + Jetpack Compose, com prioridade offline e armazenamento local em Room. O projeto é independente do Restaura Lar.

## Organização do produto

O aplicativo organiza a jornada em quatro dimensões integradas:

1. **Devocional / Espiritual** — estudos, reflexões, perguntas e aplicação das Escrituras.
2. **Matrimônio / Ingrid** — cuidado, desafios práticos, atitudes registradas e histórico.
3. **Profissional** — metas, projetos, competências e resultados.
4. **Cotidiano** — agenda, calendário, tarefas, hábitos, diário unificado, confissão opcional, revisões e alertas.

A navegação principal é uma única jornada horizontal com quatro painéis, nesta ordem: **Devoção**, **Matrimônio**, **Profissional** e **Cotidiano**. Devoção é a primeira tela e concentra a trilha de formação bíblica e pastoral. Matrimônio reúne cuidado diário, desafios, estudos temáticos e histórico; os desafios evitam repetições recentes com base nos registros. Profissional organiza metas, obstáculos, competências, entregas e próximos passos. Cotidiano concentra Agenda, Diário, Revisões e Alertas. Os registros das dimensões reutilizam a tabela de diário existente no Room; não criam um segundo banco de dados.

## Identidade visual

Design tokens centralizados em `MetanoiaDesignSystem.kt`:
- Violeta principal: `#35105F`
- Violeta profundo: `#1B092B`
- Violeta secundário: `#54218A`
- Violeta de superfície: `#2A1243`
- Dourado champanhe: `#D9BC83`
- Dourado luminoso: `#F0D9A1`
- Branco marfim: `#F3F0E7`

A escala tipográfica é centralizada. Títulos e indicadores usam dourado; descrições e conteúdo usam branco marfim.

## Dados e agenda

Room mantém agenda, diário, revisões, confissões e análises. A migração mais recente registrada é a versão 3. A agenda usa AlarmManager, com lembretes diários configuráveis às 07h, 12h e 18h e alarmes individuais configuráveis para 2h, 1h e 30min antes. As notificações de compromissos declaram visibilidade pública para permitir sua apresentação na tela bloqueada; o Android ainda exige que notificações e conteúdo na tela de bloqueio estejam permitidos nas configurações do aparelho. A tela de bloqueio também recebe um papel de parede gerado a partir dos compromissos do Room; esse papel de parede é estático, não um widget interativo.

## Inteligência contextual

O Worker Cloudflare utiliza Workers AI e agora contém uma base de API D1 para contas, sessões, dispositivos e sincronização incremental. O cadastro é protegido por convite; senhas usam PBKDF2-SHA256, sessões são armazenadas por hash, e operações de sincronização usam identificadores idempotentes e revisões otimistas. A API de IA exige sessão autenticada e aplica uma cota inicial de 30 solicitações por usuário/dia.

**Estado de integração:** o backend D1 está preparado no código, mas ainda depende da criação do banco remoto exclusivo, da aplicação das migrations e do binding `DB`. Android e PWA ainda não foram conectados à conta/sincronização compartilhada; Room e localStorage continuam locais. O histórico de IA ainda não é persistido. Não inserir credenciais de IA no aplicativo.

## Orientação das dimensões

A área devocional foi estruturada para estudos aprofundados, exegese, contexto histórico-cultural-teológico, interpretação, aplicação pastoral, caráter e histórico de estudos. A área matrimonial inclui trilhas de conversa sobre fidelidade, pornografia, finanças, seduções, comunicação, conflitos, perdão e intimidade. A área profissional estrutura metas, desafios, planejamento, competências e resultados.

O analisador contextual determinístico permanece como alternativa local quando não há conexão. A assistente generativa conectada já está integrada ao Android e à PWA por meio do Worker Cloudflare; seu uso exige autorização explícita para o envio dos registros selecionados. A IA não deve ser descrita como terapeuta, pastor ou coach humano.

## Limites visuais conhecidos

A área matrimonial contém um marcador visual temporário para o avatar. A imagem 3D final depende da referência visual aprovada para não inventar a aparência de Ingrid. O símbolo oficial deve ser mantido sem alteração geométrica.

## Compilação e validação

O workflow `.github/workflows/android.yml` executa `gradle assembleDebug`, `gradle testDebugUnitTest` e testes instrumentados Room em emulador Android API 35. A candidato de desenvolvimento atual é 0.5.0 (versionCode 6). Um APK só deve ser considerado validado quando o workflow da revisão correspondente estiver concluído com sucesso, incluindo os testes instrumentados no emulador.
