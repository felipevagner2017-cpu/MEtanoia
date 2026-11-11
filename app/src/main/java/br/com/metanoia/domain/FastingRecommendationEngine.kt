package br.com.metanoia.domain

import br.com.metanoia.data.FastingPlan
import br.com.metanoia.data.LifeDimension
import java.util.Locale

data class FastingSuggestion(
    val dimension: LifeDimension,
    val identifiedNeed: String,
    val recommendationReason: String,
    val purpose: String,
    val scriptureReferences: List<String>,
    val scriptureContext: String,
    val preparation: String,
    val dailyGuidance: String,
    val suggestedDurationDays: Int = 1,
    val type: String = "NON_FOOD"
)

/**
 * Scripture-led suggestions are reflective invitations, not spiritual guarantees or medical advice.
 * The engine intentionally recommends non-food abstinence by default.
 */
class FastingRecommendationEngine {
    fun recommend(
        dimension: LifeDimension,
        recentRecords: List<String>,
        previousPlans: List<FastingPlan>
    ): FastingSuggestion? {
        val text = recentRecords.joinToString(" ").lowercase(Locale.ROOT)
        val alreadySuggested = previousPlans.take(8).any {
            it.dimension == dimension && it.type == "NON_FOOD" &&
                (System.currentTimeMillis() - it.createdAt) < RECENCY_WINDOW_MS
        }
        if (alreadySuggested) return null

        val (need, reason, purpose) = identifyNeed(dimension, text, recentRecords.isEmpty())
        val (references, context, practice) = scriptureFor(dimension)
        return FastingSuggestion(
            dimension = dimension,
            identifiedNeed = need,
            recommendationReason = reason,
            purpose = purpose,
            scriptureReferences = references,
            scriptureContext = context,
            preparation = "Escolha voluntariamente uma distração ou hábito não essencial para pausar. Avise pessoas envolvidas quando necessário, defina um período realista e preserve sono, alimentação, hidratação, medicamentos e responsabilidades.",
            dailyGuidance = practice,
            suggestedDurationDays = 1
        )
    }

    private fun identifyNeed(dimension: LifeDimension, text: String, noRecords: Boolean): Triple<String, String, String> {
        val signals = when (dimension) {
            LifeDimension.SPIRITUAL -> listOf(
                listOf("distra", "celular", "rede social", "pressa") to "Distrações podem estar reduzindo o espaço de atenção dedicado à oração e às Escrituras."
            )
            LifeDimension.MATRIMONIAL -> listOf(
                listOf("conflito", "discuss", "comunica", "distante", "perdão") to "Os registros mencionam tensão ou necessidade de comunicação; isso não permite concluir a perspectiva da outra pessoa."
            )
            LifeDimension.INDIVIDUAL, LifeDimension.ROUTINE -> listOf(
                listOf("procrast", "ansied", "excesso", "sono", "organiza", "hábito") to "Os registros apontam uma oportunidade de rever hábitos, atenção e prioridades."
            )
            LifeDimension.PROFESSIONAL -> listOf(
                listOf("procrast", "foco", "distra", "sobrecarga") to "Os registros sugerem uma oportunidade de proteger períodos de foco e rever distrações."
            )
        }
        val match = signals.firstOrNull { (keywords, _) -> keywords.any(text::contains) }
        val need = match?.second ?: when (dimension) {
            LifeDimension.SPIRITUAL -> "Criar espaço intencional para oração e meditação bíblica."
            LifeDimension.MATRIMONIAL -> "Dedicar atenção à escuta, oração e atitudes concretas de cuidado."
            LifeDimension.PROFESSIONAL -> "Rever prioridades e proteger um período de concentração."
            else -> "Observar hábitos e escolher uma prioridade cotidiana."
        }
        val reason = if (noRecords) {
            "Não há registros recentes suficientes para inferir uma dificuldade específica; esta é uma sugestão geral, não uma avaliação."
        } else {
            match?.second ?: "A sugestão é geral porque os registros fornecidos não apresentam um sinal específico para esta dimensão."
        }
        return Triple(need, reason, when (dimension) {
            LifeDimension.SPIRITUAL -> "Separar um período de atenção para buscar a Deus em oração e na Palavra, sem tratar a prática como troca ou garantia de resultado."
            LifeDimension.MATRIMONIAL -> "Orar pelo relacionamento e praticar escuta respeitosa, responsabilidade e uma atitude concreta de cuidado, sem tentar controlar a resposta de outra pessoa."
            LifeDimension.PROFESSIONAL -> "Praticar domínio próprio sobre distrações e dedicar atenção às prioridades profissionais."
            else -> "Refletir sobre prioridades e exercitar domínio próprio com uma pausa voluntária em um hábito não essencial."
        })
    }

    private fun scriptureFor(dimension: LifeDimension): Triple<List<String>, String, String> = when (dimension) {
        LifeDimension.SPIRITUAL -> Triple(
            listOf("Mateus 6:16–18", "Mateus 4:1–4", "Atos 13:1–3"),
            "Em Mateus 6, Jesus corrige a busca por reconhecimento religioso e orienta discrição e sinceridade. Em Mateus 4, o jejum de Jesus aparece no contexto de sua tentação e fidelidade à Palavra. Em Atos 13, jejum e oração acompanham a comunidade na busca de direção; o texto não transforma o jejum em mecanismo de controle de Deus.",
            "Leia o texto em seu contexto, ore com sinceridade e anote uma aplicação concreta. Reserve um momento sem a distração escolhida para escuta, oração e leitura."
        )
        LifeDimension.MATRIMONIAL -> Triple(
            listOf("Efésios 4:1–3, 25–32", "Tiago 1:19–20", "Colossenses 3:12–14"),
            "Efésios trata da unidade da comunidade e relaciona humildade, verdade, domínio da ira, perdão e amor. Tiago orienta prontidão para ouvir e cautela ao falar e irar-se. Colossenses apresenta virtudes que sustentam relações; nenhum desses textos autoriza impor reconciliação ou ignorar limites e segurança.",
            "Ore sem exigir mudança do outro. Escolha uma atitude sua de escuta ou cuidado e, se for seguro e oportuno, convide a uma conversa respeitosa."
        )
        LifeDimension.PROFESSIONAL -> Triple(
            listOf("Provérbios 16:3", "Colossenses 3:23–24", "Lucas 14:28–30"),
            "Provérbios 16:3 pertence à literatura sapiencial e chama a confiar os planos ao Senhor. Colossenses 3:23–24 orienta o trabalho com integridade no contexto da vida comunitária cristã. Lucas 14:28–30 usa o exemplo de calcular o custo para ensinar sobre o discipulado, não como promessa de sucesso profissional.",
            "Escolha uma distração não essencial para pausar durante um bloco de trabalho e defina uma entrega concreta e realista."
        )
        else -> Triple(
            listOf("Gálatas 5:22–23", "1 Coríntios 6:12", "Provérbios 4:23"),
            "Gálatas descreve o fruto do Espírito, incluindo domínio próprio. Em 1 Coríntios 6, Paulo discute liberdade e não se deixar dominar por coisa alguma. Provérbios 4 reúne instruções de sabedoria sobre guardar o coração e orientar a vida.",
            "Escolha um hábito não essencial que esteja consumindo atenção e faça uma pausa breve. Observe o que sente e qual alternativa saudável pode ocupar esse espaço."
        )
    }

    companion object { private const val RECENCY_WINDOW_MS = 14L * 24 * 60 * 60 * 1000 }
}
