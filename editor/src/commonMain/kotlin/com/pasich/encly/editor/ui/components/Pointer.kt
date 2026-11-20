package com.pasich.encly.editor.ui.components

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
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val FocusRingWidth = 2.dp
private val FocusRingInset = 1.dp

/**
 * What a desktop pointer and keyboard expect of a pressable thing, to go before its `clickable` (or on
 * a Material surface): a hand cursor over it and a 2 dp focus ring just inside [shape] while it holds
 * keyboard focus, in the content colour so it contrasts with the fill. The ring shows only after a key
 * was pressed last (Tab, arrows), never after a tap or a click, and neither effect exists on touch.
 * Hover and pressed state layers are the Material ripple's. A disabled target keeps the arrow and
 * gets no ring. The same as the host app's design system has, which the editor cannot depend on.
 */
internal fun Modifier.editorInteractive(shape: Shape = RectangleShape, enabled: Boolean = true): Modifier =
    if (enabled) {
        this
            .pointerHoverIcon(PointerIcon.Hand)
            .then(FocusRingElement(shape))
    } else {
        this
    }

private data class FocusRingElement(val shape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape)

    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.invalidateDraw()
    }
}

private class FocusRingNode(var shape: Shape) :
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
