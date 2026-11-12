package app.velardns.vpn

import app.velardns.data.DohProvider
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface VpnStatus {
    data object Off : VpnStatus
    data object Starting : VpnStatus
    data class On(val provider: DohProvider, val sinceMillis: Long) : VpnStatus
    data class Error(val message: String) : VpnStatus
}

/** Estado da proteção, compartilhado entre o serviço e a interface. */
object VpnState {
    val status = MutableStateFlow<VpnStatus>(VpnStatus.Off)

    /** Média móvel do tempo de resposta do servidor DoH, em ms. */
    val latencyMs = MutableStateFlow<Long?>(null)

    fun recordLatency(ms: Long) {
        val prev = latencyMs.value
        latencyMs.value = if (prev == null) ms else (prev * 4 + ms) / 5
    }

    /** Consultas DNS recebidas pelo túnel desde que o app abriu. */
    val queryCount = java.util.concurrent.atomic.AtomicLong(0)

    val isOn: Boolean get() = status.value is VpnStatus.On
}
