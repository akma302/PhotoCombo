package com.combo.core.render.snap

import com.combo.core.render.Border
import com.combo.core.render.moveFrame
import kotlin.math.abs

enum class SnapAxis { X, Y }

data class SnapGuide(
    val axis: SnapAxis,
    val coordinate: Float,
    val start: Float,
    val end: Float,
    val preview: Boolean,
)

data class SnapDistances(val capture: Float, val release: Float, val preview: Float)

data class SnapStep(val state: FrameSnapState, val placed: Border, val guides: List<SnapGuide>)

class FrameSnapState internal constructor(
    val free: Border,
    internal val xLock: Alignment?,
    internal val yLock: Alignment?,
) {
    constructor(frame: Border) : this(frame, null, null)

    fun withoutSnap(): SnapStep = SnapStep(FrameSnapState(free), free, emptyList())
}

/** Geometry for one drag. All values are in canvas coordinates. */
class FrameSnap(
    private val canvasW: Float,
    private val canvasH: Float,
    frame: Border,
    targets: List<Border>,
) {
    private val xTargets = alignments(SnapAxis.X, frame, targets, canvasW)
    private val yTargets = alignments(SnapAxis.Y, frame, targets, canvasH)

    fun move(
        state: FrameSnapState,
        dx: Float,
        dy: Float,
        distances: SnapDistances,
        bypass: Boolean = false,
        preview: Boolean = false,
    ): SnapStep {
        // Advance the free position, including when the displayed frame is held by a line.
        val free = moveFrame(state.free, dx, dy, canvasW, canvasH)
        if (bypass) return SnapStep(FrameSnapState(free), free, emptyList())

        val x = choose(SnapAxis.X, xTargets, state.xLock, state.free, free, distances, preview)
        val y = choose(SnapAxis.Y, yTargets, state.yLock, state.free, free, distances, preview)
        val placed = free.copy(
            centerX = x.lock?.center ?: free.centerX,
            centerY = y.lock?.center ?: free.centerY,
        )
        return SnapStep(
            FrameSnapState(free, x.lock, y.lock),
            placed,
            listOfNotNull(x.guide(SnapAxis.X, placed), y.guide(SnapAxis.Y, placed)),
        )
    }
}

internal data class Alignment(val center: Float, val coordinate: Float, val target: Border?)

private data class AxisSnap(val lock: Alignment? = null, val preview: Alignment? = null) {
    fun guide(axis: SnapAxis, frame: Border): SnapGuide? {
        val alignment = lock ?: preview ?: return null
        val target = alignment.target
        return SnapGuide(
            axis = axis,
            coordinate = alignment.coordinate,
            start = minOf(axis.crossStart(frame), target?.let(axis::crossStart) ?: axis.crossStart(frame)),
            end = maxOf(axis.crossEnd(frame), target?.let(axis::crossEnd) ?: axis.crossEnd(frame)),
            preview = lock == null,
        )
    }
}

private fun choose(
    axis: SnapAxis,
    targets: List<Alignment>,
    lock: Alignment?,
    previous: Border,
    free: Border,
    distances: SnapDistances,
    preview: Boolean,
): AxisSnap {
    val center = axis.center(free)
    if (lock != null) {
        // On release let this movement pass through before considering another line.
        return AxisSnap(lock = lock.takeIf { abs(center - it.center) <= distances.release })
    }

    val before = axis.center(previous)
    val range = if (preview) maxOf(distances.capture, distances.preview) else distances.capture
    val nearest = targets.asSequence()
        .filter { abs(center - it.center) <= range && abs(center - it.center) < abs(before - it.center) }
        .minWithOrNull(compareBy<Alignment>(
            { abs(center - it.center) },
            { it.target?.let { target -> axis.crossGap(free, target) } ?: 0f },
        )) ?: return AxisSnap()
    return if (abs(center - nearest.center) <= distances.capture) AxisSnap(lock = nearest)
    else AxisSnap(preview = nearest)
}

private fun alignments(axis: SnapAxis, frame: Border, targets: List<Border>, limit: Float): List<Alignment> {
    val half = axis.halfSize(frame)
    return buildList {
        add(Alignment(half, 0f, null))
        add(Alignment(limit - half, limit, null))
        for (target in targets) {
            val start = axis.center(target) - axis.halfSize(target)
            val end = axis.center(target) + axis.halfSize(target)
            for (edge in listOf(start, end)) {
                add(Alignment(edge + half, edge, target))
                add(Alignment(edge - half, edge, target))
            }
            add(Alignment(axis.center(target), axis.center(target), target))
        }
    }.filter { it.center in 0f..limit && it.coordinate in 0f..limit }
}

private fun SnapAxis.center(frame: Border): Float = if (this == SnapAxis.X) frame.centerX else frame.centerY
private fun SnapAxis.halfSize(frame: Border): Float = if (this == SnapAxis.X) frame.width / 2f else frame.height / 2f
private fun SnapAxis.crossStart(frame: Border): Float =
    if (this == SnapAxis.X) frame.centerY - frame.height / 2f else frame.centerX - frame.width / 2f
private fun SnapAxis.crossEnd(frame: Border): Float =
    if (this == SnapAxis.X) frame.centerY + frame.height / 2f else frame.centerX + frame.width / 2f
private fun SnapAxis.crossGap(a: Border, b: Border): Float =
    maxOf(0f, crossStart(a) - crossEnd(b), crossStart(b) - crossEnd(a))
