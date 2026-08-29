package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        newItem = { Lens.blank() },
        onSave = { at, lens ->
            if (at == null) state.lenses.add(lens) else state.lenses[at] = lens
            // The scales are drawn from the lens in use, so if that is the one that has
            // just changed they have to be brought back inside its range.
            if (at == state.lensIndex) state.applyLensLimits()
            state.persist()
        },
        onRemove = { gone ->
            state.removeLenses(gone)
            state.persist()
        },
        onDismiss = onDismiss,
        editor = { item, canDelete, onSave, onDelete, onCancel ->
            LensEditor(item, canDelete, onSave, onDelete, onCancel)
        },
    )
}

@Composable
private fun LensEditor(
    initial: Lens,
    canDelete: Boolean,
    onSave: (Lens) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var lens by remember { mutableStateOf(initial) }

    fun update(block: Lens.() -> Lens) {
        lens = lens.block()
    }

    EquipmentEditor(
        title = lens.name.ifBlank { "Lens" },
        canDelete = canDelete,
        onDelete = onDelete,
        onCancel = onCancel,
        onConfirm = { onSave(lens) },
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
                label = if (lens.isZoom) "From mm" else "Focal length mm",
                value = lens.minFocal,
                resetKey = Unit,
                modifier = Modifier.weight(1f),
            ) { v ->
                update {
                    val lo = v.coerceIn(DofState.MIN_FOCAL, DofState.MAX_FOCAL)
                    copy(minFocal = lo, maxFocal = maxOf(maxFocal, lo))
                }
            }
            if (lens.isZoom) {
                NumberField(
                    label = "To mm",
                    value = lens.maxFocal,
                    resetKey = Unit,
                        modifier = Modifier.weight(1f),
                ) { v ->
                    update { copy(maxFocal = v.coerceIn(minFocal, DofState.MAX_FOCAL)) }
                }
            }
        }

        }

        SettingsGroup("Aperture") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Widest f/",
                value = lens.minFStop,
                resetKey = Unit,
                modifier = Modifier.weight(1f),
            ) { v ->
                update {
                    val wide = v.coerceIn(DofState.MIN_F, DofState.MAX_F)
                    copy(minFStop = wide, maxFStop = maxOf(maxFStop, wide))
                }
            }
            NumberField(
                label = "Narrowest f/",
                value = lens.maxFStop,
                resetKey = Unit,
                modifier = Modifier.weight(1f),
            ) { v ->
                update { copy(maxFStop = v.coerceIn(minFStop, DofState.MAX_F)) }
            }
        }

        }

        OutputCard(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 12.dp),
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
