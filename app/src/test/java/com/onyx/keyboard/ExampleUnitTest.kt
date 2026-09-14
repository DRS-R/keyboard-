package com.onyx.keyboard

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSliderStepValidation() {
    val from = 0.40f
    val to = 0.90f
    val step = 0.02f
    val testValues = floatArrayOf(0.40f, 0.50f, 0.62f, 0.78f, 0.90f)

    for (raw in testValues) {
      val stepCount = Math.round((raw - from) / step)
      val snapped = (from + stepCount * step).coerceIn(from, to)
      val ratio = (snapped - from) / step
      val diff = abs(ratio - ratio.roundToInt())
      assertTrue("Value $snapped with step $step should have ratio close to integer, diff=$diff", diff < 0.001)
      assertEquals(raw, snapped, 0.001f)
    }
  }

  @Test
  fun testArbitraryFloatSnapping() {
    val from = 0.40f
    val to = 0.90f
    val step = 0.02f
    val arbitrary = 0.573f

    val stepCount = Math.round((arbitrary - from) / step)
    val snapped = (from + stepCount * step).coerceIn(from, to)
    val ratio = (snapped - from) / step
    val diff = abs(ratio - ratio.roundToInt())
    assertTrue("Snapped value $snapped should satisfy stepSize", diff < 0.001)
  }
}
