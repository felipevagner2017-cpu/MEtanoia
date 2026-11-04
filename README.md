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

A inteligência do MEtanoia agora tem arquitetura híbrida: a leitura contextual local permanece disponível offline; quando há internet, a assistente integrada pode consultar o modelo generativo Workers AI por um Worker protegido. O usuário escolhe o foco e autoriza explicitamente o envio de registros recentes relevantes. O Worker não grava conversas. A credencial não fica no código: Android a cifra com Android Keystore e a PWA a mantém somente na sessão do navegador. A configuração única do segredo METANOIA_AI_TOKEN está documentada em docs/AI_HYBRID.md.

## Orientação das dimensões

A área devocional foi estruturada para estudos aprofundados, exegese, contexto histórico-cultural-teológico, interpretação, aplicação pastoral, caráter e histórico de estudos. A área matrimonial inclui trilhas de conversa sobre fidelidade, pornografia, finanças, seduções, comunicação, conflitos, perdão e intimidade. A área profissional estrutura metas, desafios, planejamento, competências e resultados.

O analisador contextual permanece determinístico e offline. Ele não deve ser descrito como terapeuta, pastor ou coach humano, nem como inteligência generativa: para respostas abertas, exegese gerada ou aconselhamento conversacional real será necessária uma integração explícita com um modelo, com controles de privacidade e segurança.

## Limites visuais conhecidos

A área matrimonial contém um marcador visual temporário para o avatar. A imagem 3D final depende da referência visual aprovada para não inventar a aparência de Ingrid. O símbolo oficial deve ser mantido sem alteração geométrica.

## Compilação e validação

O workflow `.github/workflows/android.yml` executa `gradle assembleDebug`, `gradle testDebugUnitTest` e testes instrumentados Room em emulador Android API 35. A versão de desenvolvimento atual é 0.4.1 (versionCode 5). Um APK só deve ser considerado validado quando o workflow da revisão correspondente estiver concluído com sucesso, incluindo os testes instrumentados no emulador.
