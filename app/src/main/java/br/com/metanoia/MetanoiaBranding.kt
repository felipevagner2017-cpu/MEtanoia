package br.com.metanoia

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Configuração única da identidade visual oficial. A imagem PNG não é modificada. */
internal object MetanoiaBrandConfig {
    const val watermarkOpacity: Float = 0.045f
    const val watermarkScale: Float = 0.82f
}

/** Marca-d'água global; usa o PNG original e altera apenas a opacidade de renderização. */
@Composable
internal fun MetanoiaBrandWatermark(
    modifier: Modifier = Modifier,
    opacity: Float = MetanoiaBrandConfig.watermarkOpacity,
    scale: Float = MetanoiaBrandConfig.watermarkScale,
    alignment: Alignment = Alignment.Center
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = alignment) {
        Image(
            painter = painterResource(id = R.drawable.metanoia_oficial),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(scale.coerceIn(0.1f, 1f)),
            contentScale = ContentScale.Fit,
            alpha = opacity.coerceIn(0f, 1f)
        )
    }
}

/** Logo institucional para cabeçalhos e áreas de identificação. */
@Composable
internal fun MetanoiaOfficialLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.metanoia_oficial),
        contentDescription = "Logo oficial do MEtanoia",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
