package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kutner.dofpro.model.Camera
import org.kutner.dofpro.model.CameraType
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.FocalMode
import org.kutner.dofpro.model.circleOfConfusion
import org.kutner.dofpro.model.formatSig

/**
 * The camera collection. Every field here feeds one number — the circle of confusion —
 * which is what choosing a camera really amounts to.
 */
@Composable
fun CameraDialog(state: DofState, onDismiss: () -> Unit) {
    EquipmentManager(
        title = "Cameras",
        items = state.cameras,
        selectedIndex = state.cameraIndex,
        nameOf = { it.name },
        // The name and nothing else. A camera is chosen by the name its owner gave it, and
        // the circle of confusion it works out to is a consequence rather than a way of
        // telling one from another — it belongs in the editor, where it can be changed.
        summaryOf = null,
        addLabel = "Add camera",
        onSelect = { state.cameraIndex = it },
        // Copying the current camera saves re-typing a sensor that is probably close.
        // Nothing is added here: it becomes a camera only if the form is confirmed.
        newItem = { state.camera.copy(name = "New camera") },
        onSave = { at, camera ->
            if (at == null) state.cameras.add(camera) else state.cameras[at] = camera
            state.persist()
        },
        onRemove = { gone ->
            state.removeCameras(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { item, canDelete, onSave, onDelete, onCancel ->
            CameraEditor(state, item, canDelete, onSave, onDelete, onCancel)
        },
    )
}

@Composable
private fun CameraEditor(
    state: DofState,
    initial: Camera,
    canDelete: Boolean,
    onSave: (Camera) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    // A copy, edited here and handed back only if the form is confirmed. The collection
    // is not touched until then, which is what makes Cancel, Back and the X all mean the
    // same thing, and what keeps an abandoned new camera from ever existing.
    var camera by remember { mutableStateOf(initial) }

    fun update(block: Camera.() -> Camera) {
        camera = camera.block()
    }

    EquipmentEditor(
        title = camera.name.ifBlank { "Camera" },
        canDelete = canDelete,
        onDelete = onDelete,
        onCancel = onCancel,
        onConfirm = { onSave(camera) },
    ) {
        // The search comes first, above everything it fills in, because it is the way
        // most of this form gets completed — and a rule under it says plainly that what
        // follows is the same information by hand.
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp)) {
            CameraSearch { specs ->
                update {
                    copy(
                        name = specs.name,
                        frameWidthMm = specs.widthMm ?: frameWidthMm,
                        frameHeightMm = specs.heightMm ?: frameHeightMm,
                        frameWidthPx = specs.widthPx ?: frameWidthPx,
                        frameHeightPx = specs.heightPx ?: frameHeightPx,
                        type = if (specs.hasPixels) CameraType.DIGITAL else type,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 10.dp))
        }

        SettingsGroup("Camera") {
            OutlinedTextField(
                value = camera.name,
                onValueChange = { v -> update { copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Type first: film or digital decides what the rest of the form even asks
            // for — pixels or line pairs — so it belongs above the questions it changes.
            Picker(
                label = "Type",
                value = camera.type.label,
                options = CameraType.entries.map { it.label },
            ) { i -> update { copy(type = CameraType.entries[i]) } }

            Picker(
                label = "Focal length entry",
                value = camera.focalMode.label,
                options = FocalMode.entries.map { it.label },
            ) { i -> update { copy(focalMode = FocalMode.entries[i]) } }
        }

        SettingsGroup("Sensor") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Width mm", camera.frameWidthMm, Unit, Modifier.weight(1f)) {
                    update { copy(frameWidthMm = it) }
                }
                NumberField("Height mm", camera.frameHeightMm, Unit, Modifier.weight(1f)) {
                    update { copy(frameHeightMm = it) }
                }
            }

            if (camera.type == CameraType.DIGITAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        "Width px", camera.frameWidthPx.toDouble(), Unit, Modifier.weight(1f),
                    ) { update { copy(frameWidthPx = it.toInt().coerceAtLeast(1)) } }
                    NumberField(
                        "Height px", camera.frameHeightPx.toDouble(), Unit, Modifier.weight(1f),
                    ) { update { copy(frameHeightPx = it.toInt().coerceAtLeast(1)) } }
                }
            } else {
                NumberField(
                    "Film resolution lp/mm", camera.filmResolution, Unit,
                    Modifier.fillMaxWidth(),
                ) { update { copy(filmResolution = it) } }
            }

            NumberField(
                "Wavelength nm", camera.wavelengthNm, Unit, Modifier.fillMaxWidth(),
                help = "The wavelength of light the diffraction calculation assumes. " +
                    "550 nm is green, the middle of what the eye sees, and is right for " +
                    "ordinary photography. Infrared work runs at 900-1000 nm, where " +
                    "diffraction blurs almost twice as much at the same aperture.",
            ) { update { copy(wavelengthNm = it) } }
        }

        // What the camera alone settles. Whether any of it is *visible* depends on the
        // viewing target, which is a separate list and a separate question.
        OutputCard(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 12.dp),
            title = "Computed",
            rows = listOf(
                "Pixel pitch" to "${formatSig(camera.pixelPitch, 3)} mm",
                "Crop factor" to "${formatSig(camera.cropFactor, 3)}X",
                "Megapixels" to formatSig(camera.megapixels, 3),
                "With ${state.target.name}" to
                    "CoC ${formatSig(circleOfConfusion(camera, state.target), 3)} mm",
            ),
        )
    }
}

/** A read-only field that drops a menu — Material's pattern for choosing from a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(i)
                        expanded = false
                    },
                )
            }
        }
    }
}
