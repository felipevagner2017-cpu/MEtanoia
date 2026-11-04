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
    val todayAnalysis = analyses.firstOrNull { it.dayKey == today.toString() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DeepViolet).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Mensagens e desafios", color = GoldBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Seu ponto de partida para uma vida intencional: visão do dia, mensagens, desafios e acesso às quatro dimensões.", color = Ivory, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = VioletSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RESUMO DE HOJE", color = GoldChampagne, fontWeight = FontWeight.Bold)
                    Text("${todayItems.count { !it.completed }} compromissos pendentes · ${todayItems.count { it.completed }} concluídos", color = Ivory)
                    Text("${journal.count { it.dayKey == today.toString() }} registro(s) no diário e nas dimensões", color = Ivory)
                    if (todayAnalysis != null) {
                        Text("Leitura do dia", color = GoldBright, fontWeight = FontWeight.SemiBold)
                        Text(todayAnalysis.emotionalState, color = Ivory)
                        Text(todayAnalysis.nextStep, color = Ivory)
                    } else {
                        Text("Seu resumo integrado aparecerá após salvar o relato diário.", color = Ivory.copy(alpha = .82f))
                    }
                }
            }
        }
        item {
            Text("AS QUATRO DIMENSÕES", color = GoldChampagne, fontWeight = FontWeight.Bold)
        }
        item { DimensionAccessCard("01", "DEVOCIONAL / ESPIRITUAL", "Escrituras, formação, caráter e jornada espiritual.", "Devocional") { onNavigate("Devocional") } }
        item { DimensionAccessCard("02", "MATRIMÔNIO / INGRID", "Cuidado, presença, escuta e parceria no cotidiano.", "Matrimônio") { onNavigate("Matrimônio") } }
        item { DimensionAccessCard("03", "PROFISSIONAL", "Metas, projetos, competências e resultados.", "Profissional") { onNavigate("Profissional") } }
        item { DimensionAccessCard("04", "COTIDIANO", "Agenda, calendário, tarefas, hábitos e diário unificado.", "Cotidiano") { onNavigate("Cotidiano") } }
        item {
            Text("AGENDA DE HOJE", color = GoldChampagne, fontWeight = FontWeight.Bold)
            if (todayItems.isEmpty()) Text("Nenhum compromisso registrado para hoje.", color = Ivory)
        }
        items(todayItems.take(6), key = { "today-${it.id}" }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = CardSurface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Instant.ofEpochMilli(item.scheduledAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), color = GoldBright, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(item.title, color = Ivory, fontWeight = FontWeight.SemiBold)
                        Text(if (item.completed) "Concluído" else "Previsto · ${dimensionName(item.dimension)}", color = Ivory.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionAccessCard(number: String, title: String, description: String, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = GoldChampagne, fontWeight = FontWeight.Bold)
                Text(description, color = Ivory, style = MaterialTheme.typography.bodyMedium)
                Text("ACESSAR  →", color = GoldBright, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
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
            Text("DIMENSÃO 02", color = GoldChampagne, fontWeight = FontWeight.Bold)
            Text("Matrimônio / Ingrid", color = GoldBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Cuidado que se transforma em presença, escuta e parceria.", color = Ivory)
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
        item {
            Text(eyebrow, color = GoldChampagne, fontWeight = FontWeight.Bold)
            Text(title, color = GoldBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(description, color = Ivory)
        }
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
