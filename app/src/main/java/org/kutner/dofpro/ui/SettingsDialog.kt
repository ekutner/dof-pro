package org.kutner.dofpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.kutner.dofpro.model.ApertureStep
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.ThemeChoice
import org.kutner.dofpro.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Preferences and the equipment collections, on one screenful.
 *
 * The three short choices share a card, ruled apart, and go without explanation: a
 * segmented button reading *Metric | Imperial* has already said everything a sentence
 * underneath could add, and three such sentences cost more height than the settings
 * themselves. What is left explained is the focus stack overlap, which is the one figure
 * here whose effect is not written on its face.
 *
 * A screen rather than a window of its own — see [FullScreenDialog], which holds this and
 * the equipment screens in the one window between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    state: DofState,
    onManageCameras: () -> Unit,
    onManageLenses: () -> Unit,
    onManageTargets: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCard {
                ChoiceSetting(
                    title = "Theme",
                    options = ThemeChoice.entries,
                    selected = state.theme,
                    label = { it.label },
                    onSelect = { state.theme = it },
                )
                Rule()
                ChoiceSetting(
                    title = "Units of measure",
                    options = UnitSystem.entries,
                    selected = state.units,
                    label = { it.label },
                    onSelect = { state.units = it },
                )
                Rule()
                ChoiceSetting(
                    title = "Aperture increments",
                    // Thirds first, being both the finer increment and the one most modern
                    // bodies actually click in — the default belongs on the left.
                    options = listOf(ApertureStep.THIRD, ApertureStep.HALF),
                    selected = state.apertureStep,
                    // The fraction, as an aperture ring itself is described.
                    label = { "${it.label} stop" },
                    onSelect = {
                        state.apertureStep = it
                        state.fStop = state.snapFStop(state.fStop)
                    },
                )
            }

            SettingsCard {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Focus stack overlap",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The figure belongs beside its name: a slider alone never says
                        // where it has landed.
                        Text(
                            "${(state.stackOverlap * 100.0).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = (state.stackOverlap * 100.0).toFloat(),
                        onValueChange = {
                            state.stackOverlap =
                                (it / 100.0).coerceIn(0.0, DofState.MAX_STACK_OVERLAP)
                        },
                        valueRange = 0f..(DofState.MAX_STACK_OVERLAP * 100.0).toFloat(),
                        // One step per percent: a slider free to land on 23.4% would
                        // suggest the number is worth that much precision.
                        steps = (DofState.MAX_STACK_OVERLAP * 100.0).toInt() - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "How far each frame doubles back over the one before it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            SettingsCard {
                NavigationRow(
                    headline = "Cameras",
                    supporting = "${state.cameras.size} configured · ${state.camera.name}",
                    onClick = onManageCameras,
                )
                Rule()
                NavigationRow(
                    headline = "Lenses",
                    supporting = "${state.lenses.size} configured · ${state.lens.name}",
                    onClick = onManageLenses,
                )
                Rule()
                NavigationRow(
                    headline = "Viewing",
                    supporting = "${state.targets.size} configured · ${state.target.name}",
                    onClick = onManageTargets,
                )
            }

            Column(Modifier.padding(bottom = 16.dp)) {}
        }
    }
}

/** The hairline between two settings sharing a card. */
@Composable
private fun Rule() {
    HorizontalDivider(
        Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One window filling the screen, holding whichever of the settings and equipment screens
 * is open.
 *
 * One window, not one each, and that is the whole point of it. A dialog *is* a window:
 * closing the camera list and opening settings tears the first down before the second is
 * up, and for the frame in between there is nothing over the scales — which read as the
 * main screen flashing every time you came back from editing a camera. Keeping the window
 * and swapping only its contents leaves nothing to flash through.
 */
@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        content()
    }
}
