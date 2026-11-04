# MEtanoia

**Uma mente renovada. Uma vida intencional.** (Romanos 12:2)

Aplicativo Android nativo, Kotlin + Jetpack Compose, com prioridade offline e armazenamento local em Room. O projeto é independente do Restaura Lar.

## Organização do produto

O aplicativo organiza a jornada em quatro dimensões integradas:

1. **Devocional / Espiritual** — estudos, reflexões, perguntas e aplicação das Escrituras.
2. **Matrimônio / Ingrid** — cuidado, desafios práticos, atitudes registradas e histórico.
3. **Profissional** — metas, projetos, competências e resultados.
4. **Cotidiano** — agenda, calendário, tarefas, hábitos, diário unificado, confissão opcional, revisões e alertas.

O painel inicial apresenta um resumo do dia e atalhos para as quatro dimensões. Os registros das dimensões reutilizam a tabela de diário existente no Room; não criam um segundo banco de dados.

## Identidade visual

Design tokens centralizados em `MetanoiaDesignSystem.kt`:
- Violeta principal: `#35105F`
- Violeta profundo: `#1B092B`
- Violeta de superfície: `#2A1243`
- Dourado champanhe: `#D9BC83`
- Dourado luminoso: `#F0D9A1`
- Branco marfim: `#F3F0E7`

A escala tipográfica é centralizada. Títulos e indicadores usam dourado; descrições e conteúdo usam branco marfim.

## Dados e agenda

Room mantém agenda, diário, revisões, confissões e análises. A migração mais recente registrada é a versão 3. A agenda usa AlarmManager, com lembretes diários configuráveis às 07h, 12h e 18h e alarmes individuais configuráveis para 2h, 1h e 30min antes. Alarmes exatos dependem da permissão do Android; há fallback inexato. A tela de bloqueio é gerada a partir dos compromissos do Room no aplicativo Android.

## Inteligência contextual

O analisador diário atual é local, baseado em regras explícitas e executado offline. Ele identifica temas e dimensões a partir do texto informado, integra títulos de compromissos e evita tratar a palavra “confesso” isoladamente como admissão de pecado. Não é um modelo generativo conectado nem substitui aconselhamento pastoral, psicológico ou profissional.

## Limites visuais conhecidos

A área matrimonial contém um marcador visual temporário para o avatar. A imagem 3D final depende da referência visual aprovada para não inventar a aparência de Ingrid. O símbolo oficial deve ser mantido sem alteração geométrica.

## Compilação e validação

O workflow `.github/workflows/android.yml` executa `gradle assembleDebug`, `gradle testDebugUnitTest` e testes instrumentados Room em emulador Android API 35. Um APK só deve ser considerado validado quando o workflow da revisão correspondente estiver concluído com sucesso.
