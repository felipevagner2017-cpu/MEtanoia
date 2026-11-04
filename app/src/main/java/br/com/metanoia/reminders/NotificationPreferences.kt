package br.com.metanoia.reminders

import android.content.Context

/** Persisted notification controls shared by the scheduler and notification receiver. */
class NotificationPreferences(context: Context) {
    private val values = context.applicationContext.getSharedPreferences("metanoia_notification_settings", Context.MODE_PRIVATE)

    fun dailyEnabled(hour: Int): Boolean = values.getBoolean("daily_$hour", true)
    fun setDailyEnabled(hour: Int, enabled: Boolean) { values.edit().putBoolean("daily_$hour", enabled).apply() }

    fun leadEnabled(minutes: Int): Boolean = values.getBoolean("lead_$minutes", true)
    fun setLeadEnabled(minutes: Int, enabled: Boolean) { values.edit().putBoolean("lead_$minutes", enabled).apply() }

    fun dailySound(): String = values.getString("daily_sound", SYSTEM_SOUND) ?: SYSTEM_SOUND
    fun eventSound(): String = values.getString("event_sound", SYSTEM_SOUND) ?: SYSTEM_SOUND
    fun setDailySound(value: String) { values.edit().putString("daily_sound", value).apply() }
    fun setEventSound(value: String) { values.edit().putString("event_sound", value).apply() }

    companion object {
        const val SYSTEM_SOUND = "system"
        const val SILENT_SOUND = "silent"
    }
}
