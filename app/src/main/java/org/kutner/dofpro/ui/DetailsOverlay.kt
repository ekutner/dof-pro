package org.kutner.dofpro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kutner.dofpro.calc.Dof
import org.kutner.dofpro.model.DofResult
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.TargetKind
import org.kutner.dofpro.model.formatDistance
import org.kutner.dofpro.model.formatSig
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.hypot

/**
 * The "etc" panel: everything the Windows version keeps in its extra right-hand column.
 *
 * Closed with the X, or by tapping anywhere off the text. The X is what makes it obvious
 * the panel is dismissable at all — tap-anywhere is a thing you have to already know.
 */
@Composable
fun DetailsOverlay(state: DofState, result: DofResult, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.dp, Palette.PanelBorder)
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Details",
                    color = Palette.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Palette.Text,
                    )
                }
            }
            detailItems(state, result).forEach { DetailRow(it) }

            // Folded on a first run. The summary above answers the question the
            // photographer came with; the derivation is here for the one who wants to
            // check it, and unfolded it is long enough to bury the credits under a scroll.
            SectionHeader(
                "The math",
                expanded = state.showMath,
                onToggle = {
                    state.showMath = !state.showMath
                    state.persist()
                },
            )
            if (state.showMath) {
                workingItems(state, result).forEach { DetailRow(it) }
            }

            creditLines().forEach { DetailRow(it) }
        }
    }
}

/**
 * A titled division of the panel: the title, and a rule under it.
 *
 * [expanded] is null for a heading that is only a heading. Given a value it grows a
 * chevron and the whole header becomes the target that opens and closes the section,
 * rather than a small icon the finger has to find.
 */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle == null) Modifier else Modifier.clickable(onClick = onToggle))
            .padding(top = 22.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Palette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (expanded != null) {
                // A chevron alone leaves it to be guessed whether it opens something or
                // scrolls somewhere. The word says which, and looking like a link says
                // that the whole header can be pressed.
                Text(
                    if (expanded) "hide" else "show",
                    color = Palette.Link,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Palette.Link,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(1.dp)
                .background(Palette.PanelBorder)
        )
    }
}

/** One line of the panel, whichever kind it is. */
@Composable
private fun DetailRow(item: Detail) {
    when (item) {
        is Detail.Blank -> Box(Modifier.padding(vertical = 5.dp))
        is Detail.Section -> SectionHeader(item.title)
        is Detail.Note -> Row(Modifier.fillMaxWidth()) {
            Text(
                math { sup(item.marker) },
                color = Palette.Text,
                fontSize = 15.sp,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                item.text,
                color = Palette.Text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(start = 4.dp).weight(1f).alignByBaseline(),
            )
        }

        is Detail.Line -> Beside(item.legend) {
            Text(item.text, color = Palette.Text, fontSize = 15.sp, lineHeight = 21.sp)
        }

        is Detail.Ratio -> Beside(item.legend) { EquationRow(item) }
        is Detail.Root -> Beside(item.legend) { RootRow(item) }
    }
}

/** One entry of a formula's legend: the symbol, and what it stands for. */
private data class Gloss(val symbol: AnnotatedString, val meaning: String)

private fun gloss(symbol: String, meaning: String) = Gloss(AnnotatedString(symbol), meaning)

/** For the two blurs, whose symbol carries a subscript. */
private fun glossBlur(which: String, meaning: String) =
    Gloss(buildAnnotatedString { append("B"); sub(which) }, meaning)

/** One line of the panel: prose, a gap, or an equation with a fraction in it. */
private sealed interface Detail {
    object Blank : Detail

    /** A titled division of the panel, set off by space above and a rule below. */
    data class Section(val title: String) : Detail

    /**
     * A footnote hung under the line that carries its marker.
     *
     * The marker keeps the left margin, level with the label it refers to, and the note
     * is set in a column of its own beside it. A second line then lands under the first
     * word of the note rather than under the marker or back at the margin, which is what
     * makes it read as one sentence continuing instead of a new remark starting.
     */
    data class Note(val marker: String, val text: String) : Detail
    data class Line(
        val text: AnnotatedString,
        val legend: List<Gloss> = emptyList(),
    ) : Detail

    /**
     * [lead] fraction [tail] — the parts either side of a division written as one.
     *
     * A fraction is the one piece of mathematical notation that cannot be faked with
     * characters on a line: written inline it needs brackets round the whole denominator,
     * and those brackets are exactly what makes an equation hard to read at a glance.
     * Stacked, the grouping is the layout and the brackets can go.
     */
    data class Ratio(
        val lead: AnnotatedString,
        val numerator: AnnotatedString,
        val denominator: AnnotatedString,
        val tail: AnnotatedString = AnnotatedString(""),
        val legend: List<Gloss> = emptyList(),
    ) : Detail

    /**
     * A root with the bar drawn over what it covers.
     *
     * The bar is the half of the symbol that says how far the root reaches. Without it
     * the sign has to lean on brackets, which is the same compromise the fraction made.
     */
    data class Root(
        val lead: AnnotatedString,
        val inside: AnnotatedString,
        val tail: AnnotatedString = AnnotatedString(""),
        val legend: List<Gloss> = emptyList(),
    ) : Detail
}

/**
 * A formula with a legend to its right: one line per symbol, symbol then meaning.
 *
 * A legend rather than a sentence. Prose beside an equation has to name the symbols in
 * whatever order the grammar allows and pad them out with words like "the ... of the ...",
 * which is exactly the reading the equation was meant to save. A column of symbols can be
 * scanned for the one that is puzzling.
 */
@Composable
private fun Beside(legend: List<Gloss>, formula: @Composable () -> Unit) {
    if (legend.isEmpty()) {
        formula()
        return
    }
    Row(
        // Intrinsic height so the rule between the two columns can span exactly as far
        // as the taller of them, rather than being given an arbitrary length.
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        formula()
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxHeight()
                .width(1.dp)
                .background(Palette.PanelBorder)
        )
        Column(Modifier.weight(1f)) {
            legend.forEach { entry ->
                // By baseline, not by the top of the box: the meaning sets a lineHeight
                // and the symbol does not, and Compose trims the leading above a first
                // line, so tops that agree would leave the two sitting at different
                // heights. Baselines are what the eye reads as one line anyway.
                Row {
                    Text(
                        entry.symbol,
                        color = Palette.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(22.dp).alignByBaseline(),
                    )
                    Text(
                        entry.meaning,
                        color = Palette.AxisName,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EquationRow(item: Detail.Ratio) {
    Row(
        Modifier.padding(vertical = 2.dp),
        // The lead and tail sit against the fraction's bar, where the equals sign of a
        // handwritten equation would be.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.lead.isNotEmpty()) {
            Text(item.lead, color = Palette.Text, fontSize = 15.sp)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Intrinsic width so the rule spans the wider of the two rows exactly, the
            // way a drawn fraction bar does.
            modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 4.dp),
        ) {
            Text(item.numerator, color = Palette.Text, fontSize = 15.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .height(1.dp)
                    .background(Palette.Text)
            )
            Text(item.denominator, color = Palette.Text, fontSize = 15.sp)
        }
        if (item.tail.isNotEmpty()) {
            Text(item.tail, color = Palette.Text, fontSize = 15.sp)
        }
    }
}

@Composable
private fun RootRow(item: Detail.Root) {
    val arm = 13.dp
    val stroke = 1.4.dp
    Row(
        Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.lead.isNotEmpty()) {
            Text(item.lead, color = Palette.Text, fontSize = 15.sp)
        }
        // Sign and vinculum are one stroke, drawn rather than typed. A glyph beside a
        // rule leaves a gap at the join no amount of padding closes, because the arm of
        // the character stops wherever its designer put it.
        Box {
            Text(
                item.inside,
                color = Palette.Text,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = arm + 5.dp, top = 5.dp, end = 5.dp),
            )
            Canvas(Modifier.matchParentSize()) {
                val w = arm.toPx()
                val t = stroke.toPx()
                val h = size.height
                drawPath(
                    Path().apply {
                        moveTo(0f, h * 0.58f)
                        lineTo(w * 0.30f, h * 0.66f)
                        lineTo(w * 0.58f, h - t)
                        lineTo(w * 0.88f, t)
                        lineTo(size.width, t)
                    },
                    color = Palette.Text,
                    style = Stroke(width = t, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        if (item.tail.isNotEmpty()) {
            Text(item.tail, color = Palette.Text, fontSize = 15.sp)
        }
    }
}

// ---- Setting the equations ------------------------------------------------------------

private fun AnnotatedString.Builder.sup(text: String) {
    withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 10.sp)) {
        append(text)
    }
}

private fun AnnotatedString.Builder.sub(text: String) {
    withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 10.sp)) {
        append(text)
    }
}

private fun AnnotatedString.Builder.bold(text: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
}

/** B with its subscript. The two blurs are told apart by it, so it has to be legible. */
private fun AnnotatedString.Builder.blur(which: String) {
    append("B")
    sub(which)
}

private fun plain(text: String) = AnnotatedString(text)

private fun math(build: AnnotatedString.Builder.() -> Unit) = buildAnnotatedString(build)

private fun detailItems(state: DofState, result: DofResult): List<Detail> {
    val units = state.units
    val cam = state.camera
    val out = ArrayList<Detail>()

    fun line(text: String) {
        out += if (text.isEmpty()) Detail.Blank else Detail.Line(plain(text))
    }

    line(
        "Hyperfocal: " +
            if (result.hyperfocal.isFinite()) formatDistance(result.hyperfocal, units) else "∞"
    )
    line("")

    val front = result.front
    val back = result.back
    val range = result.range
    if (front != null && front.isFinite()) line("Front: ${formatDistance(front, units)}")
    if (back != null && back.isFinite()) line("Back: ${formatDistance(back, units)}")
    if (range != null) {
        line("Range: " + if (range.isFinite()) formatDistance(range, units) else "∞")
    }
    result.focusPercent?.let { line("Subject: ${formatSig(it, 2)}% behind front") }
    if (result.near == null) {
        line("No distance is critically sharp at f/${formatSig(result.markedF)} —")
        line("diffraction blur alone exceeds the circle of confusion.")
    }
    line("")

    line("Blur at subject: ${formatSig(result.blurAtSubject, 3)}")
    line("  (diffraction only — focus blur is zero there)")
    line("")

    val m = state.magnification
    if (m > 0.0) {
        line(
            if (m >= 1.0) {
                "Magnification: ${formatSig(m, 4)} : 1"
            } else {
                "Magnification: 1 : ${formatSig(1.0 / m, 4)}"
            }
        )
        line("")
    }

    // Field of view at the subject: the frame projected out to that distance.
    val l = result.effectiveFocal
    if (result.subject.isFinite() && l > 0) {
        val scale = result.subject / l
        val w = cam.frameWidthMm * scale
        val h = cam.frameHeightMm * scale
        val diag = hypot(cam.frameWidthMm, cam.frameHeightMm) * scale
        fun angle(dim: Double) = 2.0 * atan(dim / (2.0 * l)) * 180.0 / PI
        line("Field of View @ ${formatDistance(result.subject, units)}")
        line("  Width: ${formatDistance(w, units)} (${formatSig(angle(cam.frameWidthMm), 3)}°)")
        line("  Height: ${formatDistance(h, units)} (${formatSig(angle(cam.frameHeightMm), 3)}°)")
        line(
            "  Diag: ${formatDistance(diag, units)} " +
                "(${formatSig(angle(hypot(cam.frameWidthMm, cam.frameHeightMm)), 3)}°)"
        )
        line("")
    }

    line("Camera: ${cam.name}")
    line("Pixel pitch: ${formatSig(cam.pixelPitch, 3)} mm")
    line("Crop factor: ${formatSig(cam.cropFactor, 3)}X")
    line("")
    line("Viewed on: ${state.target.name}")
    line("  ${state.target.describe(units)}")
    if (state.target.kind != TargetKind.PIXELS && state.target.kind != TargetKind.CUSTOM) {
        line("  Enlarged ${formatSig(state.target.magnification(cam.frameWidthMm), 3)}X")
        line("  Smallest visible detail: ${formatSig(state.target.detailShown, 3)} mm shown")
    }
    // Set off from the viewing target above it: these last two are what the target and
    // the camera together come to, not two more of its properties.
    line("")
    line("Circle of Confusion (CoC): ${formatSig(state.coc, 3)} mm")
    // Marked rather than explained in place: the figure belongs in this column of
    // one-line facts, and the sentence that says what it means would not fit there.
    out += Detail.Line(
        math {
            append("Diffraction limit")
            sup("*")
            append(": f/${formatSig(state.diffractionLimit)}")
        }
    )
    out += Detail.Note(
        "*",
        "The aperture where diffraction alone exceeds the acceptable blur",
    )
    if (state.teleconverter.factor != 1.0) {
        line(
            "Teleconverter: ${state.teleconverter.label}X " +
                "(${formatSig(result.effectiveFocal)} mm at f/${formatSig(result.effectiveF)})"
        )
    }

    return out
}

/**
 * Whose work this is.
 *
 * The equations were taken out of the middle of the derivation, where a citation
 * interrupted the arithmetic it was meant to support. That is a reason to move an
 * acknowledgement, not to drop one: both the optics and the shape of this app are
 * somebody's work, and saying so belongs at the foot of the page that shows the working.
 */
private fun creditLines(): List<Detail> = listOf(
    Detail.Section("Credits"),
    Detail.Line(
        AnnotatedString(
            "Based on DoF 4.0 for Windows by Jonathan M. Sachs, Digital Light & Color."
        )
    ),
    Detail.Blank,
    Detail.Line(
        AnnotatedString(
            "The depth of field equations, including the treatment of diffraction, " +
                "follow his reference manual, and the interface owes his design as much: " +
                "the parallel scales, each end of the depth of field and the aperture " +
                "driving one another, and this panel."
        )
    ),
    Detail.Blank,
    Detail.Line(
        AnnotatedString(
            "An independent implementation, not affiliated with or endorsed by the author."
        )
    ),
)

/**
 * The arithmetic, written out with this shot's own numbers in it.
 *
 * Depth of field calculators disagree with each other, sometimes by a factor of several,
 * and a reader who cannot see the working has no way to tell which one to believe. Every
 * line here is a step someone can check by hand or against another calculator, and the two
 * that usually explain a disagreement — what was taken as the circle of confusion, and
 * whether diffraction was counted at all — are the ones stated most plainly.
 *
 * Millimetres throughout, because that is what the optics are in. The distances are
 * repeated in the units in use where they are worth reading twice.
 */
private fun workingItems(state: DofState, result: DofResult): List<Detail> {
    val units = state.units
    val cam = state.camera
    val target = state.target
    val out = ArrayList<Detail>()

    fun line(text: String) {
        out += if (text.isEmpty()) Detail.Blank else Detail.Line(plain(text))
    }

    fun rich(build: AnnotatedString.Builder.() -> Unit) {
        out += Detail.Line(math(build))
    }

    fun ratio(
        lead: String,
        numerator: AnnotatedString,
        denominator: AnnotatedString,
        tail: String = "",
    ) {
        out += Detail.Ratio(plain(lead), numerator, denominator, plain(tail))
    }

    fun mm(v: Double, digits: Int = 4) = formatSig(v, digits)


    // Numbered as they are printed rather than in advance: with diffraction switched off
    // one of the steps has nothing to say, and a list that skips from 2 to 4 reads as
    // though something went missing.
    var step = 0
    fun heading(text: String) {
        out += Detail.Line(math { bold("${++step}. $text") })
    }

    // ---- the circle of confusion ------------------------------------------------------
    heading("Circle of confusion")
    when (target.kind) {
        TargetKind.CUSTOM -> line("   c = ${mm(target.customCoc)} mm, stated outright")

        TargetKind.PIXELS -> {
            out += Detail.Ratio(
                lead = plain("   p = "), numerator = plain("w"), denominator = plain("N"),
                legend = listOf(
                    gloss("p", "the pixel pitch"),
                    gloss("w", "the sensor width, mm"),
                    gloss("N", "pixels across the sensor"),
                ),
            )
            ratio(
                "     = ", plain(mm(cam.frameWidthMm)), plain("${cam.frameWidthPx}"),
                " = ${mm(cam.pixelPitch)} mm",
            )
            line("")
            out += Detail.Line(
                math { append("   c = k · p") },
                legend = listOf(
                    gloss("c", "the circle of confusion"),
                    gloss("k", "the allowable blur, as set on the viewing target"),
                ),
            )
            line("     = ${mm(target.allowableBlur, 3)} · ${mm(cam.pixelPitch)}" +
                " = ${mm(state.coc)} mm")
        }

        else -> {
            val eye = target.viewingDistanceMm * target.visualResolution * (PI / 180.0 / 60.0)
            val mag = target.magnification(cam.frameWidthMm)
            val atSensor = target.detailAtSensor(cam.frameWidthMm)
            if (target.kind == TargetKind.SCREEN) {
                out += Detail.Line(
                    math { append("   d = max(D · θ, W / N)") },
                    legend = listOf(
                        gloss("d", "the finest detail the screen shows, whichever of " +
                            "the two is coarser"),
                        gloss("D", "the viewing distance"),
                        gloss("θ", "the angle the eye can separate, as set on the viewing target"),
                        gloss("W", "the screen width"),
                        gloss("N", "pixels across the screen"),
                    ),
                )
                line("     = max(${mm(eye)}, " +
                    "${mm(target.widthMm / target.pixelsAcross.coerceAtLeast(1))})")
                line("     = ${mm(target.detailShown)} mm")
            } else {
                out += Detail.Line(
                    math { append("   d = D · θ") },
                    legend = listOf(
                        gloss("d", "the finest detail the eye resolves on the print"),
                        gloss("D", "the viewing distance"),
                        gloss("θ", "the angle the eye can separate, as set on the viewing target"),
                    ),
                )
                line("     = ${mm(target.viewingDistanceMm)} · " +
                    "${mm(target.visualResolution, 2)}′ = ${mm(eye)} mm")
            }
            line("")
            out += Detail.Ratio(
                lead = plain("   M = "), numerator = plain("W"), denominator = plain("w"),
                legend = listOf(
                    gloss("M", "the enlargement"),
                    gloss("W", "the width the picture is shown at"),
                    gloss("w", "the sensor width"),
                ),
            )
            ratio(
                "     = ", plain(mm(target.widthMm)), plain(mm(cam.frameWidthMm)),
                " = ${mm(mag, 4)}",
            )
            line("")
            out += Detail.Ratio(
                lead = plain("   p = "), numerator = plain("w"), denominator = plain("N"),
                legend = listOf(
                    gloss("p", "the pixel pitch"),
                    gloss("N", "pixels across the sensor"),
                ),
            )
            ratio(
                "     = ", plain(mm(cam.frameWidthMm)), plain("${cam.frameWidthPx}"),
                " = ${mm(cam.pixelPitch)} mm",
            )
            line("")
            out += Detail.Ratio(
                lead = plain("   c = k · max( "),
                numerator = plain("d"),
                denominator = plain("M"),
                tail = plain(" , p )"),
                legend = listOf(
                    gloss("c", "the circle of confusion: nothing finer than the coarser " +
                        "of d/M and p can be seen"),
                    gloss("k", "the allowable blur, as set on the viewing target"),
                ),
            )
            out += Detail.Ratio(
                lead = plain("     = ${mm(target.allowableBlur, 3)} · max( "),
                numerator = plain(mm(target.detailShown)),
                denominator = plain(mm(mag, 4)),
                tail = plain(" , ${mm(cam.pixelPitch)} )"),
            )
            line("     = ${mm(target.allowableBlur, 3)} · " +
                "${mm(maxOf(atSensor, cam.pixelPitch))}")
            line("     = ${mm(state.coc)} mm")
        }
    }
    line("")

    // ---- diffraction, and what is left for focus --------------------------------------
    val f = result.effectiveF
    val bd = Dof.diffractionBlur(f, state.wavelengthNm)
    val bf = Dof.focusBlurBudget(f, state.coc, 1.0, state.wavelengthNm)
    if (state.ignoreDiffraction) {
        heading("Diffraction")
        line("   ignored, by the setting: the whole of c is")
        line("   left for focus error")
        rich {
            append("   ")
            blur("f")
            append(" = c = ${mm(state.coc)} mm")
        }
        line("   (most other calculators do the same)")
    } else {
        heading("Diffraction")
        out += Detail.Line(
            math { append("   "); blur("d"); append(" = 2.44 · λ · f") },
            legend = listOf(
                glossBlur("d", "the diffraction blur: the Airy disc, out to its first " +
                    "dark ring"),
                gloss("λ", "the wavelength of the light"),
                gloss("f", "the f number"),
            ),
        )
        line("     = 2.44 · ${mm(state.wavelengthNm * 1e-6, 3)} · ${formatSig(f)}")
        line("     = ${mm(bd)} mm")
        line("")

        heading("What is left for focus error")
        out += Detail.Root(
            lead = math { append("   "); blur("f"); append(" = ") },
            inside = math {
                append("c"); sup("2"); append(" − "); blur("d"); sup("2")
            },
            legend = listOf(
                glossBlur("f", "the focus blur the limits may spend"),
                gloss("", "the two blurs add in quadrature, so this is what is left " +
                    "once diffraction has taken its cut"),
            ),
        )
        if (bf == null) {
            line("     diffraction alone exceeds c: nothing is")
            line("     critically sharp and there are no limits")
        } else {
            out += Detail.Root(
                lead = plain("     = "),
                inside = math {
                    append(mm(state.coc)); sup("2")
                    append(" − ${mm(bd)}"); sup("2")
                },
            )
            line("     = ${mm(bf)} mm  (${formatSig(100.0 * bf / state.coc, 3)}% of c)")
        }
    }
    line("")

    // ---- the distances ----------------------------------------------------------------
    val budget = if (state.ignoreDiffraction) state.coc else bf
    if (budget != null) {
        val l = result.effectiveFocal
        val a = result.subject
        heading("Hyperfocal")
        out += Detail.Ratio(
            lead = plain("   H = "),
            numerator = math { append("L"); sup("2") },
            denominator = math { append("f · "); blur("f") },
            tail = plain(" + L"),
            legend = listOf(
                gloss("H", "the hyperfocal distance: focus there and everything from " +
                    "half of it to infinity is acceptably sharp"),
                gloss("L", "the focal length"),
            ),
        )
        ratio(
            "     = ",
            plain(mm(l * l, 5)),
            plain("${formatSig(f)} · ${mm(budget)}"),
            " + ${formatSig(l)}",
        )
        line("     = ${mm(result.hyperfocal, 5)} mm" +
            if (result.hyperfocal.isFinite()) {
                "  = ${formatDistance(result.hyperfocal, units)}"
            } else {
                ""
            })
        line("")

        heading("The limits, focused at ${formatDistance(a, units)}")
        val term = f * budget * (a - l)
        out += Detail.Ratio(
            lead = plain("  near = "),
            numerator = math { append("A · L"); sup("2") },
            denominator = math {
                append("L"); sup("2"); append(" + f · "); blur("f"); append(" · (A − L)")
            },
            legend = listOf(
                gloss("A", "the distance focused on"),
                gloss("", "the near limit takes the plus sign, the far limit the minus"),
            ),
        )
        ratio(
            "       = ",
            plain(mm(a * l * l, 6)),
            plain("${mm(l * l, 5)} + ${mm(term, 5)}"),
            result.near?.let { "  = ${formatDistance(it, units)}" } ?: "",
        )
        out += Detail.Ratio(
            lead = plain("   far = "),
            numerator = math { append("A · L"); sup("2") },
            denominator = math {
                append("L"); sup("2"); append(" − f · "); blur("f"); append(" · (A − L)")
            },
        )
        val far = result.far
        ratio(
            "       = ",
            plain(mm(a * l * l, 6)),
            plain("${mm(l * l, 5)} − ${mm(term, 5)}"),
            if (far != null && far.isFinite()) "  = ${formatDistance(far, units)}" else "  = ∞",
        )
    }

    // ---- what the letters mean --------------------------------------------------------
    return out
}
