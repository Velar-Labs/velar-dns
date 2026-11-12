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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
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
    val newVersion by app.velardns.data.UpdateChecker.available.collectAsStateWithLifecycle()

    // Se a proteção estava ligada e continua desligada alguns segundos depois de abrir o app,
    // o Android/firmware não religou sozinho: avisa em vez de deixar a TV sem proteção em silêncio.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(8_000); waited = true }
    val notRestarted = waited && status is VpnStatus.Off && settings.wasEnabled

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
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (notRestarted) {
                    Banner("A proteção não religou sozinha depois de reiniciar. Vá em Início e pressione OK para ativar.", warn = true)
                }
                if (newVersion != null && settings.checkUpdates) {
                    Banner("Versão $newVersion disponível. Baixe em github.com/Velar-Labs/velar-dns/releases e instale por cima.", warn = false)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(screen, status, latency, settings, onToggle) }
            }
        }
    }
}

@Composable
private fun Banner(text: String, warn: Boolean) {
    Text(
        text,
        color = if (warn) VColors.CoralText else VColors.Lime,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (warn) VColors.CoralTint else VColors.LimeTint, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScreenContent(
    screen: Screen,
    status: VpnStatus,
    latency: Long?,
    settings: app.velardns.data.VelarSettings,
    onToggle: () -> Unit,
) {
    when (screen) {
        Screen.Home -> HomeScreen(status, latency, onToggle)
        Screen.Protection -> ProtectionScreen(settings)
        Screen.Servers -> ServersScreen(settings, status)
        Screen.Diagnostics -> DiagnosticsScreen()
        Screen.Settings -> SettingsScreen(settings)
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
