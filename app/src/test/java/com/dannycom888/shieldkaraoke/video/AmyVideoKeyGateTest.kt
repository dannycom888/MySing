package com.dannycom888.shieldkaraoke.video

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * v1.0.72 RELEASE GATE: the shipped Amy video assets vs the
 * stage shader's key math.
 *
 * Why this exists: v1.0.70/.71 shipped a keying path nobody
 * had run against the actual files. Measured after the
 * fact, the pack's backdrops were a FAMILY of greens (the
 * clips were generated in batches), and eight of them sat
 * at chroma distances the shader's 0.20–0.34 ramp only
 * half-keyed — fast-01's median background alpha was 0.70 —
 * so Amy danced in front of a green veil on the user's
 * phone while every unit test stayed green.
 *
 * The gate runs [ChromaKeyMath] (the 1:1 JVM port of the
 * fragment shader) over pixels sampled from the SHIPPED
 * MP4s by tools/sample_key_pixels.py into the fixture
 * `amy_key_samples.json`:
 *
 *  - every clip's fixture hash must match the shipped file
 *    — ANY re-encode fails here until the samples are
 *    regenerated, so the fixture can never go stale
 *    silently;
 *  - with fresh samples, every clip's background must key
 *    to alpha ≈ 0 and its figure samples must stay ≈ 1.
 *
 * If this fails after an intentional re-encode, re-run
 * `python3 tools/sample_key_pixels.py` and check the new
 * numbers are actually good — do not just regenerate to
 * make it pass.
 */
class AmyVideoKeyGateTest {

    private fun assetDir(): File {
        val candidates = listOf(
            File("src/main/assets/${AmyVideoPack.ASSET_DIR}"),
            File("app/src/main/assets/${AmyVideoPack.ASSET_DIR}"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("amy_video assets not found; tried ${candidates.map { it.absolutePath }}")
    }

    private fun fixture(): JSONObject {
        val stream = javaClass.classLoader?.getResourceAsStream("amy_key_samples.json")
        val text = if (stream != null) {
            stream.bufferedReader().use { it.readText() }
        } else {
            val candidates = listOf(
                File("src/test/resources/amy_key_samples.json"),
                File("app/src/test/resources/amy_key_samples.json"),
            )
            val file = candidates.firstOrNull { it.isFile }
                ?: throw AssertionError("amy_key_samples.json not found on classpath or at ${candidates.map { it.absolutePath }}")
            file.readText()
        }
        return JSONObject(text)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun samples(clip: JSONObject, key: String): List<Triple<Int, Int, Int>> {
        val array = clip.getJSONArray(key)
        return (0 until array.length()).map { i ->
            val px = array.getJSONArray(i)
            Triple(px.getInt(0), px.getInt(1), px.getInt(2))
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    @Test
    fun `the key math itself is sane`() {
        // The key colour keys out completely; a neutral
        // colour (zero chroma, the far side of the space)
        // stays fully opaque.
        assertEquals(
            0f,
            ChromaKeyMath.alpha(ChromaKeyMath.KEY_RED, ChromaKeyMath.KEY_GREEN, ChromaKeyMath.KEY_BLUE),
            1e-6f,
        )
        assertEquals(1f, ChromaKeyMath.alpha(0.5f, 0.5f, 0.5f), 1e-6f)
        assertEquals(1f, ChromaKeyMath.alpha(0.9f, 0.65f, 0.5f), 1e-6f) // skin tone
    }

    @Test
    fun `fixture hashes match the shipped assets`() {
        val clips = fixture().getJSONObject("clips")
        assertEquals("fixture must cover the whole pack", 24, clips.length())
        for (name in clips.keys()) {
            val file = File(assetDir(), "$name.mp4")
            assertTrue("$name.mp4 missing", file.isFile)
            val entry = clips.getJSONObject(name)
            assertEquals(
                "$name changed since the samples were taken — re-run tools/sample_key_pixels.py " +
                    "and check the new key numbers before accepting them",
                entry.getString("sha256"), sha256(file),
            )
            assertEquals("$name size drift", entry.getLong("size"), file.length())
        }
    }

    @Test
    fun `every shipped background keys out and every figure stays opaque`() {
        val clips = fixture().getJSONObject("clips")
        for (name in clips.keys()) {
            val entry = clips.getJSONObject(name)
            val bgAlphas = samples(entry, "bg_samples").map { (r, g, b) ->
                ChromaKeyMath.alpha255(r, g, b)
            }
            val bgKeyed = bgAlphas.count { it <= 0.02f }
            assertTrue(
                "$name background does not key: median alpha ${median(bgAlphas)}, " +
                    "$bgKeyed/${bgAlphas.size} samples at alpha <= 0.02 " +
                    "(bg median rgb ${entry.getJSONArray("bg_median_rgb")})",
                median(bgAlphas) <= 0.02f && bgKeyed >= 0.98 * bgAlphas.size,
            )
            val figAlphas = samples(entry, "figure_samples").map { (r, g, b) ->
                ChromaKeyMath.alpha255(r, g, b)
            }
            val figSolid = figAlphas.count { it >= 0.98f }
            assertTrue(
                "$name figure eats into the key: median alpha ${median(figAlphas)}, " +
                    "$figSolid/${figAlphas.size} samples at alpha >= 0.98",
                median(figAlphas) >= 0.99f && figSolid >= 0.99 * figAlphas.size,
            )
        }
    }
}
