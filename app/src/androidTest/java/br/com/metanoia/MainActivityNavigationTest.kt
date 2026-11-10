package br.com.metanoia

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
            target.performScrollTo()
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
                    if (tab == "Jornada diária" && expected == "DESAFIO DE HOJE") {
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
        clickNavigationControl("Meus registros")
        assertPageVisible("Devocional", "DIMENSÃO 01")

        clickNavigationControl("Matrimônio")
        assertPageVisible("Matrimônio", "Matrimônio / Ingrid")
        captureScreen("03-matrimonio")

        clickNavigationControl("Profissional")
        assertPageVisible("Profissional", "DIMENSÃO 03")
        captureScreen("04-profissional")

        clickNavigationControl("Cotidiano")
        assertPageVisible("Cotidiano", "Diário de hoje")
        captureScreen("05-cotidiano")

        clickNavigationControl("Agenda")
        assertPageVisible("Agenda", "Minha agenda")
        captureScreen("06-agenda")
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val otherDay = monday.plusDays(if (today.dayOfWeek.value == 5) 0 else 4)
        val weekEventTitle = "Compromisso de outro dia da semana"
        val weekEventId = runBlocking {
            MetanoiaDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)
                .agendaDao().insert(
                    AgendaItem(
                        title = weekEventTitle,
                        scheduledAt = otherDay.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                )
        }
        clickNavigationControl("Semana")
        assertPageVisible("Agenda", "Compromissos da semana")
        assertPageVisible("Agenda", weekEventTitle)
        captureScreen("06b-agenda-semana")
        clickNavigationControl("Dia")
        captureScreen("06c-agenda-dia")
        clickNavigationControl("Mês")
        runBlocking {
            MetanoiaDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)
                .agendaDao().findById(weekEventId)?.let { MetanoiaDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext).agendaDao().delete(it) }
        }

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
