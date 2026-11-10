package com.stoneofhope.stoneofhope.ui.effects

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "Transform" tag effect (iOS `TransformEffect`, web `transformEffect.ts`). Purely visual: the
 * note's text lifts with a violet glow, swells in one thump while a flash goes off and glowing
 * butterflies and little stars scatter out of it, holds a moment, then settles back.
 *
 * The text animates itself ([transformText], [withTransformGlow]); the flash, butterflies and stars
 * are drawn by [TransformEffectOverlay] above the whole app, where touches pass through.
 */
@Stable
class TransformEffectController(
    private val scope: CoroutineScope,
    private val reduceMotion: () -> Boolean,
    private val onThump: () -> Unit = {},
    private val onSettled: () -> Unit = {},
) {
    /** The note or comment whose text is animating. */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** 0…1: the slow sideways expansion while the text glows up. */
    val lift = Animatable(0f)

    /** 0…1: the thump's extra sideways expansion. */
    val swell = Animatable(0f)

    /** 0…1: how strongly the letters glow. */
    val glow = Animatable(0f)

    internal class Active(val burst: TransformBurst, val startedAt: Long)

    internal var active by mutableStateOf<Active?>(null)
        private set

    /** Where the overlay sits in the window and how big it is, in px: window coordinates become its own. */
    internal var overlayOrigin = Offset.Zero
    internal var overlaySize = IntSize.Zero
    internal var density = 1f

    private var running = false
    private val texts = HashMap<String, HashMap<Any, Rect>>()
    private val chips = HashMap<String, Rect>()

    internal fun registerText(id: String, token: Any, bounds: Rect) {
        texts.getOrPut(id) { HashMap() }[token] = bounds
    }

    internal fun unregisterText(id: String, token: Any) {
        texts[id]?.let { it.remove(token); if (it.isEmpty()) texts.remove(id) }
    }

    internal fun registerChip(id: String, bounds: Rect) {
        chips[id] = bounds
    }

    /** Everything is planned and drawn in dp, relative to the overlay, like iOS points. */
    private fun Rect.toDp() = Area(
        (left - overlayOrigin.x) / density, (top - overlayOrigin.y) / density,
        (right - overlayOrigin.x) / density, (bottom - overlayOrigin.y) / density,
    )

    private fun textArea(id: String): Area? =
        texts[id]?.values?.filter { it.height > 4f }?.map { it.toDp() }?.reduceOrNull(Area::union)

    /** Plays the effect for the note or comment [id] (a tap on its Transform chip, or just after adding it). */
    fun play(id: String) {
        if (running || reduceMotion()) return
        if (overlaySize == IntSize.Zero) return
        val window = Area(0f, 0f, overlaySize.width / density, overlaySize.height / density)
        val text = textArea(id)
        val visible = TransformTarget.visible(text, chips[id]?.toDp(), window) ?: return
        running = true
        scope.launch { run(id, text != null, visible) }
    }

    /** iOS posts it 0.35 s after the tag sheet is gone, so the note is in view. */
    fun playAfterSheet(id: String) {
        scope.launch {
            delay(TransformTiming.AFTER_SHEET_MILLIS)
            play(id)
        }
    }

    private suspend fun run(id: String, hasText: Boolean, visible: Area) {
        try {
            if (hasText) {
                activeId = id
                coroutineScope {
                    launch { lift.animateTo(1f, tween((TransformTiming.LIFT * 1000).toInt(), easing = CubicBezierEasing(0.25f, 0.8f, 0.3f, 1f))) }
                    launch { glow.animateTo(0.5f, tween((TransformTiming.LIFT * 1000).toInt(), easing = CubicBezierEasing(0.25f, 0.8f, 0.3f, 1f))) }
                }
            }
            // The thump: the burst goes off with it.
            onThump()
            active = Active(TransformBurstPlanner.plan(visible), SystemClock.uptimeMillis())
            if (hasText) {
                coroutineScope {
                    launch { swell.animateTo(1f, tween((TransformTiming.THUMP * 1000).toInt(), easing = EaseOut)) }
                    launch { glow.animateTo(1f, tween((TransformTiming.THUMP * 1000).toInt(), easing = EaseOut)) }
                }
                delay((TransformTiming.HOLD * 1000).toLong())
                coroutineScope {
                    val settle = tween<Float>((TransformTiming.SETTLE * 1000).toInt(), easing = EaseInOut)
                    launch { lift.animateTo(0f, settle) }
                    launch { swell.animateTo(0f, settle) }
                    launch { glow.animateTo(0f, settle) }
                }
                onSettled()
            }
        } finally {
            withContext(NonCancellable) {
                lift.snapTo(0f); swell.snapTo(0f); glow.snapTo(0f)
            }
            activeId = null
            running = false
        }
    }

    internal fun finish(done: Active) {
        if (active === done) active = null
    }
}

val LocalTransformEffects = compositionLocalOf<TransformEffectController?> { null }

/** iOS checks Reduce Motion; Android's counterpart is animations switched off. */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Marks this view as the text of note or comment [id]: the effect lifts it (sideways only, like iOS)
 * and finds where to release the butterflies.
 */
@Composable
fun Modifier.transformText(id: String?): Modifier {
    val fx = LocalTransformEffects.current ?: return this
    if (id == null) return this
    val token = remember { Any() }
    DisposableEffect(id) { onDispose { fx.unregisterText(id, token) } }
    return this
        .onGloballyPositioned { fx.registerText(id, token, it.boundsInWindow()) }
        .graphicsLayer {
            if (fx.activeId == id) scaleX = 1f + 0.03f * fx.lift.value + 0.05f * fx.swell.value
        }
}

private val Violet = Color(0xFFA78BFA)

/** The letters' violet glow while the effect plays on [id]. */
@Composable
fun TextStyle.withTransformGlow(id: String?): TextStyle {
    val fx = LocalTransformEffects.current ?: return this
    if (id == null || fx.activeId != id) return this
    val glow = fx.glow.value
    if (glow <= 0f) return this
    val px = LocalDensity.current.density
    return copy(shadow = Shadow(Violet.copy(alpha = 0.9f * glow), Offset.Zero, (6f + 16f * glow) * px))
}

/** A Transform chip replays the effect on tap (iOS: preset chips are buttons); other chips are left alone. */
@Composable
fun Modifier.transformChip(ownerId: String?, enabled: Boolean): Modifier {
    val fx = LocalTransformEffects.current ?: return this
    if (ownerId == null || !enabled) return this
    return this
        .onGloballyPositioned { fx.registerChip(ownerId, it.boundsInWindow()) }
        .clickable(role = Role.Button) { fx.play(ownerId) }
}

/** Draws the burst above everything; place it last in the app's root box. */
@Composable
fun TransformEffectOverlay(fx: TransformEffectController) {
    val active = fx.active
    fx.density = LocalDensity.current.density
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            fx.overlayOrigin = it.positionInWindow()
            fx.overlaySize = it.size
        },
    ) {
        if (active != null) {
            var elapsed by remember(active) { mutableStateOf(0f) }
            LaunchedEffect(active) {
                while (true) {
                    androidx.compose.runtime.withFrameNanos { elapsed = (SystemClock.uptimeMillis() - active.startedAt) / 1000f }
                    if (elapsed > active.burst.lifetime + 0.2f) break
                }
                fx.finish(active)
            }
            Canvas(Modifier.fillMaxSize()) { scale(density, Offset.Zero) { drawBurst(active.burst, elapsed) } }
        }
    }
}

// MARK: Drawing

private fun DrawScope.drawBurst(burst: TransformBurst, time: Float) {
    drawFlash(burst, time)
    burst.trailStars.forEach { s ->
        val t = (time - s.delay) / s.duration
        if (t in 0f..1f) drawSparkle(s.position(t), s.size, Color(s.color), s.scale(t), s.rotation(t), s.opacity(t))
    }
    burst.butterflies.forEach { b ->
        val t = (time - b.delay) / b.duration
        if (t in 0f..1f) drawButterfly(b, t, time - b.delay)
    }
    burst.stars.forEach { s ->
        val t = (time - s.delay) / s.duration
        if (t in 0f..1f) drawSparkle(s.at(t), s.size, Color(s.color), s.scale(t), s.rotation(t), s.opacity(t))
    }
}

/** The moment the text turns: a soft lilac light from its middle. */
private fun DrawScope.drawFlash(burst: TransformBurst, time: Float) {
    val t = time / TransformTiming.FLASH
    if (t !in 0f..1f) return
    val eased = 1f - (1f - t) * (1f - t)
    val size = maxOf(burst.visible.width, burst.visible.height, 160f)
    val radius = size / 2f * (0.3f + 1.0f * eased)
    val alpha = 1f - eased
    val c = Offset(burst.center.x, burst.center.y)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.95f * alpha),
            0.35f to Violet.copy(alpha = 0.45f * alpha),
            0.7f to Violet.copy(alpha = 0f),
            center = c,
            radius = radius,
        ),
        radius = radius,
        center = c,
    )
}

/** A four-pointed sparkle with a soft glow. [size] is its width, in dp like everything drawn here. */
private fun DrawScope.drawSparkle(at: Pt, size: Float, color: Color, scale: Float, turn: Float, opacity: Float) {
    if (opacity <= 0f) return
    val path = Path().apply {
        moveTo(0f, -10f)
        cubicTo(1f, -3f, 3f, -1f, 10f, 0f)
        cubicTo(3f, 1f, 1f, 3f, 0f, 10f)
        cubicTo(-1f, 3f, -3f, 1f, -10f, 0f)
        cubicTo(-3f, -1f, -1f, -3f, 0f, -10f)
        close()
    }
    translate(at.x, at.y) {
        rotate(Math.toDegrees(turn.toDouble()).toFloat(), Offset.Zero) {
            scale(scale, Offset.Zero) {
                softGlow(color, 0.45f * opacity, size * 0.7f)
                scale(size / 20f, Offset.Zero) { drawPath(path, color.copy(alpha = opacity)) }
            }
        }
    }
}

/** Translucent gradient wings around a slim body; the wings fold in and out from the body's axis. */
private fun DrawScope.drawButterfly(b: Butterfly, t: Float, sinceStart: Float) {
    val opacity = b.opacity(t)
    if (opacity <= 0f) return
    val (from, to) = TransformBurstPlanner.WING_COLORS[b.palette].let { Color(it.first) to Color(it.second) }
    val here = b.at(t)
    val size = b.size
    val s = size / 48f
    // 0 → 1 → 0 over one beat each way: wings opening and folding to 30 %.
    val phase = (sinceStart / b.beat) % 2f
    val fold = (if (phase <= 1f) phase else 2f - phase).let { 0.5f - 0.5f * cos(PI.toFloat() * it) }
    val flap = 1f - 0.7f * fold

    val upper = Path().apply {
        moveTo(0f, -2f)
        cubicTo(6f, -18f, 20f, -20f, 22f, -10f)
        cubicTo(23f, -3f, 12f, 2f, 0f, 2f)
        close()
    }
    val lower = Path().apply {
        moveTo(0f, 1f)
        cubicTo(10f, 2f, 18f, 5f, 15f, 12f)
        cubicTo(12f, 18f, 4f, 12f, 0f, 5f)
        close()
    }
    translate(here.x, here.y) {
        rotate(Math.toDegrees(b.rotation(t).toDouble()).toFloat(), Offset.Zero) {
            scale(b.scale(t), Offset.Zero) {
                softGlow(from, 0.35f * opacity, size * 0.6f)
                scale(s, Offset.Zero) {
                    for (mirrored in listOf(false, true)) {
                        scale(if (mirrored) -flap else flap, 1f, Offset.Zero) {
                            val brush = Brush.linearGradient(listOf(from, to), Offset(-24f, -20f), Offset(24f, 20f))
                            drawPath(upper, brush, alpha = 0.72f * opacity)
                            drawPath(lower, brush, alpha = 0.72f * opacity)
                            drawPath(upper, Color.White.copy(alpha = 0.75f * opacity), style = Stroke(0.6f))
                            drawPath(lower, Color.White.copy(alpha = 0.75f * opacity), style = Stroke(0.6f))
                        }
                    }
                    drawOval(to.copy(alpha = 0.9f * opacity), Offset(-1.2f, -7f), androidx.compose.ui.geometry.Size(2.4f, 15f))
                }
            }
        }
    }
}

/** A soft halo that fades to nothing at its edge (iOS draws it as a layer shadow). */
private fun DrawScope.softGlow(color: Color, alpha: Float, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f), center = Offset.Zero, radius = radius),
        radius = radius,
        center = Offset.Zero,
    )
}
