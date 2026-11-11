package br.com.metanoia

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEnvironmentTest {
    @Test
    fun debugVariantUsesOnlyTheIsolatedCloudflarePreviewWorker() {
        assertEquals(
            "https://metanoia-preview.restaura-lar.workers.dev",
            BuildConfig.METANOIA_API_BASE_URL
        )
    }
}
