/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.foundation.lazy.grid

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyGridItemInfo
import androidx.compose.foundation.lazy.layout.LazyLayoutPlaceable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Represents one measured item of the lazy grid. It can in fact consist of multiple placeables
 * if the user emit multiple layout nodes in the item callback.
 */
internal class LazyMeasuredItem(
    val index: ItemIndex,
    val key: Any,
    private val isVertical: Boolean,
    /**
     * Cross axis size is the same for all [placeables]. Take it as parameter for the case when
     * [placeables] is empty.
     */
    val crossAxisSize: Int,
    val mainAxisSpacing: Int,
    private val reverseLayout: Boolean,
    private val layoutDirection: LayoutDirection,
    private val beforeContentPadding: Int,
    private val afterContentPadding: Int,
    val placeables: Array<LazyLayoutPlaceable>,
    private val placementAnimator: LazyGridItemPlacementAnimator
) {
    /**
     * Main axis size of the item - the max main axis size of the placeables.
     */
    val mainAxisSize: Int

    /**
     * The max main axis size of the placeables plus mainAxisSpacing.
     */
    val mainAxisSizeWithSpacings: Int

    init {
        var maxMainAxis = 0
        placeables.forEach {
            val placeable = it.placeable
            maxMainAxis =
                maxOf(maxMainAxis, if (isVertical) placeable.height else placeable.width)
        }
        mainAxisSize = maxMainAxis
        mainAxisSizeWithSpacings = maxMainAxis + mainAxisSpacing
    }

    /**
     * Calculates positions for the inner placeables at [rawCrossAxisOffset], [rawCrossAxisOffset].
     * [layoutWidth] and [layoutHeight] should be provided to not place placeables which are ended
     * up outside of the viewport (for example one item consist of 2 placeables, and the first one
     * is not going to be visible, so we don't place it as an optimization, but place the second
     * one). If [reverseOrder] is true the inner placeables would be placed in the inverted order.
     */
    fun position(
        rawMainAxisOffset: Int,
        rawCrossAxisOffset: Int,
        layoutWidth: Int,
        layoutHeight: Int,
        row: Int,
        column: Int,
        lineMainAxisSizeWithSpacings: Int
    ): LazyGridPositionedItem {
        val wrappers = mutableListOf<LazyGridPlaceableWrapper>()

        val mainAxisLayoutSize = if (isVertical) layoutHeight else layoutWidth
        val mainAxisOffset = if (reverseLayout) {
            mainAxisLayoutSize - rawMainAxisOffset - mainAxisSize
        } else {
            rawMainAxisOffset
        }
        val crossAxisLayoutSize = if (isVertical) layoutWidth else layoutHeight
        val crossAxisOffset = if (layoutDirection == LayoutDirection.Rtl && isVertical) {
            crossAxisLayoutSize - rawCrossAxisOffset - crossAxisSize
        } else {
            rawCrossAxisOffset
        }
        val placeableOffset = if (isVertical) {
            IntOffset(crossAxisOffset, mainAxisOffset)
        } else {
            IntOffset(mainAxisOffset, crossAxisOffset)
        }

        var placeableIndex = if (reverseLayout) placeables.lastIndex else 0
        while (if (reverseLayout) placeableIndex >= 0 else placeableIndex < placeables.size) {
            val it = placeables[placeableIndex].placeable
            val addIndex = if (reverseLayout) 0 else wrappers.size
            wrappers.add(
                addIndex,
                LazyGridPlaceableWrapper(placeableOffset, it, placeables[placeableIndex].parentData)
            )
            if (reverseLayout) placeableIndex-- else placeableIndex++
        }

        return LazyGridPositionedItem(
            offset = placeableOffset,
            index = index.value,
            key = key,
            row = row,
            column = column,
            size = if (isVertical) {
                IntSize(crossAxisSize, mainAxisSize)
            } else {
                IntSize(mainAxisSize, crossAxisSize)
            },
            mainAxisSizeWithSpacings = mainAxisSizeWithSpacings,
            lineMainAxisSizeWithSpacings = lineMainAxisSizeWithSpacings,
            minMainAxisOffset = -if (!reverseLayout) {
                beforeContentPadding
            } else {
                afterContentPadding
            },
            maxMainAxisOffset = mainAxisLayoutSize +
                if (!reverseLayout) afterContentPadding else beforeContentPadding,
            isVertical = isVertical,
            wrappers = wrappers,
            placementAnimator = placementAnimator
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class LazyGridPositionedItem(
    override val offset: IntOffset,
    override val index: Int,
    override val key: Any,
    override val row: Int,
    override val column: Int,
    override val size: IntSize,
    val mainAxisSizeWithSpacings: Int,
    val lineMainAxisSizeWithSpacings: Int,
    private val minMainAxisOffset: Int,
    private val maxMainAxisOffset: Int,
    private val isVertical: Boolean,
    private val wrappers: List<LazyGridPlaceableWrapper>,
    private val placementAnimator: LazyGridItemPlacementAnimator
) : LazyGridItemInfo {
    val placeablesCount: Int get() = wrappers.size

    fun getMainAxisSize(index: Int) = wrappers[index].placeable.mainAxisSize

    fun getCrossAxisSize() = if (isVertical) size.width else size.height

    fun getCrossAxisOffset() = if (isVertical) offset.x else offset.y

    @Suppress("UNCHECKED_CAST")
    fun getAnimationSpec(index: Int) =
        wrappers[index].parentData as? FiniteAnimationSpec<IntOffset>?

    val hasAnimations = run {
        repeat(placeablesCount) { index ->
            if (getAnimationSpec(index) != null) {
                return@run true
            }
        }
        false
    }

    fun place(
        scope: Placeable.PlacementScope,
    ) = with(scope) {
        repeat(placeablesCount) { index ->
            val placeable = wrappers[index].placeable
            val minOffset = minMainAxisOffset - placeable.mainAxisSize
            val maxOffset = maxMainAxisOffset
            val offset = if (getAnimationSpec(index) != null) {
                placementAnimator.getAnimatedOffset(
                    key, index, minOffset, maxOffset, offset
                )
            } else {
                offset
            }
            if (offset.mainAxis > minOffset && offset.mainAxis < maxOffset) {
                if (isVertical) {
                    placeable.placeWithLayer(offset)
                } else {
                    placeable.placeRelativeWithLayer(offset)
                }
            }
        }
    }

    private val IntOffset.mainAxis get() = if (isVertical) y else x
    private val Placeable.mainAxisSize get() = if (isVertical) height else width
}

internal class LazyGridPlaceableWrapper(
    val offset: IntOffset,
    val placeable: Placeable,
    val parentData: Any?
)
