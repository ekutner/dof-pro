package org.kutner.dofpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    "details",
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
            detailLines(state, result).forEach { line ->
                if (line.isEmpty()) {
                    Box(Modifier.padding(vertical = 5.dp))
                } else {
                    Text(line, color = Palette.Text, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

private fun detailLines(state: DofState, result: DofResult): List<String> {
    val units = state.units
    val cam = state.camera
    val out = ArrayList<String>()

    out += "Hyperfocal: " +
        if (result.hyperfocal.isFinite()) formatDistance(result.hyperfocal, units) else "∞"
    out += ""

    val front = result.front
    val back = result.back
    val range = result.range
    if (front != null && front.isFinite()) out += "Front: ${formatDistance(front, units)}"
    if (back != null && back.isFinite()) out += "Back: ${formatDistance(back, units)}"
    if (range != null) {
        out += "Range: " + if (range.isFinite()) formatDistance(range, units) else "∞"
    }
    result.focusPercent?.let { out += "Subject: ${formatSig(it, 2)}% behind front" }
    if (result.near == null) {
        out += "No distance is critically sharp at f/${formatSig(result.markedF)} —"
        out += "diffraction blur alone exceeds the circle of confusion."
    }
    out += ""

    out += "Blur at subject: ${formatSig(result.blurAtSubject, 3)}"
    out += "  (diffraction only — focus blur is zero there)"
    out += ""

    val m = state.magnification
    if (m > 0.0) {
        out += if (m >= 1.0) {
            "Magnification: ${formatSig(m, 4)} : 1"
        } else {
            "Magnification: 1 : ${formatSig(1.0 / m, 4)}"
        }
        out += ""
    }

    // Field of view at the subject: the frame projected out to that distance.
    val l = result.effectiveFocal
    if (result.subject.isFinite() && l > 0) {
        val scale = result.subject / l
        val w = cam.frameWidthMm * scale
        val h = cam.frameHeightMm * scale
        val diag = hypot(cam.frameWidthMm, cam.frameHeightMm) * scale
        fun angle(dim: Double) = 2.0 * atan(dim / (2.0 * l)) * 180.0 / PI
        out += "Field of View @ ${formatDistance(result.subject, units)}"
        out += "  Width: ${formatDistance(w, units)} (${formatSig(angle(cam.frameWidthMm), 3)}°)"
        out += "  Height: ${formatDistance(h, units)} (${formatSig(angle(cam.frameHeightMm), 3)}°)"
        out += "  Diag: ${formatDistance(diag, units)} " +
            "(${formatSig(angle(hypot(cam.frameWidthMm, cam.frameHeightMm)), 3)}°)"
        out += ""
    }

    out += "Camera: ${cam.name}"
    out += "Pixel pitch: ${formatSig(cam.pixelPitch, 3)} mm"
    out += "Crop factor: ${formatSig(cam.cropFactor, 3)}X"
    out += ""
    out += "Viewed on: ${state.target.name}"
    out += "  ${state.target.describe(units)}"
    if (state.target.kind != TargetKind.PIXELS && state.target.kind != TargetKind.CUSTOM) {
        out += "  Enlarged ${formatSig(state.target.magnification(cam.frameWidthMm), 3)}X"
        out += "  Smallest visible detail: ${formatSig(state.target.detailShown, 3)} mm shown"
    }
    out += "Circle of confusion: ${formatSig(state.coc, 3)} mm"
    out += "Diffraction limit: f/${formatSig(state.diffractionLimit)}"
    if (state.teleconverter.factor != 1.0) {
        out += "Teleconverter: ${state.teleconverter.label}X " +
            "(${formatSig(result.effectiveFocal)} mm at f/${formatSig(result.effectiveF)})"
    }
    return out
}
