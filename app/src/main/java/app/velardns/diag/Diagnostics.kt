package app.velardns.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.velardns.data.Prefs
import app.velardns.dns.Doh
import app.velardns.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class CheckStatus { RUNNING, OK, WARN, FAIL }

data class Check(val title: String, val detail: String, val status: CheckStatus, val label: String)

data class DiagnosticResult(val checks: List<Check>, val headline: String, val summary: String, val good: Boolean)

/** Os quatro testes da tela Diagnóstico. */
object Diagnostics {

    fun placeholder(): List<Check> = listOf(
        Check("Conexão com a internet", "Verificando…", CheckStatus.RUNNING, "…"),
        Check("DNS padrão da rede", "Verificando…", CheckStatus.RUNNING, "…"),
        Check("Servidor DoH alcançável", "Verificando…", CheckStatus.RUNNING, "…"),
        Check("Velar DNS no aparelho", "Verificando…", CheckStatus.RUNNING, "…"),
        Check("Possíveis vazamentos de DNS", "Verificando…", CheckStatus.RUNNING, "…"),
    )

    /** A rede física (Wi-Fi/cabo), ignorando a própria VPN. */
    @Suppress("DEPRECATION")
    private fun underlyingNetwork(cm: ConnectivityManager): Pair<Network, NetworkCapabilities>? =
        cm.allNetworks.mapNotNull { n -> cm.getNetworkCapabilities(n)?.let { n to it } }
            .filter { (_, caps) ->
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            .sortedByDescending { (_, caps) -> caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
            .firstOrNull()

    suspend fun run(context: Context): DiagnosticResult = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val provider = Prefs.current.selectedProvider
        val net = underlyingNetwork(cm)

        val internet = when {
            net == null -> Check("Conexão com a internet", "Nenhuma rede conectada", CheckStatus.FAIL, "Sem rede")
            net.second.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                Check("Conexão com a internet", "Wi-Fi / cabo funcionando", CheckStatus.OK, "OK")
            else -> Check("Conexão com a internet", "Rede conectada, mas sem acesso confirmado à internet", CheckStatus.WARN, "Limitada")
        }

        val systemDns = if (net == null) {
            Check("DNS padrão da rede", "Sem rede para testar", CheckStatus.FAIL, "—")
        } else {
            val ips = runCatching {
                withTimeout(5000) { net.first.getAllByName(Doh.TEST_DOMAIN).map { it.hostAddress ?: "" } }
            }.getOrNull()
            when {
                ips == null -> Check("DNS padrão da rede", "Não respondeu ao domínio de teste", CheckStatus.WARN, "Sem resposta")
                ips.any { it in Doh.TEST_EXPECTED } -> Check("DNS padrão da rede", "Respondeu corretamente ao domínio de teste", CheckStatus.OK, "OK")
                else -> Check("DNS padrão da rede", "Resposta diferente da esperada para o domínio de teste", CheckStatus.WARN, "Alterado")
            }
        }

        val probe = runCatching { Doh.probe(provider) }.getOrNull()
        val doh = when {
            probe == null -> Check("Servidor DoH alcançável", "${provider.host} não respondeu", CheckStatus.FAIL, "Falhou")
            probe.ips.none { it in Doh.TEST_EXPECTED } ->
                Check("Servidor DoH alcançável", "${provider.host} respondeu, mas com resultado inesperado", CheckStatus.WARN, "Atenção")
            else -> Check("Servidor DoH alcançável", "${provider.host} respondeu em ${probe.ms} ms", CheckStatus.OK, "OK")
        }

        val active = if (VpnState.isOn) {
            Check("Velar DNS no aparelho", "As consultas DNS do aparelho passam pelo Velar DNS", CheckStatus.OK, "OK")
        } else {
            Check("Velar DNS no aparelho", "A proteção está desligada", CheckStatus.WARN, "Desligado")
        }

        val leak = leakCheck(context)
        val checks = listOf(internet, systemDns, doh, active, leak)
        val (headline, summary, good) = when {
            internet.status == CheckStatus.FAIL ->
                Triple("Sem internet", "Conecte a TV ao Wi-Fi ou cabo e teste de novo.", false)
            doh.status == CheckStatus.FAIL ->
                Triple("Servidor indisponível", "O servidor escolhido não respondeu. Escolha outro em Servidores ou deixe a troca automática ligada.", false)
            active.status == CheckStatus.OK && leak.status != CheckStatus.OK ->
                Triple("Atenção: possível vazamento", leak.detail, false)
            active.status != CheckStatus.OK ->
                Triple("Proteção desligada", "Tudo pronto para ativar. Volte ao Início e pressione OK.", false)
            else ->
                Triple("Conexão protegida", "Suas consultas vão criptografadas direto para o servidor escolhido, sem depender do DNS padrão da rede.", true)
        }
        DiagnosticResult(checks, headline, summary, good)
    }

    /**
     * Procura motivos pelos quais consultas DNS poderiam escapar do túnel.
     * O próprio app fica fora da VPN, então não dá para "mandar uma consulta de teste":
     * o que se verifica é a configuração do Android e se o túnel está recebendo consultas.
     */
    private fun leakCheck(context: Context): Check {
        val title = "Possíveis vazamentos de DNS"
        if (!VpnState.isOn) return Check(title, "Ligue a proteção para testar", CheckStatus.WARN, "—")
        val mode = runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        if (mode == "hostname") {
            return Check(
                title,
                "DNS privado está em modo fixo no Android: pode ignorar o Velar DNS. Desligue em Rede e internet.",
                CheckStatus.WARN, "DNS privado",
            )
        }
        val since = (VpnState.status.value as? app.velardns.vpn.VpnStatus.On)?.sinceMillis ?: System.currentTimeMillis()
        val minutes = (System.currentTimeMillis() - since) / 60_000
        if (VpnState.queryCount.get() == 0L && minutes >= 2) {
            return Check(
                title,
                "Nenhuma consulta chegou ao túnel em $minutes min. Outro app de VPN ou o DNS privado pode estar desviando o DNS.",
                CheckStatus.WARN, "Sem consultas",
            )
        }
        if (!Prefs.current.forceAll) {
            return Check(
                title,
                "\"Forçar todo DNS\" está desligado: apps com DNS próprio (ex.: 8.8.8.8) não são cobertos.",
                CheckStatus.WARN, "Parcial",
            )
        }
        return Check(title, "${VpnState.queryCount.get()} consultas tratadas; nenhuma configuração desviando o DNS", CheckStatus.OK, "OK")
    }
}
