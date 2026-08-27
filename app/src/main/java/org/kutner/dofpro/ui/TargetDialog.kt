package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
        onAdd = {
            state.targets.add(state.target.copy(name = "New target"))
            state.targets.lastIndex
        },
        onRemove = { gone ->
            state.removeTargets(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { index, close -> TargetEditor(state, index, close) },
    )
}

@Composable
private fun TargetEditor(state: DofState, index: Int, onClose: () -> Unit) {
    val target = state.targets[index]

    fun update(block: ViewingTarget.() -> ViewingTarget) {
        state.targets[index] = state.targets[index].block()
    }

    EquipmentEditor(
        title = target.name.ifBlank { "Viewing target" },
        canDelete = state.targets.size > 1,
        onDelete = {
            state.targets.removeAt(index)
            state.targetIndex = state.targetIndex.coerceAtMost(state.targets.lastIndex)
        },
        onClose = onClose,
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
                    "Width", target.widthMm, index, Modifier.fillMaxWidth(),
                    if (target.kind == TargetKind.PRINT) {
                        "mm across the print"
                    } else {
                        "mm across the screen — screens are sold by the diagonal, this is the width"
                    },
                ) { update { copy(widthMm = it) } }

                if (target.kind == TargetKind.SCREEN) {
                    NumberField(
                        "Resolution", target.pixelsAcross.toDouble(), index,
                        Modifier.fillMaxWidth(), "pixels across",
                    ) { update { copy(pixelsAcross = it.toInt().coerceAtLeast(1)) } }
                }
            }
        }

        if (target.kind != TargetKind.CUSTOM) {
            SettingsGroup("The viewer") {
                if (target.kind != TargetKind.PIXELS) {
                    NumberField(
                        "Viewing distance", target.viewingDistanceMm, index,
                        Modifier.fillMaxWidth(), "mm away",
                    ) { update { copy(viewingDistanceMm = it) } }
                    NumberField(
                        "Visual resolution", target.visualResolution, index,
                        Modifier.fillMaxWidth(),
                        "arc minutes; about 1.0 for normal eyesight",
                    ) { update { copy(visualResolution = it) } }
                }
                NumberField(
                    "Allowable blur", target.allowableBlur, index, Modifier.fillMaxWidth(),
                    "how many just-resolvable details of blur still count as sharp. " +
                        "2.0 is the usual value.",
                ) { update { copy(allowableBlur = it) } }
            }
        } else {
            SettingsGroup("The figure") {
                NumberField(
                    "Circle of confusion", target.customCoc, index, Modifier.fillMaxWidth(), "mm",
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
