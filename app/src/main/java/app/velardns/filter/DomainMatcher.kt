package app.velardns.filter

/**
 * Motor de filtragem de domínios. Armazena domínios bloqueados num HashSet
 * e suporta correspondência por subdomínio (se "tracker.com" está na lista,
 * "x.tracker.com" também é bloqueado).
 *
 * Thread-safe: a lista pode ser substituída inteira atomicamente.
 */
class DomainMatcher {

    @Volatile
    private var domains: Set<String> = emptySet()

    /** Número de domínios carregados. */
    val size: Int get() = domains.size

    /**
     * Substitui a lista inteira de domínios bloqueados.
     * Os domínios são normalizados: minúsculas, sem "www.", sem ponto final.
     */
    fun load(lines: Sequence<String>) {
        val set = HashSet<String>(200_000)
        for (raw in lines) {
            val domain = parseLine(raw) ?: continue
            set.add(domain)
        }
        domains = set
    }

    /** Verifica se [domain] ou qualquer domínio pai está bloqueado. */
    fun isBlocked(domain: String): Boolean {
        val normalized = domain.lowercase().removePrefix("www.").trimEnd('.')
        if (normalized.isEmpty()) return false
        // Whitelist do usuário tem prioridade (checada fora daqui).
        val set = domains
        // Verifica o domínio inteiro e cada nível pai.
        var d = normalized
        while (d.isNotEmpty()) {
            if (d in set) return true
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }
        return false
    }

    /**
     * Interpreta uma linha de arquivo hosts ou lista de domínios.
     * Aceita formatos:
     * - `dominio.com`
     * - `0.0.0.0 dominio.com`
     * - `127.0.0.1 dominio.com`
     * Ignora comentários (#) e linhas vazias.
     */
    internal fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('!')) return null
        // Exceções ("@@...") e regras com curinga/caminho não viram domínio.
        if (trimmed.startsWith("@@") || trimmed.startsWith('/')) return null
        // Sintaxe de filtro de anúncios: ||dominio.com^ (opcionalmente com $opções).
        if (trimmed.startsWith("||")) {
            val rule = trimmed.removePrefix("||").substringBefore('$')
            if (!rule.endsWith('^')) return null
            val d = rule.removeSuffix("^").lowercase().removePrefix("www.")
            if (d.isEmpty() || d.any { it == '*' || it == '/' || it == '^' || it == ':' } || !d.contains('.')) return null
            return d
        }
        // Remove comentário inline
        val noComment = trimmed.substringBefore('#').substringBefore('!').trim()
        if (noComment.isEmpty()) return null

        val parts = noComment.split(WHITESPACE_REGEX, limit = 3)
        val domain = when {
            // Formato hosts: 0.0.0.0 dominio.com ou 127.0.0.1 dominio.com
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
            // Formato lista pura: dominio.com
            parts.size == 1 -> parts[0]
            else -> return null
        }

        val clean = domain.lowercase().removePrefix("www.").trimEnd('.')
        // Ignora localhost e similares
        if (clean.isEmpty() || clean == "localhost" || clean == "local" ||
            clean == "broadcasthost" || !clean.contains('.')) return null
        return clean
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
