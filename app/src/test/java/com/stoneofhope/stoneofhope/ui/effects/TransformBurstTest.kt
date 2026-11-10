package com.stoneofhope.stoneofhope.ui.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformBurstTest {
    private val window = Area(0f, 0f, 400f, 800f)

    @Test fun butterflyCountFollowsTheAreaBetweenFiveAndEight() {
        assertEquals(5, TransformBurstPlanner.butterflyCount(Area(0f, 0f, 100f, 100f)))
        assertEquals(7, TransformBurstPlanner.butterflyCount(Area(0f, 0f, 300f, 200f)))
        assertEquals(8, TransformBurstPlanner.butterflyCount(Area(0f, 0f, 380f, 400f)))
    }

    @Test fun aPlanHasTheButterfliesAndSixteenStarsStartingInsideTheText() {
        val area = Area(20f, 100f, 380f, 260f)
        val burst = TransformBurstPlanner.plan(area, Random(7))
        assertEquals(TransformBurstPlanner.butterflyCount(area), burst.butterflies.size)
        assertEquals(16, burst.stars.size)
        assertEquals(burst.butterflies.size * 4, burst.trailStars.size)
        (burst.butterflies.map { it.start } + burst.stars.map { it.start }).forEach {
            assertTrue("$it outside $area", it.x in area.left..area.right && it.y in area.top..area.bottom)
        }
    }

    @Test fun theBurstLastsUntilItsLastPieceIsGone() {
        val burst = TransformBurstPlanner.plan(Area(20f, 100f, 380f, 260f), Random(3))
        val lastButterfly = burst.butterflies.maxOf { it.delay + it.duration }
        assertTrue(burst.lifetime >= lastButterfly)
        assertTrue(burst.lifetime >= burst.stars.maxOf { it.delay + it.duration })
        assertTrue(burst.lifetime in 3.2f..5.3f)
    }

    @Test fun aButterflyEasesOutAlongItsHeading() {
        val b = Butterfly(Pt(50f, 60f), Pt(1f, 0f), 400f, 4f, 0f, 2f, 0f, 0f, 40f, 0, 0.3f)
        assertEquals(50f, b.at(0f).x, 0.001f)
        assertEquals(50f + 400f * 0.75f, b.at(0.5f).x, 0.001f)
        assertEquals(450f, b.at(1f).x, 0.001f)
        assertEquals(60f, b.at(0.5f).y, 0.001f)
    }

    @Test fun aButterflySwaysSidewaysAndStartsWhereItWasPlaced() {
        val b = Butterfly(Pt(50f, 60f), Pt(1f, 0f), 400f, 4f, 20f, 2f, 1f, 0f, 40f, 0, 0.3f)
        assertEquals(50f, b.at(0f).x, 0.001f)
        assertEquals(60f, b.at(0f).y, 0.001f)
        // Heading is +x, so the sway shows in y only.
        assertTrue(abs(b.at(0.3f).y - 60f) > 1f)
    }

    @Test fun aButterflyFadesInGrowsAndFadesOut() {
        val b = TransformBurstPlanner.plan(Area(20f, 100f, 380f, 260f), Random(5)).butterflies.first()
        assertEquals(0f, b.opacity(0f), 0.0001f)
        assertEquals(0f, b.opacity(1f), 0.0001f)
        assertTrue(b.opacity(0.5f) in 0.6f..1f)
        assertEquals(0.2f, b.scale(0f), 0.0001f)
        assertEquals(1f, b.scale(0.12f), 0.0001f)
        assertEquals(1f, b.scale(0.9f), 0.0001f)
    }

    @Test fun aButterflyNeverLeansMoreThanSeventyDegrees() {
        val limit = (70 * PI / 180).toFloat() + 0.0001f
        TransformBurstPlanner.plan(Area(20f, 100f, 380f, 260f), Random(9)).butterflies.forEach { b ->
            for (i in 0..20) assertTrue(abs(b.rotation(i / 20f)) <= limit)
        }
    }

    @Test fun aStarFliesAtTheKeyframesAndTwinklesOut() {
        val s = TransformBurstPlanner.plan(Area(20f, 100f, 380f, 260f), Random(2)).stars.first()
        assertEquals(0f, s.opacity(0f), 0.0001f)
        assertEquals(1f, s.opacity(0.2f), 0.0001f)
        assertEquals(0.7f, s.opacity(0.5f), 0.0001f)
        assertEquals(0f, s.opacity(1f), 0.0001f)
        assertEquals(1.1f, s.scale(0.2f), 0.0001f)
        val a = s.at(0.2f)
        assertEquals(s.start.x + (s.end.x - s.start.x) * 0.25f, a.x, 0.001f)
        assertEquals(s.end.x, s.at(1f).x, 0.001f)
        // Halfway between two keyframes the value is halfway between them.
        assertEquals(0.85f, s.opacity(0.35f), 0.0001f)
    }

    @Test fun aTrailStarSinksAsItFades() {
        val t = TrailStar(Pt(100f, 100f), 0, 0.5f, 9f)
        assertEquals(100f, t.position(0f).y, 0.001f)
        assertEquals(114f, t.position(1f).y, 0.001f)
        assertEquals(1f, t.opacity(0.25f), 0.0001f)
        assertEquals(0f, t.opacity(1f), 0.0001f)
    }

    @Test fun piecesScatterOutwardTippedUpward() {
        val random = Random(1)
        val center = Pt(200f, 200f)
        repeat(50) {
            val d = TransformBurstPlanner.scatterDirection(Pt(300f, 200f), center, random)
            assertEquals(1f, hypot(d.x, d.y), 0.001f)
            assertTrue("moves away to the right: $d", d.x > 0.3f)
            assertTrue("tipped up: $d", d.y < 0.4f)
        }
        // From the exact middle any direction will do, still a unit vector.
        val any = TransformBurstPlanner.scatterDirection(center, center, random)
        assertEquals(1f, hypot(any.x, any.y), 0.001f)
    }

    @Test fun theBurstComesOutOfTheTextWhenThereIsText() {
        val text = Area(20f, 300f, 380f, 420f)
        assertEquals(text, TransformTarget.visible(text, Area(300f, 500f, 380f, 530f), window))
    }

    @Test fun withNoTextItComesOutOfABandAboveTheChip() {
        val chip = Area(300f, 500f, 380f, 530f)
        val band = TransformTarget.visible(null, chip, window)!!
        assertEquals(16f, band.left, 0.001f)
        assertEquals(384f, band.right, 0.001f)
        assertEquals(440f, band.top, 0.001f)
        assertEquals(560f, band.bottom, 0.001f)
    }

    @Test fun onlyTheOnScreenPartIsUsedAndNothingWhenItIsOffScreen() {
        val partly = TransformTarget.visible(Area(20f, 700f, 380f, 900f), null, window)!!
        assertEquals(800f, partly.bottom, 0.001f)
        assertNull(TransformTarget.visible(Area(20f, 900f, 380f, 1000f), null, window))
        assertNull(TransformTarget.visible(null, null, window))
        assertNotNull(TransformTarget.visible(Area(0f, 0f, 10f, 10f), null, window))
    }
}
