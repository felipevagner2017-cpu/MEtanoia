package br.com.metanoia

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.metanoia.data.AgendaItem
import br.com.metanoia.data.DailyAnalysisEntry
import br.com.metanoia.data.JournalEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DailyWord(
    val title: String,
    val reference: String,
    val context: String,
    val reflection: String,
    val challenge: String
)

private val dailyWords = listOf(
    DailyWord(
        "Renovar a mente", "Romanos 12:2",
        "Paulo apresenta a transformação da vida como resultado de uma mente renovada, em contraste com a conformidade aos padrões deste século.",
        "Observe quais ideias têm orientado suas decisões. A renovação bíblica não é pensamento positivo: é permitir que a verdade de Deus reoriente escolhas concretas.",
        "Identifique um pensamento que tem conduzido uma atitude sua. Confronte-o com o ensino de Romanos 12:2 e escolha uma resposta prática coerente com a vontade de Deus."
    ),
    DailyWord(
        "Guardar o coração", "Provérbios 4:23",
        "No contexto dos conselhos de sabedoria, guardar o coração significa vigiar as fontes interiores que orientam palavras, desejos e caminhos.",
        "Preste atenção ao que tem alimentado sua atenção e influenciado suas reações. Sabedoria também envolve escolher o que merece entrar e permanecer.",
        "Afaste hoje uma influência que alimenta uma reação destrutiva e substitua-a por uma prática que favoreça sabedoria e domínio próprio."
    ),
    DailyWord(
        "Praticar a Palavra", "Tiago 1:22–25",
        "Tiago adverte contra ouvir a mensagem sem colocá-la em prática. A escuta verdadeira aparece na obediência perseverante.",
        "Não transforme conhecimento bíblico em acúmulo de informação. Pergunte qual atitude precisa mudar à luz do texto que você já compreendeu.",
        "Escolha um ensinamento bíblico que você conhece e transforme-o em uma atitude observável ainda hoje."
    ),
    DailyWord(
        "Perseverar no bem", "Gálatas 6:9–10",
        "Paulo encoraja a comunidade a não se cansar de fazer o bem, relacionando perseverança com a oportunidade de servir ao próximo.",
        "Nem todo fruto aparece imediatamente. Fidelidade também é continuar fazendo o que é correto sem depender de reconhecimento instantâneo.",
        "Faça uma ação de bem que você vem adiando. Defina quando irá realizá-la e cumpra sem esperar aplausos."
    ),
    DailyWord(
        "Edificar com palavras", "Efésios 4:29",
        "No ensino sobre a nova vida em Cristo, Paulo orienta que a fala deixe de corromper e passe a contribuir para a edificação de quem ouve.",
        "Antes de responder, considere se suas palavras são verdadeiras, necessárias e adequadas à situação. Firmeza não exige agressividade.",
        "Em uma conversa de hoje, substitua uma resposta impulsiva por palavras honestas, respeitosas e construtivas."
    ),
    DailyWord(
        "Cultivar bons pensamentos", "Filipenses 4:8–9",
        "Paulo orienta a comunidade a considerar o que é verdadeiro, justo, puro e digno de louvor, e a praticar o que aprendeu.",
        "A atenção molda a disposição e as escolhas. Examine o conteúdo que ocupa sua mente e aproxime pensamento e prática dos valores do evangelho.",
        "Reserve alguns minutos para identificar um pensamento recorrente. Pergunte se ele é verdadeiro e digno; depois escolha uma ação coerente com essa avaliação."
    ),
    DailyWord(
        "Entregar o caminho", "Salmo 37:3–7",
        "O salmo chama à confiança no Senhor e à prática do bem em meio à aparente prosperidade da injustiça. É um convite à fidelidade paciente.",
        "Confiar em Deus não significa abandonar responsabilidades. Significa agir com integridade e não permitir que a ansiedade determine seus caminhos.",
        "Defina uma responsabilidade que está ao seu alcance, execute o próximo passo possível e entregue a Deus aquilo que não está sob seu controle."
    )
)

@Composable
internal fun DailyJourneyPage(
    analyses: List<DailyAnalysisEntry>
) {
    val today = LocalDate.now()
    val word = dailyWords[(today.dayOfYear - 1) % dailyWords.size]
    val todayAnalysis = analyses.firstOrNull { it.dayKey == today.toString() }
    val personalizedReference = todayAnalysis?.scriptureReference?.takeIf { it.isNotBlank() }
    val reference = personalizedReference ?: word.reference
    val context = if (personalizedReference != null) todayAnalysis?.scriptureContext?.takeIf { it.isNotBlank() } ?: word.context else word.context
    val reflection = if (personalizedReference != null) context else word.reflection
    val challenge = todayAnalysis?.nextStep?.takeIf { it.isNotBlank() } ?: word.challenge
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 18.dp).testTag("daily-journey-list"),
        contentPadding = PaddingValues(top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Jornada diária", color = GoldBright, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(dateLabel, color = Ivory.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
                Text("Uma mente renovada. Uma vida intencional.", color = GoldChampagne, style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = VioletPrimary)
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(VioletPrimary, DeepViolet, VioletSecondary)))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("PALAVRA DO DIA", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text(if (personalizedReference != null) "Palavra aplicada ao seu registro" else word.title, color = GoldBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(reference, color = Ivory, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(context, color = Ivory.copy(alpha = .92f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("REFLEXÃO", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(reflection, color = Ivory, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = VioletSurface)
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DESAFIO DE HOJE", color = GoldBright, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Text(challenge, color = Ivory, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (todayAnalysis != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("APLICAÇÃO A PARTIR DO SEU REGISTRO", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(todayAnalysis.attention, color = Ivory, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(modifier: Modifier, value: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = accent, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(label, color = Ivory.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
        }
    }
}

@Composable
private fun DimensionAccessCard(modifier: Modifier, number: String, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.heightIn(min = 158.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(34.dp).background(Brush.linearGradient(listOf(GoldBright, GoldChampagne)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(number, color = DeepViolet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Text(title, color = GoldChampagne, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(description, color = Ivory.copy(alpha = .90f), style = MaterialTheme.typography.bodySmall)
            Text("ABRIR ÁREA  →", color = GoldBright, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        }
    }
}

@Composable
internal fun SpiritualDimensionPage(entries: List<JournalEntry>, onSave: (String, String) -> Unit) {
    DimensionRecordPage(
        eyebrow = "DIMENSÃO 01",
        title = "Devocional",
        description = "Devocional pastoral com estudo bíblico profundo, exegese responsável, contexto histórico-teológico e formação do caráter à luz das Escrituras.",
        promptTitle = "Texto bíblico ou tema pastoral",
        promptBody = "Registre a passagem, contexto histórico, observações do texto, palavras-chave, perguntas teológicas, interpretação e aplicação ao pastoreio.",
        saveLabel = "Salvar estudo bíblico",
        focusAreas = listOf(
            "Estudos bíblicos aprofundados e exegese de passagens",
            "Contextualização histórica, cultural e teológica",
            "Estudo de livros, capítulos e textos bíblicos",
            "Reflexões devocionais diárias",
            "Aplicação prática das Escrituras ao pastoreio",
            "Princípios, integridade e formação do caráter",
            "Crescimento e maturidade espiritual",
            "Histórico organizado de estudos",
            "Acompanhamento da jornada espiritual"
        ),
        entries = entries,
        onSave = onSave
    )
}

@Composable
internal fun ProfessionalDimensionPage(entries: List<JournalEntry>, onSave: (String, String) -> Unit) {
    DimensionRecordPage(
        eyebrow = "DIMENSÃO 03",
        title = "Profissional",
        description = "Uma jornada de desenvolvimento profissional: clareza de objetivos, coragem para enfrentar desafios e evolução por meio de ações verificáveis.",
        promptTitle = "Meta, projeto ou desafio profissional",
        promptBody = "Defina o resultado desejado, o obstáculo atual, o próximo passo concreto, o prazo, os recursos e como medirá o progresso.",
        saveLabel = "Registrar plano profissional",
        focusAreas = listOf(
            "Metas e resultados mensuráveis",
            "Projetos, responsabilidades e prioridades",
            "Planejamento e organização da execução",
            "Desenvolvimento de competências",
            "Enfrentamento de desafios e tomada de decisão",
            "Resultados, conquistas e aprendizados",
            "Histórico de evolução profissional",
            "Próxima ação prática e prazo"
        ),
        entries = entries,
        onSave = onSave
    )
}

@Composable
internal fun MatrimonialDimensionPage(entries: List<JournalEntry>, onSave: (String, String) -> Unit) {
    val challenges = listOf(
        "Reserve quinze minutos de atenção integral, sem celular, para ouvir e compartilhar o dia.",
        "Expresse gratidão por uma atitude concreta que você percebeu nela.",
        "Pergunte como pode tornar o dia dela mais leve e escute sem interromper.",
        "Planeje um momento simples a dois e proteja esse tempo na agenda.",
        "Ofereça ajuda prática em uma responsabilidade que normalmente fica concentrada nela."
    )
    // O histórico evita repetir imediatamente desafios já registrados; não presume emoções ou fatos de Ingrid.
    val challenge = challenges.firstOrNull { candidate ->
        entries.take(5).none { it.content.contains(candidate, ignoreCase = true) }
    } ?: challenges.first()
    var note by remember(LocalDate.now()) { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DimensionHero(
                "DIMENSÃO 02",
                "Matrimônio / Ingrid",
                "Cuidado que se transforma em presença, escuta e parceria."
            )
        }
        item {
            JourneyProgressCard(
                totalRecords = entries.size,
                latestDate = entries.maxByOrNull { it.createdAt }?.dayKey.orEmpty(),
                title = "ACOMPANHAMENTO MATRIMONIAL",
                description = "A evolução é observada pelas atitudes registradas, não por suposições sobre os sentimentos de Ingrid."
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = VioletSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.ingrid_avatar),
                            contentDescription = "Avatar oficial de Ingrid",
                            modifier = Modifier.size(width = 116.dp, height = 142.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Text("INGRID", color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Matrimônio", color = Ivory, style = MaterialTheme.typography.bodyMedium)
                            Text("Avatar oficial preservado a partir da imagem aprovada.", color = Ivory.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ESTUDOS E REFLEXÕES DO CASAMENTO", color = GoldBright, fontWeight = FontWeight.Bold)
                    Text("Temas para estudo e conversa: fidelidade e confiança; pornografia e seus impactos no vínculo; finanças e transparência; seduções e limites; comunicação; conflitos; perdão; intimidade; parceria nas responsabilidades.", color = Ivory, style = MaterialTheme.typography.bodyMedium)
                    Text("CUIDADO DO DIA", color = GoldBright, fontWeight = FontWeight.Bold)
                    Text("Presença vale mais do que uma atitude grandiosa. Escolha um gesto possível, sincero e atento.", color = Ivory)
                    Text("DESAFIO MATRIMONIAL", color = GoldBright, fontWeight = FontWeight.Bold)
                    Text(challenge, color = Ivory)
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Como você pretende colocar isso em prática?") },
                        minLines = 3
                    )
                    Button(
                        onClick = {
                            onSave("Desafio matrimonial · ${LocalDate.now()}", "Desafio: $challenge\nAtitude registrada: ${note.trim()}")
                            note = ""
                        },
                        enabled = note.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Registrar atitude") }
                }
            }
        }
        item { Text("HISTÓRICO DE CUIDADO E PRESENÇA", color = GoldChampagne, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) item { Text("As atitudes registradas aparecerão aqui.", color = Ivory) }
        items(entries.sortedByDescending { it.createdAt }, key = { it.id }) { entry ->
            RecordCard(entry)
        }
    }
}

@Composable
private fun DimensionRecordPage(
    eyebrow: String,
    title: String,
    description: String,
    promptTitle: String,
    promptBody: String,
    saveLabel: String,
    entries: List<JournalEntry>,
    onSave: (String, String) -> Unit,
    focusAreas: List<String> = emptyList()
) {
    var recordTitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var contextNotes by remember { mutableStateOf("") }
    var analysisNotes by remember { mutableStateOf("") }
    var applicationNotes by remember { mutableStateOf("") }
    var obstacle by remember { mutableStateOf("") }
    var nextAction by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var successMeasure by remember { mutableStateOf("") }
    val isSpiritual = eyebrow.contains("01")
    val isProfessional = eyebrow.contains("03")
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DimensionHero(eyebrow, title, description) }
        item {
            JourneyProgressCard(
                totalRecords = entries.size,
                latestDate = entries.maxByOrNull { it.createdAt }?.dayKey.orEmpty(),
                title = if (eyebrow.contains("03")) "EVOLUÇÃO PROFISSIONAL" else "JORNADA DE DESENVOLVIMENTO",
                description = "Acompanhe os registros, decisões, aprendizados e próximos passos documentados."
            )
        }
        if (focusAreas.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = VioletSurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("TRILHA DE DESENVOLVIMENTO", color = GoldBright, fontWeight = FontWeight.Bold)
                        focusAreas.forEach { area ->
                            Text("• $area", color = Ivory, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NOVO REGISTRO", color = GoldChampagne, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = recordTitle, onValueChange = { recordTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text(promptTitle) }, singleLine = true)
                    if (isSpiritual) {
                        OutlinedTextField(value = contextNotes, onValueChange = { contextNotes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Contexto histórico, cultural e literário") }, minLines = 3)
                        OutlinedTextField(value = analysisNotes, onValueChange = { analysisNotes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Observações do texto, estrutura e palavras-chave") }, minLines = 3)
                        OutlinedTextField(value = applicationNotes, onValueChange = { applicationNotes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Interpretação, fundamento bíblico e aplicação pastoral") }, minLines = 4)
                    } else if (isProfessional) {
                        OutlinedTextField(value = obstacle, onValueChange = { obstacle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Obstáculo ou desafio atual") }, minLines = 2)
                        OutlinedTextField(value = nextAction, onValueChange = { nextAction = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Próxima ação concreta") }, minLines = 2)
                        OutlinedTextField(value = deadline, onValueChange = { deadline = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Prazo para executar") }, singleLine = true)
                        OutlinedTextField(value = successMeasure, onValueChange = { successMeasure = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Como vou medir o resultado?") }, minLines = 2)
                    } else {
                        OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text(promptBody) }, minLines = 5)
                    }
                    Button(
                        onClick = {
                            val content = when {
                                isSpiritual -> "Contexto: $contextNotes\nObservações: $analysisNotes\nInterpretação e aplicação pastoral: $applicationNotes"
                                isProfessional -> "Meta ou projeto: $body\nObstáculo: $obstacle\nPróxima ação: $nextAction\nPrazo: $deadline\nMedida de resultado: $successMeasure"
                                else -> body
                            }
                            onSave(recordTitle, content)
                            recordTitle = ""; body = ""; contextNotes = ""; analysisNotes = ""; applicationNotes = ""
                            obstacle = ""; nextAction = ""; deadline = ""; successMeasure = ""
                        },
                        enabled = recordTitle.isNotBlank() && when {
                            isSpiritual -> contextNotes.isNotBlank() || analysisNotes.isNotBlank() || applicationNotes.isNotBlank()
                            isProfessional -> obstacle.isNotBlank() || nextAction.isNotBlank() || deadline.isNotBlank() || successMeasure.isNotBlank()
                            else -> body.isNotBlank()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(saveLabel) }
                }
            }
        }
        item { Text("HISTÓRICO DA DIMENSÃO", color = GoldChampagne, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) item { Text("Seus registros serão preservados e apresentados nesta área.", color = Ivory) }
        items(entries.sortedByDescending { it.createdAt }, key = { it.id }) { entry -> RecordCard(entry) }
    }
}

@Composable
private fun DimensionHero(eyebrow: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = VioletPrimary)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(VioletPrimary, DeepViolet, VioletSecondary)))
        ) {
        Text(
            "M",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 2.dp),
            color = GoldBright.copy(alpha = .09f),
            fontSize = 112.sp,
            fontWeight = FontWeight.Black
        )
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(eyebrow, color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text(title, color = GoldBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, color = Ivory.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.width(38.dp).height(3.dp).background(GoldChampagne, CircleShape))
        }
        }
    }
}

@Composable
private fun JourneyProgressCard(totalRecords: Int, latestDate: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VioletSurface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("$totalRecords registro(s) documentado(s)", color = Ivory, style = MaterialTheme.typography.titleMedium)
            Text(
                if (latestDate.isBlank()) "O acompanhamento começa quando o primeiro registro for salvo."
                else "Registro mais recente: $latestDate",
                color = Ivory.copy(alpha = .9f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(description, color = Ivory.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordCard(entry: JournalEntry) {
    Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.title, color = GoldChampagne, fontWeight = FontWeight.Bold)
            Text(entry.content, color = Ivory)
            Text(
                entry.dayKey.ifBlank { Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() },
                color = Ivory.copy(alpha = .76f), style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun dimensionName(value: br.com.metanoia.data.LifeDimension): String = when (value) {
    br.com.metanoia.data.LifeDimension.SPIRITUAL -> "Devocional"
    br.com.metanoia.data.LifeDimension.MATRIMONIAL -> "Matrimônio"
    br.com.metanoia.data.LifeDimension.PROFESSIONAL -> "Profissional"
    br.com.metanoia.data.LifeDimension.INDIVIDUAL -> "Individual"
    br.com.metanoia.data.LifeDimension.ROUTINE -> "Cotidiano"
}
