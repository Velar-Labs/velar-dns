package app.velardns.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VelarSettings(
    val providerId: String = "cloudflare",
    val customUrl: String = "",
    val autostart: Boolean = true,
    val failover: Boolean = true,
    val forceAll: Boolean = true,
    val toast: Boolean = false,
    /** Se a proteção estava ligada quando o usuário saiu — usado ao reiniciar a TV. */
    val wasEnabled: Boolean = false,
    /** Proteção contra domínios maliciosos (phishing, malware, C&C). */
    val protection: Boolean = true,
    /** Lista extra de anúncios e rastreadores (AdGuard DNS filter). */
    val blockAds: Boolean = false,
    /** Avisa quando há versão nova no GitHub (uma consulta a cada ~12 h). */
    val checkUpdates: Boolean = true,
) {
    val selectedProvider: DohProvider
        get() = if (providerId == Providers.CUSTOM_ID && Providers.isValidDohUrl(customUrl)) {
            Providers.custom(customUrl.trim())
        } else {
            Providers.builtIn.firstOrNull { it.id == providerId } ?: Providers.builtIn.first()
        }
}

/** Preferências do app, com um StateFlow para a interface reagir às mudanças. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(VelarSettings())
    val state: StateFlow<VelarSettings> = _state.asStateFlow()
    val current: VelarSettings get() = _state.value

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("velar", Context.MODE_PRIVATE)
        _state.value = VelarSettings(
            providerId = sp.getString("provider_id", "cloudflare") ?: "cloudflare",
            customUrl = sp.getString("custom_url", "") ?: "",
            autostart = sp.getBoolean("autostart", true),
            failover = sp.getBoolean("failover", true),
            forceAll = sp.getBoolean("force_all", true),
            toast = sp.getBoolean("toast", false),
            wasEnabled = sp.getBoolean("was_enabled", false),
            protection = sp.getBoolean("protection", true),
            blockAds = sp.getBoolean("block_ads", false),
            checkUpdates = sp.getBoolean("check_updates", true),
        )
    }

    fun update(change: (VelarSettings) -> VelarSettings) {
        val next = change(_state.value)
        _state.value = next
        sp.edit()
            .putString("provider_id", next.providerId)
            .putString("custom_url", next.customUrl)
            .putBoolean("autostart", next.autostart)
            .putBoolean("failover", next.failover)
            .putBoolean("force_all", next.forceAll)
            .putBoolean("toast", next.toast)
            .putBoolean("was_enabled", next.wasEnabled)
            .putBoolean("protection", next.protection)
            .putBoolean("block_ads", next.blockAds)
            .putBoolean("check_updates", next.checkUpdates)
            .apply()
    }
}
