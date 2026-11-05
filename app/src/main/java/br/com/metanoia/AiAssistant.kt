package br.com.metanoia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import br.com.metanoia.data.AgendaItem
import br.com.metanoia.data.ConfessionEntry
import br.com.metanoia.data.JournalEntry
import br.com.metanoia.data.LifeDimension
import br.com.metanoia.security.AccountCrypto
import br.com.metanoia.security.AccountKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val METANOIA_API_BASE = "https://metanoia.restaura-lar.workers.dev"
private data class AiChatMessage(val role: String, val content: String)
private data class AiAuthenticatedSession(val token: String, val userId: String, val deviceId: String)

private class AiTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("metonoia_ai_secure", Context.MODE_PRIVATE)

    fun read(): String {
        return try {
            val parts = prefs.getString("credential", null)?.split(".") ?: return ""
            if (parts.size != 2) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    fun clear() { prefs.edit().remove("credential").apply() }
    fun readUserId(): String = prefs.getString("user_id", "").orEmpty()

    fun save(value: String, userId: String = readUserId()) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString("credential", stored).putString("user_id", userId).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("metonoia_ai_aes", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "metonoia_ai_aes",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

@Composable
internal fun AiAssistantDialog(
    context: Context,
    journal: List<JournalEntry>,
    confessions: List<ConfessionEntry>,
    agenda: List<AgendaItem>,
    initialDimension: String = "Visão integrada",
    onDismiss: () -> Unit
) {
    val tokenStore = remember { AiTokenStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(tokenStore.read()) }
    var userId by remember { mutableStateOf(tokenStore.readUserId()) }
    var encryptionPhrase by remember { mutableStateOf("") }
    var accountDataKey by remember { mutableStateOf<ByteArray?>(null) }
    val accountKeyStore = remember { AccountKeyStore(context.applicationContext) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registrationKey by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var dimension by remember { mutableStateOf(initialDimension) }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("A conversa não será salva automaticamente.") }
    var messages by remember { mutableStateOf(emptyList<AiChatMessage>()) }

    LaunchedEffect(userId) {
        accountDataKey = if (userId.isBlank()) null else withContext(Dispatchers.IO) { accountKeyStore.load(userId) }
        if (accountDataKey != null) status = "Chave da conta desbloqueada pelo Android Keystore neste aparelho."
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(22.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, GoldChampagne.copy(alpha = .7f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("METANOIA AI", color = GoldBright, fontWeight = FontWeight.Bold)
                        Text("Inteligência integrada às quatro dimensões", color = Ivory, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("Fechar", color = GoldBright) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Visão integrada", "Devoção", "Matrimônio", "Profissional", "Cotidiano").forEach { option ->
                        FilterChip(
                            selected = dimension == option,
                            onClick = { dimension = option },
                            label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldChampagne,
                                selectedLabelColor = DeepViolet,
                                labelColor = Ivory
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("E-mail da conta MEtanoia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = GoldBright, unfocusedBorderColor = GoldChampagne.copy(alpha = .5f), focusedLabelColor = GoldBright, unfocusedLabelColor = Ivory.copy(alpha = .8f), cursorColor = GoldBright)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Senha da conta") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = GoldBright, unfocusedBorderColor = GoldChampagne.copy(alpha = .5f), focusedLabelColor = GoldBright, unfocusedLabelColor = Ivory.copy(alpha = .8f), cursorColor = GoldBright)
                )
                OutlinedTextField(
                    value = registrationKey, onValueChange = { registrationKey = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Código de convite (primeiro cadastro)") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = GoldBright, unfocusedBorderColor = GoldChampagne.copy(alpha = .5f), focusedLabelColor = GoldBright, unfocusedLabelColor = Ivory.copy(alpha = .8f), cursorColor = GoldBright)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !authBusy, onClick = {
                        if (email.isBlank() || password.isBlank()) { status = "Informe e-mail e senha."; return@Button }
                        authBusy = true; status = "Autenticando conta..."
                        scope.launch {
                            try {
                                val session = withContext(Dispatchers.IO) { AiCloudClient.authenticate(false, email.trim(), password, "") }
                                token = session.token; userId = session.userId; tokenStore.save(session.token, session.userId); status = "Conta autenticada. Sessão protegida pelo Android Keystore."
                            } catch (error: Exception) { status = error.message ?: "Não foi possível autenticar." }
                            finally { authBusy = false }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = GoldChampagne, contentColor = DeepViolet)) { Text("Entrar") }
                    Button(enabled = !authBusy, onClick = {
                        if (email.isBlank() || password.length < 12 || registrationKey.isBlank()) { status = "Informe e-mail, senha com pelo menos 12 caracteres e convite."; return@Button }
                        authBusy = true; status = "Criando conta..."
                        scope.launch {
                            try {
                                val session = withContext(Dispatchers.IO) { AiCloudClient.authenticate(true, email.trim(), password, registrationKey.trim()) }
                                token = session.token; userId = session.userId; tokenStore.save(session.token, session.userId); registrationKey = ""; status = "Conta criada e autenticada."
                            } catch (error: Exception) { status = error.message ?: "Não foi possível criar a conta." }
                            finally { authBusy = false }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = VioletSurface, contentColor = Ivory)) { Text("Criar conta") }
                }
                if (token.isNotBlank()) {
                    OutlinedTextField(
                        value = encryptionPhrase, onValueChange = { encryptionPhrase = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Frase independente de criptografia da conta") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ivory, unfocusedTextColor = Ivory, focusedBorderColor = GoldBright, unfocusedBorderColor = GoldChampagne.copy(alpha = .5f), focusedLabelColor = GoldBright, unfocusedLabelColor = Ivory.copy(alpha = .8f), cursorColor = GoldBright)
                    )
                    Button(enabled = !authBusy && encryptionPhrase.length >= 12, onClick = {
                        if (userId.isBlank()) { status = "Entre novamente para atualizar os dados de identificação da conta."; return@Button }
                        authBusy = true
                        status = "Desbloqueando a chave criptográfica da conta..."
                        scope.launch {
                            try {
                                val phrase = encryptionPhrase.toCharArray()
                                val dataKey = withContext(Dispatchers.IO) {
                                    val existing = AiCloudClient.getAccountKeyEnvelope(token)
                                    val envelopeJson = if (existing == null) {
                                        val created = AccountCrypto().createEnvelope(userId, phrase)
                                        AiCloudClient.saveAccountKeyEnvelope(token, created)
                                        JSONObject(created)
                                    } else existing
                                    val envelope = AccountCrypto.KeyEnvelope(
                                        envelopeJson.getInt("version"), envelopeJson.getString("kdf"),
                                        envelopeJson.getInt("iterations"), envelopeJson.getString("salt"),
                                        envelopeJson.getString("nonce"), envelopeJson.getString("ciphertext")
                                    )
                                    AccountCrypto().openEnvelope(userId, phrase, envelope)
                                }
                                accountKeyStore.save(userId, dataKey)
                                accountDataKey?.fill(0)
                                accountDataKey = dataKey
                                phrase.fill('\u0000')
                                encryptionPhrase = ""
                                status = "Chave de conta desbloqueada e protegida localmente pelo Android Keystore."
                            } catch (error: Exception) {
                                status = error.message ?: "Não foi possível desbloquear a chave da conta."
                            } finally { authBusy = false }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = VioletSurface, contentColor = Ivory)) {
                        Text(if (accountDataKey == null) "Inicializar / desbloquear criptografia" else "Atualizar chave protegida")
                    }
                    Text(
                        if (accountDataKey != null) "Dados da conta protegidos neste aparelho." else "A chave ainda não foi desbloqueada neste aparelho.",
                        color = Ivory.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, colors = CheckboxDefaults.colors(checkedColor = GoldBright))
                    Text(
                        "Autorizo enviar os registros recentes relacionados ao foco escolhido e minha pergunta ao serviço de IA na Cloudflare para gerar esta resposta. A conversa não será salva automaticamente.",
                        color = Ivory, style = MaterialTheme.typography.bodySmall
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 100.dp, max = 250.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) item {
                        Text("Faça uma pergunta sobre sua jornada. Nenhum registro será enviado sem sua autorização.", color = Ivory.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    }
                    items(messages) { message ->
                        Surface(
                            color = if (message.role == "user") VioletSurface else DeepViolet,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(if (message.role == "user") "VOCÊ" else "METANOIA AI", color = GoldBright, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                Text(message.content, color = Ivory)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4, label = { Text("Sua pergunta") },
                    placeholder = { Text("O que deseja compreender, estudar ou planejar?") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Ivory, unfocusedTextColor = Ivory,
                        focusedBorderColor = GoldBright, unfocusedBorderColor = GoldChampagne.copy(alpha = .5f),
                        focusedLabelColor = GoldBright, unfocusedLabelColor = Ivory.copy(alpha = .8f),
                        cursorColor = GoldBright
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            val cleanToken = token.trim()
                            val cleanQuestion = question.trim()
                            when {
                                cleanToken.length < 24 -> status = "Entre com sua conta MEtanoia antes de usar a IA."
                                !consent -> status = "Autorize o envio dos registros antes de conversar com a IA."
                                cleanQuestion.isBlank() -> status = "Escreva sua pergunta."
                                else -> {
                                    tokenStore.save(cleanToken)
                                    val history = messages.takeLast(8)
                                    val contextText = buildAiContext(dimension, journal, confessions, agenda)
                                    messages = messages + AiChatMessage("user", cleanQuestion)
                                    question = ""
                                    busy = true
                                    status = "A IA está analisando o contexto autorizado..."
                                    scope.launch {
                                        try {
                                            val answer = withContext(Dispatchers.IO) {
                                                AiCloudClient.ask(cleanToken, dimension, contextText, history, cleanQuestion)
                                            }
                                            messages = messages + AiChatMessage("assistant", answer)
                                            status = "Resposta gerada pelo modelo conectado."
                                        } catch (error: Exception) {
                                            messages = messages.dropLast(1)
                                            status = error.message ?: "Falha de conexão. A análise local continua disponível."
                                        } finally { busy = false }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldChampagne, contentColor = DeepViolet)
                    ) { Text(if (busy) "Aguarde..." else "Enviar à IA") }
                    TextButton(onClick = { messages = emptyList(); status = "Conversa limpa. Seus registros continuam no aparelho." }) {
                        Text("Limpar conversa", color = GoldBright)
                    }
                    TextButton(enabled = !authBusy, onClick = {
                        val currentToken = token.trim()
                        if (currentToken.isBlank()) {
                            tokenStore.clear()
                            token = ""; userId = ""; accountDataKey?.fill(0); accountDataKey = null
                            status = "Nenhuma sessão ativa neste aparelho."
                        } else {
                            authBusy = true
                            status = "Encerrando sessão da conta..."
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { AiCloudClient.logout(currentToken) }
                                    tokenStore.clear()
                                    token = ""; userId = ""; accountDataKey?.fill(0); accountDataKey = null
                                    messages = emptyList()
                                    status = "Sessão encerrada no servidor e neste aparelho."
                                } catch (error: Exception) {
                                    status = error.message ?: "Não foi possível encerrar a sessão. Verifique a conexão e tente novamente."
                                } finally { authBusy = false }
                            }
                        }
                    }) {
                        Text("Sair da conta", color = GoldBright)
                    }
                }
                Text(status, color = Ivory.copy(alpha = .76f), style = MaterialTheme.typography.labelSmall)
                Text(
                    "A IA na nuvem exige internet e uma sessão autenticada. A sessão fica protegida pelo Android Keystore. Sem conexão, a leitura contextual local continua disponível.",
                    color = Ivory.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun buildAiContext(
    dimension: String,
    journal: List<JournalEntry>,
    confessions: List<ConfessionEntry>,
    agenda: List<AgendaItem>
): String {
    fun label(value: LifeDimension) = when (value) {
        LifeDimension.SPIRITUAL -> "Devoção"
        LifeDimension.MATRIMONIAL -> "Matrimônio"
        LifeDimension.PROFESSIONAL -> "Profissional"
        LifeDimension.INDIVIDUAL, LifeDimension.ROUTINE -> "Cotidiano"
    }
    val allowed = when (dimension) {
        "Devoção" -> setOf(LifeDimension.SPIRITUAL)
        "Matrimônio" -> setOf(LifeDimension.MATRIMONIAL)
        "Profissional" -> setOf(LifeDimension.PROFESSIONAL)
        "Cotidiano" -> setOf(LifeDimension.INDIVIDUAL, LifeDimension.ROUTINE)
        else -> emptySet()
    }
    val selected = if (allowed.isEmpty()) {
        journal.groupBy { it.dimension }.values.flatMap { group -> group.sortedByDescending { it.createdAt }.take(3) }
            .sortedByDescending { it.createdAt }.take(12)
    } else {
        journal.filter { it.dimension in allowed }
            .distinctBy { it.id }.sortedByDescending { it.createdAt }.take(10)
    }
    val records = selected.joinToString("\n") { entry ->
        entry.dayKey + " | " + label(entry.dimension) + " | " + entry.title + ": " + entry.content.take(1000)
    }
    val confessionText = if (dimension == "Visão integrada" || dimension == "Devoção") {
        confessions.sortedByDescending { it.createdAt }.take(3).joinToString("\n") {
            it.dayKey + " | Registro pessoal: " + it.content.take(700)
        }
    } else ""
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    val agendaText = agenda.filter { it.scheduledAt >= now - 7 * 86400000L && (allowed.isEmpty() || it.dimension in allowed) }
        .sortedBy { it.scheduledAt }.take(10).joinToString("\n") { item ->
            val whenText = Instant.ofEpochMilli(item.scheduledAt).atZone(zone).format(dateFormat)
            whenText + " | " + label(item.dimension) + " | " + item.title + " | " + if (item.completed) "concluído" else "previsto"
        }
    return buildString {
        appendLine("FOCO: " + dimension)
        appendLine("REGISTROS RECENTES:")
        appendLine(records.ifBlank { "Nenhum registro disponível para este foco." })
        appendLine("CONFISSÕES/REGISTROS PESSOAIS RECENTES:")
        appendLine(confessionText.ifBlank { "Nenhum registro pessoal adicional." })
        appendLine("AGENDA:")
        appendLine(agendaText.ifBlank { "Nenhum compromisso próximo ou recente." })
    }.take(12000)
}

private object AiCloudClient {
    fun authenticate(register: Boolean, email: String, password: String, registrationKey: String): AiAuthenticatedSession {
        val connection = URL(METANOIA_API_BASE + if (register) "/api/auth/register" else "/api/auth/login").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (register) connection.setRequestProperty("X-Registration-Key", registrationKey)
        try {
            val payload = JSONObject().put("email", email).put("password", password).put("platform", "android").put("deviceLabel", android.os.Build.MODEL)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw IOException(response.optString("error").ifBlank { "Autenticação respondeu HTTP " + code + "." })
            val sessionToken = response.optString("token").takeIf { it.length >= 24 } ?: throw IOException("A sessão não foi retornada pelo servidor.")
            val userId = response.optString("userId").takeIf { it.isNotBlank() } ?: throw IOException("O identificador da conta não foi retornado.")
            val deviceId = response.optString("deviceId").takeIf { it.isNotBlank() } ?: throw IOException("O identificador do aparelho não foi retornado.")
            return AiAuthenticatedSession(sessionToken, userId, deviceId)
        } finally { connection.disconnect() }
    }

    fun getAccountKeyEnvelope(token: String): JSONObject? {
        val connection = URL(METANOIA_API_BASE + "/api/crypto/key-envelope").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Authorization", "Bearer " + token)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw IOException(response.optString("error").ifBlank { "Falha ao consultar a chave da conta (HTTP $code)." })
            return if (response.isNull("envelope")) null else response.getJSONObject("envelope")
        } finally { connection.disconnect() }
    }

    fun saveAccountKeyEnvelope(token: String, envelopeJson: String) {
        val connection = URL(METANOIA_API_BASE + "/api/crypto/key-envelope").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("envelope", JSONObject(envelopeJson)).toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val responseText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
                throw IOException(response.optString("error").ifBlank { "Falha ao salvar a chave da conta (HTTP $code)." })
            }
        } finally { connection.disconnect() }
    }

    fun logout(token: String) {
        val connection = URL(METANOIA_API_BASE + "/api/auth/logout").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299 && code != 401) {
                val responseText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
                throw IOException(response.optString("error").ifBlank { "Não foi possível encerrar a sessão (HTTP " + code + ")." })
            }
        } finally { connection.disconnect() }
    }

    fun ask(token: String, dimension: String, context: String, history: List<AiChatMessage>, question: String): String {
        val payload = JSONObject()
            .put("dimension", dimension)
            .put("context", context)
            .put("question", question)
            .put("history", JSONArray().apply {
                history.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        val connection = URL(METANOIA_API_BASE + "/api/ai/chat").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (code !in 200..299) throw IOException(response.optString("error").ifBlank { "O serviço de IA respondeu com erro HTTP " + code + "." })
            return response.optString("answer").ifBlank { throw IOException("A IA não retornou uma resposta válida.") }
        } finally { connection.disconnect() }
    }
}
