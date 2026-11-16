package com.dannycom888.shieldkaraoke.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dannycom888.shieldkaraoke.lyrics.LrcParser
import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.lyrics.SweepProgress

/** Karaoke gold: characters already sung (same as MyLRC Auto).
 *  Since v1.0.26 this is the GOLD entry — and the default — of
 *  the admin page's sweep-colour choice
 *  ([com.dannycom888.shieldkaraoke.state.SweepColor]); the view
 *  takes the chosen colour as a parameter and this constant is
 *  only the fallback for callers that pass none. */
val SWEEP_COLOR = Color(0xFFFFD54F)

/** Cyan glow base of the current line. */
val GLOW_COLOR = Color(0xFF4DD8E6)

private val dimColor = Color(0xFF8FA3C8)

/**
 * How many lyric lines the stage shows and where the current line
 * sits (v1.0.25). Until now the lyrics were a fixed six lines
 * (-2..+3) glued to the TOP of their slot: the block hung in the
 * upper stage and everything below it was empty. Now the window
 * is computed from the slot's real height — the current line's
 * centre rests at [CURRENT_CENTER_FRACTION] of the height (the
 * reading position the fixed block approximated on a full stage),
 * up to [ABOVE_MAX] sung lines above it, and upcoming lines fill
 * whatever height remains below. Pure arithmetic, unit-tested.
 */
object LyricsWindow {
    const val ABOVE_MAX = 2
    const val CURRENT_CENTER_FRACTION = 0.40f
    const val BELOW_MAX = 24

    /** Line-box height estimate per sp of font size. Slightly
     *  generous on purpose: an overestimate costs a line of slack
     *  at the bottom, an underestimate would clip one. */
    const val LINE_HEIGHT_PER_SP = 1.35f

    data class Window(val above: Int, val below: Int, val topSpacerDp: Float)

    fun compute(
        availableHeightDp: Float,
        currentLineDp: Float,
        sideLineDp: Float,
    ): Window {
        if (availableHeightDp <= 0f || currentLineDp <= 0f || sideLineDp <= 0f) {
            return Window(above = ABOVE_MAX, below = 3, topSpacerDp = 0f)
        }
        // How many side lines fit beside the current line at all.
        val sideFit = ((availableHeightDp - currentLineDp) / sideLineDp)
            .toInt().coerceAtLeast(0)
        val above = minOf(ABOVE_MAX, sideFit)
        // Place the current line's centre on the fraction mark;
        // the spacer is whatever the lines above don't cover, and
        // never so much that the current line itself overflows.
        val desiredSpacer = CURRENT_CENTER_FRACTION * availableHeightDp -
            currentLineDp / 2f - above * sideLineDp
        val maxSpacer = (availableHeightDp - above * sideLineDp - currentLineDp)
            .coerceAtLeast(0f)
        val spacer = desiredSpacer.coerceIn(0f, maxSpacer)
        val below = ((availableHeightDp - spacer - above * sideLineDp - currentLineDp) /
            sideLineDp).toInt().coerceIn(0, BELOW_MAX)
        return Window(above = above, below = below, topSpacerDp = spacer)
    }
}

/**
 * Big-screen scrolling lyrics. The current line sits centre with a
 * cyan glow; for enhanced LRCs the gold sweep fills each character
 * smoothly from its left edge to its right edge over the
 * character's own singing window (see [SweepProgress]); plain LRC
 * lines highlight whole, exactly as in MyLRC Auto. The column
 * fills its slot's whole height (v1.0.25, see [LyricsWindow]):
 * line spacing and the sweep rendering are untouched.
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    compact: Boolean,
    modifier: Modifier = Modifier,
    sweepColor: Color = SWEEP_COLOR,
) {
    val current = LrcParser.currentIndex(lines, positionMs)
    // v1.0.24: a touch larger now the stage is wider (the queue
    // panel floats instead of taking a column): current line
    // 27/40 -> 28/42sp, side lines 17/25 -> 18/26sp.
    val currentSize = if (compact) 28 else 42
    val sideSize = if (compact) 18 else 26
    val sidePadV = if (compact) 2 else 5
    // v1.0.28: this view only ever renders inside the fixed safe
    // band (see LyricsBand), so its own width IS the band width —
    // the yardstick every line is fitted against (see LyricFit).
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bandWidthPx = with(density) { maxWidth.toPx() }
        val win = LyricsWindow.compute(
            availableHeightDp = maxHeight.value,
            currentLineDp = currentSize * LyricsWindow.LINE_HEIGHT_PER_SP,
            sideLineDp = sideSize * LyricsWindow.LINE_HEIGHT_PER_SP + 2 * sidePadV,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (win.topSpacerDp > 0f) {
                Spacer(modifier = Modifier.height(win.topSpacerDp.dp))
            }
            for (offset in -win.above..win.below) {
                val idx = current + offset
                if (idx == current) {
                    CurrentLine(
                        line = lines.getOrNull(idx),
                        positionMs = positionMs,
                        fontSize = currentSize,
                        sweepColor = sweepColor,
                        measurer = measurer,
                        bandWidthPx = bandWidthPx,
                    )
                } else {
                    SideLine(
                        text = lines.getOrNull(idx)?.text ?: " ",
                        sung = idx < current,
                        baseSize = sideSize,
                        padV = sidePadV,
                        measurer = measurer,
                        bandWidthPx = bandWidthPx,
                    )
                }
            }
        }
    }
}

/**
 * One non-current line, fitted to the band (v1.0.28): measured at
 * its base size and shrunk via [LyricFit] when it would overflow,
 * so the whole line stays visible inside the band instead of
 * being centre-clipped at both ends.
 */
@Composable
private fun SideLine(
    text: String,
    sung: Boolean,
    baseSize: Int,
    padV: Int,
    measurer: TextMeasurer,
    bandWidthPx: Float,
) {
    val scale = remember(text, baseSize, bandWidthPx) {
        val natural = measurer.measure(
            text = text,
            style = TextStyle(fontSize = baseSize.sp),
        ).size.width
        LyricFit.scaleFor(natural.toFloat(), bandWidthPx)
    }
    Text(
        text = text,
        color = if (sung) dimColor.copy(alpha = 0.55f) else dimColor,
        fontSize = (baseSize * scale).sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = padV.dp),
        maxLines = 1,
    )
}

@Composable
private fun CurrentLine(
    line: LyricLine?,
    positionMs: Long,
    fontSize: Int,
    sweepColor: Color,
    measurer: TextMeasurer,
    bandWidthPx: Float,
) {
    if (line == null) {
        Text("♪ ♪ ♪", color = GLOW_COLOR, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        return
    }
    // v1.0.28: fit the whole line into the band (see LyricFit) —
    // both render paths below take the same fitted size, so the
    // sweep's layout-derived boundary stays exact.
    val fittedSize = remember(line.text, fontSize, bandWidthPx) {
        val natural = measurer.measure(
            text = line.text,
            style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold),
        ).size.width
        (fontSize * LyricFit.scaleFor(natural.toFloat(), bandWidthPx)).toInt().coerceAtLeast(1)
    }
    val glow = Shadow(color = GLOW_COLOR, offset = Offset(0f, 0f), blurRadius = 24f)
    if (line.words.isEmpty()) {
        // Plain LRC: the whole line highlights.
        Text(
            text = line.text,
            color = sweepColor,
            fontSize = fittedSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = glow),
            maxLines = 1,
        )
    } else {
        SweepLine(line = line, positionMs = positionMs, fontSize = fittedSize, glow = glow, sweepColor = sweepColor)
    }
}

/**
 * The current enhanced-LRC line: the whole line drawn once in the
 * cyan glow base, then the same text drawn again in gold with the
 * draw clipped at the sweep boundary — the x of the character
 * currently being sung, advanced by that character's own fill
 * progress, so the gold edge travels continuously through each
 * character instead of jumping character by character.
 */
@Composable
private fun SweepLine(line: LyricLine, positionMs: Long, fontSize: Int, glow: Shadow, sweepColor: Color) {
    val windows = remember(line.words) { SweepProgress.charWindows(line.words) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = line.text,
            color = GLOW_COLOR,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = glow),
            maxLines = 1,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        val result = layout
        if (result != null) {
            val boundary = sweepBoundaryX(result, windows, positionMs)
            if (boundary > 0f) {
                Text(
                    text = line.text,
                    color = sweepColor,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().drawWithContent {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, boundary, size.height)
                        drawContent()
                        drawContext.canvas.restore()
                    },
                )
            }
        }
    }
}

/**
 * X pixel (in the line's layout coordinates) of the gold sweep
 * boundary at [nowMs]: every fully sung character contributes its
 * whole width, the character being sung contributes the fraction
 * [SweepProgress.progress] reports for its own window.
 */
private fun sweepBoundaryX(
    result: TextLayoutResult,
    windows: List<SweepProgress.CharWindow>,
    nowMs: Long,
): Float {
    if (windows.isEmpty()) return 0f
    var fullChars = 0
    var fraction = 0f
    for (index in windows.indices) {
        val p = SweepProgress.progress(nowMs, windows[index].startMs, windows[index].endMs)
        if (p >= 1f) {
            fullChars = index + 1
        } else {
            fraction = p
            break
        }
    }
    val count = result.layoutInput.text.length
    val from = fullChars.coerceAtMost(count)
    if (from >= count) return result.getHorizontalPosition(count, true)
    val x0 = result.getHorizontalPosition(from, true)
    if (fraction <= 0f) return x0
    val x1 = result.getHorizontalPosition(from + 1, true)
    return x0 + (x1 - x0) * fraction
}
