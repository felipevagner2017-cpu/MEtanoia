# MEtanoia

**A transformação é uma construção diária.**  
Uma mente renovada. Uma vida intencional. (Romanos 12:2)

Aplicativo Android nativo, com prioridade offline e armazenamento local. O projeto é independente do Restaura Lar.

## Diretrizes
- Kotlin + Jetpack Compose
- Room como banco de dados local
- Identidade visual: azul-marinho `#102542`, branco `#FFFFFF`, dourado `#C5A15A`
- Cinco dimensões: espiritual, matrimonial, profissional, individual e agenda/rotina
- Escritura como autoridade; a aplicação não é chatbot nem conselheiro espiritual automatizado
- Ausência de registros nunca deve ser interpretada como negligência ou falta de maturidade

## Escopo do produto
Agenda diária/semanal/mensal; tarefas, compromissos, hábitos recorrentes e lembretes; diário e reflexões; revisões semanais e mensais com histórico.

## Estado
Fundação do projeto iniciada. Esta primeira estrutura ainda precisa de implementação incremental, compilação, testes em emulador/dispositivo e homologação. Não considerar o APK homologado até que o workflow Android seja concluído com sucesso.

## Compilação
O workflow em `.github/workflows/android.yml` prepara Gradle e executa `assembleDebug` e testes unitários em cada push.
