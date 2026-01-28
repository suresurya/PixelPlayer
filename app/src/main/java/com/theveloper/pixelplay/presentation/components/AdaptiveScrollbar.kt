package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Adaptive UI Scrollbar
 *
 * WHY this exists (design intent):
 * - Compose's default scrollbars are platform/OS-driven and don't match PixelPlayer's premium theme.
 * - This is a lightweight overlay that reads existing Lazy list/grid state (no duplication of list logic).
 * - Colors come from MaterialTheme.colorScheme so it automatically adapts to dynamic/light/dark themes.
 * - Visibility follows user intent: fully visible while scrolling/dragging and gently fades out when idle.
 */
@Composable
fun AdaptiveScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    touchTargetWidth: Dp = 22.dp,
    minThumbHeight: Dp = 36.dp,
    contentPaddingEnd: Dp = 6.dp
) {
    AdaptiveScrollbarInternal(
        modifier = modifier,
        thickness = thickness,
        touchTargetWidth = touchTargetWidth,
        minThumbHeight = minThumbHeight,
        contentPaddingEnd = contentPaddingEnd,
        isScrollInProgress = { state.isScrollInProgress },
        totalItemsCount = { state.layoutInfo.totalItemsCount },
        visibleItemsInfoProvider = {
            state.layoutInfo.visibleItemsInfo.map { it.index to it.size }
        },
        firstVisibleItemIndexProvider = { state.firstVisibleItemIndex },
        firstVisibleItemScrollOffsetProvider = { state.firstVisibleItemScrollOffset },
        scrollToItem = { index, _ -> state.scrollToItem(index) }
    )
}

@Composable
fun AdaptiveScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    touchTargetWidth: Dp = 22.dp,
    minThumbHeight: Dp = 36.dp,
    contentPaddingEnd: Dp = 6.dp
) {
    AdaptiveScrollbarInternal(
        modifier = modifier,
        thickness = thickness,
        touchTargetWidth = touchTargetWidth,
        minThumbHeight = minThumbHeight,
        contentPaddingEnd = contentPaddingEnd,
        isScrollInProgress = { state.isScrollInProgress },
        totalItemsCount = { state.layoutInfo.totalItemsCount },
        visibleItemsInfoProvider = {
            state.layoutInfo.visibleItemsInfo.map { it.index to it.size }
        },
        firstVisibleItemIndexProvider = { state.firstVisibleItemIndex },
        firstVisibleItemScrollOffsetProvider = { state.firstVisibleItemScrollOffset },
        scrollToItem = { index, offset -> state.scrollToItem(index, offset) }
    )
}

@Composable
private fun AdaptiveScrollbarInternal(
    modifier: Modifier,
    thickness: Dp,
    touchTargetWidth: Dp,
    minThumbHeight: Dp,
    contentPaddingEnd: Dp,
    isScrollInProgress: () -> Boolean,
    totalItemsCount: () -> Int,
    visibleItemsInfoProvider: () -> List<Pair<Int, Int>>,
    firstVisibleItemIndexProvider: () -> Int,
    firstVisibleItemScrollOffsetProvider: () -> Int,
    scrollToItem: suspend (index: Int, offset: Int) -> Unit
) {
    val totalItems = totalItemsCount()
    if (totalItems <= 0) return

    val visibleItems = visibleItemsInfoProvider()
    if (visibleItems.isEmpty()) return

    val firstVisibleIndex = firstVisibleItemIndexProvider()
    val firstItemSizePx = visibleItems.firstOrNull { it.first == firstVisibleIndex }?.second ?: visibleItems.first().second

    // If everything is visible, we don't show a scrollbar.
    val minIndex = visibleItems.minOf { it.first }
    val maxIndex = visibleItems.maxOf { it.first }
    val visibleCount = (maxIndex - minIndex + 1).coerceAtLeast(1)
    if (totalItems <= visibleCount) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var showThumb by remember { mutableStateOf(false) }
    var dragPositionFraction by remember { mutableFloatStateOf(0f) }

    // Visibility behavior:
    // - Show while scrolling or dragging.
    // - Fade out after a short idle delay (premium / non-distracting).
    LaunchedEffect(Unit) {
        snapshotFlow { isScrollInProgress() }
            .collect { scrolling ->
                if (scrolling) {
                    showThumb = true
                } else {
                    delay(900)
                    if (!isScrollInProgress() && !isDragging) {
                        showThumb = false
                    }
                }
            }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            showThumb = true
        } else {
            delay(900)
            if (!isScrollInProgress() && !isDragging) {
                showThumb = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (showThumb) 1f else 0f,
        animationSpec = tween(durationMillis = if (showThumb) 160 else 420, easing = FastOutSlowInEasing),
        label = "AdaptiveScrollbarAlpha"
    )

    // Theme-derived styling (no random colors):
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    val thumbShape = RoundedCornerShape(percent = 100)

    val canInteract = showThumb || isScrollInProgress() || isDragging

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(touchTargetWidth)
            .padding(end = contentPaddingEnd)
            .alpha(alpha)
            .then(
                if (canInteract) {
                    Modifier.pointerInput(totalItems) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragPositionFraction = (offset.y / size.height).coerceIn(0f, 1f)
                                scope.launch {
                                    val maxScrollableIndex = (totalItems - visibleCount).coerceAtLeast(1)
                                    val targetIndex = (dragPositionFraction * maxScrollableIndex)
                                        .roundToInt()
                                        .coerceIn(0, maxScrollableIndex)
                                    scrollToItem(targetIndex, 0)
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                dragPositionFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                                scope.launch {
                                    val maxScrollableIndex = (totalItems - visibleCount).coerceAtLeast(1)
                                    val targetIndex = (dragPositionFraction * maxScrollableIndex)
                                        .roundToInt()
                                        .coerceIn(0, maxScrollableIndex)
                                    scrollToItem(targetIndex, 0)
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val minThumbPx = with(density) { minThumbHeight.toPx() }

        // Smooth-ish thumb position by incorporating scroll offset within the first visible item.
        // WHY: avoids a "steppy" thumb when items are tall.
        val indexWithOffset = run {
            val itemSize = firstItemSizePx.coerceAtLeast(1)
            firstVisibleIndex + (firstVisibleItemScrollOffsetProvider().toFloat() / itemSize.toFloat())
        }

        val maxScrollableIndex = (totalItems - visibleCount).coerceAtLeast(1)
        val scrollFraction = (indexWithOffset / maxScrollableIndex.toFloat()).coerceIn(0f, 1f)

        val visibleFraction = visibleCount.toFloat() / totalItems.toFloat()
        val thumbHeightPx = (heightPx * visibleFraction).coerceAtLeast(minThumbPx).coerceAtMost(heightPx)
        val thumbOffsetPx = (heightPx - thumbHeightPx) * scrollFraction

        // Subtle elevation only while actively interacting.
        val elevation = if (isDragging || isScrollInProgress()) 2.dp else 0.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(thickness)
                    .background(trackColor, thumbShape)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = with(density) { thumbOffsetPx.toDp() })
                    .width(thickness)
                    .height(with(density) { thumbHeightPx.toDp() })
                    .shadow(elevation = elevation, shape = thumbShape, clip = false)
                    .background(thumbColor, thumbShape)
            )
        }
    }
}
