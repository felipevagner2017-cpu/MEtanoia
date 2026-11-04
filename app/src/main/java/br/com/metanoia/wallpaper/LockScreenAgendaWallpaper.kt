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

object LockScreenAgendaWallpaper {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
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
        val items = db.agendaDao().observeRange(start, end).first().filterNot { it.completed }.sortedBy { it.scheduledAt }
        val bitmap = render(items, today, zone)
        val manager = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        } else {
            manager.setBitmap(bitmap)
        }
    }

    private fun render(items: List<AgendaItem>, date: LocalDate, zone: ZoneId): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(Color.rgb(7, 20, 38), Color.rgb(18, 48, 77), Color.rgb(5, 16, 31)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold; textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("METANOIA  •  SUA JORNADA", 78f, 230f, p)
        p.color = white; p.textSize = 72f
        val day = date.format(DateTimeFormatter.ofPattern("EEEE", Locale("pt", "BR"))).replaceFirstChar { it.uppercase() }
        canvas.drawText(day, 78f, 350f, p)
        p.color = gold; p.textSize = 112f
        canvas.drawText(date.format(DateTimeFormatter.ofPattern("dd/MM")), 78f, 480f, p)
        p.color = white; p.textSize = 34f
        canvas.drawText("AGENDA DE HOJE", 78f, 590f, p)
        canvas.drawRoundRect(RectF(78f, 625f, 1002f, 629f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
        val shown = items.take(9)
        if (shown.isEmpty()) {
            p.color = white; p.textSize = 40f; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("Um dia aberto para", 78f, 760f, p)
            canvas.drawText("viver com intenção.", 78f, 820f, p)
        } else {
            shown.forEachIndexed { index, item ->
                val y = 710f + index * 116f
                val color = dimensionColor(item.dimension)
                canvas.drawRoundRect(RectF(78f, y - 53f, 1002f, y + 53f), 22f, 22f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(48, 255, 255, 255) })
                canvas.drawRoundRect(RectF(78f, y - 53f, 91f, y + 53f), 6f, 6f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
                val time = Instant.ofEpochMilli(item.scheduledAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                p.color = color; p.textSize = 31f; p.typeface = Typeface.create("sans-serif-bold", Typeface.NORMAL)
                canvas.drawText(time, 120f, y - 3f, p)
                p.color = white; p.textSize = 37f; p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                canvas.drawText(item.title.take(34), 270f, y + 8f, p)
            }
            if (items.size > shown.size) {
                p.color = gold; p.textSize = 28f
                canvas.drawText("+ " + (items.size - shown.size) + " compromissos", 120f, 710f + shown.size * 116f, p)
            }
        }
        p.color = gold; p.textSize = 25f; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText("Organize o dia. Viva com intenção.", 78f, 1690f, p)
        p.color = Color.argb(155, 255, 255, 255); p.textSize = 22f
        canvas.drawText("Atualizado automaticamente pelo MEtanoia", 78f, 1740f, p)
        return bitmap
    }

    private fun dimensionColor(dimension: LifeDimension): Int = when (dimension) {
        LifeDimension.SPIRITUAL -> Color.rgb(121, 134, 203)
        LifeDimension.MATRIMONIAL -> Color.rgb(230, 124, 115)
        LifeDimension.PROFESSIONAL -> Color.rgb(3, 155, 229)
        else -> Color.rgb(51, 182, 121)
    }
}
