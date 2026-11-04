package br.com.metanoia.domain

/** Transparent offline rules; "confesso" alone is never treated as sin. */
data class DailyContextResult(
    val categories: List<String>, val dimensions: List<String>, val emotionalState: String,
    val summary: String, val attention: String, val learning: String, val direction: String,
    val nextStep: String, val scriptureReference: String, val scriptureContext: String
)

object DailyContextAnalyzer {
    fun analyze(diary: String, confession: String, agenda: List<String> = emptyList()): DailyContextResult {
        val combined = listOf(diary, confession).filter { it.isNotBlank() }.joinToString("\n")
        val text = normalize(combined)
        val categories = linkedSetOf<String>()
        val dimensions = linkedSetOf<String>()
        val emotions = listOf("triste", "desanim", "medo", "ansios", "preocup", "frustr", "cansad", "esgotad", "mago", "decepcion", "angust", "impot")
        if (emotions.any(text::contains)) categories += "Expressão emocional"
        if (listOf("dinheiro", "financeir", "divida", "conta atrasada", "sem renda", "orcamento").any(text::contains)) {
            categories += "Dificuldade financeira"; dimensions += "Individual"
            if (listOf("salario", "trabalho", "emprego", "empresa", "cliente", "comissao", "renda profissional", "venda").any(text::contains)) dimensions += "Profissional"
        }
        if (listOf("esposa", "marido", "casamento", "conjugal", "matrimonio", "discussao em casa", "filho", "familia").any(text::contains)) { categories += "Conflito ou vínculo familiar"; dimensions += "Matrimonial" }
        if (listOf("trabalho", "trabalhei", "trabalhando", "emprego", "chefe", "colega", "cliente", "empresa", "profissional", "carreira").any(text::contains)) { categories += "Questão profissional"; dimensions += "Profissional" }
        if (listOf("deus", "orar", "oracao", "fe", "biblia", "igreja", "espiritual", "pecado", "arrepend", "afastado de deus").any(text::contains)) { categories += "Questão espiritual"; dimensions += "Espiritual" }
        val explicitError = listOf("fiz algo errado", "cometi um erro", "menti", "contei uma mentira", "roubei", "trai", "pequei", "ofendi", "machuquei alguem", "quebrei minha palavra").any(text::contains)
        if (explicitError) categories += "Reconhecimento de erro"
        if (listOf("culpa", "culpado", "culpada", "arrepend", "me arrependo").any(text::contains)) categories += "Culpa ou arrependimento"
        if (listOf("quero mudar", "preciso mudar", "quero abandonar", "luto contra", "estou lutando contra", "quero melhorar").any(text::contains)) categories += "Comportamento a mudar"
        if (listOf("medo", "receio", "preocup", "insegur").any(text::contains)) categories += "Medos e preocupações"
        if (listOf("agrade", "grato", "grata", "conquista", "consegui", "vitoria", "feliz").any(text::contains)) categories += "Gratidão ou conquista"
        if (listOf("nao sei", "por que", "porque", "duvida", "question").any(text::contains)) categories += "Dúvida ou questionamento"
        if (emotions.any(text::contains)) dimensions += "Individual"
        if (dimensions.isEmpty() && combined.isNotBlank()) dimensions += "Individual"
        val feeling = when {
            listOf("triste", "desanim", "decepcion", "mago", "angust").any(text::contains) -> "O relato expressa tristeza, desânimo ou mágoa."
            listOf("ansios", "preocup", "medo", "insegur").any(text::contains) -> "O relato expressa preocupação, medo ou insegurança."
            listOf("cansad", "esgotad").any(text::contains) -> "O relato expressa cansaço."
            listOf("feliz", "grato", "grata", "agrade", "conquista").any(text::contains) -> "O relato expressa gratidão ou satisfação."
            combined.isBlank() -> "Ainda não há relato suficiente para identificar como você está."
            else -> "O relato não permite afirmar com segurança um estado emocional específico."
        }
        val events = agenda.filter { it.isNotBlank() }.distinct().take(5)
        val summary = (if (combined.isBlank()) "Não foi registrado um relato pessoal hoje." else "Registro integrado: " + combined.trim()) +
            if (events.isNotEmpty()) "\nCompromissos relacionados ao dia: " + events.joinToString("; ") + "." else ""
        val attention = when {
            explicitError || categories.contains("Culpa ou arrependimento") -> "Há um erro ou sentimento de culpa explicitamente mencionado. Compreenda os fatos e possíveis reparações antes de concluir."
            categories.contains("Dificuldade financeira") -> "A preocupação financeira merece ser separada em fatos, urgências e alternativas possíveis."
            categories.contains("Conflito ou vínculo familiar") -> "O conflito familiar merece escuta dos envolvidos, sem atribuir culpa automaticamente."
            categories.contains("Expressão emocional") -> "O sofrimento relatado merece acolhimento; ele não é, por si só, evidência de falha espiritual."
            else -> "Nenhum ponto de atenção específico pode ser concluído apenas com os registros disponíveis."
        }
        val learning = if (combined.isBlank()) "Não há relato suficiente para identificar um aprendizado sem inventar conteúdo." else "O aprendizado deve partir do que você próprio reconheceu; não vamos atribuir conclusões que não escreveu."
        val scripture = when {
            explicitError || categories.contains("Culpa ou arrependimento") -> "1 João 1:9" to "No contexto da carta, João trata da vida na luz, da verdade sobre o pecado e da confiança no perdão de Deus. Pertinente quando há reconhecimento real de erro; não se aplica automaticamente à tristeza ou dificuldade comum."
            dimensions.contains("Espiritual") -> "Lucas 11:9-13" to "Jesus ensina os discípulos sobre pedir, buscar e bater, no contexto de sua instrução sobre oração e a bondade do Pai. É convite à relação com Deus, não condenação."
            dimensions.contains("Matrimonial") -> "Efésios 4:1-3" to "Paulo chama a comunidade à humildade, mansidão, paciência e unidade. O princípio convida à responsabilidade mútua, sem decidir quem teve razão numa discussão."
            categories.contains("Dificuldade financeira") -> "Provérbios 21:5" to "O provérbio contrasta planejamento diligente com precipitação. É princípio de prudência prática; não promete enriquecimento nem atribui dificuldade à falta de fé."
            categories.contains("Expressão emocional") -> "Salmo 34:18" to "O salmo nasce em contexto de aflição e livramento; afirma a proximidade do Senhor com os quebrantados. A dor é acolhida, não tratada como culpa."
            else -> "" to ""
        }
        val direction = if (scripture.first.isNotBlank()) "Considere " + scripture.first + ": " + scripture.second else if (combined.isBlank()) "Registre livremente o que desejar. Não é necessário preencher todas as dimensões." else "Não há elementos suficientes para escolher uma passagem específica com responsabilidade. Uma pergunta de esclarecimento é melhor que um versículo aleatório."
        val next = when {
            categories.contains("Dificuldade financeira") -> "Anote uma preocupação financeira concreta, o prazo e uma pequena ação verificável que esteja ao seu alcance."
            categories.contains("Conflito ou vínculo familiar") -> "Quando houver tranquilidade, descreva o que você sentiu e o que precisa comunicar, sem preparar uma acusação."
            explicitError -> "Identifique o fato específico e, se for seguro e apropriado, uma forma responsável de reparar o dano."
            categories.contains("Expressão emocional") -> "Escolha uma pessoa de confiança com quem possa conversar e defina uma pequena necessidade para amanhã."
            else -> "Escolha uma prioridade realista para amanhã, sem transformar produtividade em medida do seu valor."
        }
        return DailyContextResult(categories.toList(), dimensions.toList(), feeling, summary, attention, learning, direction, next, scripture.first, scripture.second)
    }
    private fun normalize(value: String): String = value.lowercase()
        .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a").replace("é", "e").replace("ê", "e")
        .replace("í", "i").replace("ó", "o").replace("ô", "o").replace("õ", "o").replace("ú", "u").replace("ç", "c")
}
