package br.com.metanoia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.metanoia.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Navy = Color(0xFF102542)
private val Gold = Color(0xFFC5A15A)
private val Paper = Color(0xFFF7F8FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = MetanoiaDatabase.get(this).agendaDao()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Navy, secondary = Gold, background = Paper, surface = Color.White
            )) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    AgendaScreen(AgendaViewModel(dao))
                }
            }
        }
    }
}

class AgendaViewModel(dao: AgendaDao) : ViewModel() {
    val items = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val scope get() = viewModelScope
    fun add(title: String) {
        if (title.isBlank()) return
        scope.launch { dao.insert(AgendaItem(title = title.trim(), scheduledAt = System.currentTimeMillis())) }
    }
    fun complete(item: AgendaItem) { scope.launch { dao.setCompleted(item.id, !item.completed) } }
    fun delete(item: AgendaItem) { scope.launch { dao.delete(item) } }
}

@Composable
private fun AgendaScreen(vm: AgendaViewModel) {
    val items by vm.items.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Paper)) {
        Column(
            Modifier.fillMaxWidth().background(Navy).padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text("MEtonoia", color = Gold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A transformação é uma construção diária.", color = Color.White)
            Text("Uma mente renovada. Uma vida intencional.", color = Color.White.copy(alpha = .82f))
        }
        Column(Modifier.padding(20.dp)) {
            Text("Minha agenda", color = Navy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Nova tarefa ou compromisso") }, singleLine = true
            )
            Button(
                onClick = { vm.add(title); title = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { Text("Adicionar à agenda") }
            Spacer(Modifier.height(18.dp))
            if (items.isEmpty()) {
                Text("Sua agenda começa com uma decisão intencional.", color = Navy.copy(alpha = .7f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.id }) { item ->
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = item.completed, onCheckedChange = { vm.complete(item) })
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, color = Navy, fontWeight = FontWeight.Medium)
                                    Text(item.kind.name.lowercase().replaceFirstChar { it.uppercase() }, color = Gold, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { vm.delete(item) }) { Text("Excluir", color = Navy) }
                            }
                        }
                    }
                }
            }
        }
    }
}
