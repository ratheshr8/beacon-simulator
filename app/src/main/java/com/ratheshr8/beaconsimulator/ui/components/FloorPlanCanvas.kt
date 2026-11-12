package com.ratheshr8.beaconsimulator.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratheshr8.beaconsimulator.floorplan.LocatedBeaconStatus
import com.ratheshr8.beaconsimulator.floorplan.Point2d
import com.ratheshr8.beaconsimulator.floorplan.Trilateration
import com.ratheshr8.beaconsimulator.ui.theme.OceanDeep
import com.ratheshr8.beaconsimulator.ui.theme.SignalAmber
import com.ratheshr8.beaconsimulator.ui.theme.SignalTeal
import java.io.File
import kotlin.math.hypot
import kotlin.math.min
import androidx.compose.ui.Modifier as ComposeModifierAlias

@Composable
fun FloorPlanCanvas(
    imagePath: String?,
    planWidthMeters: Float,
    beaconStatuses: List<LocatedBeaconStatus>,
    userLocation: Point2d?,
    receiverPin: Point2d?,
    trackedBeaconKey: String?,
    allowDragBeacons: Boolean,
    onTapNormalized: (Float, Float) -> Unit,
    onLongPressNormalized: (Float, Float) -> Unit,
    onBeaconDrag: (beaconId: String, x: Float, y: Float) -> Unit,
    modifier: ComposeModifierAlias = ComposeModifierAlias,
) {
    val bitmap = remember(imagePath) {
        imagePath
            ?.takeIf { File(it).exists() }
            ?.let { path ->
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var draggingId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .background(Color(0xFFE8EEF2), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = ComposeModifierAlias
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            onLongPressNormalized(
                                (offset.x / w).coerceIn(0f, 1f),
                                (offset.y / h).coerceIn(0f, 1f),
                            )
                        },
                        onTap = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            onTapNormalized(
                                (offset.x / w).coerceIn(0f, 1f),
                                (offset.y / h).coerceIn(0f, 1f),
                            )
                        },
                    )
                }
                .pointerInput(beaconStatuses, allowDragBeacons) {
                    if (!allowDragBeacons) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val nx = offset.x / w
                            val ny = offset.y / h
                            val hit = beaconStatuses.minByOrNull { status ->
                                hypot(status.placed.x - nx, status.placed.y - ny)
                            }
                            draggingId = if (hit != null &&
                                hypot(hit.placed.x - nx, hit.placed.y - ny) < 0.08f
                            ) {
                                hit.placed.id
                            } else {
                                null
                            }
                        },
                        onDragEnd = { draggingId = null },
                        onDragCancel = { draggingId = null },
                        onDrag = { change, _ ->
                            val id = draggingId ?: return@detectDragGestures
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            onBeaconDrag(
                                id,
                                (change.position.x / w).coerceIn(0f, 1f),
                                (change.position.y / h).coerceIn(0f, 1f),
                            )
                        },
                    )
                },
        ) {
            if (bitmap != null) {
                val scale = min(size.width / bitmap.width, size.height / bitmap.height)
                val dstW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val dstH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val left = ((size.width - dstW) / 2f).toInt()
                val top = ((size.height - dstH) / 2f).toInt()
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(dstW, dstH),
                )
            } else {
                val step = size.width / 10f
                for (i in 0..10) {
                    val x = i * step
                    drawLine(Color(0xFFB7C4CC), Offset(x, 0f), Offset(x, size.height), 1f)
                }
                val stepY = size.height / 8f
                for (i in 0..8) {
                    val y = i * stepY
                    drawLine(Color(0xFFB7C4CC), Offset(0f, y), Offset(size.width, y), 1f)
                }
            }

            receiverPin?.let { pin ->
                val px = pin.x * size.width
                val py = pin.y * size.height
                drawCircle(color = Color(0xFF5B2C6F), radius = 14f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 5f, center = Offset(px, py))
                val label = textMeasurer.measure(
                    text = "Receiver",
                    style = TextStyle(fontSize = 11.sp, color = Color(0xFF5B2C6F)),
                )
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(px - label.size.width / 2f, py - with(density) { 22.dp.toPx() }),
                )
            }

            beaconStatuses.forEach { status ->
                val cx = status.placed.x * size.width
                val cy = status.placed.y * size.height
                val key = "${status.placed.uuid.toString().uppercase()}|${status.placed.major}|${status.placed.minor}"
                val tracked = trackedBeaconKey != null && key == trackedBeaconKey
                val color = when {
                    tracked && status.detected -> Color(0xFFC47B2C)
                    status.detected -> SignalTeal
                    else -> OceanDeep.copy(alpha = 0.55f)
                }

                status.distanceMeters?.let { meters ->
                    val radiusPx =
                        Trilateration.metersToNormalized(meters, planWidthMeters) * size.width
                    drawCircle(
                        color = SignalAmber.copy(alpha = 0.18f),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = SignalAmber.copy(alpha = 0.55f),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (tracked) 4f else 2f),
                    )
                }

                drawCircle(color = color, radius = if (tracked) 20f else 16f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 6f, center = Offset(cx, cy))

                val label = buildString {
                    append(status.placed.label)
                    if (tracked) append(" ★")
                    if (status.detected && status.rssi != null) {
                        append("  ")
                        append(status.rssi)
                        append(" dBm")
                    }
                }
                val layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 11.sp, color = Color(0xFF102332)),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        (cx - layout.size.width / 2f)
                            .coerceIn(4f, size.width - layout.size.width - 4f),
                        (cy + with(density) { 14.dp.toPx() })
                            .coerceAtMost(size.height - layout.size.height - 4f),
                    ),
                )
            }

            userLocation?.let { loc ->
                val ux = loc.x * size.width
                val uy = loc.y * size.height
                drawCircle(color = Color(0xFFC47B2C), radius = 18f, center = Offset(ux, uy))
                drawCircle(color = Color.White, radius = 7f, center = Offset(ux, uy))
                val you = textMeasurer.measure(
                    text = "You",
                    style = TextStyle(fontSize = 12.sp, color = Color(0xFFC47B2C)),
                )
                drawText(
                    textLayoutResult = you,
                    topLeft = Offset(ux - you.size.width / 2f, uy - with(density) { 28.dp.toPx() }),
                )
            }
        }

        if (bitmap == null && beaconStatuses.isEmpty()) {
            Text(
                text = "Blank floor plan — Place beacons or Scan QR to add a tracked transmitter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
