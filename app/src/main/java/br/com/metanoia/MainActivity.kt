package br.com.metanoia
import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.metanoia.data.*
import br.com.metanoia.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF102542)
private val Gold = Color(0xFFC5A15A)
private val Paper = Color(0xFFF4F6F9)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val db = MetanoiaDatabase.get(this)
  val scheduler = ReminderScheduler(this)
  setContent { MaterialTheme(colorScheme = lightColorScheme(primary=Navy, secondary=Gold, background=Paper)) {
   Surface(Modifier.fillMaxSize(), color=Paper) { MetanoiaApp(db, scheduler) }
  } }
 }
}
private class AppViewModel(private val db: MetanoiaDatabase, private val scheduler: ReminderScheduler): ViewModel() {
 val agenda = db.agendaDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val journal = db.journalDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val reviews = db.reviewDao().observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 fun addAgenda(title:String, kind:AgendaKind, dimension:LifeDimension, reminder:Boolean) {
  if(title.isBlank()) return
  viewModelScope.launch { val at=System.currentTimeMillis(); val item=AgendaItem(title=title.trim(),kind=kind,dimension=dimension,scheduledAt=at,reminderAt=if(reminder) at+60*60*1000L else null); val id=db.agendaDao().insert(item); if(reminder) scheduler.schedule(item.copy(id=id)) }
 }
 fun toggle(item:AgendaItem) { viewModelScope.launch { db.agendaDao().setCompleted(item.id,!item.completed) } }
 fun delete(item:AgendaItem) { viewModelScope.launch { scheduler.cancel(item); db.agendaDao().delete(item) } }
 fun addJournal(title:String, body:String, dimension:LifeDimension) {
  if(body.isBlank()) return
  viewModelScope.launch { db.journalDao().insert(JournalEntry(title=title.trim(),content=body.trim(),dimension=dimension)) }
 }
 fun addReview(period:ReviewPeriod, body:String) {
  if(body.isBlank()) return
  val today=LocalDate.now()
  val start=if(period==ReviewPeriod.WEEKLY) today.minusDays((today.dayOfWeek.value-1).toLong()) else today.withDayOfMonth(1)
  val end=if(period==ReviewPeriod.WEEKLY) start.plusDays(6) else start.plusMonths(1).minusDays(1)
  val zone=ZoneId.systemDefault()
  viewModelScope.launch { db.reviewDao().insert(ReviewEntry(period=period,periodStart=start.atStartOfDay(zone).toInstant().toEpochMilli(),periodEnd=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1,reflection=body.trim())) }
 }
}
@Composable private fun MetanoiaApp(db:MetanoiaDatabase,scheduler:ReminderScheduler) {
 val context=LocalContext.current
 val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
 LaunchedEffect(Unit) { if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
 val vm=remember { AppViewModel(db,scheduler) }
 val agenda by vm.agenda.collectAsStateWithLifecycle()
 val journal by vm.journal.collectAsStateWithLifecycle()
 val reviews by vm.reviews.collectAsStateWithLifecycle()
 var tab by remember { mutableStateOf("Agenda") }
 Column(Modifier.fillMaxSize()) {
  Column(Modifier.fillMaxWidth().background(Navy).padding(22.dp)) {
   Text("MEtonoia",color=Gold,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
   Text("A transformação é uma construção diária.",color=Color.White)
   Text("Uma mente renovada. Uma vida intencional.",color=Color.White.copy(alpha=.8f))
  }
  Row(Modifier.fillMaxWidth().background(Color.White),horizontalArrangement=Arrangement.SpaceEvenly) {
   listOf("Agenda","Diário","Revisões").forEach { label -> TextButton(onClick={tab=label}) { Text(label,color=if(tab==label) Navy else Color.Gray) } }
  }
  when(tab) { "Agenda"->AgendaPage(agenda,vm); "Diário"->JournalPage(journal,vm); else->ReviewPage(reviews,vm) }
 }
}
@Composable private fun AgendaPage(items:List<AgendaItem>,vm:AppViewModel) {
 var title by remember { mutableStateOf("") }
 var kind by remember { mutableStateOf(AgendaKind.TASK) }
 var dimension by remember { mutableStateOf(LifeDimension.ROUTINE) }
 var range by remember { mutableStateOf("Todos") }; var reminder by remember { mutableStateOf(false) }
 val now=System.currentTimeMillis(); val day=86400000L
 val visible=when(range) { "Hoje"->items.filter{it.scheduledAt in now-day..now+day}; "Semana"->items.filter{it.scheduledAt in now-day..now+7*day}; "Mês"->items.filter{it.scheduledAt in now-day..now+30*day}; else->items }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Minha agenda",style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
  OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Tarefa, hábito ou compromisso")},singleLine=true)
  Row { AgendaKind.entries.forEach { v-> FilterChip(kind==v,{kind=v},label={Text(kindLabel(v))}) } }
  var menu by remember { mutableStateOf(false) }
  Box { OutlinedButton(onClick={menu=true}) { Text(dimensionLabel(dimension)) }
   DropdownMenu(menu,{menu=false}) { LifeDimension.entries.forEach { v-> DropdownMenuItem(text={Text(dimensionLabel(v))},onClick={dimension=v;menu=false}) } }
  }
  Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(reminder,{reminder=it}); Text("Lembrar em 1 hora",color=Navy) }
  Button(onClick={vm.addAgenda(title,kind,dimension,reminder).also { title=""; reminder=false }},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Navy)) { Text("Adicionar à agenda") }
  Row { listOf("Hoje","Semana","Mês","Todos").forEach { v-> FilterChip(range==v,{range=v},label={Text(v)}) } }
  Text(visible.count{!it.completed}.toString()+" pendentes · "+visible.count{it.completed}+" concluídos",color=Gold)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(visible,key={it.id}) { item->
   Card(colors=CardDefaults.cardColors(containerColor=Color.White)) { Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
    Checkbox(item.completed,{vm.toggle(item)})
    Column(Modifier.weight(1f)) { Text(item.title,color=Navy,fontWeight=FontWeight.SemiBold); Text(kindLabel(item.kind)+" · "+dimensionLabel(item.dimension)+" · "+formatDate(item.scheduledAt),style=MaterialTheme.typography.labelSmall) }
    TextButton(onClick={vm.delete(item)}) { Text("Excluir") }
   } }
  } }
 }
}
@Composable private fun JournalPage(entries:List<JournalEntry>,vm:AppViewModel) {
 var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }
 var dimension by remember { mutableStateOf(LifeDimension.INDIVIDUAL) }; var menu by remember { mutableStateOf(false) }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Diário pessoal",style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
  OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Título (opcional)")})
  Box { OutlinedButton(onClick={menu=true}) { Text(dimensionLabel(dimension)) }
   DropdownMenu(menu,{menu=false}) { LifeDimension.entries.forEach { v->DropdownMenuItem(text={Text(dimensionLabel(v))},onClick={dimension=v;menu=false})} }
  }
  OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=110.dp),label={Text("Escreva sua reflexão")})
  Button(onClick={vm.addJournal(title,body,dimension).also{title="";body=""}},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Navy)) { Text("Guardar registro") }
  LazyColumn { items(entries,key={it.id}) { e-> Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
   Column(Modifier.padding(12.dp)) { Text(e.title.ifBlank{"Registro pessoal"},color=Navy,fontWeight=FontWeight.Bold); Text(dimensionLabel(e.dimension),color=Gold); Text(e.content) }
  } } }
 }
}
@Composable private fun ReviewPage(entries:List<ReviewEntry>,vm:AppViewModel) {
 var period by remember { mutableStateOf(ReviewPeriod.WEEKLY) }; var body by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Revisões históricas",style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
  Text("Observe sua caminhada sem transformar registros em julgamento.")
  Row { ReviewPeriod.entries.forEach { v->FilterChip(period==v,{period=v},label={Text(if(v==ReviewPeriod.WEEKLY) "Semanal" else "Mensal")}) } }
  OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("O que aprendi? O que merece atenção? Qual será meu próximo passo?")})
  Button(onClick={vm.addReview(period,body).also{body=""}},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Navy)) { Text("Salvar revisão") }
  LazyColumn { items(entries,key={it.id}) { e->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
   Column(Modifier.padding(12.dp)) { Text(if(e.period==ReviewPeriod.WEEKLY) "Revisão semanal" else "Revisão mensal",color=Navy,fontWeight=FontWeight.Bold); Text(formatDate(e.periodStart),color=Gold); Text(e.reflection) }
  } } }
 }
}
private fun dimensionLabel(v:LifeDimension)=when(v){LifeDimension.SPIRITUAL->"Espiritual";LifeDimension.MATRIMONIAL->"Matrimonial";LifeDimension.PROFESSIONAL->"Profissional";LifeDimension.INDIVIDUAL->"Individual";LifeDimension.ROUTINE->"Agenda e rotina"}
private fun kindLabel(v:AgendaKind)=when(v){AgendaKind.TASK->"Tarefa";AgendaKind.APPOINTMENT->"Compromisso";AgendaKind.HABIT->"Hábito"}
private fun formatDate(value:Long)=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
