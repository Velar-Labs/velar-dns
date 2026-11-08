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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.velardns.data.DohProvider
import app.velardns.data.Prefs
import app.velardns.data.Providers
import app.velardns.data.VelarSettings
import app.velardns.dns.Doh
import app.velardns.vpn.VelarVpnService
import app.velardns.vpn.VpnStatus
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(settings: VelarSettings, status: VpnStatus) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latency = remember { mutableStateMapOf<String, String>() }
    var editingCustom by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val inUseId = (status as? VpnStatus.On)?.provider?.id
    val customProvider = if (Providers.isValidDohUrl(settings.customUrl)) Providers.custom(settings.customUrl) else null

    fun select(provider: DohProvider) {
        Prefs.update { it.copy(providerId = provider.id) }
        VelarVpnService.reload(context)
    }

    fun testAll() {
        val all = Providers.builtIn + listOfNotNull(customProvider)
        all.forEach { p ->
            latency[p.id] = "…"
            scope.launch {
                latency[p.id] = runCatching { Doh.probe(p).ms }.fold({ "$it ms" }, { "falhou" })
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Servidores DoH", color = VColors.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text("Escolha para onde vão as consultas. Todos usam HTTPS na porta 443.", color = VColors.Muted, fontSize = 12.sp)
            }
            Focusable(onClick = ::testAll, color = VColors.Surface, border = BorderStroke(1.dp, VColors.KeyBorder)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    VIcon(VIcons.Pulse, VColors.Text, 13.dp)
                    Text("Testar velocidade de todos", color = VColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Providers.builtIn.forEachIndexed { i, p ->
                ProviderRow(
                    provider = p,
                    selected = settings.providerId == p.id,
                    inUse = inUseId == p.id,
                    latency = latency[p.id],
                    onClick = { select(p) },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            if (customProvider != null && !editingCustom) {
                ProviderRow(
                    provider = customProvider,
                    selected = settings.providerId == Providers.CUSTOM_ID,
                    inUse = inUseId == Providers.CUSTOM_ID,
                    latency = latency[Providers.CUSTOM_ID],
                    onClick = { select(customProvider) },
                )
            }
            if (editingCustom) {
                CustomEditor(
                    initial = settings.customUrl,
                    onSave = { url ->
                        Prefs.update { it.copy(customUrl = url, providerId = Providers.CUSTOM_ID) }
                        VelarVpnService.reload(context)
                        editingCustom = false
                    },
                    onCancel = { editingCustom = false },
                )
            } else {
                Focusable(
                    onClick = { editingCustom = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, VColors.KeyBorder),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        VIcon(VIcons.Plus, VColors.Muted, 18.dp)
                        Column {
                            Text(
                                if (customProvider == null) "Personalizado" else "Editar servidor personalizado",
                                color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text("Digite um endereço https://…/dns-query", color = VColors.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        KeyHints(
            "OK" to "Usar este servidor (troca na hora, sem desligar)",
            "▲ ▼" to "Navegar",
            "Voltar" to "Menu",
        )
    }
}

@Composable
private fun ProviderRow(
    provider: DohProvider,
    selected: Boolean,
    inUse: Boolean,
    latency: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (selected) VColors.LimeTint else VColors.Surface,
        focusedColor = if (selected) VColors.LimeTint else VColors.Active,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) VColors.LimeBorder else VColors.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(18.dp)) {
                if (selected) VIcon(VIcons.Check, VColors.Lime, 18.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(provider.name, color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${provider.host} · ${provider.note}", color = VColors.Muted, fontSize = 10.sp, maxLines = 1)
            }
            if (inUse) {
                Text(
                    "Em uso",
                    color = VColors.Lime,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VColors.Active)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Text(
                latency ?: "",
                color = VColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(70.dp),
            )
        }
    }
}

@Composable
private fun CustomEditor(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial.ifBlank { "https://" }) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    fun trySave() {
        val url = text.trim()
        if (Providers.isValidDohUrl(url)) onSave(url) else error = "Endereço inválido. Use https:// e o caminho do servidor."
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(VColors.Surface)
            .border(1.dp, VColors.KeyBorder, RoundedCornerShape(9.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Servidor personalizado (DoH)", color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = text,
            onValueChange = { text = it; error = null },
            singleLine = true,
            textStyle = TextStyle(color = VColors.Text, fontSize = 14.sp),
            cursorBrush = SolidColor(VColors.Lime),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { trySave() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onPreviewKeyEvent { e ->
                    // No controle remoto, OK abre o teclado da TV.
                    if (e.key == Key.DirectionCenter && e.type == KeyEventType.KeyUp) {
                        keyboard?.show()
                        true
                    } else {
                        false
                    }
                }
                .clip(RoundedCornerShape(7.dp))
                .background(VColors.Bg)
                .border(1.dp, VColors.Line, RoundedCornerShape(7.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        error?.let { Text(it, color = VColors.Coral, fontSize = 11.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Focusable(onClick = ::trySave, color = VColors.Lime, focusedColor = VColors.LimeLight, border = null) {
                Text(
                    "Salvar e usar",
                    color = VColors.OnLime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Focusable(onClick = onCancel) {
                Text(
                    "Cancelar",
                    color = VColors.Text,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
