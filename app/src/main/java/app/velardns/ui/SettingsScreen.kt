package app.velardns.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velardns.BuildConfig
import app.velardns.data.Prefs
import app.velardns.data.VelarSettings
import app.velardns.vpn.VelarVpnService
import app.velardns.vpn.VpnState

@Composable
fun SettingsScreen(settings: VelarSettings) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajustes", color = VColors.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ToggleRow(
                title = "Ligar junto com a TV",
                detail = "Ativa a proteção sozinho depois de reiniciar",
                on = settings.autostart,
                focusRequester = focus,
            ) { Prefs.update { it.copy(autostart = !it.autostart) } }

            ToggleRow(
                title = "Trocar de servidor se falhar",
                detail = "Se o DoH escolhido cair, usa o próximo da lista",
                on = settings.failover,
            ) { Prefs.update { it.copy(failover = !it.failover) } }

            ToggleRow(
                title = "Forçar todo DNS pelo Velar DNS",
                detail = "Impede apps de usarem DNS sem criptografia por conta própria",
                on = settings.forceAll,
            ) {
                Prefs.update { it.copy(forceAll = !it.forceAll) }
                // A lista de rotas muda: o túnel precisa ser recriado.
                if (VpnState.isOn) VelarVpnService.start(context)
            }

            ToggleRow(
                title = "Aviso na tela ao ligar e desligar",
                detail = "Mostra um aviso rápido quando a proteção muda de estado",
                on = settings.toast,
            ) { Prefs.update { it.copy(toast = !it.toast) } }

            Focusable(
                onClick = {
                    try {
                        context.startActivity(Intent("android.settings.VPN_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Esta TV não tem a tela de VPN nas configurações", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VPN sempre ativa (Android)", color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Abra e marque o Velar DNS como “sempre ativa” para máxima estabilidade",
                            color = VColors.Muted, fontSize = 10.sp,
                        )
                    }
                    VIcon(VIcons.Chevron, VColors.Muted, 16.dp)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(VColors.Surface)
                    .border(1.dp, VColors.Line, RoundedCornerShape(9.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Velar DNS ${BuildConfig.VERSION_NAME}", color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("DNS criptografado + proteção", color = VColors.Lime, fontSize = 10.sp)
                    Text("Desenvolvido por Velar Labs", color = VColors.Muted, fontSize = 10.sp)
                }
                LogoMark(22.dp)
            }
        }

        KeyHints("OK" to "Ligar / desligar", "▲ ▼" to "Navegar", "Voltar" to "Menu")
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    on: Boolean,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit,
) {
    Focusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = VColors.Muted, fontSize = 10.sp)
            }
            SwitchPill(on)
        }
    }
}
