package com.glasschat.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * iPhone-style line icons for the info and settings rows (thin rounded
 * strokes, a few solid ones like the photo and the half-filled shield).
 * Drawn in black; tint them like any icon.
 */
object LineIcons {
    private const val W = 1.7f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: String) {
        addPath(
            addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = W,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.solid(d: String, evenOdd: Boolean = false) {
        addPath(
            addPathNodes(d),
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        )
    }

    /** Media, links and docs: a photo with mountains and the sun. */
    val Photo = icon("Photo") {
        line("M6,4.5H18A3,3 0 0 1 21,7.5V16.5A3,3 0 0 1 18,19.5H6A3,3 0 0 1 3,16.5V7.5A3,3 0 0 1 6,4.5Z")
        line("M3.8,17.2L9.2,11.6L13,15.4L15.2,13.2L20.2,18.2")
        line("M16,8.6m-1.5,0a1.5,1.5 0 1 0 3,0a1.5,1.5 0 1 0 -3,0")
    }

    /** Storage: a drive. */
    val Storage = icon("Storage") {
        line("M6.2,6H17.8L21,12V16.5A2,2 0 0 1 19,18.5H5A2,2 0 0 1 3,16.5V12Z")
        line("M3,12H21")
        solid("M17,15.25m-1.1,0a1.1,1.1 0 1 0 2.2,0a1.1,1.1 0 1 0 -2.2,0Z")
    }

    val Star = icon("Star") {
        line("M12,3.4L14.6,8.7L20.4,9.5L16.2,13.6L17.2,19.4L12,16.6L6.8,19.4L7.8,13.6L3.6,9.5L9.4,8.7Z")
    }

    val Bell = icon("Bell") {
        line("M6.2,16.5V11A5.8,5.8 0 0 1 17.8,11V16.5L19.6,18.3H4.4Z")
        line("M10,20.6A2.2,2.2 0 0 0 14,20.6")
    }

    val BellOff = icon("BellOff") {
        line("M6.2,16.5V11A5.8,5.8 0 0 1 17.8,11V16.5L19.6,18.3H4.4Z")
        line("M10,20.6A2.2,2.2 0 0 0 14,20.6")
        line("M4,4L20,20")
    }

    /** Chat theme: a painter's palette. */
    val Palette = icon("Palette") {
        line(
            "M12,3.5C7.3,3.5 3.5,7.3 3.5,12C3.5,16.7 7.3,20.5 12,20.5C13.3,20.5 13.9,19.6 13.4,18.5" +
                "C12.9,17.4 13.6,16.2 14.9,16.2H16.6C18.8,16.2 20.5,14.4 20.5,12.2C20.5,7.4 16.7,3.5 12,3.5Z",
        )
        solid("M7.8,11m-1.3,0a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z")
        solid("M10.6,7.4m-1.3,0a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z")
        solid("M15.2,7.9m-1.3,0a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z")
        solid("M8.4,15.4m-1.3,0a1.3,1.3 0 1 0 2.6,0a1.3,1.3 0 1 0 -2.6,0Z")
    }

    /** Save to Photos: an arrow into a tray. */
    val SaveDown = icon("SaveDown") {
        line("M8.5,9.5H7A2,2 0 0 0 5,11.5V18.5A2,2 0 0 0 7,20.5H17A2,2 0 0 0 19,18.5V11.5A2,2 0 0 0 17,9.5H15.5")
        line("M12,3.5V14.5")
        line("M8.6,11.1L12,14.5L15.4,11.1")
    }

    /** Disappearing messages: a timer with a dotted part. */
    val Timer = icon("Timer") {
        line("M12,3.5A8.5,8.5 0 1 1 3.5,12")
        solid("M4.3,8.6m-0.9,0a0.9,0.9 0 1 0 1.8,0a0.9,0.9 0 1 0 -1.8,0Z")
        solid("M6.3,5.9m-0.9,0a0.9,0.9 0 1 0 1.8,0a0.9,0.9 0 1 0 -1.8,0Z")
        solid("M9.1,4.1m-0.9,0a0.9,0.9 0 1 0 1.8,0a0.9,0.9 0 1 0 -1.8,0Z")
        line("M12,12L15.4,8.6")
        solid("M12,12m-1.2,0a1.2,1.2 0 1 0 2.4,0a1.2,1.2 0 1 0 -2.4,0Z")
    }

    /** Lock chat: a message bubble with a padlock shackle. */
    val ChatLock = icon("ChatLock") {
        line("M9,8V6.2A3,3 0 0 1 15,6.2V8")
        line("M6.5,8H17.5A2,2 0 0 1 19.5,10V16A2,2 0 0 1 17.5,18H11L7.5,20.8V18H6.5A2,2 0 0 1 4.5,16V10A2,2 0 0 1 6.5,8Z")
        line("M8.5,12H15.5")
        line("M8.5,14.8H13")
    }

    /** Restricted chat: a shield, half filled. */
    val Shield = icon("Shield") {
        line("M12,3L19.5,6V11.6C19.5,16 16.4,19.6 12,21C7.6,19.6 4.5,16 4.5,11.6V6Z")
        solid("M12,3L19.5,6V12H12Z")
        solid("M12,12V21C7.6,19.6 4.5,16 4.5,12Z")
    }

    val Lock = icon("Lock") {
        line("M8,10.5V7.8A4,4 0 0 1 16,7.8V10.5")
        line("M7,10.5H17A1.8,1.8 0 0 1 18.8,12.3V18.7A1.8,1.8 0 0 1 17,20.5H7A1.8,1.8 0 0 1 5.2,18.7V12.3A1.8,1.8 0 0 1 7,10.5Z")
    }

    val Pin = icon("Pin") {
        line("M9,3.8H15")
        line("M10,3.8V9.5L7.2,13.5H16.8L14,9.5V3.8")
        line("M12,13.5V20.5")
    }

    val Archive = icon("Archive") {
        line("M4,5H20V8.8H4Z")
        line("M5.5,8.8V18A1.8,1.8 0 0 0 7.3,19.8H16.7A1.8,1.8 0 0 0 18.5,18V8.8")
        line("M9.8,12.5H14.2")
    }

    /** Linked devices: a laptop. */
    val Laptop = icon("Laptop") {
        line("M5.5,6H18.5A1.5,1.5 0 0 1 20,7.5V15.5H4V7.5A1.5,1.5 0 0 1 5.5,6Z")
        line("M2.5,18.5H21.5")
    }

    /** A chat bubble (outline). */
    val Chat = icon("Chat") {
        line("M6.5,4.5H17.5A3,3 0 0 1 20.5,7.5V14.5A3,3 0 0 1 17.5,17.5H10.5L6,20.8V17.5A3,3 0 0 1 3.5,14.5V7.5A3,3 0 0 1 6.5,4.5Z")
    }

    /** Open in another app: an arrow leaving a box. */
    val OpenOut = icon("OpenOut") {
        line("M10.5,5.5H7A2,2 0 0 0 5,7.5V17A2,2 0 0 0 7,19H16.5A2,2 0 0 0 18.5,17V13.5")
        line("M13.5,4.5H19.5V10.5")
        line("M19.5,4.5L11,13")
    }

    /** Contact details: a person in a circle. */
    val Person = icon("Person") {
        line("M12,12m-8.5,0a8.5,8.5 0 1 0 17,0a8.5,8.5 0 1 0 -17,0")
        line("M12,10.2m-3,0a3,3 0 1 0 6,0a3,3 0 1 0 -6,0")
        line("M6.6,18.4C7.8,16.4 9.7,15.4 12,15.4C14.3,15.4 16.2,16.4 17.4,18.4")
    }
}
