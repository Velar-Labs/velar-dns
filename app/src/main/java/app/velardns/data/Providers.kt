package app.velardns.data

import java.net.URI

/**
 * Um servidor DNS-over-HTTPS.
 *
 * [bootstrap] são os IPs fixos do servidor. O app usa esses IPs para se
 * conectar sem precisar perguntar ao DNS da rede onde fica o servidor DoH
 * (o DNS da rede pode estar alterado — é justamente o que queremos evitar).
 */
data class DohProvider(
    val id: String,
    val name: String,
    val url: String,
    val bootstrap: List<String>,
    val note: String,
) {
    val host: String get() = runCatching { URI(url).host }.getOrNull() ?: url
}

object Providers {
    const val CUSTOM_ID = "custom"

    val builtIn: List<DohProvider> = listOf(
        DohProvider(
            id = "cloudflare",
            name = "Cloudflare",
            url = "https://cloudflare-dns.com/dns-query",
            bootstrap = listOf("1.1.1.1", "1.0.0.1"),
            note = "rápido, sem filtro",
        ),
        DohProvider(
            id = "google",
            name = "Google",
            url = "https://dns.google/dns-query",
            bootstrap = listOf("8.8.8.8", "8.8.4.4"),
            note = "estável, sem filtro",
        ),
        DohProvider(
            id = "quad9",
            name = "Quad9",
            url = "https://dns.quad9.net/dns-query",
            bootstrap = listOf("9.9.9.9", "149.112.112.112"),
            note = "bloqueia domínios maliciosos",
        ),
        DohProvider(
            id = "adguard",
            name = "AdGuard",
            url = "https://dns.adguard-dns.com/dns-query",
            bootstrap = listOf("94.140.14.14", "94.140.15.15"),
            note = "bloqueia anúncios",
        ),
    )

    fun custom(url: String): DohProvider = DohProvider(
        id = CUSTOM_ID,
        name = "Personalizado",
        url = url,
        bootstrap = emptyList(),
        note = "servidor próprio",
    )

    /** Lista na ordem de uso: o escolhido primeiro, depois os outros como reserva. */
    fun failoverOrder(selected: DohProvider): List<DohProvider> =
        listOf(selected) + builtIn.filter { it.id != selected.id }

    fun isValidDohUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank()
    }
}
