package com.dannycom888.shieldkaraoke.video

import kotlin.math.sqrt

/**
 * The stage shader's chroma-key math, ported 1:1 from the
 * GLSL in [ChromaKeyRenderer] (v1.0.72). The shader cannot
 * share code with the JVM, so this object is its twin: it
 * holds the constants the renderer feeds the shader, and
 * [AmyVideoKeyGateTest] runs THIS math against pixels
 * sampled from the shipped assets (tools/sample_key_pixels.py)
 * so the encode and the key can never drift apart silently
 * again — the v1.0.71 failure mode, where several clips'
 * backdrops sat at chroma distances the ramp only half-keyed
 * (fast-01's median background alpha was 0.70) and nobody
 * knew until the user saw green on his phone.
 *
 * The math itself (identical in GLSL and here): a pixel's
 * chroma is its (Cb, Cr)-style offset from its own luma,
 * computed with the BT.601 weights the shader has always
 * used; alpha is the smoothstep of the Euclidean distance
 * between the pixel's chroma and the key colour's chroma
 * over [RAMP_LOW, RAMP_HIGH]. Despill lives only in the
 * shader (it shapes colour, not alpha) and is not ported.
 */
object ChromaKeyMath {

    /** The packaged green, #0af20a, per channel (float). */
    const val KEY_RED = 0.039f
    const val KEY_GREEN = 0.949f
    const val KEY_BLUE = 0.039f

    /** Distances at or below this key fully out (alpha 0). */
    const val RAMP_LOW = 0.20f

    /** Distances at or above this stay fully opaque (alpha 1). */
    const val RAMP_HIGH = 0.34f

    private fun chroma(r: Float, g: Float, b: Float): Pair<Float, Float> {
        val y = r * 0.299f + g * 0.587f + b * 0.114f
        return Pair((b - y) * 0.564f, (r - y) * 0.713f)
    }

    private val KEY_CHROMA = chroma(KEY_RED, KEY_GREEN, KEY_BLUE)

    /** Chroma distance between the pixel and the key colour. */
    fun chromaDistance(r: Float, g: Float, b: Float): Float {
        val (cb, cr) = chroma(r, g, b)
        val dcb = cb - KEY_CHROMA.first
        val dcr = cr - KEY_CHROMA.second
        return sqrt(dcb * dcb + dcr * dcr)
    }

    /** The shader's alpha for one pixel (channels 0..1). */
    fun alpha(r: Float, g: Float, b: Float): Float {
        val d = chromaDistance(r, g, b)
        val t = ((d - RAMP_LOW) / (RAMP_HIGH - RAMP_LOW)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** The shader's alpha for one 8-bit pixel. */
    fun alpha255(r: Int, g: Int, b: Int): Float =
        alpha(r / 255f, g / 255f, b / 255f)
}
