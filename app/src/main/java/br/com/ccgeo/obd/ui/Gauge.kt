package br.com.ccgeo.obd.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ccgeo.obd.elm.PidDef

/** Mostrador circular (270°) para RPM / velocidade. */
@Composable
fun Gauge(def: PidDef, value: Double?, modifier: Modifier = Modifier, warnFrom: Double? = null) {
    val frac = if (value == null) 0f
    else ((value - def.min) / (def.max - def.min)).toFloat().coerceIn(0f, 1f)
    val anim by animateFloatAsState(targetValue = frac, animationSpec = tween(250), label = "gauge")
    val warn = warnFrom != null && value != null && value >= warnFrom
    val color = if (warn) Danger else Amber
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = size.minDimension * 0.075f
            val d = size.minDimension - stroke
            val arcSize = Size(d, d)
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (anim > 0.001f) {
                drawArc(color, 135f, 270f * anim, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                def.format(value),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (warn) Danger else MaterialTheme.colorScheme.onSurface,
            )
            Text(def.unit, fontSize = 13.sp, color = Muted)
            Text(def.name, fontSize = 12.sp, color = Color(0xFFB4C0CA))
        }
    }
}
