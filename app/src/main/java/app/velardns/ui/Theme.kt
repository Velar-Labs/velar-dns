package app.velardns.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.velardns.R

/** Cores do Velar DNS (mesmas do canvas de design). */
object VColors {
    val Bg = Color(0xFF0A0A0B)
    val Nav = Color(0xFF111113)
    val Line = Color(0xFF232327)
    val Active = Color(0xFF1C1C20)
    val Surface = Color(0xFF151518)
    val Text = Color(0xFFF4F4F5)
    val Soft = Color(0xFFC4C4CC)
    val Muted = Color(0xFFA1A1AA)
    val Faint = Color(0xFF8B8B94)
    val KeyBorder = Color(0xFF3A3A40)
    val Track = Color(0xFF2A2A30)

    val Lime = Color(0xFFC8F542)
    val LimeLight = Color(0xFFDBFF6B)
    val LimeDeep = Color(0xFF9FE22B)
    val LimeTint = Color(0xFF1E2610)
    val LimeBorder = Color(0xFF4E6B1A)
    val OnLime = Color(0xFF141A05)

    val Coral = Color(0xFFFF7A59)
    val CoralTint = Color(0xFF2B1611)
    val CoralBorder = Color(0xFF5A2A1E)
    val CoralText = Color(0xFFE3A797)
}

val Sora = FontFamily(
    Font(R.font.sora_light, FontWeight.Light),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

/** Ícones de traço, desenhados a partir dos mesmos paths SVG do design. */
object VIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = stroke("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Server = stroke(
        "server",
        "M5 4h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M5 13h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        "M7 7.5h.01M7 16.5h.01",
    )
    val Pulse = stroke("pulse", "M3 12h4l3-8 4 16 3-8h4")
    val Sliders = stroke(
        "sliders",
        "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12",
        "M14 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        "M8 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        "M16 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )
    val Power = stroke("power", "M12 3v8", "M6.3 7.2a8 8 0 1 0 11.4 0")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Alert = stroke("alert", "M12 7v6M12 17h.01")
    val Refresh = stroke("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val Chevron = stroke("chevron", "M9 5l7 7-7 7")
    val Shield = stroke("shield", "M12 3l8 4v5c0 5.5-3.8 10.7-8 12-4.2-1.3-8-6.5-8-12V7z")
    val ShieldCheck = stroke("shieldCheck", "M12 3l8 4v5c0 5.5-3.8 10.7-8 12-4.2-1.3-8-6.5-8-12V7z", "M9 12l2 2 4-4")
    val Ban = stroke("ban", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M5.6 5.6l12.8 12.8")
    val List = stroke("list", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01")
}
