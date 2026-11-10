package br.com.metanoia

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        assertPageVisible("Devocional", "DIMENSÃO 01")
        captureScreen("02-devocao")

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

        clickNavigationControl("Cotidiano")
        clickNavigationControl("Diário")
        assertPageVisible("Diário", "Diário de hoje")

        clickNavigationControl("Revisões")
        assertPageVisible("Revisões", "Revisões históricas")
        captureScreen("08-revisoes")

        clickNavigationControl("Alertas")
        assertPageVisible("Alertas", "Notificações e alarmes")
        captureScreen("09-alertas")
    }
}
