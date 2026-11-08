package app.velardns.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.velardns.data.Prefs

/** Religa a proteção depois que a TV reinicia (ou depois de uma atualização do app). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        val settings = Prefs.current
        if (!settings.autostart || !settings.wasEnabled) return
        // Se a permissão de VPN foi perdida, só dá para religar abrindo o app.
        if (VpnService.prepare(context) != null) return
        try {
            VelarVpnService.start(context)
        } catch (_: Exception) {
            // Alguns firmwares bloqueiam serviços no boot; a "VPN sempre ativa" do Android cobre esse caso.
        }
    }
}
