package com.stoneofhope.stoneofhope.ui.effects

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The flash, butterflies and stars of the "Transform" tag effect: iOS `TransformTagAnimation.swift`
 * (itself a port of the web app's `transformEffect.ts`). This file is the arithmetic only — where each
 * piece starts, how it moves and fades over its own time — so it can be tested without a screen;
 * `TransformEffect.kt` draws it.
 */

data class Pt(val x: Float, val y: Float)

data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val center get() = Pt((left + right) / 2, (top + bottom) / 2)

    fun intersect(other: Area): Area? {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = min(right, other.right)
        val b = min(bottom, other.bottom)
        return if (r > l && b > t) Area(l, t, r, b) else null
    }

    fun union(other: Area) = Area(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))
}

/** iOS `TransformEffect` timings, in seconds. */
object TransformTiming {
    const val LIFT = 0.8f
    const val THUMP = 0.24f
    const val HOLD = 0.3f
    const val SETTLE = 1.0f

    /** The flash's own length. */
    const val FLASH = 0.52f

    /** iOS posts the effect 0.35 s after the tag sheet closes, so the note is in view. */
    const val AFTER_SHEET_MILLIS = 350L
}

/** Where the burst comes out of: the note's text, else a band around the tag chip (iOS `play`). */
object TransformTarget {
    private const val SIDE_MARGIN = 16f
    private const val BAND_ABOVE = 60f
    private const val BAND_HEIGHT = 120f

    /** The part of the target that is on screen, or null when none is. */
    fun visible(text: Area?, chip: Area?, window: Area): Area? {
        val target = text ?: chip?.let { Area(window.left + SIDE_MARGIN, it.top - BAND_ABOVE, window.right - SIDE_MARGIN, it.top - BAND_ABOVE + BAND_HEIGHT) } ?: return null
        return target.intersect(window)
    }
}

/** A butterfly's palette in [TransformBurstPlanner.WING_COLORS] and how it moves. */
class Butterfly(
    val start: Pt,
    private val dir: Pt,
    private val distance: Float,
    val duration: Float,
    private val sway: Float,
    private val waves: Float,
    private val phase: Float,
    val delay: Float,
    val size: Float,
    val palette: Int,
    /** Seconds one wing beat takes (open → folded). */
    val beat: Float,
) {
    private val side = Pt(-dir.y, dir.x)

    /** Position at 0…1 of its flight: eases out along its heading while swaying side to side. */
    fun at(t: Float): Pt {
        val along = distance * (1 - (1 - t) * (1 - t))
        val wobble = sway * (sin(2 * PI * waves * t + phase).toFloat() - sin(phase))
        return Pt(start.x + dir.x * along + side.x * wobble, start.y + dir.y * along + side.y * wobble)
    }

    /** It leans into its turns, never more than 70° either way. */
    fun rotation(t: Float): Float {
        val here = at(t)
        val next = at(min(t + 0.01f, 1.01f))
        val heading = atan2(next.x - here.x, here.y - next.y)
        val limit = (70 * PI / 180).toFloat()
        return (heading * 0.5f).coerceIn(-limit, limit)
    }

    fun scale(t: Float) = 0.2f + 0.8f * min(t / 0.12f, 1f)

    fun opacity(t: Float): Float {
        val twinkle = 0.8f + 0.15f * sin(t * PI * 10 + phase).toFloat()
        return twinkle * min(t / 0.1f, 1f) * min((1 - t) / 0.3f, 1f)
    }

    /** The moments a little star is left twinkling where the butterfly was. */
    val trailTimes = listOf(0.14f, 0.26f, 0.4f, 0.55f)
}

/** Piecewise-linear values over 0…1, like a Core Animation keyframe track. */
class Track(private val times: List<Float>, private val values: List<Float>) {
    fun at(t: Float): Float {
        if (t <= times.first()) return values.first()
        if (t >= times.last()) return values.last()
        val i = times.indexOfLast { it <= t }
        val f = (t - times[i]) / (times[i + 1] - times[i])
        return values[i] + (values[i + 1] - values[i]) * f
    }
}

/** A star bursting out of the break: it flies, twinkles and fades. */
class Star(
    val start: Pt,
    val end: Pt,
    val duration: Float,
    val delay: Float,
    val size: Float,
    val color: Int,
    private val spin: Float,
) {
    private val travel = Track(listOf(0f, 0.2f, 0.5f, 0.7f, 1f), listOf(0f, 0.25f, 0.6f, 0.8f, 1f))
    private val scaleTrack = Track(listOf(0f, 0.2f, 0.5f, 0.7f, 1f), listOf(0.1f, 1.1f, 0.6f, 1f, 0.1f))
    private val opacityTrack = Track(listOf(0f, 0.2f, 0.5f, 0.7f, 1f), listOf(0f, 1f, 0.7f, 1f, 0f))
    private val turn = Track(listOf(0f, 0.2f, 0.5f, 0.7f, 1f), listOf(0f, spin * 0.25f, spin * 0.6f, spin * 0.8f, spin))

    fun at(t: Float): Pt {
        val f = travel.at(t)
        return Pt(start.x + (end.x - start.x) * f, start.y + (end.y - start.y) * f)
    }

    fun scale(t: Float) = scaleTrack.at(t)
    fun opacity(t: Float) = opacityTrack.at(t)
    fun rotation(t: Float) = turn.at(t)
}

/** A small star left twinkling behind a butterfly; sinks a little as it fades. */
class TrailStar(val at: Pt, val color: Int, val delay: Float, val size: Float) {
    val duration = 1.2f
    private val scaleTrack = Track(listOf(0f, 0.25f, 1f), listOf(0.2f, 1.1f, 0.2f))
    private val opacityTrack = Track(listOf(0f, 0.25f, 1f), listOf(0f, 1f, 0f))
    private val turnTrack = Track(listOf(0f, 0.25f, 1f), listOf(0f, (30 * PI / 180).toFloat(), (90 * PI / 180).toFloat()))

    fun position(t: Float) = Pt(at.x, at.y + 14f * t)
    fun scale(t: Float) = scaleTrack.at(t)
    fun opacity(t: Float) = opacityTrack.at(t)
    fun rotation(t: Float) = turnTrack.at(t)
}

class TransformBurst(
    val center: Pt,
    val visible: Area,
    val butterflies: List<Butterfly>,
    val stars: List<Star>,
    val trailStars: List<TrailStar>,
) {
    /** Seconds until the last piece is gone. */
    val lifetime: Float = max(
        0.6f,
        max(
            butterflies.maxOfOrNull { it.delay + it.duration } ?: 0f,
            stars.maxOfOrNull { it.delay + it.duration } ?: 0f,
        ),
    )
}

object TransformBurstPlanner {
    /** Star colours, ARGB. */
    val STAR_COLORS = listOf(0xFFF6C667.toInt(), 0xFFFFE7A3.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9B8FF.toInt(), 0xFF9AD7FF.toInt())

    /** Wing gradients: (from, to), ARGB. */
    val WING_COLORS = listOf(
        0xFFA78BFA.toInt() to 0xFF67C8F5.toInt(),
        0xFFF59AC6.toInt() to 0xFFA78BFA.toInt(),
        0xFF5FD4CC.toInt() to 0xFF7FA8FF.toInt(),
        0xFFF6C667.toInt() to 0xFFF59AC6.toInt(),
    )

    private const val STAR_COUNT = 16

    /** One butterfly per 9000 px² of text, between five and eight. */
    fun butterflyCount(area: Area): Int = (area.width * area.height / 9000f).roundToInt().coerceIn(5, 8)

    /** A jittered grid over [area]: where the pieces start. */
    fun scatterPoints(area: Area, count: Int, random: Random): List<Pt> {
        val cols = max(ceil(sqrt(count * area.width / max(area.height, 1f))).toInt(), 1)
        val rows = max(ceil(count / cols.toFloat()).toInt(), 1)
        return List(count) { i ->
            val jx = random.range(-0.35f, 0.35f)
            val jy = random.range(-0.35f, 0.35f)
            Pt(
                area.left + ((i % cols) + 0.5f + jx) * (area.width / cols),
                area.top + ((i / cols) + 0.5f + jy) * (area.height / rows),
            )
        }
    }

    /** Unit direction from the break's middle out through [from], tipped a little upward. */
    fun scatterDirection(from: Pt, center: Pt, random: Random): Pt {
        val angle = if (from == center) random.range(0f, (2 * PI).toFloat()) else atan2(from.y - center.y, from.x - center.x)
        val x = cos(angle) + random.range(-0.25f, 0.25f)
        val y = sin(angle) + random.range(-0.25f, 0.25f) - 0.3f
        val length = max(hypot(x, y), 0.001f)
        return Pt(x / length, y / length)
    }

    fun plan(visible: Area, random: Random = Random): TransformBurst {
        val center = visible.center
        val butterflies = scatterPoints(visible, butterflyCount(visible), random).mapIndexed { index, start ->
            Butterfly(
                start = start,
                dir = scatterDirection(start, center, random),
                distance = random.range(280f, 540f),
                duration = random.range(3.2f, 5.0f),
                sway = random.range(14f, 34f),
                waves = random.range(1.5f, 2.5f),
                phase = random.range(0f, (2 * PI).toFloat()),
                delay = random.range(0f, 0.18f),
                size = random.range(30f, 52f),
                palette = index % WING_COLORS.size,
                beat = random.range(0.24f, 0.36f),
            )
        }
        val stars = scatterPoints(visible, STAR_COUNT, random).map { start ->
            val dir = scatterDirection(start, center, random)
            val distance = random.range(90f, 330f)
            Star(
                start = start,
                end = Pt(start.x + dir.x * distance, start.y + dir.y * distance + 20f),
                duration = random.range(1.5f, 2.7f),
                delay = random.range(0f, 0.2f),
                size = random.range(9f, 20f),
                color = STAR_COLORS[random.nextInt(STAR_COLORS.size)],
                spin = random.range(-110f, 110f) * (PI / 180).toFloat(),
            )
        }
        val trails = butterflies.flatMap { b ->
            b.trailTimes.map { t -> TrailStar(b.at(t), WING_COLORS[b.palette].first, b.delay + t * b.duration, random.range(7f, 12f)) }
        }
        return TransformBurst(center, visible, butterflies, stars, trails)
    }

    private fun Random.range(from: Float, to: Float) = from + nextFloat() * (to - from)
}
