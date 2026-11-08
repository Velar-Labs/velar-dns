package app.velardns.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Elemento focável pelo controle remoto: ao receber foco ganha anel claro,
 * fundo mais claro e um leve aumento — o padrão de foco de todas as telas.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(9.dp),
    color: Color = VColors.Surface,
    focusedColor: Color = VColors.Active,
    border: BorderStroke? = BorderStroke(1.dp, VColors.Line),
    focusRequester: FocusRequester? = null,
    focusedScale: Float = 1.02f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    val outline = when {
        focused -> BorderStroke(2.dp, VColors.Text)
        else -> border
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(if (focused) focusedColor else color, shape)
            .then(if (outline != null) Modifier.border(outline, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

private val shieldPath = PathParser()
    .parsePathString("M24 8.5l13 4.6v10.6c0 7.6-5.4 13.6-13 16.3-7.6-2.7-13-8.7-13-16.3V13.1z")
    .toPath()
private val vPath = PathParser().parsePathString("M17.6 18.5L24 30.5l6.4-12").toPath()

/** Símbolo do Velar DNS: escudo grafite com V, sobre quadrado lima. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 48f
        val gradient = Brush.linearGradient(
            colors = listOf(VColors.LimeLight, VColors.LimeDeep),
            start = Offset.Zero,
            end = Offset(48f, 48f),
        )
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawRoundRect(brush = gradient, size = Size(48f, 48f), cornerRadius = CornerRadius(13f, 13f))
            drawPath(shieldPath, color = VColors.Bg)
            drawPath(vPath, brush = gradient, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun Wordmark(fontSize: TextUnit = 16.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = VColors.Text)) { append("velar") }
            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = VColors.Lime)) { append("dns") }
        },
        fontFamily = Sora,
        fontSize = fontSize,
    )
}

@Composable
fun VIcon(icon: ImageVector, tint: Color, size: Dp = 15.dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun StatusChip(text: String, fg: Color, bg: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Rodapé com as teclas do controle remoto. */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    key,
                    color = VColors.Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, VColors.KeyBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(label, color = VColors.Muted, fontSize = 10.sp)
            }
        }
    }
}

/** Interruptor visual (o foco e o clique ficam na linha inteira). */
@Composable
fun SwitchPill(on: Boolean) {
    Box(
        Modifier
            .size(width = 48.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) VColors.Lime else VColors.Track)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) VColors.OnLime else VColors.Muted),
        )
    }
}
