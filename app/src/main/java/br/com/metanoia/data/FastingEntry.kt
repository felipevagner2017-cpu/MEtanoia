package br.com.metanoia.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A fasting practice is recorded as a voluntary spiritual/reflection plan.
 * Food abstinence is opt-in and defaults to false; non-food alternatives are the default.
 */
@Entity(
    tableName = "fasting_plans",
    indices = [Index(value = ["dimension", "createdAt"]), Index(value = ["status"])]
)
data class FastingPlan(
    @PrimaryKey val id: String,
    val dimension: LifeDimension,
    val type: String,
    val identifiedNeed: String,
    val recommendationReason: String,
    val purpose: String,
    val scriptureReferences: String,
    val scriptureContext: String,
    val preparation: String,
    val dailyGuidance: String,
    val durationDays: Int,
    val startAt: Long,
    val endAt: Long,
    val foodAbstinence: Boolean = false,
    val status: String = "PROPOSED",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "O plano de jejum precisa de um identificador." }
        require(type == "NON_FOOD" || type == "FOOD") { "Tipo de jejum inválido." }
        require(durationDays > 0) { "A duração precisa ser positiva." }
        require(startAt > 0 && endAt >= startAt) { "O período do plano é inválido." }
        require(createdAt > 0) { "A data de criação do plano é inválida." }
        require(!foodAbstinence || type == "FOOD") { "Abstinência alimentar exige tipo FOOD explícito." }
    }
}

@Entity(
    tableName = "fasting_daily_entries",
    indices = [Index(value = ["planId", "dayIndex"], unique = true)]
)
data class FastingDailyEntry(
    @PrimaryKey val id: String,
    val planId: String,
    val dayIndex: Int,
    val reflection: String,
    val experience: String,
    val recordedAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank() && planId.isNotBlank()) { "A reflexão precisa estar vinculada a um plano." }
        require(dayIndex >= 0) { "O índice do dia não pode ser negativo." }
        require(recordedAt > 0) { "A data da reflexão é inválida." }
    }
}
