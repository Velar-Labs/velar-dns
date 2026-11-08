package app.velardns.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velardns.data.Prefs
import app.velardns.filter.BlockLog
import app.velardns.filter.BlocklistManager
import app.velardns.vpn.VpnStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(status: VpnStatus, latencyMs: Long?, onToggle: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val on = status is VpnStatus.On
    val starting = status is VpnStatus.Starting

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Focusable(
                    onClick = onToggle,
                    modifier = Modifier.size(190.dp),
                    shape = CircleShape,
                    color = if (on) Color(0xFF1B2410) else Color(0xFF18181C),
                    focusedColor = if (on) Color(0xFF243012) else Color(0xFF222228),
                    border = if (on) BorderStroke(4.dp, VColors.Lime) else BorderStroke(2.dp, VColors.KeyBorder),
                    focusRequester = focus,
                    focusedScale = 1.04f,
                ) {
                    VIcon(
                        VIcons.Power,
                        tint = if (on) VColors.Lime else VColors.Text,
                        size = 76.dp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(
                    when {
                        starting -> "Ativando…"
                        on -> "Pressione OK para desativar"
                        else -> "Pressione OK para ativar"
                    },
                    color = VColors.Muted,
                    fontSize = 11.sp,
                )
            }

            Column(
                Modifier.widthIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (status) {
                    is VpnStatus.On -> StatusChip("DoH ativo · criptografado", VColors.Lime, VColors.LimeTint)
                    VpnStatus.Starting -> StatusChip("Ativando…", VColors.Lime, VColors.LimeTint)
                    is VpnStatus.Error -> StatusChip("Não foi possível ativar", VColors.Coral, VColors.CoralTint)
                    VpnStatus.Off -> StatusChip("DNS padrão da rede, sem criptografia", VColors.Coral, VColors.CoralTint)
                }
                Text(
                    if (on) "Protegido" else "Desligado",
                    color = VColors.Text,
                    fontFamily = Sora,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = (-1).sp,
                )
                Text(
                    when (status) {
                        is VpnStatus.On -> "Suas consultas DNS saem criptografadas por HTTPS, protegidas contra espionagem e alteração no caminho."
                        is VpnStatus.Error -> status.message
                        else -> "Sem proteção, as consultas DNS saem abertas e podem ser vistas ou alteradas no caminho. Ative para criptografar."
                    },
                    color = VColors.Soft,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                if (status is VpnStatus.On) {
                    InfoGrid(status, latencyMs)
                } else {
                    FirstTimeSteps()
                }
            }
        }
        KeyHints(
            "OK" to if (on) "Desativar" else "Ativar",
            "◀ ▶ ▲ ▼" to "Navegar",
            "Voltar" to if (on) "Minimizar (continua protegido)" else "Sair",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun InfoGrid(status: VpnStatus.On, latencyMs: Long?) {
    val since = remember(status.sinceMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.sinceMillis))
    }
    val blockedCount by BlockLog.blockedCount.collectAsStateWithLifecycle()
    val protectionOn = Prefs.current.protection
    val domainsLoaded = BlocklistManager.matcher.size

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("Servidor", status.provider.name, status.provider.host, Modifier.weight(1f))
            InfoCard("Latência", latencyMs?.let { "$it ms" } ?: "—", "medida agora", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard("Protocolo", "DoH", "HTTPS · porta 443", Modifier.weight(1f))
            InfoCard(
                "Proteção",
                if (protectionOn) "$blockedCount bloqueadas" else "desligada",
                if (protectionOn && domainsLoaded > 0) "${domainsLoaded / 1000}k domínios" else "ative em Proteção",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VColors.Surface)
            .border(1.dp, VColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = VColors.Muted, fontSize = 10.sp)
        Text(value, color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, color = VColors.Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun FirstTimeSteps() {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VColors.Surface)
            .border(1.dp, VColors.Line, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ao ativar pela primeira vez", color = VColors.Muted, fontSize = 10.sp)
        Step("1", "O Android pede permissão de conexão VPN. Aceite: ela é local, só o DNS passa por ela.")
        Step("2", "Se aparecer “DNS privado não pode ser acessado”, desligue o DNS privado nas configurações de rede da TV.")
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(VColors.Active),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = VColors.Lime, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text, color = VColors.Text, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
