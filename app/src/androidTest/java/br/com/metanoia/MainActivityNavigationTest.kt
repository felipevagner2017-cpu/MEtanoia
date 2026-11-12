package br.com.metanoia

import android.Manifest
import br.com.metanoia.data.LifeDimension
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.printToLog
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class MainActivityNavigationTest {
    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun enterTextInLazyList(listTag: String, label: String, value: String) {
        val matcher = hasText(label, substring = false) and hasSetTextAction()
        composeRule.onNodeWithTag(listTag).performScrollToNode(matcher)
        composeRule.onNode(matcher).performTextInput(value)
    }

    private fun awaitJournalPersisted(title: String, dimension: LifeDimension) {
        val database = MetanoiaDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                database.journalDao().observeAll().first().any {
                    it.title == title && it.dimension == dimension
                }
            }
        }
        runBlocking {
            val matches = database.journalDao().observeAll().first().filter { it.title == title }
            check(matches.size == 1) { "Esperado exatamente um registro Room para '$title'; encontrados: ${matches.size}." }
            check(matches.single().dimension == dimension) {
                "Dimensão persistida incorreta para '$title': ${matches.single().dimension}."
            }
        }
    }

    private fun clickNavigationControl(label: String) {
        // Avoid issuing a bring-into-view request for a tab that is already
        // visible. For clipped tabs, scroll only when visibility requires it.
        composeRule.waitForIdle()
        val target = composeRule.onAllNodes(
            hasText(label, substring = false) and hasClickAction()
        )[0]
        val alreadyVisible = runCatching {
            target.assertIsDisplayed()
            true
        }.getOrDefault(false)
        if (!alreadyVisible) {
            // Dimension tabs live in a horizontal scroller. performScrollTo()
            // only handles bring-into-view in the vertical direction, so it
            // leaves off-screen tabs such as Matrimonio unreachable on phones.
            val scrollContainers = composeRule.onAllNodes(hasScrollAction())
            for (index in 0 until scrollContainers.fetchSemanticsNodes().size) {
                runCatching { scrollContainers[index].performScrollToNode(hasText(label, substring = false) and hasClickAction()) }
                val nowVisible = runCatching {
                    target.assertIsDisplayed()
                    true
                }.getOrDefault(false)
                if (nowVisible) break
            }
            val visibleAfterHorizontalScroll = runCatching {
                target.assertIsDisplayed()
                true
            }.getOrDefault(false)
            if (!visibleAfterHorizontalScroll) target.performScrollTo()
        }
        target.assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
    }

    private fun captureScreen(name: String) {
        composeRule.waitForIdle()
        val path = "/sdcard/Download/MEtanoia-$name.png"
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("screencap -p $path")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            input.copyTo(ByteArrayOutputStream())
        }
    }

    private fun assertPageVisible(tab: String, expected: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    val target = hasText(expected, substring = false)
                    if (tab == "Matrimônio") {
                        // Keep the section heading as a first-class LazyColumn item and
                        // target its stable semantics tag instead of matching text nested
                        // inside a Material Card's merged semantics subtree.
                        val matcher = if (expected == "META E CONTEXTO DO CASAL") {
                            hasTestTag("matrimonial-goal-heading")
                        } else target
                        composeRule.onNodeWithTag("matrimonial-dimension-list").performScrollToNode(matcher)
                    } else if (tab == "Jornada diária" && expected == "DESAFIO DE HOJE") {
                        composeRule.onNodeWithTag("daily-journey-list").performScrollToNode(target)
                    } else if (tab == "Devocional" && expected == "REFERÊNCIAS BIBLIOGRÁFICAS E FONTES") {
                        composeRule.onNodeWithTag("pastoral-study-list").performScrollToNode(target)
                    } else {
                        val scrollContainers = composeRule.onAllNodes(hasScrollAction())
                        var reached = false
                        for (index in 0 until scrollContainers.fetchSemanticsNodes().size) {
                            runCatching {
                                scrollContainers[index].performScrollToNode(target)
                                reached = true
                            }
                            if (reached) break
                        }
                        if (!reached) composeRule.onNodeWithText(expected, substring = false).performScrollTo()
                    }
                    composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithText(expected, substring = false).assertIsDisplayed()
        } catch (failure: Throwable) {
            composeRule.onRoot().printToLog("METoNOIA_NAV_FAILURE_$tab")
            throw AssertionError("A navegação para '$tab' não exibiu '$expected'.", failure)
        }
    }

    @Test
    fun fourDimensionsAndDailyAreasAreReachable() {
        assertPageVisible("Jornada diária", "PALAVRA DO DIA")
        assertPageVisible("Jornada diária", "DESAFIO DE HOJE")
        captureScreen("01-jornada-diaria")
        clickNavigationControl("Áreas")
        clickNavigationControl("Devocional")
        assertPageVisible("Devocional", "Avivamento bíblico")
        captureScreen("02-devocao")
        assertPageVisible("Devocional", "REFERÊNCIAS BIBLIOGRÁFICAS E FONTES")
        captureScreen("02b-fontes")
        composeRule.onNodeWithTag("spiritual-records-tab").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("dimension-record-list").fetchSemanticsNodes().isNotEmpty()
        }
        assertPageVisible("Devocional", "DIMENSÃO 01")
        val runSuffix = System.nanoTime().toString()
        val devotionalTitle = "Estudo bíblico Atos 2:42 $runSuffix"
        composeRule.onNodeWithTag("dimension-record-list")
            .performScrollToNode(hasText("Texto bíblico ou tema pastoral", substring = false) and hasSetTextAction())
        composeRule.onNode(hasText("Texto bíblico ou tema pastoral", substring = false) and hasSetTextAction())
            .performTextInput(devotionalTitle)
        composeRule.onNode(hasText("Contexto histórico, cultural e literário", substring = false) and hasSetTextAction())
            .performTextInput("Atos 2 descreve a formação da comunidade cristã após Pentecostes.")
        composeRule.onNodeWithText("Salvar estudo bíblico", substring = false)
            .performScrollTo().assertIsDisplayed().performClick()
        // Separate Room persistence from Compose presentation: first wait for a
        // direct DAO read, then independently assert that the visible list renders it.
        awaitJournalPersisted(devotionalTitle, LifeDimension.SPIRITUAL)
        // Room has emitted the saved row. Confirm the ViewModel/Compose Flow
        // updated the always-composed count marker before scrolling to the lazy item.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasContentDescription("1 registro na dimensão")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dimension-record-list")
            .performScrollToNode(hasText(devotionalTitle, substring = false))
        composeRule.onNodeWithText(devotionalTitle, substring = false).assertIsDisplayed()

        // Return to Áreas, then use a real horizontal swipe on the narrow-screen tab strip.
        clickNavigationControl("Áreas")
        composeRule.onNodeWithTag("area-dimension-tabs").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        clickNavigationControl("Matrimônio")
        assertPageVisible("Matrimônio", "Matrimônio / Ingrid")
        assertPageVisible("Matrimônio", "META E CONTEXTO DO CASAL")
        val relationshipGoal = "Fortalecer a comunicação do casal"
        enterTextInLazyList("matrimonial-dimension-list", "O que deseja fortalecer ou reconstruir?", relationshipGoal)
        enterTextInLazyList("matrimonial-dimension-list", "O que aconteceu? Descreva fatos e contexto sem atribuir culpa.", "Quero conversar com mais calma sobre nossas prioridades.")
        // The six-field form extends below the viewport. With the IME open, the
        // save action is composed but off-screen; explicitly bring it into view
        // before clicking so the test exercises the real Room save callback.
        composeRule.onNodeWithText("Salvar meta e plano matrimonial", substring = false)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        // assertPageVisible waits for the Room-backed Flow to render the inserted
        // record, rather than assuming that the asynchronous ViewModel write is done.
        assertPageVisible("Matrimônio", "Meta matrimonial · $relationshipGoal")
        awaitJournalPersisted("Meta matrimonial · $relationshipGoal", LifeDimension.MATRIMONIAL)
        captureScreen("03-matrimonio")

        // Leave and re-enter Matrimonio to verify pager state and content remain reachable.
        clickNavigationControl("Devocional")
        clickNavigationControl("Matrimônio")
        assertPageVisible("Matrimônio", "META E CONTEXTO DO CASAL")
        clickNavigationControl("Profissional")
        assertPageVisible("Profissional", "DIMENSÃO 03")
        val professionalGoal = "Organizar uma transição profissional"
        enterTextInLazyList("dimension-record-list", "Meta, projeto ou desafio profissional", professionalGoal)
        enterTextInLazyList("dimension-record-list", "Descreva sua situação profissional atual, experiência, responsabilidades e recursos disponíveis.", "Atualmente trabalho com vendas e quero migrar para trabalho remoto.")
        enterTextInLazyList("dimension-record-list", "Obstáculo ou dificuldade atual", "Preciso identificar competências transferíveis.")
        enterTextInLazyList("dimension-record-list", "Próxima ação concreta", "Mapear três funções compatíveis.")
        enterTextInLazyList("dimension-record-list", "Prazo ou frequência desejada", "Próximos sete dias")
        enterTextInLazyList("dimension-record-list", "Como vou reconhecer o resultado?", "Lista de competências e três vagas analisadas.")
        composeRule.onNodeWithText("Registrar plano profissional", substring = false).performClick()
        assertPageVisible("Profissional", professionalGoal)
        awaitJournalPersisted(professionalGoal, LifeDimension.PROFESSIONAL)
        captureScreen("04-profissional")

        clickNavigationControl("Cotidiano")
        assertPageVisible("Cotidiano", "EVOLUÇÃO COTIDIANA")
        assertPageVisible("Cotidiano", "Objetivo de rotina ou desenvolvimento pessoal")
        val dailyGoal = "Criar uma rotina diária consistente"
        enterTextInLazyList("dimension-record-list", "Objetivo de rotina ou desenvolvimento pessoal", dailyGoal)
        enterTextInLazyList("dimension-record-list", "Descreva sua realidade atual, rotina, energia, responsabilidades e o que deseja melhorar.", "Minha rotina varia e preciso organizar horários.")
        enterTextInLazyList("dimension-record-list", "Obstáculo ou dificuldade atual", "Falta de constância.")
        enterTextInLazyList("dimension-record-list", "Próxima ação concreta", "Definir horário de início do dia.")
        enterTextInLazyList("dimension-record-list", "Prazo ou frequência desejada", "Todos os dias")
        enterTextInLazyList("dimension-record-list", "Como vou reconhecer o resultado?", "Cumprir o horário em cinco dias da semana.")
        composeRule.onNodeWithText("Registrar objetivo cotidiano", substring = false).performClick()
        assertPageVisible("Cotidiano", dailyGoal)
        awaitJournalPersisted(dailyGoal, LifeDimension.ROUTINE)
        captureScreen("05-cotidiano")

        clickNavigationControl("Agenda")
        assertPageVisible("Agenda", "Minha agenda")
        captureScreen("06-agenda")
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val otherDay = monday.plusDays(if (today.dayOfWeek.value == 5) 0 else 4)
        val weekEventTitle = "Compromisso de outro dia da semana"
        // Create the fixture through the real Agenda form so the test exercises
        // the same ViewModel -> Room -> Flow path used by the installed app.
        composeRule.onNodeWithText(otherDay.dayOfMonth.toString(), substring = false).performClick()
        composeRule.onNode(hasText("Título", substring = false) and hasSetTextAction())
            .performTextInput(weekEventTitle)
        composeRule.onNodeWithText("Adicionar em " + otherDay.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), substring = false)
            .performClick()
        composeRule.waitForIdle()
        clickNavigationControl("Semana")
        assertPageVisible("Agenda", "Compromissos da semana")
        assertPageVisible("Agenda", weekEventTitle)
        captureScreen("06b-agenda-semana")
        clickNavigationControl("Dia")
        captureScreen("06c-agenda-dia")
        clickNavigationControl("Mês")

        clickNavigationControl("Áreas")
        clickNavigationControl("Cotidiano")
        clickNavigationControl("Diário")
        assertPageVisible("Diário", "Diário de hoje")
        captureScreen("07-diario")

        clickNavigationControl("Revisões")
        assertPageVisible("Revisões", "Revisões históricas")
        captureScreen("08-revisoes")

        clickNavigationControl("Alertas")
        assertPageVisible("Alertas", "Notificações e alarmes")
        captureScreen("09-alertas")
    }
}