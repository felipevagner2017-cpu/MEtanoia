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
import java.util.UUID

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
  // Official MEtanoia navy, gold and white Material 3 system.
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
 fun addAgenda(
  title:String,
  description:String,
  location:String,
  kind:AgendaKind,
  dimension:LifeDimension,
  scheduledAt:Long,
  endAt:Long?,
  reminderOffsets:Set<Int>,
  recurrenceRule:String?
 ) {
  if(title.isBlank() || (endAt!=null && endAt<=scheduledAt)) return
  viewModelScope.launch {
   val zone=ZoneId.systemDefault()
   val start=Instant.ofEpochMilli(scheduledAt).atZone(zone)
   val offsets=reminderOffsets.filter { it in listOf(120,60,30) }.distinct().sortedDescending()
   val offsetsCsv=offsets.joinToString(",")
   val duration=endAt?.minus(scheduledAt)
   val dates=if(recurrenceRule!=null) {
    RecurrenceEngine.datesUntil(start.toLocalDate(),RecurrenceFrequency.valueOf(recurrenceRule),start.toLocalDate().plusDays(365))
   } else listOf(start.toLocalDate())
   val seriesId=if(recurrenceRule!=null) UUID.randomUUID().toString() else null
   val now=System.currentTimeMillis()
   dates.forEach { date->
    val occurrenceAt=date.atTime(start.hour,start.minute).atZone(zone).toInstant().toEpochMilli()
    val occurrenceEndAt=duration?.let { occurrenceAt+it }
    val reminderAt=offsets.maxOrNull()?.let { occurrenceAt-it*60_000L }
    val item=AgendaItem(
     title=title.trim(),description=description.trim(),kind=kind,dimension=dimension,
     scheduledAt=occurrenceAt,reminderAt=reminderAt,recurrenceRule=recurrenceRule,
     location=location.trim(),endAt=occurrenceEndAt,reminderOffsets=offsetsCsv,seriesId=seriesId
    )
    val id=db.agendaDao().insert(item)
    if(reminderAt!=null && occurrenceAt>now) scheduler.schedule(item.copy(id=id))
   }
   LockScreenAgendaWallpaper.refresh(appContext, db)
  }
 }
 fun updateAgenda(item:AgendaItem, applyToSeries:Boolean=false) {
  if(item.title.isBlank() || (item.endAt!=null && item.endAt<=item.scheduledAt)) return
  viewModelScope.launch {
   val offsets=item.reminderOffsets.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in listOf(120,60,30) }.distinct().sortedDescending()
   val offsetsCsv=offsets.joinToString(",")
   val duration=item.endAt?.minus(item.scheduledAt)
   val reminderFor:(Long)->Long?={ start->offsets.maxOrNull()?.let { start-it*60_000L } }
   if(applyToSeries && item.seriesId!=null) {
    val series=db.agendaDao().findSeries(item.seriesId)
    val reference=series.firstOrNull { it.id==item.id }
    if(reference!=null) {
     val shift=item.scheduledAt-reference.scheduledAt
     val updated=series.map { occurrence->
      val newStart=occurrence.scheduledAt+shift
      occurrence.copy(
       title=item.title.trim(),description=item.description,location=item.location,
       kind=item.kind,dimension=item.dimension,scheduledAt=newStart,
       endAt=duration?.let { newStart+it },reminderOffsets=offsetsCsv,
       reminderAt=reminderFor(newStart)
      )
     }
     updated.forEach { scheduler.cancel(it) }
     db.agendaDao().updateAll(updated)
     updated.forEach { if(it.reminderAt!=null && it.scheduledAt>System.currentTimeMillis() && !it.completed) scheduler.schedule(it) }
    }
   } else {
    val updated=item.copy(
     title=item.title.trim(),reminderOffsets=offsetsCsv,reminderAt=reminderFor(item.scheduledAt),
     seriesId=null,recurrenceRule=null
    )
    scheduler.cancel(item)
    db.agendaDao().update(updated)
    if(updated.reminderAt!=null && updated.scheduledAt>System.currentTimeMillis() && !updated.completed) scheduler.schedule(updated)
   }
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
 fun deleteSeries(item:AgendaItem) { viewModelScope.launch {
  val seriesId=item.seriesId
  if(seriesId==null){scheduler.cancel(item);db.agendaDao().delete(item)}
  else {
   db.agendaDao().findSeries(seriesId).forEach { scheduler.cancel(it) }
   db.agendaDao().deleteSeries(seriesId)
  }
  LockScreenAgendaWallpaper.refresh(appContext, db)
 } }
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
 var cotidianoTab by remember { mutableStateOf("Diário") }
 var showAiAssistant by remember { mutableStateOf(false) }
 var aiInitialDimension by remember { mutableStateOf("Visão integrada") }
 val sectionLabels = listOf("Jornada diária","Áreas","Agenda")
 val areaLabels = listOf("Devocional","Matrimônio","Profissional","Cotidiano")
 val areaPagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { areaLabels.size })
 val areaPagerScope = rememberCoroutineScope()
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
     Text(
      "MEtonoia",
      color=GoldBright,
      style=MaterialTheme.typography.headlineMedium,
      fontWeight=FontWeight.Bold,
      modifier=Modifier.clickable {
       aiInitialDimension=when(pagerState.currentPage){0->"Jornada diária";1->areaLabels[areaPagerState.currentPage];else->"Agenda"}
       showAiAssistant=true
      }
     )
     Text("UMA MENTE RENOVADA",color=Ivory.copy(alpha=.86f),style=MaterialTheme.typography.labelSmall,letterSpacing=1.2.sp)
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
    0 -> DailyJourneyPage(analyses)
    1 -> Column(Modifier.fillMaxSize().background(DeepViolet)) {
     Row(
      Modifier.fillMaxWidth().background(VioletSurface).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp, vertical=6.dp),
      horizontalArrangement=Arrangement.spacedBy(3.dp),
      verticalAlignment=Alignment.CenterVertically
     ) {
      areaLabels.forEachIndexed { index, label ->
       TextButton(
        onClick={ areaPagerScope.launch { areaPagerState.animateScrollToPage(index) } },
        modifier=Modifier.background(if(areaPagerState.currentPage==index) CardSurface else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
       ) {
        Text(label,color=if(areaPagerState.currentPage==index) GoldBright else Ivory.copy(alpha=.78f),fontWeight=if(areaPagerState.currentPage==index) FontWeight.Bold else FontWeight.Medium,style=MaterialTheme.typography.labelMedium)
       }
      }
     }
     androidx.compose.foundation.pager.HorizontalPager(
      state=areaPagerState,
      modifier=Modifier.fillMaxSize().weight(1f)
     ) { areaPage ->
      when(areaPage) {
       0 -> SpiritualDimensionPage(journal.filter{it.dimension==LifeDimension.SPIRITUAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.SPIRITUAL) }
       1 -> MatrimonialDimensionPage(journal.filter{it.dimension==LifeDimension.MATRIMONIAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.MATRIMONIAL) }
       2 -> ProfessionalDimensionPage(journal.filter{it.dimension==LifeDimension.PROFESSIONAL}) { title,body -> vm.saveDimensionRecord(title,body,LifeDimension.PROFESSIONAL) }
       3 -> Column(Modifier.fillMaxSize().background(DeepViolet)) {
        Row(Modifier.fillMaxWidth().background(VioletSurface).horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
         listOf("Diário","Revisões","Alertas").forEach { label ->
          TextButton(onClick={cotidianoTab=label}) { Text(label,color=if(cotidianoTab==label) GoldBright else Ivory,fontWeight=if(cotidianoTab==label) FontWeight.Bold else FontWeight.Normal) }
         }
        }
        when(cotidianoTab) {
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
    }
    2 -> AgendaPage(agenda,vm)
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
  var calendarMode by remember { mutableStateOf("MÊS") }
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
 var description by remember { mutableStateOf("") }
 var location by remember { mutableStateOf("") }
 var kind by remember { mutableStateOf(AgendaKind.TASK) }
 var recurrenceFrequency by remember { mutableStateOf<RecurrenceFrequency?>(null) }
 var dimension by remember { mutableStateOf(LifeDimension.ROUTINE) }
 var reminderOffsets by remember { mutableStateOf(setOf(120,60,30)) }
 var hour by remember { mutableIntStateOf(9) }
 var minute by remember { mutableIntStateOf(0) }
 var endTimeEnabled by remember { mutableStateOf(false) }
 var endHour by remember { mutableIntStateOf(10) }
 var endMinute by remember { mutableIntStateOf(0) }
 var dimensionMenu by remember { mutableStateOf(false) }
 val first=displayedMonth.atDay(1)
 val leading=first.dayOfWeek.value-1
 val dayCount=displayedMonth.lengthOfMonth()
 val cells=buildList<LocalDate?> {
  repeat(leading) { add(null) }
  for(dayNumber in 1..dayCount) add(displayedMonth.atDay(dayNumber))
  while(size%7!=0) add(null)
 }
 val weekStart=selectedDate.minusDays((selectedDate.dayOfWeek.value-1).toLong())
 val weekDates=(0..6).map { weekStart.plusDays(it.toLong()) }
 val weekEnd=weekDates.last()
 val selectedItems=items.filter { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate()==selectedDate }.sortedBy { it.scheduledAt }
 val weekItems=items.filter {
  val date=Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate()
  !date.isBefore(weekStart) && !date.isAfter(weekEnd)
 }.sortedBy { it.scheduledAt }
 val displayedItems=if(calendarMode=="SEMANA") weekItems else selectedItems
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
    val calendarTitle=when(calendarMode) {
      "DIA" -> selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",java.util.Locale("pt","BR"))).replaceFirstChar { it.uppercase() }
      "SEMANA" -> "Semana · "+weekStart.format(DateTimeFormatter.ofPattern("dd/MM"))+" – "+weekDates.last().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
      else -> displayedMonth.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("pt","BR")).replaceFirstChar { it.uppercase() }+" "+displayedMonth.year
     }
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
      Text(calendarTitle,color=GoldBright,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
      Row {
       IconButton(onClick={
        when(calendarMode) {
         "DIA" -> selectedDate=selectedDate.minusDays(1)
         "SEMANA" -> selectedDate=selectedDate.minusWeeks(1)
         else -> displayedMonth=displayedMonth.minusMonths(1)
        }
        if(calendarMode!="MÊS") displayedMonth=YearMonth.from(selectedDate)
       }) { Text("‹",color=GoldBright,style=MaterialTheme.typography.headlineMedium) }
       IconButton(onClick={
        when(calendarMode) {
         "DIA" -> selectedDate=selectedDate.plusDays(1)
         "SEMANA" -> selectedDate=selectedDate.plusWeeks(1)
         else -> displayedMonth=displayedMonth.plusMonths(1)
        }
        if(calendarMode!="MÊS") displayedMonth=YearMonth.from(selectedDate)
       }) { Text("›",color=GoldBright,style=MaterialTheme.typography.headlineMedium) }
      }
     }
     Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
      listOf("DIA","SEMANA","MÊS").forEach { mode ->
       FilterChip(selected=calendarMode==mode,onClick={calendarMode=mode},label={Text(when(mode){"DIA"->"Dia";"SEMANA"->"Semana";else->"Mês"})})
      }
     }
     if(calendarMode!="DIA") {
      Row(Modifier.fillMaxWidth()) {
       weekdays.forEach { day->Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { Text(day,color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.labelSmall) } }
      }
      val visibleCells: List<LocalDate?> = if(calendarMode=="SEMANA") weekDates.map { it as LocalDate? } else cells
      visibleCells.chunked(7).forEach { week->
       Row(Modifier.fillMaxWidth()) {
        week.forEach { date->
         Box(Modifier.weight(1f).height(43.dp).padding(2.dp),contentAlignment=Alignment.Center) {
          if(date!=null) {
           val isSelected=date==selectedDate
           val isToday=date==LocalDate.now()
           val count=monthItems[date]?.size?:0
           Column(Modifier.fillMaxSize().background(if(isSelected) VioletPrimary else Color.Transparent,MaterialTheme.shapes.small).clickable { selectedDate=date;displayedMonth=YearMonth.from(date) }.padding(vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
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
     }
    Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween) {
     Text("• Registros indicados em dourado",style=MaterialTheme.typography.labelSmall,color = Ivory.copy(alpha = .78f))
     Text("${items.size} registros",style=MaterialTheme.typography.labelSmall,color=Gold)
    }
   }
  }
  Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column {
    Text(if(calendarMode=="SEMANA") "Compromissos da semana" else selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",java.util.Locale("pt","BR"))).replaceFirstChar { it.uppercase() },color=GoldBright,fontWeight=FontWeight.Bold)
    Text("${displayedItems.count{!it.completed}} pendentes · ${displayedItems.count{it.completed}} concluídos"+if(calendarMode=="SEMANA") " na semana" else "",color=Gold,style=MaterialTheme.typography.labelSmall)
   }
   TextButton(onClick={selectedDate=selectedDate.plusDays(1);if(YearMonth.from(selectedDate)!=displayedMonth) displayedMonth=YearMonth.from(selectedDate)}) { Text("Próximo dia  ›",color=Navy) }
  }
  Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor = CardSurface)) {
   Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("Novo compromisso ou hábito",color=GoldBright,fontWeight=FontWeight.Bold)
    OutlinedTextField(value=title,onValueChange={title=it},modifier=Modifier.fillMaxWidth(),label={Text("Título")},singleLine=true)
    OutlinedTextField(value=description,onValueChange={description=it},modifier=Modifier.fillMaxWidth(),label={Text("Descrição")},minLines=2,maxLines=3)
    OutlinedTextField(value=location,onValueChange={location=it},modifier=Modifier.fillMaxWidth(),label={Text("Local (opcional)")},singleLine=true)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     AgendaKind.entries.forEach { value->FilterChip(selected=kind==value,onClick={kind=value},label={Text(kindLabel(value))}) }
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
     Text("Recorrência",color=GoldBright,style=MaterialTheme.typography.labelMedium)
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
      FilterChip(selected=recurrenceFrequency==null,onClick={recurrenceFrequency=null},label={Text("Único")})
      FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.DAILY,onClick={recurrenceFrequency=RecurrenceFrequency.DAILY},label={Text("Diário")})
      FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.WEEKDAYS,onClick={recurrenceFrequency=RecurrenceFrequency.WEEKDAYS},label={Text("Dias úteis")})
     }
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
      FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.WEEKLY,onClick={recurrenceFrequency=RecurrenceFrequency.WEEKLY},label={Text("Semanal")})
      FilterChip(selected=recurrenceFrequency==RecurrenceFrequency.MONTHLY,onClick={recurrenceFrequency=RecurrenceFrequency.MONTHLY},label={Text("Mensal")})
     }
     if(recurrenceFrequency!=null) Text("A série cria ocorrências futuras por até 12 meses. Você poderá editar uma ocorrência ou toda a série.",color=Ivory.copy(alpha=.78f),style=MaterialTheme.typography.labelSmall)
    }
    Box {
     OutlinedButton(onClick={dimensionMenu=true}) { Text(dimensionLabel(dimension),color=Navy) }
     DropdownMenu(expanded=dimensionMenu,onDismissRequest={dimensionMenu=false}) {
      LifeDimension.entries.forEach { value->DropdownMenuItem(text={Text(dimensionLabel(value))},onClick={dimension=value;dimensionMenu=false}) }
     }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()}) { Text("Início: %02d:%02d".format(hour,minute),color=Navy) }
     Row(verticalAlignment=Alignment.CenterVertically) {
      Checkbox(checked=endTimeEnabled,onCheckedChange={endTimeEnabled=it})
      Text("Definir término",style=MaterialTheme.typography.labelSmall,color=Navy)
     }
    }
    if(endTimeEnabled) {
     TextButton(onClick={TimePickerDialog(context,{_,h,m->endHour=h;endMinute=m},endHour,endMinute,true).show()}) { Text("Término: %02d:%02d".format(endHour,endMinute),color=Navy) }
    }
    val endInvalid=endTimeEnabled && (endHour*60+endMinute<=hour*60+minute)
    if(endInvalid) Text("O término deve ser posterior ao horário de início.",color=Color(0xFFFF9A8B),style=MaterialTheme.typography.labelSmall)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
     Text("Lembretes deste compromisso",color=GoldBright,style=MaterialTheme.typography.labelMedium)
     Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
      listOf(120 to "2 horas",60 to "1 hora",30 to "30 min").forEach { (minutes,label)->
       FilterChip(selected=minutes in reminderOffsets,onClick={reminderOffsets=if(minutes in reminderOffsets) reminderOffsets-minutes else reminderOffsets+minutes},label={Text(label)})
      }
     }
    }
    Button(onClick={
     val scheduled=selectedDate.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
     val endAt=if(endTimeEnabled) selectedDate.atTime(endHour,endMinute).atZone(zone).toInstant().toEpochMilli() else null
     vm.addAgenda(title,description,location,kind,dimension,scheduled,endAt,reminderOffsets,recurrenceFrequency?.name)
     title="";description="";location="";endTimeEnabled=false;reminderOffsets=setOf(120,60,30);recurrenceFrequency=null
    },enabled=title.isNotBlank()&&!endInvalid,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=VioletPrimary)) {
     Text("Adicionar em "+selectedDate.format(DateTimeFormatter.ofPattern("dd/MM")))
    }
   }
  }
  if(displayedItems.isEmpty()) {
   Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
    Text(if(calendarMode=="SEMANA") "Nenhum compromisso nesta semana." else "Nenhum registro para este dia.\nEscolha outra data ou adicione um compromisso.",color = Ivory.copy(alpha = .78f),style=MaterialTheme.typography.bodyMedium)
   }
  } else {
   LazyColumn(Modifier.weight(1f).padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
    items(displayedItems,key={it.id}) { item->
     Card(colors=CardDefaults.cardColors(containerColor = CardSurface)) {
      Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
       Checkbox(checked=item.completed,onCheckedChange={vm.toggle(item)})
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        val itemTime=Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
        if(calendarMode=="SEMANA") Text(itemTime.format(DateTimeFormatter.ofPattern("EEEE, dd/MM",java.util.Locale("pt","BR"))).replaceFirstChar { it.uppercase() },color=Gold,style=MaterialTheme.typography.labelSmall)
        Text(item.title,color=GoldBright,fontWeight=FontWeight.SemiBold)
        val endTime=item.endAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val timeLabel="%02d:%02d".format(itemTime.hour,itemTime.minute)+(endTime?.let { " – %02d:%02d".format(it.hour,it.minute) } ?: "")
        Text(timeLabel+" · "+kindLabel(item.kind)+" · "+dimensionLabel(item.dimension),style=MaterialTheme.typography.labelSmall,color=Ivory.copy(alpha=.78f))
        if(item.description.isNotBlank()) Text(item.description,color=Ivory.copy(alpha=.88f),style=MaterialTheme.typography.bodySmall)
        if(item.location.isNotBlank()) Text("Local: "+item.location,color=Ivory.copy(alpha=.68f),style=MaterialTheme.typography.labelSmall)
        if(item.seriesId!=null) Text("Série recorrente",color=Gold,style=MaterialTheme.typography.labelSmall)
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
  EditAgendaDialog(item=editingItem!!,onDismiss={editingItem=null},onSave={updated,applyToSeries->vm.updateAgenda(updated,applyToSeries);editingItem=null},onDeleteSeries={vm.deleteSeries(editingItem!!);editingItem=null})
 }
}

@Composable private fun EditAgendaDialog(
 item:AgendaItem,
 onDismiss:()->Unit,
 onSave:(AgendaItem,Boolean)->Unit,
 onDeleteSeries:()->Unit
) {
 val context=LocalContext.current
 val zone=remember { ZoneId.systemDefault() }
 val startDateTime=Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
 val endDateTime=item.endAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
 var title by remember(item.id) { mutableStateOf(item.title) }
 var description by remember(item.id) { mutableStateOf(item.description) }
 var location by remember(item.id) { mutableStateOf(item.location) }
 var kind by remember(item.id) { mutableStateOf(item.kind) }
 var dimension by remember(item.id) { mutableStateOf(item.dimension) }
 var date by remember(item.id) { mutableStateOf(startDateTime.toLocalDate()) }
 var hour by remember(item.id) { mutableIntStateOf(startDateTime.hour) }
 var minute by remember(item.id) { mutableIntStateOf(startDateTime.minute) }
 var endTimeEnabled by remember(item.id) { mutableStateOf(endDateTime!=null) }
 var endHour by remember(item.id) { mutableIntStateOf(endDateTime?.hour ?: (startDateTime.hour+1)%24) }
 var endMinute by remember(item.id) { mutableIntStateOf(endDateTime?.minute ?: startDateTime.minute) }
 var reminderOffsets by remember(item.id) { mutableStateOf(item.reminderOffsets.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()) }
 var applyToSeries by remember(item.id) { mutableStateOf(false) }
 var dimensionMenu by remember { mutableStateOf(false) }
 val endInvalid=endTimeEnabled && (endHour*60+endMinute<=hour*60+minute)
 AlertDialog(
  onDismissRequest=onDismiss,
  title={Text("Editar compromisso",color=GoldBright,fontWeight=FontWeight.Bold)},
  text={
   Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(value=title,onValueChange={title=it},label={Text("Título")},singleLine=true)
    OutlinedTextField(value=description,onValueChange={description=it},label={Text("Descrição")},minLines=2,maxLines=3)
    OutlinedTextField(value=location,onValueChange={location=it},label={Text("Local (opcional)")},singleLine=true)
    Text("Tipo",color=GoldBright,fontWeight=FontWeight.SemiBold)
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
     AgendaKind.entries.forEach { value->FilterChip(selected=kind==value,onClick={kind=value},label={Text(kindLabel(value))}) }
    }
    Box {
     OutlinedButton(onClick={dimensionMenu=true}) { Text(dimensionLabel(dimension),color=Navy) }
     DropdownMenu(expanded=dimensionMenu,onDismissRequest={dimensionMenu=false}) {
      LifeDimension.entries.forEach { value->DropdownMenuItem(text={Text(dimensionLabel(value))},onClick={dimension=value;dimensionMenu=false}) }
     }
    }
    OutlinedButton(onClick={
     DatePickerDialog(context,{_,y,m,d->date=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).show()
    }) { Text("Data: "+date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),color=Navy) }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={TimePickerDialog(context,{_,h,m->hour=h;minute=m},hour,minute,true).show()}) { Text("Início: %02d:%02d".format(hour,minute),color=Navy) }
     Row(verticalAlignment=Alignment.CenterVertically) {
      Checkbox(checked=endTimeEnabled,onCheckedChange={endTimeEnabled=it})
      Text("Definir término",color=Navy,style=MaterialTheme.typography.labelSmall)
     }
    }
    if(endTimeEnabled) TextButton(onClick={TimePickerDialog(context,{_,h,m->endHour=h;endMinute=m},endHour,endMinute,true).show()}) { Text("Término: %02d:%02d".format(endHour,endMinute),color=Navy) }
    if(endInvalid) Text("O término deve ser posterior ao início.",color=Color(0xFFFF9A8B),style=MaterialTheme.typography.labelSmall)
    Text("Lembretes",color=GoldBright,fontWeight=FontWeight.SemiBold)
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
     listOf(120 to "2 horas",60 to "1 hora",30 to "30 min").forEach { (minutes,label)->
      FilterChip(selected=minutes in reminderOffsets,onClick={reminderOffsets=if(minutes in reminderOffsets) reminderOffsets-minutes else reminderOffsets+minutes},label={Text(label)})
     }
    }
    if(item.seriesId!=null) {
     Row(verticalAlignment=Alignment.CenterVertically) {
      Checkbox(checked=applyToSeries,onCheckedChange={applyToSeries=it})
      Text("Aplicar alterações a toda a série",color=Navy,style=MaterialTheme.typography.bodySmall)
     }
     Text("Desmarcado: altera somente esta ocorrência. Marcado: atualiza as ocorrências vinculadas.",color=Ivory.copy(alpha=.78f),style=MaterialTheme.typography.labelSmall)
    }
   }
  },
  confirmButton={
   TextButton(enabled=title.isNotBlank()&&!endInvalid,onClick={
    val scheduled=date.atTime(hour,minute).atZone(zone).toInstant().toEpochMilli()
    val endAt=if(endTimeEnabled) date.atTime(endHour,endMinute).atZone(zone).toInstant().toEpochMilli() else null
    val offsetsCsv=reminderOffsets.filter { it in listOf(120,60,30) }.sortedDescending().joinToString(",")
    val reminderAt=reminderOffsets.maxOrNull()?.let { scheduled-it*60_000L }
    onSave(item.copy(title=title.trim(),description=description.trim(),location=location.trim(),kind=kind,dimension=dimension,scheduledAt=scheduled,endAt=endAt,reminderAt=reminderAt,reminderOffsets=offsetsCsv),applyToSeries)
   }) { Text("Salvar alterações",color=Gold) }
  },
  dismissButton={
   Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
    if(item.seriesId!=null) TextButton(onClick=onDeleteSeries) { Text("Excluir série",color=Color(0xFFFF9A8B)) }
    TextButton(onClick=onDismiss) { Text("Cancelar",color=Ivory.copy(alpha=.78f)) }
   }
  }
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
 val violet=CardSurface
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
