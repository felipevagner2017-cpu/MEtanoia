package br.com.metanoia

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

@Composable
internal fun DashboardPage(
    agenda: List<AgendaItem>,
    journal: List<JournalEntry>,
    analyses: List<DailyAnalysisEntry>,
    onNavigate: (String) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    val todayItems = agenda.filter { it.scheduledAt in start..end }.sortedBy { it.scheduledAt }
    val pending = todayItems.count { !it.completed }
    val completed = todayItems.count { it.completed }
    val todayAnalysis = analyses.firstOrNull { it.dayKey == today.toString() }
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = VioletPrimary)
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(VioletPrimary, Color(0xFF241039), Color(0xFF4B3264))))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("METANOIA  /  PAINEL PESSOAL", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Mensagens e desafios", color = Ivory, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(dateLabel, color = GoldBright, style = MaterialTheme.typography.titleMedium)
                    Text("Uma mente renovada. Uma vida intencional.", color = Ivory.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(34.dp).height(3.dp).background(GoldChampagne, CircleShape))
                        Text("ROMANOS 12:2", color = GoldChampagne, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetricCard(Modifier.weight(1f), pending.toString(), "PENDENTES HOJE", GoldBright)
                SummaryMetricCard(Modifier.weight(1f), completed.toString(), "CONCLUÍDOS HOJE", Color(0xFF9ED6B5))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SUAS QUATRO DIMENSÕES", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Escolha onde deseja investir sua atenção.", color = Ivory.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DimensionAccessCard(Modifier.weight(1f), "01", "DEVOCIONAL", "Palavra, formação e vida com Deus.") { onNavigate("Devocional") }
                DimensionAccessCard(Modifier.weight(1f), "02", "MATRIMÔNIO", "Presença, cuidado e parceria.") { onNavigate("Matrimônio") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DimensionAccessCard(Modifier.weight(1f), "03", "PROFISSIONAL", "Metas, projetos e realizações.") { onNavigate("Profissional") }
                DimensionAccessCard(Modifier.weight(1f), "04", "COTIDIANO", "Agenda, hábitos e organização.") { onNavigate("Cotidiano") }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LEITURA DO DIA", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (todayAnalysis != null) {
                        Text(todayAnalysis.emotionalState, color = Ivory, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(todayAnalysis.nextStep, color = Ivory.copy(alpha = .84f), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Seu dia ainda está em construção.", color = Ivory, style = MaterialTheme.typography.titleMedium)
                        Text("Registre sua reflexão no Diário para reunir acontecimentos, sentimentos e próximos passos em uma visão integrada.", color = Ivory.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${journal.count { it.dayKey == today.toString() }} registro(s) feitos hoje", color = GoldBright, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("AGENDA DE HOJE", color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (todayItems.isEmpty()) "Seu dia está livre de registros." else "$pending pendente(s) · $completed concluído(s)", color = Ivory.copy(alpha = .76f), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onNavigate("Cotidiano") }) {
                    Text("VER AGENDA  →", color = GoldBright, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (todayItems.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                    Text("Adicione um compromisso para visualizar seus próximos horários aqui.", modifier = Modifier.padding(16.dp), color = Ivory.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(todayItems.take(5), key = { "today-${it.id}" }) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Instant.ofEpochMilli(item.scheduledAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.padding(top = 5.dp).width(24.dp).height(2.dp).background(GoldChampagne, CircleShape))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, color = Ivory, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(if (item.completed) "Concluído" else "Previsto · ${dimensionName(item.dimension)}", color = Ivory.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.completed) Text("✓", color = Color(0xFF9ED6B5), fontWeight = FontWeight.Bold)
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
            Text(description, color = Ivory.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
            Text("ABRIR ÁREA  →", color = GoldBright, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
        }
    }
}

@Composable
internal fun SpiritualDimensionPage(entries: List<JournalEntry>, onSave: (String, String) -> Unit) {
    DimensionRecordPage(
        eyebrow = "DIMENSÃO 01",
        title = "Devocional / Espiritual",
        description = "Formação fundamentada nas Escrituras: texto, contexto histórico, sentido teológico e aplicação responsável à vida.",
        promptTitle = "Passagem ou tema estudado",
        promptBody = "Registre a passagem, o contexto observado, suas perguntas e a aplicação que deseja praticar.",
        saveLabel = "Salvar estudo ou reflexão",
        entries = entries,
        onSave = onSave
    )
}

@Composable
internal fun ProfessionalDimensionPage(entries: List<JournalEntry>, onSave: (String, String) -> Unit) {
    DimensionRecordPage(
        eyebrow = "DIMENSÃO 03",
        title = "Profissional",
        description = "Transforme objetivos em entregas: metas, projetos, responsabilidades, competências e resultados verificáveis.",
        promptTitle = "Meta ou projeto",
        promptBody = "Descreva o resultado desejado, o próximo passo, o prazo e os recursos necessários.",
        saveLabel = "Registrar plano profissional",
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
    val challenge = challenges[(LocalDate.now().dayOfYear - 1) % challenges.size]
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
            Card(colors = CardDefaults.cardColors(containerColor = VioletSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(124.dp).background(Brush.verticalGradient(listOf(GoldBright, GoldChampagne)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("I", color = DeepViolet, fontSize = 54.sp, fontWeight = FontWeight.Light) }
                    Text("INGRID", color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Espaço reservado para o avatar premium aprovado.", color = Ivory, style = MaterialTheme.typography.bodySmall)
                    Text("A imagem 3D definitiva será aplicada a partir da referência visual, sem inventar a aparência de Ingrid.", color = Ivory.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CUIDADO DO DIA", color = GoldChampagne, fontWeight = FontWeight.Bold)
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
    onSave: (String, String) -> Unit
) {
    var recordTitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DimensionHero(eyebrow, title, description) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NOVO REGISTRO", color = GoldChampagne, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = recordTitle, onValueChange = { recordTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text(promptTitle) }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text(promptBody) }, minLines = 5)
                    Button(
                        onClick = { onSave(recordTitle, body); recordTitle = ""; body = "" },
                        enabled = recordTitle.isNotBlank() && body.isNotBlank(),
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
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(VioletPrimary, Color(0xFF241039), Color(0xFF4B3264))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(eyebrow, color = GoldChampagne, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text(title, color = Ivory, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, color = Ivory.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.width(38.dp).height(3.dp).background(GoldChampagne, CircleShape))
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
