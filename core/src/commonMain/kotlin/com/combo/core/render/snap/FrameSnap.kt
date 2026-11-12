package com.combo.core.render.snap

import com.combo.core.render.Border
import com.combo.core.render.Edge
import com.combo.core.render.FrameEdgeResize
import com.combo.core.render.clampPhotoPan
import com.combo.core.render.moveFrame
import kotlin.math.abs

enum class SnapAxis { X, Y }

data class SnapGuide(
    val axis: SnapAxis,
    val coordinate: Float,
    val start: Float,
    val end: Float,
)

data class SnapDistances(val capture: Float, val release: Float)

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
class FrameSnap private constructor(
    private val xTargets: List<Alignment>,
    private val yTargets: List<Alignment>,
    private val moveFree: (Border, Float, Float) -> Border,
    private val position: (SnapAxis, Border) -> Float = { axis, frame -> axis.center(frame) },
    private val place: (Border, Float?, Float?) -> Border = ::placeCenter,
    private val guideFrame: Border? = null,
) {
    constructor(canvasW: Float, canvasH: Float, frame: Border, targets: List<Border>) : this(
        alignments(SnapAxis.X, frame, targets, canvasW),
        alignments(SnapAxis.Y, frame, targets, canvasH),
        { free, dx, dy -> moveFrame(free, dx, dy, canvasW, canvasH) },
    )

    fun move(
        state: FrameSnapState,
        dx: Float,
        dy: Float,
        distances: SnapDistances,
        bypass: Boolean = false,
    ): SnapStep {
        // Advance the free position, including when the displayed frame is held by a line.
        val free = moveFree(state.free, dx, dy)
        if (bypass) return SnapStep(FrameSnapState(free), free, emptyList())

        val x = choose(
            SnapAxis.X, xTargets, state.xLock, position(SnapAxis.X, state.free), position(SnapAxis.X, free), free, distances,
        )
        val y = choose(
            SnapAxis.Y, yTargets, state.yLock, position(SnapAxis.Y, state.free), position(SnapAxis.Y, free), free, distances,
        )
        val placed = place(free, x?.position, y?.position)
        val span = guideFrame ?: placed
        return SnapStep(
            FrameSnapState(free, x, y),
            placed,
            listOfNotNull(x?.guide(SnapAxis.X, span), y?.guide(SnapAxis.Y, span)),
        )
    }

    companion object {
        fun photo(frame: Border, photo: Border): FrameSnap {
            return FrameSnap(
                photoAlignments(SnapAxis.X, frame, photo),
                photoAlignments(SnapAxis.Y, frame, photo),
                { free, dx, dy ->
                    free.copy(
                        centerX = frame.centerX + clampPhotoPan(free.centerX - frame.centerX + dx, frame.width, photo.width),
                        centerY = frame.centerY + clampPhotoPan(free.centerY - frame.centerY + dy, frame.height, photo.height),
                    )
                },
                guideFrame = frame,
            )
        }

        fun resize(
            canvasW: Float,
            canvasH: Float,
            frame: Border,
            targets: List<Border>,
            edge: Edge,
            minSize: Float,
            oneSided: Boolean,
        ): FrameSnap {
            val resize = FrameEdgeResize(frame, edge, minSize, oneSided)
            val axis = if (resize.horizontal) SnapAxis.X else SnapAxis.Y
            val limit = if (resize.horizontal) canvasW else canvasH
            val guides = buildList {
                val boundary = if (resize.towardEnd) limit else 0f
                add(Alignment(boundary, boundary, null))
                for (target in targets) {
                    val start = axis.center(target) - axis.halfSize(target)
                    val end = axis.center(target) + axis.halfSize(target)
                    add(Alignment(start, start, target))
                    add(Alignment(end, end, target))
                }
            }.filter { it.coordinate in 0f..limit && resize.canReach(it.coordinate) }
            return FrameSnap(
                if (axis == SnapAxis.X) guides else emptyList(),
                if (axis == SnapAxis.Y) guides else emptyList(),
                resize::move,
                position = { _, border -> resize.coordinate(border) },
                place = { free, x, y -> (x ?: y)?.let { resize.place(free, it) } ?: free },
            )
        }
    }
}

internal data class Alignment(val position: Float, val coordinate: Float, val target: Border?) {
    fun guide(axis: SnapAxis, frame: Border): SnapGuide {
        return SnapGuide(
            axis = axis,
            coordinate = coordinate,
            start = minOf(axis.crossStart(frame), target?.let(axis::crossStart) ?: axis.crossStart(frame)),
            end = maxOf(axis.crossEnd(frame), target?.let(axis::crossEnd) ?: axis.crossEnd(frame)),
        )
    }
}

private fun choose(
    axis: SnapAxis,
    targets: List<Alignment>,
    lock: Alignment?,
    before: Float,
    position: Float,
    free: Border,
    distances: SnapDistances,
): Alignment? {
    if (lock != null) {
        // On release let this movement pass through before considering another line.
        return lock.takeIf { abs(position - it.position) <= distances.release }
    }

    return targets.asSequence()
        .filter { abs(position - it.position) <= distances.capture && abs(position - it.position) < abs(before - it.position) }
        .minWithOrNull(compareBy<Alignment>(
            { abs(position - it.position) },
            { it.target?.let { target -> axis.crossGap(free, target) } ?: 0f },
        ))
}

private fun placeCenter(frame: Border, x: Float?, y: Float?): Border =
    frame.copy(centerX = x ?: frame.centerX, centerY = y ?: frame.centerY)

private fun photoAlignments(axis: SnapAxis, frame: Border, photo: Border): List<Alignment> {
    val start = axis.center(frame) - axis.halfSize(frame)
    val end = axis.center(frame) + axis.halfSize(frame)
    return listOf(Alignment(start + axis.halfSize(photo), start, frame), Alignment(end - axis.halfSize(photo), end, frame))
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
    }.filter { it.position in 0f..limit && it.coordinate in 0f..limit }
}

private fun SnapAxis.center(frame: Border): Float = if (this == SnapAxis.X) frame.centerX else frame.centerY
private fun SnapAxis.halfSize(frame: Border): Float = if (this == SnapAxis.X) frame.width / 2f else frame.height / 2f
private fun SnapAxis.crossStart(frame: Border): Float =
    if (this == SnapAxis.X) frame.centerY - frame.height / 2f else frame.centerX - frame.width / 2f
private fun SnapAxis.crossEnd(frame: Border): Float =
    if (this == SnapAxis.X) frame.centerY + frame.height / 2f else frame.centerX + frame.width / 2f
private fun SnapAxis.crossGap(a: Border, b: Border): Float =
    maxOf(0f, crossStart(a) - crossEnd(b), crossStart(b) - crossEnd(a))
