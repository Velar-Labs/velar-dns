package app.velardns.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velardns.BuildConfig
import app.velardns.data.Prefs
import app.velardns.vpn.VelarVpnService
import app.velardns.vpn.VpnState
import app.velardns.vpn.VpnStatus

class MainActivity : ComponentActivity() {

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startProtection()
            } else {
                VpnState.status.value =
                    VpnStatus.Error("Permissão de VPN recusada. Pressione OK e aceite para ativar.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { VelarRoot(onToggle = ::toggle) }
    }

    private fun toggle() {
        val status = VpnState.status.value
        if (status is VpnStatus.On || status is VpnStatus.Starting) {
            VelarVpnService.stop(this)
            notify("Velar DNS desligado")
            return
        }
        val consent = VpnService.prepare(this)
        if (consent == null) {
            startProtection()
            return
        }
        try {
            vpnPermission.launch(consent)
        } catch (_: ActivityNotFoundException) {
            VpnState.status.value = VpnStatus.Error("Este aparelho não permite apps de VPN.")
        }
    }

    private fun startProtection() {
        VelarVpnService.start(this)
        notify("Velar DNS ativado")
    }

    private fun notify(message: String) {
        if (Prefs.current.toast) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

enum class Screen(val label: String) { Home("Início"), Protection("Proteção"), Servers("Servidores"), Diagnostics("Diagnóstico"), Settings("Ajustes") }

private fun Screen.icon(): ImageVector = when (this) {
    Screen.Home -> VIcons.Home
    Screen.Protection -> VIcons.Shield
    Screen.Servers -> VIcons.Server
    Screen.Diagnostics -> VIcons.Pulse
    Screen.Settings -> VIcons.Sliders
}

@Composable
fun VelarRoot(onToggle: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val status by VpnState.status.collectAsStateWithLifecycle()
    val latency by VpnState.latencyMs.collectAsStateWithLifecycle()
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity

    BackHandler {
        if (screen != Screen.Home) screen = Screen.Home else activity?.moveTaskToBack(true)
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(VColors.Bg),
    ) {
        NavRail(current = screen, onSelect = { screen = it })
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            when (screen) {
                Screen.Home -> HomeScreen(status, latency, onToggle)
                Screen.Protection -> ProtectionScreen(settings)
                Screen.Servers -> ServersScreen(settings, status)
                Screen.Diagnostics -> DiagnosticsScreen()
                Screen.Settings -> SettingsScreen(settings)
            }
        }
    }
}

@Composable
private fun NavRail(current: Screen, onSelect: (Screen) -> Unit) {
    Column(
        Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(VColors.Nav)
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogoMark(28.dp)
            Wordmark(16.sp)
        }
        Screen.entries.forEach { item ->
            val selected = item == current
            Focusable(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth(),
                color = if (selected) VColors.Active else VColors.Nav,
                focusedColor = VColors.Active,
                border = null,
            ) { focused ->
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    VIcon(item.icon(), if (selected) VColors.Lime else if (focused) VColors.Text else VColors.Muted)
                    Text(
                        item.label,
                        color = if (selected || focused) VColors.Text else VColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("versão ${BuildConfig.VERSION_NAME} · Velar Labs", color = VColors.Faint, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp))
    }
}
