package br.com.metanoia.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import br.com.metanoia.R
import br.com.metanoia.data.AgendaItem
import br.com.metanoia.data.LifeDimension
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Agenda rendered from the native agenda database and applied to the lock-screen wallpaper slot. */
object LockScreenAgendaWallpaper {
    private const val WIDTH = 1080
    private const val HEIGHT = 2400
    private const val LEFT = 76f
    private const val RIGHT = 1004f
    private val gold = Color.rgb(197, 161, 90)
    private val white = Color.WHITE

    fun refresh(context: Context, db: MetanoiaDatabase) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { refreshNow(context.applicationContext, db) }
                .onFailure { android.util.Log.e("MEtonoiaLockScreen", "Falha ao atualizar a agenda da tela de bloqueio", it) }
        }
    }

    suspend fun refreshNow(context: Context, db: MetanoiaDatabase) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(7).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        // Include today and the following seven days so upcoming commitments are not hidden.
        val items = db.agendaDao().observeRange(start, end).first()
            .filterNot { it.completed }
            .sortedBy { it.scheduledAt }
        val bitmap = render(context, items, today, zone)
        try {
            val manager = WallpaperManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            } else {
                manager.setBitmap(bitmap)
            }
        } finally {
            bitmap.recycle()
        }
    }

    internal fun render(context: Context, items: List<AgendaItem>, date: LocalDate, zone: ZoneId): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(Color.rgb(7, 21, 37), Color.rgb(16, 37, 66), Color.rgb(8, 25, 43)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        context.getDrawable(R.drawable.metanoia_brain_mark)?.mutate()?.let { mark ->
            mark.alpha = 24
            mark.setBounds(350, 1030, 730, 1410)
            mark.draw(canvas)
        }

        canvas.drawText("MEtonoia", LEFT, 315f, textPaint(30f, gold, Typeface.BOLD))
        canvas.drawText(
            "UMA MENTE RENOVADA. UMA VIDA INTENCIONAL.",
            LEFT, 360f, textPaint(23f, Color.argb(190, 255, 255, 255), Typeface.NORMAL)
        )
        val day = date.format(DateTimeFormatter.ofPattern("EEEE", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        canvas.drawText(day, LEFT, 565f, textPaint(58f, white, Typeface.NORMAL))
        val dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "BR")))
            .uppercase(Locale("pt", "BR"))
        canvas.drawText(dateLabel, LEFT, 715f, textPaint(116f, gold, Typeface.BOLD))
        canvas.drawText("AGENDA · PRÓXIMOS 7 DIAS", LEFT, 830f, textPaint(34f, white, Typeface.BOLD))
        canvas.drawRoundRect(
            RectF(LEFT, 866f, RIGHT, 871f), 3f, 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
        )

        val visible = items.take(8)
        if (visible.isEmpty()) {
            canvas.drawText("Nenhum compromisso", LEFT, 1060f, textPaint(42f, white, Typeface.NORMAL))
            canvas.drawText("nos próximos 7 dias.", LEFT, 1125f, textPaint(42f, white, Typeface.NORMAL))
        } else {
            visible.forEachIndexed { index, item ->
                val top = 915f + index * 153f
                val bottom = top + 132f
                val accent = dimensionColor(item.dimension)
                canvas.drawRoundRect(
                    RectF(LEFT, top, RIGHT, bottom), 22f, 22f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 255, 255, 255) }
                )
                canvas.drawRoundRect(
                    RectF(LEFT, top, LEFT + 11f, bottom), 6f, 6f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                )
                val eventDateTime = Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
                val time = eventDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                val eventDate = eventDateTime.toLocalDate()
                val dateText = when (eventDate) {
                    date -> "HOJE"
                    date.plusDays(1) -> "AMANHÃ"
                    else -> eventDate.format(DateTimeFormatter.ofPattern("EEE · dd/MM", Locale("pt", "BR")))
                        .uppercase(Locale("pt", "BR"))
                }
                canvas.drawText(time, 112f, top + 55f, textPaint(34f, accent, Typeface.BOLD))
                canvas.drawText(dateText, 112f, top + 94f, textPaint(19f, Color.argb(205, 238, 242, 248), Typeface.BOLD))
                canvas.drawText(
                    ellipsize(item.title, 650f, 38f), 285f, top + 52f,
                    textPaint(38f, white, Typeface.BOLD)
                )
                val description = item.description.trim()
                if (description.isNotEmpty()) {
                    wrap(description, 650f, 25f).take(2).forEachIndexed { line, value ->
                        canvas.drawText(
                            value, 285f, top + 91f + line * 29f,
                            textPaint(25f, Color.argb(205, 238, 242, 248), Typeface.NORMAL)
                        )
                    }
                }
            }
            if (items.size > visible.size) {
                canvas.drawText(
                    "+ ${items.size - visible.size} compromissos",
                    112f, 915f + visible.size * 153f + 20f,
                    textPaint(27f, gold, Typeface.BOLD)
                )
            }
        }

        canvas.drawRoundRect(
            RectF(LEFT, 2200f, RIGHT, 2203f), 2f, 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, Color.red(gold), Color.green(gold), Color.blue(gold)) }
        )
        canvas.drawText("Organize o dia. Viva com intenção.", LEFT, 2260f, textPaint(29f, gold, Typeface.NORMAL))
        canvas.drawText(
            "Romanos 12:2  •  Atualizado pelo MEtanoia",
            LEFT, 2308f, textPaint(22f, Color.argb(175, 255, 255, 255), Typeface.NORMAL)
        )
        return bitmap
    }

    private fun textPaint(size: Float, color: Int, style: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", style)
        }

    private fun ellipsize(value: String, maxWidth: Float, size: Float): String {
        val paint = textPaint(size, white, Typeface.BOLD)
        if (paint.measureText(value) <= maxWidth) return value
        var shortened = value
        while (shortened.isNotEmpty() && paint.measureText("${shortened}…") > maxWidth) {
            shortened = shortened.dropLast(1)
        }
        return "${shortened}…"
    }

    private fun wrap(value: String, maxWidth: Float, size: Float): List<String> {
        val paint = textPaint(size, white, Typeface.NORMAL)
        val result = mutableListOf<String>()
        var line = ""
        value.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) result += line
                line = word
            }
        }
        if (line.isNotEmpty()) result += line
        if (result.size <= 2) return result
        return listOf(result[0], ellipsize(result[1], maxWidth, size))
    }

    private fun dimensionColor(dimension: LifeDimension): Int = when (dimension) {
        LifeDimension.SPIRITUAL -> Color.rgb(121, 134, 203)
        LifeDimension.MATRIMONIAL -> Color.rgb(230, 124, 115)
        LifeDimension.PROFESSIONAL -> Color.rgb(3, 155, 229)
        else -> Color.rgb(51, 182, 121)
    }
}
