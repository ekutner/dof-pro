package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.TargetKind
import org.kutner.dofpro.model.UnitSystem
import org.kutner.dofpro.model.ViewingTarget
import org.kutner.dofpro.model.circleOfConfusion
import org.kutner.dofpro.model.formatSig

/**
 * Where the pictures will be looked at.
 *
 * Its own collection, separate from the cameras, because the two answer different
 * questions. A camera settles how much detail was recorded; a viewing target settles how
 * much of it can be seen. The same body shooting the same frame is critically sharp on a
 * phone and visibly soft blown up across a wall, and one photographer uses both, so tying
 * the viewing conditions to the camera meant duplicating the camera to change them.
 */
@Composable
fun TargetDialog(state: DofState, onDismiss: () -> Unit) {
    EquipmentManager(
        title = "Viewing",
        items = state.targets,
        selectedIndex = state.targetIndex,
        nameOf = { it.listLabel(state.units) },
        summaryOf = null,
        addLabel = "Add target",
        onSelect = { state.targetIndex = it },
        newItem = { state.target.copy(name = "New target") },
        onSave = { at, target ->
            if (at == null) state.targets.add(target) else state.targets[at] = target
            state.persist()
        },
        onRemove = { gone ->
            state.removeTargets(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { item, canDelete, onSave, onDelete, onCancel ->
            TargetEditor(state, item, canDelete, onSave, onDelete, onCancel)
        },
    )
}

@Composable
private fun TargetEditor(
    state: DofState,
    initial: ViewingTarget,
    canDelete: Boolean,
    onSave: (ViewingTarget) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var target by remember { mutableStateOf(initial) }

    fun update(block: ViewingTarget.() -> ViewingTarget) {
        target = target.block()
    }

    // A print is 71 centimetres wide and seen from 50, not 710 millimetres from 500.
    // The optics are in mm and stay that way; only the two fields anyone actually types
    // into are converted, and only to the unit the rest of the app is already reading in.
    val big = if (state.units == UnitSystem.METRIC) 10.0 else 25.4
    val bigLabel = if (state.units == UnitSystem.METRIC) "cm" else "in"

    EquipmentEditor(
        title = target.name.ifBlank { "Viewing target" },
        canDelete = canDelete,
        onDelete = onDelete,
        onCancel = onCancel,
        onConfirm = { onSave(target) },
    ) {
        SettingsGroup("Target") {
            OutlinedTextField(
                value = target.name,
                onValueChange = { v -> update { copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KindPicker(target.kind) { k -> update { copy(kind = k) } }
        }

        if (target.kind == TargetKind.PRINT || target.kind == TargetKind.SCREEN) {
            SettingsGroup(if (target.kind == TargetKind.PRINT) "The print" else "The screen") {
                NumberField(
                    (if (target.kind == TargetKind.PRINT) "Print width " else "Screen width ") +
                        bigLabel,
                    target.widthMm / big, Unit, Modifier.fillMaxWidth(),
                ) { update { copy(widthMm = it * big) } }

                if (target.kind == TargetKind.SCREEN) {
                    NumberField(
                        "Pixels across", target.pixelsAcross.toDouble(), Unit,
                        Modifier.fillMaxWidth(),
                    ) { update { copy(pixelsAcross = it.toInt().coerceAtLeast(1)) } }
                }
            }
        }

        if (target.kind != TargetKind.CUSTOM) {
            SettingsGroup("The viewer") {
                if (target.kind != TargetKind.PIXELS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            "Viewed from $bigLabel", target.viewingDistanceMm / big, Unit,
                            Modifier.weight(1f),
                        ) { update { copy(viewingDistanceMm = it * big) } }
                        NumberField(
                            "Eyesight arcmin", target.visualResolution, Unit,
                            Modifier.weight(1f),
                        ) { update { copy(visualResolution = it) } }
                    }
                }
                NumberField(
                    "Allowable blur", target.allowableBlur, Unit, Modifier.fillMaxWidth(),
                    help = "How many just-resolvable details of blur still count as " +
                        "sharp. 1.0 is the finest detail this target can show at all; " +
                        "2.0, the usual value, accepts twice that. Raising it widens the " +
                        "depth of field and pushes the diffraction limit further down " +
                        "the scale, in exchange for a slightly softer result.",
                ) { update { copy(allowableBlur = it) } }
            }
        } else {
            SettingsGroup("The figure") {
                NumberField(
                    "Circle of confusion mm", target.customCoc, Unit,
                    Modifier.fillMaxWidth(),
                ) { update { copy(customCoc = it) } }
            }
        }

        // Shown against the camera in use, because a target on its own has no circle of
        // confusion — the whole point of the split is that it takes both.
        val camera = state.camera
        val rows = buildList {
            if (target.kind == TargetKind.SCREEN || target.kind == TargetKind.PRINT) {
                add("Enlarged" to "${formatSig(target.magnification(camera.frameWidthMm), 3)}X")
                add("Smallest visible detail" to "${formatSig(target.detailShown, 3)} mm shown")
                if (target.kind == TargetKind.SCREEN) {
                    val eyeLimited = target.viewingDistanceMm * (Math.PI / 180.0 / 60.0) *
                        target.visualResolution >= target.widthMm / target.pixelsAcross.coerceAtLeast(1)
                    add("Limited by" to if (eyeLimited) "the eye, at this distance" else "the screen's own pixels")
                }
            }
            add(
                "With ${camera.name}" to
                    "CoC ${formatSig(circleOfConfusion(camera, target), 3)} mm",
            )
        }
        OutputCard(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 12.dp),
            title = "Computed",
            rows = rows,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindPicker(value: TargetKind, onSelect: (TargetKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Kind") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TargetKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.label) },
                    onClick = {
                        onSelect(kind)
                        expanded = false
                    },
                )
            }
        }
    }
}
