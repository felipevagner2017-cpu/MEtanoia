package br.com.metanoia
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.time.YearMonth
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
 fun addAgenda(title:String, kind:AgendaKind, dimension:LifeDimension, scheduledAt:Long, reminder:Boolean) {
  if(title.isBlank()) return
  viewModelScope.launch { val reminderAt=if(reminder) scheduledAt-15*60*1000L else null; val item=AgendaItem(title=title.trim(),kind=kind,dimension=dimension,scheduledAt=scheduledAt,reminderAt=reminderAt); val id=db.agendaDao().insert(item); if(reminder && reminderAt!!>System.currentTimeMillis()) scheduler.schedule(item.copy(id=id)) }
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
 val context=LocalContext.current
 val zone=remember { ZoneId.systemDefault() }
 var selectedDate by remember { mutableStateOf(LocalDate.now()) }
 var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
 var title by remember { mutableStateOf("") }
 var kind by remember { mutableStateOf(AgendaKind.TASK) }
 var dimension by remember { mutableStateOf(LifeDimension.ROUTINE) }
 var reminder by remember { mutableStateOf(false) }
 var hour by remember { mutableIntStateOf(9) }
 var minute by remember { mutableIntStateOf(0) }
 var dimensionMenu by remember { mutableStateOf(false) }
 val first=displayedMonth.atDay(1)
 val leading=first.dayOfWeek.value-1
 val dayCount=displayedMonth.lengthOfMonth()
 val cells=buildList<LocalDate?> {
  repeat(leading) { add(null) }
  for(dayNumber in 1..dayCount) add(displayedMonth.atDay(dayNumber))
  while(size%7!=0) add(null)
 }
 val selectedItems=items.filter { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate()==selectedDate }.sortedBy { it.scheduledAt }
 val monthItems=items.groupBy { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() }
 val weekdays=listOf("SEG","TER","QUA","QUI","SEX","SÁB","DOM")
 Column(Modifier.fillMaxSize().padding(horizontal=14.dp, vertical=10.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column {
    Text("Minha agenda",style=MaterialTheme.typography.headlineSmall,color=Navy,fontWeight=FontWeight.Bold)
    Text("Organize seus dias com intenção.",color=Color.Gray,style=MaterialTheme.typography.bodySmall)
   }
   TextButton(onClick={selectedDate=LocalDate.now();displayedMonth=YearMonth.now()}) { Text("Hoje",color=Gold) }
  }
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
   Column(Modifier.padding(12.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     Text(displayedMonth.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("pt","BR")).replaceFirstChar { it.uppercase() }+" "+displayedMonth.year,color=Navy,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
     Row {
      IconButton(onClick={displayedMonth=displayedMonth.minusMonths(1)}) { Text("‹",color=Navy,style=MaterialTheme.typography.headlineMedium) }
      IconButton(onClick={displayedMonth=displayedMonth.plusMonths(1)}) { Text("›",color=Navy,style=MaterialTheme.typography.headlineMedium) }
     }
    }
    Row(Modifier.fillMaxWidth()) {
     weekdays.forEach { day->Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { Text(day,color=Color.Gray,style=MaterialTheme.typography.labelSmall) } }
    }
    cells.chunked(7).forEach { week->
     Row(Modifier.fillMaxWidth()) {
      week.forEach { date->
       Box(Modifier.weight(1f).height(43.dp).padding(2.dp),contentAlignment=Alignment.Center) {
        if(date!=null) {
         val isSelected=date==selectedDate
         val isToday=date==LocalDate.now()
         val count=monthItems[date]?.size?:0
         Column(Modifier.fillMaxSize().background(if(isSelected) Navy else Color.Transparent,MaterialTheme.shapes.small).clickable { selectedDate=date }.padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
          Text(date.dayOfMonth.toString(),color=if(isSelected) Color.White else Navy,fontWeight=if(isSelected||isToday) FontWeight.Bold else FontWeight.Normal)
          if(count>0) Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
           repeat(minOf(count,3)) { Box(Modifier.size(4.dp).background(Gold,MaterialTheme.shapes.small)) }
          } else if(isToday) Box(Modifier.size(4.dp).background(Gold,MaterialTheme.shapes.small))
         }
        }
       }
      }
     }
    }
    Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween) {
     Text("• Registros indicados em dourado",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
     Text("\${items.size} registros",style=MaterialTheme.typography.labelSmall,color=Gold)
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column {
    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",java.util.Locale("pt","BR"))).replaceFirstChar { it.uppercase() },color=Navy,fontWeight=FontWeight.Bold)
    Text("\${selectedItems.count{!it.completed}} pendentes · \${selectedItems.count{it.completed}} concluídos",color=Gold,style=MaterialTheme.typography.labelSmall)
   }
   TextButton(onClick={selectedDate=selectedDate.plusDays(1);if(YearMonth.from(selectedDate)!=displayedMonth) displayedMonth=YearMonth.from(selectedDate)}) { Text("Próximo dia  ›",color=Navy) }
  }
  Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)) {
   Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Nova tarefa, hábito ou compromisso")},singleLine=true)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     AgendaKind.entries.forEach { value->FilterChip(selected=kind==value,onClick={kind=value},label={Text(kindLabel(value))}) }
    }
    Box {
     OutlinedButton(onClick={dimensionMenu=true}) { Text(dimensionLabel(dimension),color=Navy) }
     DropdownMenu(expanded=dimensionMenu,onDismissRequest={dimensionMenu=false}) {
      LifeDimension.entries.forEach { value->DropdownMenuItem(text={Text(dimensionLabel(value))},onClick={dimension=value;dimensionMenu=false}) }
     }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()}) { Text("Horário: %02d:%02d".format(hour,minute),color=Navy) }
     Row(verticalAlignment=Alignment.CenterVertically) {
      Checkbox(checked=reminder,onCheckedChange={reminder=it})
      Text("Avisar 15 min antes",style=MaterialTheme.typography.labelSmall,color=Navy)
     }
    }
    Button(onClick={
     val scheduled=selectedDate.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
     vm.addAgenda(title,kind,dimension,scheduled,reminder)
     title=""
     reminder=false
    },enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Navy)) {
     Text("Adicionar em \${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM"))}")
    }
   }
  }
  if(selectedItems.isEmpty()) {
   Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
    Text("Nenhum registro para este dia.\nEscolha outra data ou adicione um compromisso.",color=Color.Gray,style=MaterialTheme.typography.bodyMedium)
   }
  } else {
   LazyColumn(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    items(selectedItems,key={it.id}) { item->
     Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
      Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
       Checkbox(checked=item.completed,onCheckedChange={vm.toggle(item)})
       Column(Modifier.weight(1f)) {
        Text(item.title,color=Navy,fontWeight=FontWeight.SemiBold)
        val itemTime=Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
        Text("%02d:%02d".format(itemTime.hour,itemTime.minute)+" · "+kindLabel(item.kind)+" · "+dimensionLabel(item.dimension),style=MaterialTheme.typography.labelSmall,color=Color.Gray)
       }
       TextButton(onClick={vm.delete(item)}) { Text("Excluir",color=Color.Gray) }
      }
     }
    }
   }
  }
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
