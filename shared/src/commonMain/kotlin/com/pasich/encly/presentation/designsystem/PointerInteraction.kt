package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val FocusRingWidth = 2.dp
private val FocusRingInset = 1.dp

/**
 * What a desktop pointer and keyboard expect of anything that can be pressed, to go before its
 * `clickable` / `selectable` / `toggleable` (or on a Material button): a hand cursor while the
 * pointer is over it, and a focus ring while it holds keyboard focus. The hover and pressed state
 * layers are the Material ripple's, so this adds none of its own. Neither effect shows on touch:
 * a finger has no pointer, and the ring is drawn only for focus that did not come from a press, so
 * after Tab or the arrows, never after a tap or a mouse click. A disabled target ([enabled] false) keeps the
 * arrow and gets no ring. [shape] is the ring's outline, so pass the target's own shape.
 *
 * The ring is 2 dp, drawn just inside the bounds in the content colour at the target, which is
 * the colour that contrasts with the target's fill (the label colour of a button, `onSurface` of
 * a card).
 */
fun Modifier.enclyInteractive(shape: Shape = RectangleShape, enabled: Boolean = true): Modifier = if (enabled) {
    this
        .pointerHoverIcon(PointerIcon.Hand)
        .then(KeyboardFocusRingElement(shape))
} else {
    this
}

/** [enclyInteractive] for a fully round target (a pill button, an icon button, a chip). */
fun Modifier.enclyInteractiveRound(enabled: Boolean = true): Modifier = enclyInteractive(CircleShape, enabled)

/**
 * Calls [onClick] when the middle mouse button is pressed and let go over this: a browser's way to close a tab.
 * The press is used up here, so nothing under it starts a click. A finger and the other buttons do nothing.
 */
@Composable
fun Modifier.enclyMiddleClick(onClick: () -> Unit): Modifier {
    val current by rememberUpdatedState(onClick)
    return pointerInput(Unit) {
        val bounds = size
        awaitPointerEventScope {
            while (true) {
                if (awaitClickOf(bounds) { it.buttons.isTertiaryPressed }) current()
            }
        }
    }
}

/**
 * Calls [onClick] instead of the click below when the primary mouse button is pressed with the command key held
 * (⌘ on a Mac, Ctrl elsewhere, as [LocalEnclyShortcuts] says) and let go over this: the "open in a new tab" click
 * of a browser. An ordinary click, a touch and the other buttons pass through to the click below.
 */
@Composable
fun Modifier.enclyCommandClick(onClick: () -> Unit): Modifier {
    val current by rememberUpdatedState(onClick)
    val meta = LocalEnclyShortcuts.current.usesMetaKey
    return pointerInput(meta) {
        val bounds = size
        awaitPointerEventScope {
            while (true) {
                val clicked = awaitClickOf(bounds) { press ->
                    val modifiers = press.keyboardModifiers
                    press.buttons.isPrimaryPressed && if (meta) modifiers.isMetaPressed else modifiers.isCtrlPressed
                }
                if (clicked) current()
            }
        }
    }
}

/**
 * Waits for a press that [accepts] and uses it up, then for the release that ends it. True when the pointer was
 * let go inside [bounds]; any other press is passed over without being used.
 */
private suspend fun AwaitPointerEventScope.awaitClickOf(bounds: IntSize, accepts: (PointerEvent) -> Boolean): Boolean {
    var press: PointerInputChange?
    do {
        press = awaitPointerEvent(PointerEventPass.Initial).acceptedPress(accepts)
    } while (press == null)
    press.consume()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val up = event.changes.firstOrNull()
        if (up != null && event.type == PointerEventType.Release) {
            up.consume()
            return up.position.x in 0f..bounds.width.toFloat() && up.position.y in 0f..bounds.height.toFloat()
        }
    }
}

/** The change of a press that [accepts], or null for any other event. */
private fun PointerEvent.acceptedPress(accepts: (PointerEvent) -> Boolean): PointerInputChange? {
    val change = changes.firstOrNull() ?: return null
    return change.takeIf { type == PointerEventType.Press && it.changedToDown() && accepts(this) }
}

private data class KeyboardFocusRingElement(val shape: Shape) : ModifierNodeElement<KeyboardFocusRingNode>() {
    override fun create() = KeyboardFocusRingNode(shape)

    override fun update(node: KeyboardFocusRingNode) {
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "keyboardFocusRing"
        properties["shape"] = shape
    }
}

private class KeyboardFocusRingNode(var shape: Shape) :
    Modifier.Node(),
    FocusEventModifierNode,
    DrawModifierNode,
    PointerInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private var focused = false

    // Focus that a press gave (a click, a tap) is not keyboard focus: no ring until focus leaves and returns.
    private var pressed = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focused != focusState.hasFocus) {
            focused = focusState.hasFocus
            if (!focused) pressed = false
            invalidateDraw()
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press && !pressed) {
            pressed = true
            invalidateDraw()
        }
    }

    override fun onCancelPointerInput() = Unit

    override fun sharePointerInputWithSiblings() = true

    override fun ContentDrawScope.draw() {
        drawContent()
        // Read while drawing, so a change of input mode redraws.
        if (!focused || pressed || currentValueOf(LocalInputModeManager).inputMode != InputMode.Keyboard) return
        val inset = FocusRingInset.toPx() + FocusRingWidth.toPx() / 2
        val ringSize = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate(inset, inset) {
            drawOutline(outline, currentValueOf(LocalContentColor), style = Stroke(FocusRingWidth.toPx()))
        }
    }
}
