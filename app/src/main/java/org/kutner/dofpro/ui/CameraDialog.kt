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
        onAdd = {
            // Copying the current camera saves re-typing a sensor that is probably close.
            state.cameras.add(state.camera.copy(name = "New camera"))
            state.cameras.lastIndex
        },
        onRemove = { gone ->
            state.removeCameras(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { index, close -> CameraEditor(state, index, close) },
    )
}

@Composable
private fun CameraEditor(state: DofState, index: Int, onClose: () -> Unit) {
    val camera = state.cameras[index]

    fun update(block: Camera.() -> Camera) {
        state.cameras[index] = state.cameras[index].block()
    }

    EquipmentEditor(
        title = camera.name.ifBlank { "Camera" },
        canDelete = state.cameras.size > 1,
        onDelete = {
            state.cameras.removeAt(index)
            state.cameraIndex = state.cameraIndex.coerceAtMost(state.cameras.lastIndex)
        },
        onClose = onClose,
    ) {
        // The search comes first, above everything it fills in, because it is the way
        // most of this form gets completed — and a rule under it says plainly that what
        // follows is the same information by hand.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
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
            HorizontalDivider(Modifier.padding(top = 16.dp))
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
            Picker(
                label = "Frame size preset",
                value = Camera.FRAME_PRESETS.firstOrNull {
                    it.second == camera.frameWidthMm && it.third == camera.frameHeightMm
                }?.first ?: "Custom",
                options = Camera.FRAME_PRESETS.map { it.first },
            ) { i ->
                val preset = Camera.FRAME_PRESETS[i]
                update { copy(frameWidthMm = preset.second, frameHeightMm = preset.third) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Frame width", camera.frameWidthMm, index, Modifier.weight(1f), "mm") {
                    update { copy(frameWidthMm = it) }
                }
                NumberField("Frame height", camera.frameHeightMm, index, Modifier.weight(1f), "mm") {
                    update { copy(frameHeightMm = it) }
                }
            }

            if (camera.type == CameraType.DIGITAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        "Width", camera.frameWidthPx.toDouble(), index, Modifier.weight(1f), "pixels",
                    ) { update { copy(frameWidthPx = it.toInt().coerceAtLeast(1)) } }
                    NumberField(
                        "Height", camera.frameHeightPx.toDouble(), index, Modifier.weight(1f), "pixels",
                    ) { update { copy(frameHeightPx = it.toInt().coerceAtLeast(1)) } }
                }
            } else {
                NumberField(
                    "Film resolution", camera.filmResolution, index, Modifier.fillMaxWidth(),
                    "line pairs per mm",
                ) { update { copy(filmResolution = it) } }
            }
        }

        SettingsGroup("Sensor detail") {
            NumberField(
                "Wavelength", camera.wavelengthNm, index, Modifier.fillMaxWidth(),
                "nm; 550 is green light, 900-1000 for infrared",
            ) { update { copy(wavelengthNm = it) } }
        }

        // What the camera alone settles. Whether any of it is *visible* depends on the
        // viewing target, which is a separate list and a separate question.
        OutputCard(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
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
