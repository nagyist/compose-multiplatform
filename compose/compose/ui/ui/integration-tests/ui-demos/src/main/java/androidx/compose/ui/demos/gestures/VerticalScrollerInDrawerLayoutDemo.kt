/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.demos.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Demonstration for how multiple DragGestureDetectors interact.
 */
@Composable
fun VerticalScrollerInDrawerDemo() {
    Column {
        Text("Demonstrates scroll orientation locking.")
        Text(
            "There is a vertically scrolling column and a drawer layout.  A pointer can only " +
                "contribute to dragging the column or the drawer, but not both."
        )
        DrawerLayout(280.dp) {
            Scrollable(Orientation.Vertical) {
                RepeatingColumn(repetitions = 10) {
                    Pressable(
                        height = 96.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerLayout(drawerWidth: Dp, content: @Composable ColumnScope.() -> Unit) {

    val minOffset =
        with(LocalDensity.current) {
            -drawerWidth.toPx()
        }

    val currentOffset = remember { mutableStateOf(minOffset) }
    val maxOffset = 0f

    Box(
        Modifier.scrollable(
            orientation = Orientation.Horizontal,
            state = rememberScrollableState { scrollDistance ->
                val originalOffset = currentOffset.value
                currentOffset.value =
                    (currentOffset.value + scrollDistance).coerceIn(minOffset, maxOffset)
                currentOffset.value - originalOffset
            }
        )
    ) {
        Column(content = content)
        Box(
            Modifier
                .fillMaxHeight()
                .requiredWidth(drawerWidth)
                .offset { IntOffset(currentOffset.value.roundToInt(), 0) }
                .background(color = DefaultBackgroundColor)
        ) {
            Text(
                "This is empty drawer content",
                Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}

/**
 * A very simple ScrollView like implementation that allows for vertical scrolling.
 */
@Composable
private fun Scrollable(orientation: Orientation, content: @Composable () -> Unit) {
    val maxOffset = 0f
    val offset = remember { mutableStateOf(maxOffset) }
    val minOffset = remember { mutableStateOf(0f) }

    Layout(
        content = content,
        modifier = Modifier.scrollable(
            orientation = orientation,
            state = rememberScrollableState { scrollDistance ->
                val resultingOffset = offset.value + scrollDistance
                val toConsume =
                    when {
                        resultingOffset > maxOffset -> {
                            maxOffset - offset.value
                        }
                        resultingOffset < minOffset.value -> {
                            minOffset.value - offset.value
                        }
                        else -> {
                            scrollDistance
                        }
                    }
                offset.value = offset.value + toConsume
                toConsume
            }
        ).then(ClipModifier),
        measurePolicy = { measurables, constraints ->
            val placeable =
                when (orientation) {
                    Orientation.Horizontal -> measurables.first().measure(
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = Constraints.Infinity
                        )
                    )
                    Orientation.Vertical -> measurables.first().measure(
                        constraints.copy(
                            minHeight = 0,
                            maxHeight = Constraints.Infinity
                        )
                    )
                }

            minOffset.value =
                when (orientation) {
                    Orientation.Horizontal -> constraints.maxWidth.toFloat() - placeable.width
                    Orientation.Vertical -> constraints.maxHeight.toFloat() - placeable.height
                }

            val width =
                when (orientation) {
                    Orientation.Horizontal -> constraints.maxWidth
                    Orientation.Vertical -> placeable.width
                }

            val height =
                when (orientation) {
                    Orientation.Horizontal -> placeable.height
                    Orientation.Vertical -> constraints.maxHeight
                }

            layout(width, height) {
                when (orientation) {
                    Orientation.Horizontal -> placeable.placeRelative(offset.value.roundToInt(), 0)
                    Orientation.Vertical -> placeable.placeRelative(0, offset.value.roundToInt())
                }
            }
        }
    )
}

private val ClipModifier = object : DrawModifier {
    override fun ContentDrawScope.draw() {
        clipRect {
            this@draw.drawContent()
        }
    }
}

/**
 * A very simple Button like implementation that visually indicates when it is being pressed.
 */
@Composable
private fun Pressable(
    height: Dp
) {

    val pressedColor = PressedColor
    val defaultColor = Red

    val color = remember { mutableStateOf(defaultColor) }
    val showPressed = remember { mutableStateOf(false) }

    val onPress: (Offset) -> Unit = {
        showPressed.value = true
    }

    val onRelease = {
        showPressed.value = false
    }

    val onTap: (Offset) -> Unit = {
        color.value = color.value.next()
    }

    val onDoubleTap: (Offset) -> Unit = {
        color.value = color.value.prev()
    }

    val onLongPress = { _: Offset ->
        color.value = defaultColor
        showPressed.value = false
    }

    val gestureDetectors =
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress.invoke(it)
                        val success = tryAwaitRelease()
                        if (success) onRelease.invoke() else onRelease.invoke()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                    onLongPress = onLongPress
                )
            }

    val pressOverlay =
        if (showPressed.value) Modifier.background(color = pressedColor) else Modifier

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .border(1.dp, Color.Black)
            .background(color = color.value)
            .then(pressOverlay).then(gestureDetectors)
    )
}

/**
 * A simple composable that repeats its children as a vertical list of divided items [repetitions]
 * times.
 */
@Suppress("SameParameterValue")
@Composable
private fun RepeatingColumn(repetitions: Int, content: @Composable () -> Unit) {
    Column {
        for (i in 1..repetitions) {
            content()
        }
    }
}

/**
 * A simple composable that repeats its children as a vertical list of divided items [repetitions]
 * times.
 */
@Suppress("SameParameterValue")
@Composable
private fun RepeatingRow(repetitions: Int, content: @Composable () -> Unit) {
    Row {
        for (i in 1..repetitions) {
            content()
        }
    }
}