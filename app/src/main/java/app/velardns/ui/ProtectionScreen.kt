package app.velardns.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.velardns.data.Prefs
import app.velardns.data.VelarSettings
import app.velardns.filter.BlockLog
import app.velardns.filter.BlocklistManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProtectionScreen(settings: VelarSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val blockedCount by BlockLog.blockedCount.collectAsStateWithLifecycle()
    val entries by BlockLog.entries.collectAsStateWithLifecycle()
    var updating by remember { mutableStateOf(false) }
    val lastUpdate = remember { BlocklistManager.lastUpdateMillis(context) }
    val domainsLoaded = BlocklistManager.matcher.size

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Proteção", color = VColors.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Toggle proteção
            ToggleRow(
                title = "Bloquear domínios maliciosos",
                detail = "Impede acesso a phishing, malware e servidores de controle",
                on = settings.protection,
                focusRequester = focus,
            ) { Prefs.update { it.copy(protection = !it.protection) } }

            // Estatísticas
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Ameaças bloqueadas", "$blockedCount", Modifier.weight(1f))
                StatCard("Domínios na lista", formatNumber(domainsLoaded), Modifier.weight(1f))
            }

            // Status da lista
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
                    Text("Listas de proteção", color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (lastUpdate > 0L) "Atualizada em ${formatDate(lastUpdate)}"
                        else "Nunca atualizada — pressione OK para baixar",
                        color = if (lastUpdate > 0L) VColors.Muted else VColors.Coral,
                        fontSize = 10.sp,
                    )
                }
            }

            // Botão atualizar
            Focusable(
                onClick = {
                    if (updating) return@Focusable
                    updating = true
                    scope.launch {
                        try {
                            val result = BlocklistManager.update()
                            BlocklistManager.saveLastUpdate(context)
                            Toast.makeText(
                                context,
                                "Atualizado: ${formatNumber(result.totalDomains)} domínios" +
                                    if (result.errors.isNotEmpty()) " (${result.errors.size} fonte(s) falharam)" else "",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erro ao atualizar: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            updating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VIcon(VIcons.Refresh, VColors.Lime)
                    Text(
                        if (updating) "Atualizando…" else "Atualizar listas agora",
                        color = VColors.Lime,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Fontes
            Spacer(Modifier.height(4.dp))
            Text("Fontes", color = VColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            BlocklistManager.SOURCES.forEach { source ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(VColors.Surface)
                        .border(1.dp, VColors.Line, RoundedCornerShape(9.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name, color = VColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(source.description, color = VColors.Muted, fontSize = 10.sp)
                    }
                    VIcon(VIcons.ShieldCheck, VColors.Lime, 16.dp)
                }
            }

            // Log de bloqueios recentes
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Bloqueios recentes", color = VColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                entries.take(20).forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(VColors.Surface)
                            .border(1.dp, VColors.Line, RoundedCornerShape(9.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VIcon(VIcons.Ban, VColors.Coral, 14.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text(entry.domain, color = VColors.Text, fontSize = 12.sp, maxLines = 1)
                        }
                        Text(entry.timeFormatted, color = VColors.Faint, fontSize = 10.sp)
                    }
                }
            }
        }

        KeyHints("OK" to "Ação", "▲ ▼" to "Navegar", "Voltar" to "Menu")
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VColors.Surface)
            .border(1.dp, VColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = VColors.Muted, fontSize = 10.sp)
        Text(value, color = VColors.Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Sora)
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

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
        n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
        else -> "$n"
    }
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}
