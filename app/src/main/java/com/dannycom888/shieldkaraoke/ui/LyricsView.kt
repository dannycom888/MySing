package com.dannycom888.shieldkaraoke.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dannycom888.shieldkaraoke.lyrics.LrcParser
import com.dannycom888.shieldkaraoke.lyrics.LyricLine
import com.dannycom888.shieldkaraoke.lyrics.SweepProgress

/** Karaoke gold: characters already sung (same as MyLRC Auto). */
val SWEEP_COLOR = Color(0xFFFFD54F)

/** Cyan glow base of the current line. */
val GLOW_COLOR = Color(0xFF4DD8E6)

private val dimColor = Color(0xFF8FA3C8)

/**
 * Big-screen scrolling lyrics. The current line sits centre with a
 * cyan glow; for enhanced LRCs the gold sweep fills each character
 * smoothly from its left edge to its right edge over the
 * character's own singing window (see [SweepProgress]); plain LRC
 * lines highlight whole, exactly as in MyLRC Auto.
 */
@Composable
fun LyricsView(lines: List<LyricLine>, positionMs: Long, compact: Boolean, modifier: Modifier = Modifier) {
    val current = LrcParser.currentIndex(lines, positionMs)
    val currentSize = if (compact) 27 else 40
    val sideSize = if (compact) 17 else 25
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        for (offset in -2..3) {
            val idx = current + offset
            if (idx == current) {
                CurrentLine(
                    line = lines.getOrNull(idx),
                    positionMs = positionMs,
                    fontSize = currentSize,
                )
            } else {
                Text(
                    text = lines.getOrNull(idx)?.text ?: " ",
                    color = if (idx < current) dimColor.copy(alpha = 0.55f) else dimColor,
                    fontSize = sideSize.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = if (compact) 2.dp else 5.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CurrentLine(line: LyricLine?, positionMs: Long, fontSize: Int) {
    if (line == null) {
        Text("♪ ♪ ♪", color = GLOW_COLOR, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        return
    }
    val glow = Shadow(color = GLOW_COLOR, offset = Offset(0f, 0f), blurRadius = 24f)
    if (line.words.isEmpty()) {
        // Plain LRC: the whole line highlights.
        Text(
            text = line.text,
            color = SWEEP_COLOR,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = glow),
            maxLines = 1,
        )
    } else {
        SweepLine(line = line, positionMs = positionMs, fontSize = fontSize, glow = glow)
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
private fun SweepLine(line: LyricLine, positionMs: Long, fontSize: Int, glow: Shadow) {
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
                    color = SWEEP_COLOR,
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
