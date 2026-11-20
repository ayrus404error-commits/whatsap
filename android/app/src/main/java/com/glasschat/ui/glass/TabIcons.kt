package com.glasschat.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons for the tab bar in the iOS style (thin strokes, filled when
 * selected). Tinted by the tab bar, so they follow the theme colour.
 */
object TabIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: String, width: Float = 1.7f) = addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

    private fun ImageVector.Builder.solid(d: String) = addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))

    // Status ring: an open circle with two dots, and a circle inside.
    private const val RING = "M12,3 A9,9 0 1,1 4.21,7.5"
    private const val DOTS = "M6.21,5.11 L6.22,5.11 M8.92,3.54 L8.93,3.54"
    private const val INNER = "M12,8 A4,4 0 1,1 11.99,8 Z"

    val Updates: ImageVector by lazy {
        icon("Updates") {
            line(RING)
            line(DOTS, 2.2f)
            line(INNER)
        }
    }
    val UpdatesSelected: ImageVector by lazy {
        icon("UpdatesSelected") {
            line(RING, 2f)
            line(DOTS, 2.6f)
            solid(INNER)
        }
    }

    // Two speech bubbles, the front one with a tail.
    private const val BACK = "M8,7.3 V5.3 A2.3,2.3 0 0,1 10.3,3 H18.7 A2.3,2.3 0 0,1 21,5.3 V11.2 A2.3,2.3 0 0,1 18.7,13.5 H17.5"
    private const val FRONT = "M3,10 A2.3,2.3 0 0,1 5.3,7.7 H13.7 A2.3,2.3 0 0,1 16,10 V15.9 A2.3,2.3 0 0,1 13.7,18.2 H9.2 L5.6,21 V18.2 H5.3 A2.3,2.3 0 0,1 3,15.9 Z"

    val Chats: ImageVector by lazy {
        icon("Chats") {
            line(BACK)
            line(FRONT)
        }
    }
    val ChatsSelected: ImageVector by lazy {
        icon("ChatsSelected") {
            line(BACK, 2f)
            solid(FRONT)
            line(FRONT, 2f)
        }
    }
}
