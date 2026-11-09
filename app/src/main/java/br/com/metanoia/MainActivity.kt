package br.com.metanoia
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import br.com.metanoia.wallpaper.LockScreenAgendaWallpaper
import br.com.metanoia.wallpaper.LockScreenWallpaperWorker
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.RingtoneManager
import android.provider.Settings
import android.content.res.Configuration
import android.app.DatePickerDialog
import android.os.Build
import android.Manifest
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.metanoia.data.*
import br.com.metanoia.domain.DailyContextAnalyzer
import br.com.metanoia.domain.RecurrenceEngine
import br.com.metanoia.domain.RecurrenceFrequency
import br.com.metanoia.reminders.ReminderScheduler
import br.com.metanoia.reminders.NotificationPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Navy = Ivory
private val Gold = GoldChampagne
private val Paper = GoldChampagne.copy(alpha = .28f)

class MainActivity : ComponentActivity() {
 override fun attachBaseContext(newBase: Context) {
  val locale = Locale("pt", "BR")
  Locale.setDefault(locale)
  val configuration = Configuration(newBase.resources.configuration)
  configuration.setLocale(locale)
  super.attachBaseContext(newBase.createConfigurationContext(configuration))
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  installSplashScreen()
  super.onCreate(savedInstanceState)
  val db = MetanoiaDatabase.get(this)
  val scheduler = ReminderScheduler(this)
  scheduler.scheduleDailyReminders()
  lifecycleScope.launch(Dispatchers.IO) { db.agendaDao().pendingReminders(System.currentTimeMillis()).forEach(scheduler::schedule) }
  LockScreenWallpaperWorker.schedule(this)
  LockScreenAgendaWallpaper.refresh(this, db)
  // Official MEtanoia violet, champagne-gold and ivory Material 3 system.
  setContent { MaterialTheme(
   colorScheme = darkColorScheme(
    primary = GoldBright,
    onPrimary = DeepViolet,
    primaryContainer = VioletSecondary,
    onPrimaryContainer = Ivory,
    secondary = GoldChampagne,
    onSecondary = DeepViolet,
    secondaryContainer = VioletSurface,
    onSecondaryContainer = Ivory,
    tertiary = GoldChampagne,
    onTertiary = DeepViolet,
    background = DeepViolet,
    onBackground = Ivory,
    surface = CardSurface,
    onSurface = Ivory,
    surfaceVariant = VioletSurface,
    onSurfaceVariant = Ivory,
    outline = GoldChampagne.copy(alpha = 0.48f),
    outlineVariant = GoldChampagne.copy(alpha = 0.24f),
    error = Color(0xFFFF9A8B),
    onError = DeepViolet
   ),
   typography = MetanoiaTypography,
   shapes = MetanoiaShapes
  ) {
   Surface(Modifier.fillMaxSize(), color=DeepViolet) { Box(Modifier.fillMaxSize()) { MetanoiaApp(db, scheduler); MetanoiaBrandWatermark() } }
  } }
 }
}
private class AppViewModel(private val appContext: Context, private val db: MetanoiaDatabase, private val scheduler: ReminderScheduler): ViewModel() {
 val agenda = db.agendaDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val journal = db.journalDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val reviews = db.reviewDao().observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val confessions = db.confessionDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 val analyses = db.dailyAnalysisDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
 fun addAgenda(title:String, kind:AgendaKind, dimension:LifeDimension, scheduledAt:Long, reminder:Boolean, recurrenceRule:String?) {
  if(title.isBlank()) return
  viewModelScope.launch {
   val zone=ZoneId.systemDefault()
   val start=Instant.ofEpochMilli(scheduledAt).atZone(zone)
   val dates=if(kind==AgendaKind.HABIT && recurrenceRule!=null) {
    RecurrenceEngine.datesUntil(start.toLocalDate(),RecurrenceFrequency.valueOf(recurrenceRule),start.toLocalDate().plusDays(365))
   } else listOf(start.toLocalDate())
   dates.forEach { date->
    val occurrenceAt=date.atTime(start.hour,start.minute).atZone(zone).toInstant().toEpochMilli()
    val reminderAt=if(reminder) occurrenceAt-30*60*1000L else null
    val item=AgendaItem(title=title.trim(),kind=kind,dimension=dimension,scheduledAt=occurrenceAt,reminderAt=reminderAt,recurrenceRule=recurrenceRule)
    val id=db.agendaDao().insert(item)
    if(reminder && reminderAt!=null && occurrenceAt>System.currentTimeMillis()) scheduler.schedule(item.copy(id=id))
    LockScreenAgendaWallpaper.refresh(appContext, db)
   }
  }
 }
 fun updateAgenda(item:AgendaItem) {
  viewModelScope.launch {
   scheduler.cancel(item)
   db.agendaDao().update(item)
   if (item.reminderAt != null && item.scheduledAt > System.currentTimeMillis()) scheduler.schedule(item)
   LockScreenAgendaWallpaper.refresh(appContext, db)
  }
 }
 fun toggle(item:AgendaItem) { viewModelScope.launch {
  val completed = !item.completed
  db.agendaDao().setCompleted(item.id,completed)
  if (completed) scheduler.cancel(item) else scheduler.schedule(item.copy(completed = false))
  LockScreenAgendaWallpaper.refresh(appContext, db)
 } }
 fun delete(item:AgendaItem) { viewModelScope.launch { scheduler.cancel(item); db.agendaDao().delete(item); LockScreenAgendaWallpaper.refresh(appContext, db) } }
 fun saveDailyReflection(diary:String, confession:String) {
  if(diary.isBlank() && confession.isBlank()) return
  viewModelScope.launch {
   val dayKey=LocalDate.now().toString()
   val now=System.currentTimeMillis()
   val previousDiary=db.journalDao().findForDay(dayKey)
   val effectiveDiary=diary.trim().ifBlank { previousDiary?.content.orEmpty() }
   if(diary.isNotBlank()) {
    if(previousDiary==null) db.journalDao().insert(JournalEntry(title="Diário de hoje",content=diary.trim(),dimension=LifeDimension.INDIVIDUAL,dayKey=dayKey))
    else db.journalDao().update(previousDiary.copy(title="Diário de hoje",content=diary.trim(),updatedAt=now,dayKey=dayKey))
   } else if(previousDiary==null && confession.isNotBlank()) {
    db.journalDao().insert(JournalEntry(title="Registro diário",content="",dimension=LifeDimension.INDIVIDUAL,dayKey=dayKey))
   }
   val previousConfession=db.confessionDao().findForDay(dayKey)
   val effectiveConfession=if(confession.isNotBlank()) confession.trim() else previousConfession?.content.orEmpty()
   if(confession.isNotBlank()) {
    if(previousConfession==null) db.confessionDao().insert(ConfessionEntry(dayKey=dayKey,content=confession.trim()))
    else db.confessionDao().update(previousConfession.copy(content=confession.trim(),updatedAt=now))
   }
   val zone=ZoneId.systemDefault()
   val today=LocalDate.now(zone)
   val start=today.atStartOfDay(zone).toInstant().toEpochMilli()
   val end=today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1
   val agendaToday=agenda.value.filter { it.scheduledAt in start..end }
   val result=DailyContextAnalyzer.analyze(effectiveDiary,effectiveConfession,agendaToday.filterNot{it.completed}.map{it.title})
   db.dailyAnalysisDao().save(DailyAnalysisEntry(
    dayKey=dayKey,
    categories=result.categories.joinToString(" · "),
    dimensions=result.dimensions.joinToString(" · "),
    emotionalState=result.emotionalState,
    summary=result.summary,
    attention=result.attention,
    learning=result.learning,
    direction=result.direction,
    nextStep=result.nextStep,
    scriptureReference=result.scriptureReference,
    scriptureContext=result.scriptureContext,
    updatedAt=now
   ))
  }
 }
 fun saveDimensionRecord(title:String,body:String,dimension:LifeDimension) {
  if(title.isBlank() || body.isBlank()) return
  viewModelScope.launch(Dispatchers.IO) {
   db.journalDao().insert(JournalEntry(title=title.trim(),content=body.trim(),dimension=dimension,dayKey=LocalDate.now().toString()))
  }
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
 val vm=remember { AppViewModel(context.applicationContext,db,scheduler) }
 val agenda by vm.agenda.collectAsStateWithLifecycle()
 val journal by vm.journal.collectAsStateWithLifecycle()
 val reviews by vm.reviews.collectAsStateWithLifecycle()
 val confessions by vm.confessions.collectAsStateWithLifecycle()
 val analyses by vm.analyses.collectAsStateWithLifecycle()
 var cotidianoTab by remember { mutableStateOf("Agenda") }
 var showAiAssistant by remember { mutableStateOf(false) }
 var aiInitialDimension by remember { mutableStateOf("Visão integrada") }
 val sectionLabels = listOf("Painel","Devoção","Matrimônio","Profissional","Cotidiano")
 val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { sectionLabels.size })
 val pagerScope = rememberCoroutineScope()
 val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  if (result.resultCode == android.app.Activity.RESULT_OK) {
   val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
             else result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
   NotificationPreferences(context).setDailySound(uri?.toString() ?: NotificationPreferences.SILENT_SOUND)
  }
 }
 val eventRingtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  if (result.resultCode == android.app.Activity.RESULT_OK) {
   val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
             else result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
   NotificationPreferences(context).setEventSound(uri?.toString() ?: NotificationPreferences.SILENT_SOUND)
  }
 }
 Column(Modifier.fillMaxSize().background(DeepViolet)) {
  Column(
   Modifier.fillMaxWidth()
    .background(Brush.verticalGradient(listOf(VioletPrimary.copy(alpha=.94f), DeepViolet)))
    .padding(horizontal=20.dp, vertical=14.dp)
  ) {
   Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Image(
     painter=painterResource(id=R.drawable.metanoia_oficial),
     contentDescription="Símbolo oficial MEtanoia",
     modifier=Modifier.size(48.dp),
     contentScale=ContentScale.Fit
    )
    Column(Modifier.weight(1f)) {
     Text("MEtanoia",color=GoldBright,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
     Text("UMA MENTE RENOVADA",color=Ivory.copy(alpha=.86f),style=MaterialTheme.typography.labelSmall,letterSpacing=1.2.sp)
    }
    TextButton(onClick={aiInitialDimension=when(pagerState.currentPage){0->"Visão integrada";1->"Devoção";2->"Matrimônio";3->"Profissional";else->"Cotidiano"};showAiAssistant=true}) {
     Text("IA",color=DeepViolet,fontWeight=FontWeight.Bold,modifier=Modifier.background(GoldChampagne,MaterialTheme.shapes.large).padding(horizontal=15.dp,vertical=8.dp))
    }
   }
   Text(
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",Locale("pt","BR"))).replaceFirstChar { it.uppercase(Locale("pt","BR")) },
    color=Ivory,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp)
   )
   Text("Uma mente renovada. Uma vida intencional.",color=Ivory.copy(alpha=.86f),style=MaterialTheme.typography.bodyMedium)
  }
  Row(
   Modifier.fillMaxWidth().background(VioletSurface).horizontalScroll(rememberScrollState())
    .padding(horizontal=12.dp, vertical=9.dp),
   horizontalArrangement=Arrangement.spacedBy(8.dp),
   verticalAlignment=Alignment.CenterVertically
  ) {
   sectionLabels.forEachIndexed { index, label ->
    val selected = pagerState.currentPage == index
    TextButton(
     onClick={ pagerScope.launch { pagerState.animateScrollToPage(index) } },
     modifier=Modifier.background(
      color=if(selected) CardSurface else Color.Transparent,
      shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
     )
    ) {
     Text(
      label,
      color=if(selected) GoldBright else Ivory.copy(alpha=.76f),
      fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium,
      style=MaterialTheme.typography.labelMedium
     )
    }
   }
  }
  androidx.compose.foundation.pager.HorizontalPager(
   state = pagerState,
   modifier = Modifier.fillMaxSize().weight(1f)
  ) { page ->
   when(page) {
    0 -> DashboardPage(agenda, journal, analyses) { destination ->
     val target = when(destination) { "Devocional" -> 1; "Matrimônio" -> 2; "Profissional" -> 3; else -> 4 }
     pagerScope.launch { pagerState.animateScrollToPage(target) }
    }
    1 -> SpiritualDimensionPage(journal.filter{it.dimension==LifeDimension.SPIRITUAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.SPIRITUAL) }
    2 -> MatrimonialDimensionPage(journal.filter{it.dimension==LifeDimension.MATRIMONIAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.MATRIMONIAL) }
    3 -> ProfessionalDimensionPage(journal.filter{it.dimension==LifeDimension.PROFESSIONAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.PROFESSIONAL) }
    else -> Column(Modifier.fillMaxSize().background(DeepViolet)) {
     Row(Modifier.fillMaxWidth().background(VioletSurface).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
      listOf("Agenda","Diário","Revisões","Alertas").forEach { label ->
       TextButton(onClick={cotidianoTab=label}) { Text(label,color=if(cotidianoTab==label) GoldBright else Ivory,fontWeight=if(cotidianoTab==label) FontWeight.Bold else FontWeight.Normal) }
      }
     }
     when(cotidianoTab) {
      "Agenda" -> AgendaPage(agenda,vm)
      "Diário" -> JournalPage(journal,confessions,analyses,agenda,vm)
      "Revisões" -> ReviewPage(reviews,vm)
      else -> NotificationSettingsPage(context,agenda,{
       val prefs=NotificationPreferences(context)
       val pick=Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Escolha o som das notificações diárias")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,if(prefs.dailySound()==NotificationPreferences.SYSTEM_SOUND) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(prefs.dailySound()))
       }
       ringtoneLauncher.launch(pick)
      },{
       val prefs=NotificationPreferences(context)
       val pick=Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Escolha o som dos alarmes de compromissos")
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,if(prefs.eventSound()==NotificationPreferences.SYSTEM_SOUND) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(prefs.eventSound()))
       }
       eventRingtoneLauncher.launch(pick)
      })
     }
    }
   }
  }
  if(showAiAssistant) {
   AiAssistantDialog(context,journal,confessions,agenda,aiInitialDimension) { showAiAssistant=false }
  }
 }
}
@Composable private fun AgendaPage(items:List<AgendaItem>,vm:AppViewModel) {
 val context=LocalContext.current
 val zone=remember { ZoneId.systemDefault() }
 var selectedDate by remember { mutableStateOf(LocalDate.now()) }
 var editingItem by remember { mutableStateOf<AgendaItem?>(null) }
 var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
 val lifecycleOwner = LocalLifecycleOwner.current
 var lastObservedDate by remember { mutableStateOf(LocalDate.now(zone)) }
 LaunchedEffect(lifecycleOwner) {
  lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
   while (isActive) {
    val today = LocalDate.now(zone)
    if (today != lastObservedDate) {
     if (selectedDate == lastObservedDate) selectedDate = today
     if (displayedMonth == YearMonth.from(lastObservedDate)) displayedMonth = YearMonth.from(today)
     lastObservedDate = today
    }
    delay(30_000)
   }
  }
 }
 var title by remember { mutableStateOf("") }
 var kind by remember { mutableStateOf(AgendaKind.TASK) }
 var recurrenceFrequency by remember { mutableStateOf<RecurrenceFrequency?>(null) }
 var dimension by remember { mutableStateOf(LifeDimension.ROUTINE) }
 var reminder by remember { mutableStateOf(true) }
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
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp, vertical=10.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column {
    Text("Minha agenda",style=MaterialTheme.typography.headlineSmall,color=GoldBright,fontWeight=FontWeight.Bold)
    Text("Organize seus dias com intenção.",color=Ivory.copy(alpha=.78f),style=MaterialTheme.typography.bodySmall)
   }
   TextButton(onClick={selectedDate=LocalDate.now();displayedMonth=YearMonth.now()}) { Text("Hoje",color=Gold) }
  }
  Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=CardSurface)) {
   Column(Modifier.padding(12.dp)) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     Text(displayedMonth.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("pt","BR")).replaceFirstChar { it.uppercase() }+" "+displayedMonth.year,color=GoldBright,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
     Row {
      IconButton(onClick={displayedMonth=displayedMonth.minusMonths(1)}) { Text("‹",color=GoldBright,style=MaterialTheme.typography.headlineMedium) }
      IconButton(onClick={displayedMonth=displayedMonth.plusMonths(1)}) { Text("›",color=GoldBright,style=MaterialTheme.typography.headlineMedium) }
     }
    }
    Row(Modifier.fillMaxWidth()) {
     weekdays.forEach { day->Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { Text(day,color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.labelSmall) } }
    }
    cells.chunked(7).forEach { week->
     Row(Modifier.fillMaxWidth()) {
      week.forEach { date->
       Box(Modifier.weight(1f).height(43.dp).padding(2.dp),contentAlignment=Alignment.Center) {
        if(date!=null) {
         val isSelected=date==selectedDate
         val isToday=date==LocalDate.now()
         val count=monthItems[date]?.size?:0
         Column(Modifier.fillMaxSize().background(if(isSelected) VioletPrimary else Color.Transparent,MaterialTheme.shapes.small).clickable { selectedDate=date }.padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
          Text(date.dayOfMonth.toString(),color=Ivory,fontWeight=if(isSelected||isToday) FontWeight.Bold else FontWeight.Normal)
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
     Text("• Registros indicados em dourado",style=MaterialTheme.typography.labelSmall,color = Ivory.copy(alpha = .78f))
     Text("${items.size} registros",style=MaterialTheme.typography.labelSmall,color=Gold)
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column {
    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",java.util.Locale("pt","BR"))).replaceFirstChar { it.uppercase() },color=GoldBright,fontWeight=FontWeight.Bold)
    Text("${selectedItems.count{!it.completed}} pendentes · ${selectedItems.count{it.completed}} concluídos",color=Gold,style=MaterialTheme.typography.labelSmall)
   }
   TextButton(onClick={selectedDate=selectedDate.plusDays(1);if(YearMonth.from(selectedDate)!=displayedMonth) displayedMonth=YearMonth.from(selectedDate)}) { Text("Próximo dia  ›",color=Navy) }
  }
  Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("Nova tarefa, hábito ou compromisso")},singleLine=true)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     AgendaKind.entries.forEach { value->FilterChip(selected=kind==value,onClick={kind=value;if(value!=AgendaKind.HABIT) recurrenceFrequency=null},label={Text(kindLabel(value))}) }
    }
    if(kind==AgendaKind.HABIT) {
     Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
      Text("Repetição do hábito",color=GoldBright,style=MaterialTheme.typography.labelMedium)
      Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
       FilterChip(selected=recurrenceFrequency==null,onClick={recurrenceFrequency=null},label={Text("Único")})
       FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.DAILY,onClick={recurrenceFrequency=RecurrenceFrequency.DAILY},label={Text("Diário")})
       FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.WEEKDAYS,onClick={recurrenceFrequency=RecurrenceFrequency.WEEKDAYS},label={Text("Dias úteis")})
      }
      Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
       FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.WEEKLY,onClick={recurrenceFrequency=RecurrenceFrequency.WEEKLY},label={Text("Semanal")})
       FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.MONTHLY,onClick={recurrenceFrequency=RecurrenceFrequency.MONTHLY},label={Text("Mensal")})
      }
      Text("As próximas ocorrências serão organizadas automaticamente.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.labelSmall)
     }
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
      Text("Lembretes: 2h, 1h e 30 min antes",style=MaterialTheme.typography.labelSmall,color=Navy)
     }
    }
    Button(onClick={
     val scheduled=selectedDate.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
     vm.addAgenda(title,kind,dimension,scheduled,reminder,recurrenceFrequency?.name)
     title=""
     reminder=true
    },enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor = VioletPrimary)) {
     Text("Adicionar em ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM"))}")
    }
   }
  }
  if(selectedItems.isEmpty()) {
   Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
    Text("Nenhum registro para este dia.\nEscolha outra data ou adicione um compromisso.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodyMedium)
   }
  } else {
   LazyColumn(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    items(selectedItems,key={it.id}) { item->
     Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
      Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
       Checkbox(checked=item.completed,onCheckedChange={vm.toggle(item)})
       Column(Modifier.weight(1f)) {
        Text(item.title,color=GoldBright,fontWeight=FontWeight.SemiBold)
        val itemTime=Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
        Text("%02d:%02d".format(itemTime.hour,itemTime.minute)+" · "+kindLabel(item.kind)+" · "+dimensionLabel(item.dimension),style=MaterialTheme.typography.labelSmall,color = Ivory.copy(alpha = .78f))
       }
       Column(horizontalAlignment=Alignment.End) {
        TextButton(onClick={editingItem=item}) { Text("Editar",color=Gold) }
        TextButton(onClick={vm.delete(item)}) { Text("Excluir",color = Ivory.copy(alpha = .78f)) }
       }
      }
     }
    }
   }
  }
 }
 if (editingItem != null) {
  EditAgendaDialog(item=editingItem!!, onDismiss={editingItem=null}, onSave={updated->vm.updateAgenda(updated);editingItem=null})
 }
}

@Composable private fun EditAgendaDialog(item:AgendaItem,onDismiss:()->Unit,onSave:(AgendaItem)->Unit) {
 val context=LocalContext.current
 val zone=remember { ZoneId.systemDefault() }
 var title by remember(item.id) { mutableStateOf(item.title) }
 var kind by remember(item.id) { mutableStateOf(item.kind) }
 var dimension by remember(item.id) { mutableStateOf(item.dimension) }
 var date by remember(item.id) { mutableStateOf(Instant.ofEpochMilli(item.scheduledAt).atZone(zone).toLocalDate()) }
 var hour by remember(item.id) { mutableIntStateOf(Instant.ofEpochMilli(item.scheduledAt).atZone(zone).hour) }
 var minute by remember(item.id) { mutableIntStateOf(Instant.ofEpochMilli(item.scheduledAt).atZone(zone).minute) }
 var reminder by remember(item.id) { mutableStateOf(item.reminderAt != null) }
 var dimensionMenu by remember { mutableStateOf(false) }
 AlertDialog(
  onDismissRequest=onDismiss,
  title={Text("Editar registro",color=GoldBright,fontWeight=FontWeight.Bold)},
  text={
   Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(value=title,onValueChange={title=it},label={Text("Título")},singleLine=true)
    Text("Tipo",color=GoldBright,fontWeight=FontWeight.SemiBold)
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
     AgendaKind.entries.forEach { v->FilterChip(selected=kind==v,onClick={kind=v},label={Text(kindLabel(v))}) }
    }
    Box {
     OutlinedButton(onClick={dimensionMenu=true}) { Text(dimensionLabel(dimension),color=Navy) }
     DropdownMenu(expanded=dimensionMenu,onDismissRequest={dimensionMenu=false}) {
      LifeDimension.entries.forEach { v->DropdownMenuItem(text={Text(dimensionLabel(v))},onClick={dimension=v;dimensionMenu=false}) }
     }
    }
    OutlinedButton(onClick={
     DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).show()
    }) { Text("Data: "+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),color=Navy) }
    OutlinedButton(onClick={
     TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()
    }) { Text("Horário: %02d:%02d".format(hour,minute),color=Navy) }
    Row(verticalAlignment=Alignment.CenterVertically) {
     Checkbox(checked=reminder,onCheckedChange={reminder=it})
     Text("Lembretes 2 horas, 1 hora e 30 minutos antes",color=Navy)
    }
   }
  },
  confirmButton={
   TextButton(enabled=title.isNotBlank(),onClick={
    val scheduled=date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    val reminderAt=if(reminder) scheduled-30*60*1000L else null
    onSave(item.copy(title=title.trim(),kind=kind,dimension=dimension,scheduledAt=scheduled,reminderAt=reminderAt))
   }) { Text("Salvar alterações",color=Gold) }
  },
  dismissButton={TextButton(onClick=onDismiss) { Text("Cancelar",color = Ivory.copy(alpha = .78f)) }}
 )
}


@Composable private fun NotificationSettingsPage(context:Context, items:List<AgendaItem>, onPickDailySound:()->Unit, onPickEventSound:()->Unit) {
 val prefs = remember { NotificationPreferences(context) }
 val scheduler = remember { ReminderScheduler(context) }
 var d7 by remember { mutableStateOf(prefs.dailyEnabled(7)) }
 var d12 by remember { mutableStateOf(prefs.dailyEnabled(12)) }
 var d18 by remember { mutableStateOf(prefs.dailyEnabled(18)) }
 var l120 by remember { mutableStateOf(prefs.leadEnabled(120)) }
 var l60 by remember { mutableStateOf(prefs.leadEnabled(60)) }
 var l30 by remember { mutableStateOf(prefs.leadEnabled(30)) }
 var eventSound by remember { mutableStateOf(prefs.eventSound()) }
 var dailySound by remember { mutableStateOf(prefs.dailySound()) }
 val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
  (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
 Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("Notificações e alarmes",style=MaterialTheme.typography.headlineSmall,color=GoldBright,fontWeight=FontWeight.Bold)
  Text("Configure cada lembrete e mantenha sua agenda sob controle.",color = Ivory.copy(alpha = .78f))
  val notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
  Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text(if(notificationsEnabled) "NOTIFICAÇÕES ATIVAS" else "NOTIFICAÇÕES DESATIVADAS",color=if(notificationsEnabled) GoldBright else Color(0xFFFF9A8B),fontWeight=FontWeight.Bold)
    Text(
     if(notificationsEnabled) "Os lembretes do MEtanoia podem ser exibidos pelo Android. Para vê-los na tela bloqueada, permita notificações e conteúdo na tela de bloqueio nas configurações do aparelho."
     else "O Android está bloqueando as notificações do aplicativo. Ative-as para receber os lembretes dos seus compromissos.",
     color=Ivory,style=MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick={
     context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    },modifier=Modifier.fillMaxWidth()) { Text("Configurar notificações e tela de bloqueio") }
   }
  }
  Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(14.dp)) {
    Text("LEMBRETES DIÁRIOS",color=Gold,fontWeight=FontWeight.Bold)
    listOf(Triple("07:00 · Planeje seu dia",d7,7),Triple("12:00 · Confira sua agenda",d12,12),Triple("18:00 · Organize o restante",d18,18)).forEach { row ->
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
      Text(row.first,color=Navy)
      Switch(checked=row.second,onCheckedChange={enabled->
       prefs.setDailyEnabled(row.third,enabled)
       when(row.third){7->d7=enabled;12->d12=enabled;18->d18=enabled}
       scheduler.cancelDaily(row.third)
       if(enabled) scheduler.scheduleNextDailyReminder(row.third)
      })
     }
    }
   }
  }
  Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(14.dp)) {
    Text("ALARMES DE COMPROMISSOS",color=Gold,fontWeight=FontWeight.Bold)
    listOf(Triple("2 horas antes",l120,120),Triple("1 hora antes",l60,60),Triple("30 minutos antes",l30,30)).forEach { row ->
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
      Text(row.first,color=Navy)
      Switch(checked=row.second,onCheckedChange={enabled->
       prefs.setLeadEnabled(row.third,enabled)
       when(row.third){120->l120=enabled;60->l60=enabled;30->l30=enabled}
       items.filter{it.reminderAt!=null}.forEach(scheduler::schedule)
      })
     }
    }
   }
  }
  Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    Text("TOQUES",color=Gold,fontWeight=FontWeight.Bold)
    Text("Som diário: "+when(dailySound){NotificationPreferences.SYSTEM_SOUND->"Padrão do sistema";NotificationPreferences.SILENT_SOUND->"Silencioso";else->"Personalizado"},color=Navy)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     OutlinedButton(onClick=onPickDailySound){Text("Escolher toque")}
     OutlinedButton(onClick={prefs.setDailySound(NotificationPreferences.SYSTEM_SOUND);dailySound=prefs.dailySound()}){Text("Padrão")}
     OutlinedButton(onClick={prefs.setDailySound(NotificationPreferences.SILENT_SOUND);dailySound=prefs.dailySound()}){Text("Silencioso")}
     OutlinedButton(onClick={ val uri=when(dailySound){NotificationPreferences.SILENT_SOUND->null;NotificationPreferences.SYSTEM_SOUND->RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);else->Uri.parse(dailySound)}; if(uri!=null) RingtoneManager.getRingtone(context,uri)?.play() }){Text("Testar")}
    }
    Text("Som dos compromissos: "+when(eventSound){NotificationPreferences.SYSTEM_SOUND->"Padrão do sistema";NotificationPreferences.SILENT_SOUND->"Silencioso";else->"Personalizado"},color=Navy)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     OutlinedButton(onClick=onPickEventSound){Text("Escolher toque")}
     OutlinedButton(onClick={prefs.setEventSound(NotificationPreferences.SYSTEM_SOUND);eventSound=prefs.eventSound()}){Text("Padrão")}
     OutlinedButton(onClick={prefs.setEventSound(NotificationPreferences.SILENT_SOUND);eventSound=prefs.eventSound()}){Text("Silencioso")}
     OutlinedButton(onClick={ val uri=when(eventSound){NotificationPreferences.SILENT_SOUND->null;NotificationPreferences.SYSTEM_SOUND->RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);else->Uri.parse(eventSound)}; if(uri!=null) RingtoneManager.getRingtone(context,uri)?.play() }){Text("Testar")}
    }
    Text("O volume é controlado pelos botões e configurações de som do Android.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
   }
  }
  if(!exactAllowed && Build.VERSION.SDK_INT>=Build.VERSION_CODES.S) {
   Card(colors=CardDefaults.cardColors(containerColor=CardSurface)) {
    Column(Modifier.padding(12.dp)) {
     Text("Horário exato limitado pelo Android",color=GoldBright,fontWeight=FontWeight.Bold)
     Text("Sem esta permissão, o sistema pode atrasar alarmes durante a economia de bateria.",color=Ivory,style=MaterialTheme.typography.bodySmall)
     TextButton(onClick={context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))}){Text("Configurar alarmes exatos",color=Gold)}
    }
   }
  } else {
   Text("Alarmes exatos permitidos neste aparelho.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
  }
  Text("Os sons escolhidos são aplicados por canais de notificação. Você também pode ajustar o volume no Android.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
 }
}

@Composable private fun JournalPage(
 entries:List<JournalEntry>,
 confessions:List<ConfessionEntry>,
 analyses:List<DailyAnalysisEntry>,
 agenda:List<AgendaItem>,
 vm:AppViewModel
) {
 val todayKey=LocalDate.now().toString()
 val todayEntry=entries.firstOrNull{it.dayKey==todayKey && it.dimension==LifeDimension.INDIVIDUAL}
 val todayConfession=confessions.firstOrNull{it.dayKey==todayKey}
 val todayAnalysis=analyses.firstOrNull{it.dayKey==todayKey}
 var diary by remember(todayKey,entries) { mutableStateOf(todayEntry?.content.orEmpty()) }
 var confession by remember(todayKey,confessions) { mutableStateOf(todayConfession?.content.orEmpty()) }
 var confessionExpanded by remember(todayKey,confessions) { mutableStateOf(todayConfession!=null) }
 val violet=Ivory
 val gold=GoldChampagne
 val todayAgenda=agenda.filter {
  Instant.ofEpochMilli(it.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()
 }
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)) {
  Text("Diário de hoje",style=MaterialTheme.typography.headlineSmall,color=GoldBright,fontWeight=FontWeight.Bold)
  Text("Um único relato para compreender o dia por inteiro.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
  Card(Modifier.fillMaxWidth().padding(top=12.dp),colors=CardDefaults.cardColors(containerColor=violet)) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Text("DIÁRIO DE HOJE",color=gold,fontWeight=FontWeight.Bold)
    Text("Como foi seu dia? Conte o que aconteceu, o que sentiu, o que aprendeu e o que gostaria de compartilhar.",color=Color.White)
    OutlinedTextField(
     value=diary,onValueChange={diary=it},modifier=Modifier.fillMaxWidth().heightIn(min=170.dp),
     placeholder={Text("Escreva livremente. Não precisa separar por áreas.")},
     minLines=6,colors=OutlinedTextFieldDefaults.colors(
      focusedTextColor=Color.White,unfocusedTextColor=Color.White,
      focusedBorderColor=gold,unfocusedBorderColor=Color.White.copy(alpha=.55f),
      focusedLabelColor=gold,unfocusedLabelColor=Color.White.copy(alpha=.75f),
      cursorColor=gold
     )
    )
    TextButton(onClick={confessionExpanded=!confessionExpanded}) {
     Text(if(confessionExpanded) "Ocultar confissão opcional" else "＋ Incluir confissão diária (opcional)",color=gold)
    }
    if(confessionExpanded) {
     Text("O que você deseja revelar, reconhecer ou colocar em palavras?",color=Color.White)
     OutlinedTextField(
      value=confession,onValueChange={confession=it},modifier=Modifier.fillMaxWidth().heightIn(min=110.dp),
      placeholder={Text("Pode ser uma emoção, uma preocupação, uma dúvida, um erro ou qualquer coisa que queira compartilhar.")},
      minLines=4,colors=OutlinedTextFieldDefaults.colors(
       focusedTextColor=Color.White,unfocusedTextColor=Color.White,
       focusedBorderColor=gold,unfocusedBorderColor=Color.White.copy(alpha=.55f),
       cursorColor=gold
      )
     )
    }
    Text("A agenda do dia fornece contexto, sem presumir que um compromisso foi realizado.",color=Color.White.copy(alpha=.75f),style=MaterialTheme.typography.bodySmall)
    Button(
     onClick={vm.saveDailyReflection(diary,if(confessionExpanded) confession else "")},
     enabled=diary.isNotBlank() || (confessionExpanded && confession.isNotBlank()),
     modifier=Modifier.fillMaxWidth(),
     colors=ButtonDefaults.buttonColors(containerColor=gold,contentColor=violet)
    ) { Text("Salvar e consolidar meu dia",fontWeight=FontWeight.Bold) }
   }
  }
  if(todayAnalysis!=null) {
   Card(Modifier.fillMaxWidth().padding(top=12.dp),colors=CardDefaults.cardColors(containerColor=CardSurface)) {
    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("LEITURA CONTEXTUAL DO DIA",color=gold,fontWeight=FontWeight.Bold)
     Text("O que aconteceu hoje",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.summary,color=violet)
     Text("Como você está",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.emotionalState,color=violet)
     Text("Dimensões identificadas",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.dimensions.ifBlank{"Nenhuma dimensão específica foi identificada."},color=violet)
     Text("Pontos de atenção",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.attention,color=violet)
     Text("Aprendizados",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.learning,color=violet)
     Text("Direcionamento",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.direction,color=violet)
     if(todayAnalysis.scriptureReference.isNotBlank()) {
      Text(todayAnalysis.scriptureReference,color=gold,fontWeight=FontWeight.Bold)
      Text(todayAnalysis.scriptureContext,color=violet,style=MaterialTheme.typography.bodySmall)
     }
     Text("Próximo passo",color=GoldBright,fontWeight=FontWeight.SemiBold)
     Text(todayAnalysis.nextStep,color=violet)
     Text("Leitura contextual local; não substitui aconselhamento pastoral, psicológico ou profissional.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.labelSmall)
    }
   }
  }
  if(todayAgenda.isNotEmpty()) {
   Text("Compromissos previstos hoje",modifier=Modifier.padding(top=12.dp),color=GoldBright,fontWeight=FontWeight.SemiBold)
   todayAgenda.sortedBy{it.scheduledAt}.forEach { item->
    Text("• "+Instant.ofEpochMilli(item.scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))+"  "+item.title+(if(item.completed) " · concluído" else " · previsto"),color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
   }
  }
  Text("Histórico do diário",modifier=Modifier.padding(top=14.dp,bottom=6.dp),color=GoldBright,fontWeight=FontWeight.Bold)
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   items(entries.filter{it.dayKey!=todayKey && it.dimension==LifeDimension.INDIVIDUAL},key={it.id}) { entry->
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor = CardSurface)) {
     Column(Modifier.padding(12.dp)) {
      Text(if(entry.dayKey.isBlank()) formatDate(entry.createdAt) else entry.dayKey,color=gold,fontWeight=FontWeight.Bold)
      Text(entry.content,color=violet,maxLines=5)
      confessions.firstOrNull{it.dayKey==entry.dayKey}?.let { old->
       Text("Confissão integrada",color=gold,fontWeight=FontWeight.SemiBold)
       Text(old.content,color=violet,maxLines=3)
      }
      analyses.firstOrNull{it.dayKey==entry.dayKey}?.let { oldAnalysis->
       Text("Análise consolidada",color=gold,fontWeight=FontWeight.SemiBold)
       Text(oldAnalysis.dimensions.ifBlank{"Dimensões não identificadas"},color=violet,style=MaterialTheme.typography.bodySmall)
       Text(oldAnalysis.attention,color=violet,style=MaterialTheme.typography.bodySmall,maxLines=3)
       Text("Próximo passo: "+oldAnalysis.nextStep,color=violet,style=MaterialTheme.typography.bodySmall,maxLines=3)
      }
     }
    }
   }
  }
 }
}
@Composable private fun ReviewPage(entries:List<ReviewEntry>,vm:AppViewModel) {
 var period by remember { mutableStateOf(ReviewPeriod.WEEKLY) }; var body by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Revisões históricas",style=MaterialTheme.typography.headlineSmall,color=GoldBright,fontWeight=FontWeight.Bold)
  Text("Observe sua caminhada sem transformar registros em julgamento.")
  Row { ReviewPeriod.entries.forEach { v->FilterChip(period==v,{period=v},label={Text(if(v==ReviewPeriod.WEEKLY) "Semanal" else "Mensal")}) } }
  OutlinedTextField(body,{body=it},Modifier.fillMaxWidth().heightIn(min=120.dp),label={Text("O que aprendi? O que merece atenção? Qual será meu próximo passo?")})
  Button(onClick={vm.addReview(period,body).also{body=""}},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor = VioletPrimary)) { Text("Salvar revisão") }
  val periodEntries=entries.filter { it.period==period }.sortedByDescending { it.periodStart }
  if(periodEntries.size>=2) {
   Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor = CardSurface)) {
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("Comparação com a revisão anterior",color=GoldBright,fontWeight=FontWeight.Bold)
     Text("Observe mudanças, temas recorrentes e decisões registradas. Esta comparação não atribui notas nem mede seu valor pessoal.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodySmall)
     Text("Mais recente · "+formatDate(periodEntries[0].periodStart),color=Gold,fontWeight=FontWeight.SemiBold)
     Text(periodEntries[0].reflection,color=Ivory)
     Divider(color=Paper)
     Text("Anterior · "+formatDate(periodEntries[1].periodStart),color=Gold,fontWeight=FontWeight.SemiBold)
     Text(periodEntries[1].reflection,color=Ivory)
    }
   }
  }
  LazyColumn { items(entries,key={it.id}) { e->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(12.dp)) { Text(if(e.period==ReviewPeriod.WEEKLY) "Revisão semanal" else "Revisão mensal",color=GoldBright,fontWeight=FontWeight.Bold); Text(formatDate(e.periodStart),color=Gold); Text(e.reflection) }
  } } }
 }
}
private fun dimensionLabel(v:LifeDimension)=when(v){LifeDimension.SPIRITUAL->"Espiritual";LifeDimension.MATRIMONIAL->"Matrimonial";LifeDimension.PROFESSIONAL->"Profissional";LifeDimension.INDIVIDUAL->"Individual";LifeDimension.ROUTINE->"Agenda e rotina"}
private fun kindLabel(v:AgendaKind)=when(v){AgendaKind.TASK->"Tarefa";AgendaKind.APPOINTMENT->"Compromisso";AgendaKind.HABIT->"Hábito"}
private fun formatDate(value:Long)=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
