package app.velardns.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velardns.diag.Check
import app.velardns.diag.CheckStatus
import app.velardns.diag.DiagnosticResult
import app.velardns.diag.Diagnostics

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    var run by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(run) {
        result = null
        result = Diagnostics.run(context)
    }

    val checks = result?.checks ?: Diagnostics.placeholder()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Diagnóstico", color = VColors.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text(
                    "Confere se o DNS da rede responde corretamente e se a conexão criptografada está funcionando.",
                    color = VColors.Muted, fontSize = 12.sp,
                )
            }
            Focusable(
                onClick = { run++ },
                color = VColors.Lime,
                focusedColor = VColors.LimeLight,
                border = null,
                focusRequester = focus,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    VIcon(VIcons.Refresh, VColors.OnLime, 13.dp)
                    Text("Testar de novo", color = VColors.OnLime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                checks.forEach { CheckRow(it) }
            }
            ResultCard(result, Modifier.weight(2f))
        }

        KeyHints("OK" to "Testar de novo", "Voltar" to "Menu")
    }
}

@Composable
private fun CheckRow(check: Check) {
    val warn = check.status == CheckStatus.WARN || check.status == CheckStatus.FAIL
    val accent = when (check.status) {
        CheckStatus.OK -> VColors.Lime
        CheckStatus.RUNNING -> VColors.Muted
        else -> VColors.Coral
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (warn) Color(0xFF1F120E) else VColors.Surface)
            .border(1.dp, if (warn) VColors.CoralBorder else VColors.Line, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (warn) VColors.CoralTint else VColors.LimeTint),
            contentAlignment = Alignment.Center,
        ) {
            VIcon(if (check.status == CheckStatus.OK) VIcons.Check else VIcons.Alert, accent, 14.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(check.title, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(check.detail, color = if (warn) VColors.CoralText else VColors.Muted, fontSize = 10.sp)
        }
        Text(check.label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultCard(result: DiagnosticResult?, modifier: Modifier = Modifier) {
    val good = result?.good == true
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(11.dp))
            .background(if (good || result == null) VColors.LimeTint else VColors.CoralTint)
            .border(
                BorderStroke(1.dp, if (good || result == null) VColors.LimeBorder else VColors.CoralBorder),
                RoundedCornerShape(11.dp),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LogoMark(28.dp)
        Text(
            result?.headline ?: "Testando…",
            color = VColors.Text,
            fontFamily = Sora,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
        Text(
            result?.summary ?: "Isso leva alguns segundos.",
            color = VColors.Soft,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Se o servidor DoH falhar, o app tenta o próximo da lista automaticamente.",
            color = VColors.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}
