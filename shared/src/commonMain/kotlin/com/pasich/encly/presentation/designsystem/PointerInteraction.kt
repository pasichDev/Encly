package com.pasich.encly.presentation.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
