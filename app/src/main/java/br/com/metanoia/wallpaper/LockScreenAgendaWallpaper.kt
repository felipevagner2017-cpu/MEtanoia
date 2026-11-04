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
import br.com.metanoia.data.AgendaItem
import br.com.metanoia.data.LifeDimension
import br.com.metanoia.data.MetanoiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Agenda rendered from the existing Room database, applied to the lock-screen wallpaper slot. */
object LockScreenAgendaWallpaper {
    private const val WIDTH = 1080
    private const val HEIGHT = 2400
    private const val LEFT = 76f
    private const val RIGHT = 1004f
    private val gold = Color.rgb(205, 169, 99)
    private val white = Color.WHITE

    fun refresh(context: Context, db: MetanoiaDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { refreshNow(context.applicationContext, db) }
        }
    }

    suspend fun refreshNow(context: Context, db: MetanoiaDatabase) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val items = db.agendaDao().observeRange(start, end).first()
            .filterNot { it.completed }
            .sortedBy { it.scheduledAt }
        val bitmap = render(items, today, zone)
        val manager = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        } else {
            // Android versions before N cannot target the lock-screen wallpaper separately.
            manager.setBitmap(bitmap)
        }
        bitmap.recycle()
    }

    internal fun render(items: List<AgendaItem>, date: LocalDate, zone: ZoneId): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(Color.rgb(5, 17, 33), Color.rgb(16, 43, 70), Color.rgb(4, 14, 27)),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(17, 220, 190, 130)
            textSize = 900f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("M", 380f, 1470f, watermark)

        canvas.drawText("MEtonoia", LEFT, 315f, textPaint(30f, gold, Typeface.BOLD))
        canvas.drawText(
            "UMA MENTE RENOVADA. UMA VIDA INTENCIONAL.",
            LEFT, 360f, textPaint(23f, Color.argb(190, 255, 255, 255), Typeface.NORMAL)
        )
        val day = date.format(DateTimeFormatter.ofPattern("EEEE", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        canvas.drawText(day, LEFT, 565f, textPaint(58f, white, Typeface.MEDIUM))
        val dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "BR")))
            .uppercase(Locale("pt", "BR"))
        canvas.drawText(dateLabel, LEFT, 715f, textPaint(116f, gold, Typeface.BOLD))
        canvas.drawText("SUA AGENDA DE HOJE", LEFT, 830f, textPaint(34f, white, Typeface.BOLD))
        canvas.drawRoundRect(
            RectF(LEFT, 866f, RIGHT, 871f), 3f, 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
        )

        val visible = items.take(8)
        if (visible.isEmpty()) {
            canvas.drawText("Um dia aberto para", LEFT, 1060f, textPaint(42f, white, Typeface.NORMAL))
            canvas.drawText("viver com intenção.", LEFT, 1125f, textPaint(42f, white, Typeface.NORMAL))
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
                val time = Instant.ofEpochMilli(item.scheduledAt).atZone(zone)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                canvas.drawText(time, 112f, top + 57f, textPaint(34f, accent, Typeface.BOLD))
                canvas.drawText(
                    ellipsize(item.title, 620f, 38f), 285f, top + 52f,
                    textPaint(38f, white, Typeface.BOLD)
                )
                val description = item.description.trim()
                if (description.isNotEmpty()) {
                    wrap(description, 620f, 27f).take(2).forEachIndexed { line, value ->
                        canvas.drawText(
                            value, 285f, top + 91f + line * 31f,
                            textPaint(27f, Color.argb(205, 238, 242, 248), Typeface.NORMAL)
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
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, gold) }
        )
        canvas.drawText(
            "Organize o dia. Viva com intenção.",
            LEFT, 2260f, textPaint(29f, gold, Typeface.MEDIUM)
        )
        canvas.drawText(
            "Romanos 12:2  •  Atualizado pelo MEtanoia",
            LEFT, 2308f, textPaint(22f, Color.argb(175, white), Typeface.NORMAL)
        )
        return bitmap
    }

    private fun textPaint(size: Float, color: Int, style: Typeface): Paint =
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
