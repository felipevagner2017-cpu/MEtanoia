package br.com.metanoia.domain

/**
 * Leitura contextual offline e transparente.
 * Não substitui aconselhamento pastoral, terapia, consultoria financeira ou IA generativa.
 */
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

        val emotionalTerms = listOf("triste", "desanim", "medo", "ansios", "preocup", "frustr", "cansad", "esgotad", "mago", "decepcion", "angust", "impot", "solitario", "sobrecarreg")
        val fearTerms = listOf("medo", "receio", "preocup", "insegur", "incerteza")
        val sadnessTerms = listOf("triste", "desanim", "decepcion", "mago", "angust", "solitario")
        val workTerms = listOf("trabalho", "trabalhei", "trabalhando", "emprego", "chefe", "colega", "cliente", "empresa", "profissional", "carreira", "meta profissional", "projeto")
        val marriageTerms = listOf("esposa", "marido", "casamento", "conjugal", "matrimonio", "discussao em casa", "relacionamento", "intimidade", "fidelidade", "confianca", "dialogo", "parceria")
        val spiritualTerms = listOf("deus", "orar", "oracao", "fe", "biblia", "igreja", "espiritual", "pecado", "arrepend", "afastado de deus", "escrituras", "palavra")
        val financeTerms = listOf("dinheiro", "financeir", "divida", "conta atrasada", "sem renda", "orcamento", "despesa", "salario", "renda")

        if (emotionalTerms.any(text::contains)) categories += "Expressão emocional"
        if (financeTerms.any(text::contains)) {
            categories += "Dificuldade financeira"
            dimensions += "Individual"
            if (workTerms.any(text::contains)) dimensions += "Profissional"
        }
        if (marriageTerms.any(text::contains)) {
            categories += "Relacionamento conjugal"
            dimensions += "Matrimonial"
        }
        if (workTerms.any(text::contains)) {
            categories += "Questão profissional"
            dimensions += "Profissional"
        }
        if (spiritualTerms.any(text::contains)) {
            categories += "Questão espiritual"
            dimensions += "Espiritual"
        }

        val explicitError = listOf("fiz algo errado", "cometi um erro", "menti", "contei uma mentira", "roubei", "trai", "pequei", "ofendi", "machuquei alguem", "quebrei minha palavra").any(text::contains)
        if (explicitError) categories += "Reconhecimento de erro"
        if (listOf("culpa", "culpado", "culpada", "arrepend", "me arrependo").any(text::contains)) categories += "Culpa ou arrependimento"
        if (listOf("quero mudar", "preciso mudar", "quero abandonar", "luto contra", "estou lutando contra", "quero melhorar").any(text::contains)) categories += "Comportamento a mudar"
        if (fearTerms.any(text::contains)) categories += "Medos e preocupações"
        if (listOf("agrade", "grato", "grata", "conquista", "consegui", "vitoria", "feliz").any(text::contains)) categories += "Gratidão ou conquista"
        if (listOf("nao sei", "por que", "porque", "duvida", "question").any(text::contains)) categories += "Dúvida ou questionamento"
        if (emotionalTerms.any(text::contains)) dimensions += "Individual"
        if (listOf("agenda", "compromisso", "rotina", "habito", "organizar meu dia", "sem tempo").any(text::contains)) dimensions += "Cotidiano"

        val feeling = when {
            sadnessTerms.any(text::contains) -> "O relato expressa tristeza, desânimo ou mágoa."
            listOf("ansios", "preocup", "medo", "insegur", "incerteza").any(text::contains) -> "O relato expressa preocupação, medo ou insegurança."
            listOf("cansad", "esgotad", "sobrecarreg").any(text::contains) -> "O relato expressa cansaço ou sobrecarga."
            listOf("feliz", "grato", "grata", "agrade", "conquista").any(text::contains) -> "O relato expressa gratidão ou satisfação."
            combined.isBlank() -> "Ainda não há relato suficiente para identificar como você está."
            else -> "O relato não permite afirmar com segurança um estado emocional específico."
        }

        val events = agenda.filter { it.isNotBlank() }.distinct().take(5)
        val summary = (if (combined.isBlank()) "Não foi registrado um relato pessoal hoje." else "Registro integrado: " + combined.trim()) +
            if (events.isNotEmpty()) "\nCompromissos relacionados ao dia: " + events.joinToString("; ") + "." else ""

        val attention = when {
            explicitError || categories.contains("Culpa ou arrependimento") -> "Há um erro ou sentimento de culpa explicitamente mencionado. Compreenda os fatos, a responsabilidade e possíveis reparações antes de concluir."
            categories.contains("Relacionamento conjugal") -> "O vínculo conjugal merece escuta, clareza e responsabilidade mútua. Este registro não permite presumir o que Ingrid sente; é preciso compreender os fatos sem atribuir culpa antecipadamente."
            categories.contains("Dificuldade financeira") -> "A preocupação financeira merece separar valores, vencimentos, necessidades essenciais e alternativas possíveis, sem culpa ou promessas fáceis."
            categories.contains("Questão profissional") -> "A situação profissional pode ser organizada em fatos, influência possível, recursos disponíveis e uma próxima decisão."
            categories.contains("Expressão emocional") -> "O sofrimento relatado merece acolhimento e escuta. Ele não é, por si só, evidência de falha espiritual."
            else -> "Nenhum ponto de atenção específico pode ser concluído apenas com os registros disponíveis."
        }

        val learning = when {
            combined.isBlank() -> "Não há relato suficiente para identificar um aprendizado sem inventar conteúdo."
            categories.contains("Relacionamento conjugal") -> "Observe o que o relato revela sobre comunicação, presença, limites e parceria. Registre também o que ainda precisa ser ouvido."
            categories.contains("Questão profissional") -> "Separe resultado, esforço, habilidade utilizada e aprendizado. Um resultado isolado não define sua capacidade."
            categories.contains("Questão espiritual") -> "Identifique o que o próprio texto bíblico afirma, seu contexto e como o princípio pode ser praticado com integridade."
            else -> "O aprendizado deve partir do que você próprio reconheceu; não vamos atribuir conclusões que não escreveu."
        }

        val scripture = when {
            explicitError || categories.contains("Culpa ou arrependimento") ->
                "1 João 1:9" to "No contexto da carta, João trata da vida na luz, da verdade sobre o pecado e da confiança no perdão de Deus. Pertinente quando há reconhecimento real de erro; não se aplica automaticamente à tristeza ou dificuldade comum."
            dimensions.contains("Espiritual") ->
                "Lucas 11:9-13" to "Jesus ensina os discípulos sobre pedir, buscar e bater, no contexto de sua instrução sobre oração e a bondade do Pai. É convite à relação com Deus, não condenação."
            dimensions.contains("Matrimonial") ->
                "Efésios 4:1-3" to "Paulo chama a comunidade à humildade, mansidão, paciência e unidade. O princípio convida à responsabilidade mútua, sem decidir quem teve razão numa discussão."
            categories.contains("Dificuldade financeira") ->
                "Provérbios 21:5" to "O provérbio contrasta planejamento diligente com precipitação. É princípio de prudência prática; não promete enriquecimento nem atribui dificuldade à falta de fé."
            categories.contains("Expressão emocional") ->
                "Salmo 34:18" to "O salmo nasce em contexto de aflição e livramento; afirma a proximidade do Senhor com os quebrantados. A dor é acolhida, não tratada como culpa."
            else -> "" to ""
        }

        val direction = when {
            scripture.first.isNotBlank() -> "Considere ${scripture.first}: ${scripture.second}"
            combined.isBlank() -> "Registre livremente o que desejar. Não é necessário preencher todas as dimensões."
            else -> "Para compreender melhor, o que mais pesou para você hoje e que tipo de apoio ou direção está buscando?"
        }
        val next = when {
            explicitError -> "Descreva o fato sem justificativas precipitadas. Depois identifique como reparar o dano, respeitando a segurança e os limites de todos."
            categories.contains("Relacionamento conjugal") -> "Escolha um momento tranquilo para ouvir sem interromper. Compartilhe sua experiência em primeira pessoa e pergunte qual atitude prática pode fortalecer a parceria."
            categories.contains("Dificuldade financeira") -> "Registre um valor, um vencimento e a prioridade correspondente. Escolha uma ação verificável para organizar essa pendência."
            categories.contains("Questão profissional") -> "Defina o resultado desejado, o obstáculo sob seu controle e uma ação de até 30 minutos com prazo."
            categories.contains("Expressão emocional") -> "Nomeie uma necessidade concreta e, se possível, converse com alguém de confiança. Não precisa resolver tudo hoje."
            dimensions.contains("Espiritual") -> "Leia a passagem em seu contexto imediato, observe o que o texto diz e registre uma aplicação prática que não ultrapasse o sentido do texto."
            dimensions.contains("Cotidiano") -> "Escolha uma prioridade para hoje, reserve um horário realista e retire uma tarefa que não seja essencial."
            else -> "Escolha uma prioridade realista para amanhã, sem transformar produtividade em medida do seu valor."
        }

        return DailyContextResult(categories.toList(), dimensions.toList(), feeling, summary, attention, learning, direction, next, scripture.first, scripture.second)
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
        .replace("é", "e").replace("ê", "e").replace("í", "i").replace("ó", "o")
        .replace("ô", "o").replace("õ", "o").replace("ú", "u").replace("ç", "c")
}
