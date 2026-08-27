package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.Lens

/** The lens collection: what you own, which one is on the camera, and a form behind each. */
@Composable
fun LensDialog(state: DofState, onDismiss: () -> Unit) {
    EquipmentManager(
        title = "Lenses",
        items = state.lenses,
        selectedIndex = state.lensIndex,
        nameOf = { it.name },
        summaryOf = { it.specification },
        addLabel = "Add lens",
        onSelect = { state.selectLens(it) },
        onAdd = {
            state.lenses.add(Lens.blank())
            state.lenses.lastIndex
        },
        onRemove = { gone ->
            state.removeLenses(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { index, close -> LensEditor(state, index, close) },
    )
}

@Composable
private fun LensEditor(state: DofState, index: Int, onClose: () -> Unit) {
    val lens = state.lenses[index]

    fun update(block: Lens.() -> Lens) {
        state.lenses[index] = state.lenses[index].block()
        if (index == state.lensIndex) state.applyLensLimits()
    }

    EquipmentEditor(
        title = lens.name.ifBlank { "Lens" },
        canDelete = state.lenses.size > 1,
        onDelete = {
            state.lenses.removeAt(index)
            state.selectLens(state.lensIndex.coerceAtMost(state.lenses.lastIndex))
        },
        onClose = onClose,
    ) {
        SettingsGroup("Lens") {
        OutlinedTextField(
            value = lens.name,
            onValueChange = { v -> update { copy(name = v) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Zoom lens",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = lens.isZoom,
                onCheckedChange = { zoom ->
                    // A prime collapses to one focal length; a zoom needs a second to
                    // open up a range.
                    update {
                        if (zoom) copy(maxFocal = minFocal * 2.0) else copy(maxFocal = minFocal)
                    }
                },
            )
        }

        }

        SettingsGroup("Focal length") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = if (lens.isZoom) "Focal length from" else "Focal length",
                value = lens.minFocal,
                resetKey = index,
                supporting = "mm",
                modifier = Modifier.weight(1f),
            ) { v ->
                update {
                    val lo = v.coerceIn(DofState.MIN_FOCAL, DofState.MAX_FOCAL)
                    copy(minFocal = lo, maxFocal = maxOf(maxFocal, lo))
                }
            }
            if (lens.isZoom) {
                NumberField(
                    label = "to",
                    value = lens.maxFocal,
                    resetKey = index,
                    supporting = "mm",
                    modifier = Modifier.weight(1f),
                ) { v ->
                    update { copy(maxFocal = v.coerceIn(minFocal, DofState.MAX_FOCAL)) }
                }
            }
        }

            SettingNote(
                if (lens.isZoom) {
                    "The focal length scale is drawn over exactly this range, the way a " +
                        "zoom barrel is engraved."
                } else {
                    "A prime has one focal length, so its scale becomes a read-out."
                }
            )
        }

        SettingsGroup("Aperture") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Widest aperture",
                value = lens.minFStop,
                resetKey = index,
                supporting = "f/ number",
                modifier = Modifier.weight(1f),
            ) { v ->
                update {
                    val wide = v.coerceIn(DofState.MIN_F, DofState.MAX_F)
                    copy(minFStop = wide, maxFStop = maxOf(maxFStop, wide))
                }
            }
            NumberField(
                label = "Narrowest",
                value = lens.maxFStop,
                resetKey = index,
                supporting = "f/ number",
                modifier = Modifier.weight(1f),
            ) { v ->
                update { copy(maxFStop = v.coerceIn(minFStop, DofState.MAX_F)) }
            }
        }

            SettingNote("Only the stops between these appear on the aperture scale.")
        }

        OutputCard(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            title = "This lens",
            rows = listOf(
                "Specification" to lens.specification,
                "Focal length scale" to
                    if (lens.isZoom) "limited to its range" else "fixed, a prime",
                "Aperture scale" to "only its own stops",
            ),
        )
    }
}
