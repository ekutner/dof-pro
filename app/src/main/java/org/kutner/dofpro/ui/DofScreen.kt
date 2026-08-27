package org.kutner.dofpro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.kutner.dofpro.calc.Dof
import org.kutner.dofpro.model.DofResult
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.Notice
import org.kutner.dofpro.model.Teleconverter
import org.kutner.dofpro.model.UnitSystem
import org.kutner.dofpro.model.distinguishingSig
import org.kutner.dofpro.model.formatDistance
import org.kutner.dofpro.model.formatSig

/**
 * Blur levels that get a graduation on the left side of the distance scale, in units of
 * one pixel or one resolvable detail.
 *
 * The camera's allowable blur is added to these, wherever it falls: that is the level the
 * red lines sit on, so it is the one the rest of the scale is read against and the one
 * never dropped to make room for a neighbour.
 */
private val BLUR_TICK_LEVELS = listOf(
    1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0,
    20.0, 25.0, 30.0, 40.0,
)

/** Which panel the kebab menu has opened over the scales, if any. */
private enum class Panel { NONE, SETTINGS, CAMERAS, LENSES, TARGETS, DETAILS, HELP }

@Composable
fun DofScreen(state: DofState) {
    val result = state.compute()
    var panel by remember { mutableStateOf(Panel.NONE) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Window)
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            ConfigRow(
                state = state,
                onSettings = { panel = Panel.SETTINGS },
                onHelp = { panel = Panel.HELP },
            )
            Scales(state, result, { panel = Panel.DETAILS }, Modifier.weight(1f))
        }

        // Floated over the scales rather than inserted above them. Anything that reflows
        // the column mid-drag moves the canvas under a still finger, and a scale reading
        // pointer positions takes that for a drag — which sent the aperture several stops
        // in one step the moment crossing the limit made this appear.
        //
        // Two readings of the same fact, and at most one of them at a time — which one is
        // the model's call, not this layout's. Both are answered by the same lever, how
        // sharp this camera is being asked to be, so both offer the way to it.
        when (state.noticeFor(result)) {
            Notice.NOTHING_SHARP -> DiffractionNotice(
                lastSharp = state.lastSharpFStop,
                onOpenViewing = { panel = Panel.TARGETS },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Notice.BEST_DEPTH -> BestDepthNotice(
                fStop = state.bestDepthFStop,
                onOpenViewing = { panel = Panel.TARGETS },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Notice.NONE -> Unit
        }

        if (panel == Panel.DETAILS) {
            DetailsOverlay(state, result) { panel = Panel.NONE }
        }
    }

    // Settings and the two equipment screens share one window. Each used to open a window
    // of its own, and moving between them tore the first down before the second was up —
    // for that frame nothing covered the scales, which read as the main screen flashing on
    // the way back from editing a camera. One window, contents swapped, nothing to flash.
    if (panel == Panel.SETTINGS || panel == Panel.CAMERAS || panel == Panel.LENSES ||
        panel == Panel.TARGETS
    ) {
        // Editing equipment returns to the settings it was opened from; settings returns
        // to the scales. The system back gesture has to mean the same as the arrow.
        val back = {
            state.persist()
            panel = if (panel == Panel.SETTINGS) Panel.NONE else Panel.SETTINGS
        }
        FullScreenDialog(onDismiss = back) {
            when (panel) {
                Panel.CAMERAS -> CameraDialog(state, back)
                Panel.LENSES -> LensDialog(state, back)
                Panel.TARGETS -> TargetDialog(state, back)
                else -> SettingsDialog(
                    state = state,
                    onManageCameras = { panel = Panel.CAMERAS },
                    onManageLenses = { panel = Panel.LENSES },
                    onManageTargets = { panel = Panel.TARGETS },
                    onDismiss = back,
                )
            }
        }
    }

    if (panel == Panel.HELP) {
        InfoDialog("Help", HELP_TEXT) { panel = Panel.NONE }
    }
}

/**
 * The one row above the scales: which camera and which lens the numbers are for, and a
 * kebab for everything that is not a per-shot decision.
 *
 * The two pickers are Material assist chips rather than a bespoke widget — they are
 * compact enough to leave the scales their height, and a chip with a dropdown arrow reads
 * as "a choice you can change", which is exactly what they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigRow(
    state: DofState,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    var kebab by remember { mutableStateOf(false) }

    if (choosing) {
        SetupDialog(
            state = state,
            onEditEquipment = {
                choosing = false
                onSettings()
            },
            onDismiss = { choosing = false },
        )
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading the row, because it is what the two lines beside it are about:
            // the icon says "the kit", the lines say which kit. Set-up sits at the start
            // of the header for the same reason it sits at the start of a shoot.
            IconButton(onClick = { choosing = true }) {
                Icon(CameraSettingsIcon, contentDescription = "Choose camera, lens and viewing")
            }

            // A reading of what the numbers below are for, rather than three controls.
            // Choosing equipment is something you do once at the start and then leave
            // alone, so it does not deserve a third of the width apiece at the top of
            // every screen — while *knowing* what is chosen matters all the time. The
            // header states it; the icon leading it is where changing happens.
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { choosing = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${state.camera.name} · ${state.lens.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Viewed on ${state.target.listLabel(state.units)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Focus stacking is a mode, not an action, and a mode wants a control that
            // shows its state without being opened — in a menu it could only say so
            // once you had gone looking. Filled when it is on, bare when it is off, so
            // the header answers "am I stacking?" at a glance.
            FilledIconToggleButton(
                checked = state.stacking,
                onCheckedChange = {
                    state.stacking = it
                    state.persist()
                },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    StacksIcon,
                    contentDescription = if (state.stacking) {
                        "Focus stacking on"
                    } else {
                        "Focus stacking off"
                    },
                )
            }

            Box {
                IconButton(onClick = { kebab = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = kebab, onDismissRequest = { kebab = false }) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            kebab = false
                            onSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Help") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = {
                            kebab = false
                            onHelp()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Choosing all three at once: the camera, the lens, and where the picture will be seen.
 *
 * One dialog rather than three pickers along the top, because these are set once and then
 * left alone — a decision you make before shooting, not while you drag a scale. Keeping
 * them together also says what they are: three parts of one description of the job, rather
 * than three unrelated controls that happen to share a row.
 *
 * Picking from the three lists and editing them are the same errand arrived at from two
 * directions — you come here to choose a camera and find the one you want is not on the
 * list yet. Without a way through, that means backing out to the menu, into Settings, and
 * down to the right list, having already been shown all three.
 */
@Composable
private fun SetupDialog(
    state: DofState,
    onEditEquipment: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceField(
                    label = "Camera",
                    options = state.cameras.map { it.name },
                    selected = state.cameraIndex,
                ) {
                    state.cameraIndex = it
                    state.persist()
                }
                ChoiceField(
                    label = "Lens",
                    options = state.lenses.map { it.name },
                    selected = state.lensIndex,
                ) {
                    state.selectLens(it)
                    state.persist()
                }
                ChoiceField(
                    label = "Viewed on",
                    options = state.targets.map { it.listLabel(state.units) },
                    selected = state.targetIndex,
                ) {
                    state.targetIndex = it
                    state.persist()
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TextButton(
                    onClick = onEditEquipment,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add or edit these lists", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

/** A labelled read-only field that drops the list it is chosen from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options.getOrElse(selected) { "" },
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
                EquipmentMenuItem(name = option, selected = i == selected) {
                    onSelect(i)
                    expanded = false
                }
            }
        }
    }
}

/** A menu entry naming a camera or lens, with what distinguishes it underneath. */
@Composable
private fun EquipmentMenuItem(
    name: String,
    selected: Boolean,
    detail: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

/**
 * Shown only when the aperture has passed the camera's diffraction limit, where blur from
 * the aperture alone already exceeds the circle of confusion and no distance meets the
 * sharpness criterion — so there is genuinely no depth of field to draw.
 *
 * The reference app does the same thing and simply drops its markers. Bare dashes read
 * like a fault, though, so this says what happened and where the remedy is: a demanding
 * viewing target asks for a very small circle of confusion, and accepting a slightly
 * larger one is the recognised way to keep working past the limit.
 */
@Composable
private fun DiffractionNotice(
    lastSharp: Double?,
    onOpenViewing: () -> Unit,
    modifier: Modifier = Modifier,
) = NoticeBar(
    // Named as a stop the lens has, not as the exact f number where the budget runs out.
    // When the lens has no such stop there is nothing to name, and saying so is the point.
    text = lastSharp?.let { "Reduced sharpness past f/${formatSig(it)}" }
        ?: "Nothing sharp at any aperture",
    container = MaterialTheme.colorScheme.errorContainer,
    content = MaterialTheme.colorScheme.onErrorContainer,
    onAction = onOpenViewing,
    modifier = modifier,
)

/**
 * Shown once the aperture is at or past the stop that gives the most depth of field, which
 * is where dragging a limit stops moving.
 *
 * Without it the drag simply appears to jam, with the lens plainly capable of stopping down
 * further — the depth of field is at its widest, but nothing on screen says so. Stated as
 * where the most depth is rather than as a limit, because that is the useful reading: it is
 * the aperture to pick when depth of field is what matters.
 *
 * The remedy is the same one the diffraction notice offers. Both walls are placed by the
 * circle of confusion — this one at f = c*750/sqrt(2), that one at f = c*750 — so asking
 * for slightly less sharpness moves them both further down the scale.
 */
@Composable
private fun BestDepthNotice(
    fStop: Double,
    onOpenViewing: () -> Unit,
    modifier: Modifier = Modifier,
) = NoticeBar(
    text = "Most depth of field at f/${formatSig(fStop)}",
    container = MaterialTheme.colorScheme.secondaryContainer,
    content = MaterialTheme.colorScheme.onSecondaryContainer,
    onAction = onOpenViewing,
    modifier = modifier,
)

/**
 * A single line along the bottom of the scales. Floated over them rather than placed in the
 * column: see [DofScreen] for why anything that reflows mid-drag cannot go there.
 *
 * The action is outlined rather than bare text. Both notices only earn their place by
 * offering a way out, and a bare label inside an already coloured bar reads as more of the
 * notice — an outline is the least that says "this is a control", without a filled button
 * shouting over the sentence it belongs to.
 */
@Composable
private fun NoticeBar(
    text: String,
    container: Color,
    content: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = container,
        contentColor = content,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                border = BorderStroke(1.dp, content.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) { Text("Viewing", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Scales(
    state: DofState,
    result: DofResult,
    onDetails: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(3.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        // Text grows and shrinks with the window, as in the desktop app.
        val fontPx = (widthPx * 0.031f).coerceIn(21f, 60f)
        val headerSp = with(density) { (fontPx * 1.02f).toSp() }
        val valueSp = with(density) { (fontPx * 1.05f).toSp() }
        // The hyperfocal reading is not a column heading but a figure in its own right,
        // and the only one on screen with no second line under it to carry the value. It
        // is sized above the headings so it reads as the number it is.
        val hyperfocalSp = with(density) { (fontPx * 1.14f).toSp() }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LensColumn(state, headerSp, valueSp, fontPx, Modifier.weight(1f))
            ApertureColumn(state, result, headerSp, valueSp, fontPx, Modifier.weight(1f))
            // No blur column: the distance scale already carries blur down its left side,
            // graduated in the same units and against the distances it applies to, which
            // is more than a column of its own could say.
            DistanceColumn(
                state, result, onDetails, headerSp, valueSp, hyperfocalSp, fontPx,
                Modifier.weight(3.05f),
            )
        }
    }
}

/**
 * The subject distance as a text field, for when placing it by eye is the wrong tool —
 * a measured distance, or one read off a tape.
 *
 * The unit sits beside the field rather than inside it, so what is typed is only ever a
 * number and there is nothing to strip back off. It is whichever unit the column is
 * already showing, so the figure being replaced and the figure being typed mean the same
 * thing.
 *
 * Committed on Done and on losing focus, never abandoned: tapping away to look at
 * something else and coming back to find the number reverted is worse than taking a value
 * the user had finished typing.
 */
@Composable
private fun SubjectEditor(
    value: TextFieldValue,
    unitLabel: String,
    size: TextUnit,
    onValueChange: (TextFieldValue) -> Unit,
    onCommit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    // onFocusChanged fires once with isFocused false as the field is composed, before the
    // request below has landed. Committing on that would close the editor the instant it
    // opened, so only a loss of focus that follows a gain of it counts.
    var everFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Palette.SubjectLine,
                fontSize = size,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(Palette.SubjectLine),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier
                // Sized to its content, with just enough of a floor to stay tappable when
                // it is empty. A wider floor would strand the number out to the right of
                // the column, since it is set against the unit beside it.
                .widthIn(min = 44.dp)
                .focusRequester(focus)
                .onFocusChanged { st ->
                    if (st.isFocused) everFocused = true
                    else if (everFocused) onCommit()
                },
        )
        Text(
            text = " $unitLabel",
            color = Palette.SubjectLine,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The bordered panel each scale lives in. */
@Composable
private fun ScalePanel(
    modifier: Modifier,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Panel)
            .border(1.dp, Palette.PanelBorder)
            .padding(2.dp),
    ) {
        header()
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 7.dp)
        ) { body() }
    }
}

@Composable
private fun HeaderText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Text,
    bold: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Lens focal length in mm. Only the user ever moves this one. */
@Composable
private fun LensColumn(
    state: DofState,
    headerSp: TextUnit,
    valueSp: TextUnit,
    fontPx: Float,
    modifier: Modifier,
) {
    var tcMenu by remember { mutableStateOf(false) }

    ScalePanel(
        modifier = modifier,
        header = {
            // Pinned to the same height as the other columns' first rows. Left to size
            // itself the chip claimed Material's 48dp minimum touch target, which is
            // taller than the row beside it, and every heading in this column started a
            // line lower than the headings in the other two.
            Box(
                Modifier.fillMaxWidth().height(TOOL_BUTTON_ROW_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                // "1.4" alone reads as a setting of some kind; "1.4X" reads as what it
                // is, the factor everything on this scale is being multiplied by.
                // A filter chip, because that is what a teleconverter is: a modifier that
                // is either fitted or not. Unselected while it reads 1.0X and nothing is
                // in the way; filled once one is on, so a converter left fitted is visible
                // rather than hiding in a badge that looks the same either way.
                FilterChip(
                    selected = state.teleconverter != Teleconverter.NONE,
                    onClick = { tcMenu = true },
                    label = {
                        Text(
                            "${state.teleconverter.label}X",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    },
                )
                DropdownMenu(expanded = tcMenu, onDismissRequest = { tcMenu = false }) {
                    Teleconverter.entries.forEach { tc ->
                        DropdownMenuItem(
                            text = { Text("${tc.label}X teleconverter") },
                            onClick = {
                                state.teleconverter = tc
                                tcMenu = false
                            },
                        )
                    }
                }
            }
            HeaderText("lens", headerSp)
            HeaderText("${formatSig(state.focalLength)} mm", valueSp, bold = true)
        },
        body = {
            // A prime has one focal length, so its scale becomes a read-out.
            HarmonicScale(
                value = state.focalLength,
                zoom = 0.83,
                bounds = state.lens.scaleRange(),
                minValue = state.lens.minFocal,
                maxValue = state.lens.maxFocal,
                ceiling = DofState.MAX_FOCAL,
                editable = state.lens.isZoom,
                ticks = { lo, hi, pos ->
                    // What the lens can actually do is worth more than a round number, so
                    // its ends are always graduated and labelled — both of them on a zoom,
                    // the single focal length on a prime. They are laid down first and any
                    // ordinary label that would collide gives up its text, keeping its
                    // tick: two numbers on top of each other read as neither.
                    val l = state.lens
                    val ends = (if (l.isZoom) listOf(l.minFocal, l.maxFocal) else listOf(l.minFocal))
                        .distinct()
                        .map { ScaleTick(it, formatSig(it, 4), Palette.Tick) }
                    val gap = fontPx * 1.3f
                    val rest = decadeTicks(lo, hi, pos, gap, fontPx * 0.3f)
                        // Nothing is engraved past the ends of a lens, so nothing is drawn
                        // there either — the aperture scale has always worked this way,
                        // showing only the stops the lens actually has. The window itself
                        // still runs a little wider, so a graduation sitting on a limit is
                        // not half off the canvas.
                        .filter { it.value >= l.minFocal - 1e-9 && it.value <= l.maxFocal + 1e-9 }
                        .map { t ->
                            if (t.label != null && ends.any { abs(pos(it.value) - pos(t.value)) < gap }) {
                                t.copy(label = null, major = false)
                            } else {
                                t
                            }
                        }
                    rest + ends
                },
                fontPx = fontPx,
                modifier = Modifier.fillMaxSize(),
                anchor = state.focalAnchor,
                onValueChange = { state.changeFocalLength(it, settle = false) },
                onAnchorChange = { state.nudgeFocalAnchor(it) },
                onSettle = { state.settleFocalLength() },
            )
        },
    )
}

/**
 * The aperture. The user can drag it directly, or it gets set for them when they drag a
 * depth of field limit on the distance scale.
 */
@Composable
private fun ApertureColumn(
    state: DofState,
    result: DofResult,
    headerSp: TextUnit,
    valueSp: TextUnit,
    fontPx: Float,
    modifier: Modifier,
) {
    val wavelength = state.camera.wavelengthNm
    val coc = result.coc

    ScalePanel(
        modifier = modifier,
        header = {
            // Half or third stops is a preference, set once in Settings, so it does not
            // need a control of its own here. The row stays, keeping this header the same
            // height as the others so all four scales start at the same line.
            Box(Modifier.fillMaxWidth().height(TOOL_BUTTON_ROW_HEIGHT))
            HeaderText("aperture", headerSp)
            HeaderText("f/${formatSig(state.fStop)}", valueSp, bold = true)
        },
        body = {
            ApertureScale(
                value = state.fStop.coerceIn(DofState.MIN_F, DofState.MAX_F),
                center = 9.5,
                span = 5.6,
                minValue = state.lens.minFStop,
                maxValue = state.lens.maxFStop,
                editable = true,
                ticks = { lo, hi ->
                    apertureTicks(state.apertureStops(), lo, hi) { f ->
                        Dof.diffractionBlur(f * state.teleconverter.factor, wavelength) / coc
                    }
                },
                fontPx = fontPx,
                modifier = Modifier.fillMaxSize(),
                onValueChange = { state.fStop = state.snapFStop(it) },
            )
        },
    )
}

@Composable
private fun DistanceColumn(
    state: DofState,
    result: DofResult,
    onDetails: () -> Unit,
    headerSp: TextUnit,
    valueSp: TextUnit,
    hyperfocalSp: TextUnit,
    fontPx: Float,
    modifier: Modifier,
) {
    var editingSubject by remember { mutableStateOf(false) }
    var showFrames by remember { mutableStateOf(false) }
    val frames = if (state.stacking) state.frames(result) else Dof.Frames(emptyList(), false)
    var subjectText by remember { mutableStateOf(TextFieldValue()) }

    // One unit for the whole column, so the three read-outs and every graduation below
    // them are in the same terms. Taken from the subject rather than from the window: the
    // window slides as the marker is dragged away from the middle of the scale, which
    // would move the distance at which centimetres become metres along with it.
    val window = state.distanceWindow(result)
    val format = state.units.formatFor(result.subject)
    // Where the format leaves the precision open, a tight depth of field would otherwise
    // print the same number three times.
    val sig = distinguishingSig(
        listOfNotNull(result.near, result.subject, result.far), format.unit,
    )

    // Distances at which blur reaches each labelled level, either side of the subject.
    val sharpBlur = result.sharpBlur
    val levels = (BLUR_TICK_LEVELS + sharpBlur).distinct().sorted()
    val blurTicks = levels.flatMap { level ->
        val budget = Dof.focusBlurBudget(
            result.effectiveF, result.blurUnit, level, state.camera.wavelengthNm,
        ) ?: return@flatMap emptyList()
        val l = result.effectiveFocal
        val n = Dof.nearLimit(l, result.effectiveF, result.subject, budget)
        val f = Dof.farLimit(l, result.effectiveF, result.subject, budget)
        listOfNotNull(
            n.takeIf { it.isFinite() && it > 0 }?.let { it to level },
            f.takeIf { it.isFinite() && it > 0 }?.let { it to level },
        )
    }

    if (showFrames) {
        InfoDialog("Focus stack", frameList(frames, state.units)) { showFrames = false }
    }

    ScalePanel(
        modifier = modifier,
        header = {
            // The green line on the scale is often off the top of the visible window, so
            // the number is worth having in writing. It is a distance in its own right
            // rather than one of the three being compared, so it is written in whatever
            // unit suits its own size, not the column's.
            Row(
                Modifier.fillMaxWidth().height(TOOL_BUTTON_ROW_HEIGHT),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tapping it focuses there. The hyperfocal distance is the one focus
                // setting worth reaching for by name — everything from half of it to
                // infinity comes out acceptably sharp — and hunting for it by dragging a
                // scale that has to run to infinity is the wrong way to spend a gesture.
                // Nothing to go to when it is infinite, which is also when there is no
                // depth of field at all, so it stays a read-out there.
                val reachable = result.hyperfocal.isFinite()
                // Not [HeaderText]: that one fills the width, which is right when a
                // read-out has its row to itself and wrong when something has to sit
                // beside it. Weighted without filling, so the two of them centre as a
                // pair and the text gives way first in a narrow column.
                Text(
                    text = "hyperfocal ${formatDistance(result.hyperfocal, state.units)}",
                    color = Palette.HyperfocalLine,
                    fontSize = hyperfocalSp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (reachable) {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { state.moveSubject(result.hyperfocal) }
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            } else {
                                Modifier
                            }
                        ),
                )
                // Every figure the details panel lists is a figure about this column —
                // the near and far limits, the hyperfocal distance, the circle of
                // confusion they all come from. So it opens from here, next to the
                // read-out it elaborates, rather than from a menu that has to be opened
                // before it can be read.
                IconButton(
                    onClick = onDetails,
                    modifier = Modifier.size(TOOL_BUTTON_ROW_HEIGHT),
                ) {
                    Icon(
                        EyeIcon,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Typing the distance takes the whole width of the column rather than the
            // third the subject read-out normally gets. A field that narrow cannot show
            // "1.998" and a caret at once, and the limits are not worth reading while
            // their cause is being retyped anyway. Two rows either way, so the scales
            // below stay put.
            if (editingSubject) {
                HeaderText("subject distance", headerSp, color = Palette.SubjectLine)
                SubjectEditor(
                    value = subjectText,
                    unitLabel = format.unit.label,
                    size = valueSp,
                    onValueChange = { subjectText = it },
                    onCommit = {
                        format.parse(subjectText.text)?.let { state.moveSubject(it) }
                        editingSubject = false
                    },
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    HeaderText(
                        if (state.stacking) "closest" else "near",
                        headerSp,
                        color = if (state.stacking) Palette.SubjectLine else Palette.LimitLine,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderText(
                        if (state.stacking) "images" else "subject",
                        headerSp,
                        color = if (state.stacking) Palette.StackLine else Palette.SubjectLine,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderText(
                        "far",
                        headerSp,
                        color = if (state.stacking) Palette.AxisName else Palette.LimitLine,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    HeaderText(
                        // Stacking, the closest point is the user's own and the near limit
                        // of one frame means nothing; the far end is infinity by
                        // definition, which is the whole point of the mode.
                        text = if (state.stacking) format.text(result.subject, sig)
                        else result.near?.let { format.text(it, sig) } ?: "—",
                        size = valueSp,
                        bold = true,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderText(
                        text = if (state.stacking) {
                            frames.count.takeIf { it > 0 }?.toString() ?: "—"
                        } else {
                            format.text(result.subject, sig)
                        },
                        size = valueSp,
                        bold = true,
                        color = if (state.stacking) Palette.StackLine else Palette.Text,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (state.stacking) {
                                    if (frames.count > 0) showFrames = true
                                    return@clickable
                                }
                                // Seeded with what is on screen, selected whole, so the
                                // first keystroke replaces it — the common case is a new
                                // distance, not an edit to the old one.
                                val shown = format.number(result.subject, sig)
                                subjectText = TextFieldValue(shown, TextRange(0, shown.length))
                                editingSubject = true
                            },
                    )
                    HeaderText(
                        text = if (state.stacking) "∞"
                        else result.far?.let { format.text(it, sig) } ?: "—",
                        size = valueSp,
                        bold = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        body = {
            DistanceScale(
                window = window,
                format = format,
                subjectRange = state.minSubject..DofState.MAX_DISTANCE,
                subject = result.subject,
                near = result.near,
                far = result.far,
                hyperfocal = result.hyperfocal,
                subjectBlurLabel = formatSig(result.blurAtSubject, 2),
                sharpBlur = sharpBlur,
                stackPoints = frames.focusPoints,
                blurTicks = blurTicks,
                coneKey = result,
                blurAt = { d ->
                    Dof.blurValue(
                        result.effectiveFocal, result.effectiveF, result.subject, d,
                        result.coc, state.camera.wavelengthNm,
                    )
                },
                fontPx = fontPx,
                modifier = Modifier.fillMaxSize(),
                onSubjectChange = { state.moveSubject(it) },
                onSubjectAnchorChange = { state.nudgeSubjectAnchor(it) },
                onHoldSpan = { state.holdDistanceSpan(it) },
                onZoom = { factor, span -> state.zoomDistance(factor, span) },
                onNearChange = { state.dragNearLimit(it) },
                onFarChange = { state.dragFarLimit(it) },
            )
        },
    )
}


@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        // Scrollable: the help has outgrown any dialog that would hold it whole, and a
        // dialog's text slot does not scroll on its own — it simply clips, silently.
        text = {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
    )
}

/**
 * The stack written out frame by frame: where to focus, and what each one holds sharp.
 *
 * Each frame's own limits are given rather than only its focus distance, because that is
 * what tells the photographer the stack really joins up — one frame's far limit reaching
 * past the next one's near limit is the overlap, visible rather than promised.
 */
private fun frameList(frames: Dof.Frames, units: UnitSystem): String {
    if (frames.focusPoints.isEmpty()) return "No depth of field to stack at this aperture."
    val out = StringBuilder("Focus each frame here, nearest first.\n\n")
    frames.focusPoints.forEachIndexed { i, d ->
        out.append("${i + 1}.  ${formatDistance(d, units)}\n")
    }
    if (!frames.complete) {
        out.append("\nStopped at ${frames.count} frames; the range needs more than this.")
    }
    return out.toString()
}

/**
 * What the app is and how to work it.
 *
 * Ordered the way it is used rather than the way it is built: what has to be set before any
 * number means anything, then the scales, then what the readings say, then the parts that
 * can be ignored until wanted. The optics are explained only where the screen would
 * otherwise be puzzling — why the limits vanish, why stopping down stops helping — since
 * everything else is in the details panel with its own figures beside it.
 */
private val HELP_TEXT = """
    SETTING UP

    The camera icon at the top left opens Setup: a camera, a lens, and a viewing target.

    The camera and the viewing target are deliberately separate, because they answer
    different questions. The camera settles how much detail was recorded — its sensor
    size and pixel pitch, or the film's resolution. The viewing target settles how much
    of that detail can be seen: how large the picture is shown, and from how far away.
    The same frame is critically sharp on a phone and visibly soft as a wall print, and
    one camera shoots both, so neither alone can say what counts as sharp.

    Together they give the circle of confusion — the blur the app will call sharp. A
    target can be a print, a screen (which is limited by the eye or by its own pixels,
    whichever is coarser), pixel level for judging at 100%, or a circle of confusion
    stated outright.

    The lens confines the focal length and aperture scales to settings that lens
    actually has.

    THE SCALES

    Focal length, aperture, and distance, left to right. The distance scale carries two
    sets of graduations: blur down its left side, distance down its right.

    Drag the blue line to your subject. The two red lines show how much either side of
    it will be acceptably sharp, and the green line is the hyperfocal distance, which is
    also written above the scale.

    Tap the subject reading to type a distance instead. Tap the hyperfocal reading to
    focus there. Pinch the distance scale to zoom in or out.

    Change the aperture and the red lines move. Or drag a red line to where you need it
    and the aperture is chosen for you — it can only land on real f stops, so the limits
    jump from one to the next. Half or third stops, in Settings.

    Every marker you can drag moves with your finger, and hands over to the scale once
    it reaches the edge of its travel.

    READING THE BLUR

    The blur scale counts just-resolvable details, so 1.0 is the finest detail the
    viewing target can show at all. The red lines sit at that target's allowable blur —
    two details by default, which is why the depth of field limits usually read 2.0
    rather than 1.0.

    The blur figure above the subject is the blur at the subject itself, which is pure
    diffraction: focus blur is zero exactly where you focused. When it climbs to the
    allowable blur, nothing in the frame is critically sharp any more and the limits
    disappear.

    Two notices can appear along the bottom. "Most depth of field at f/x" means stopping
    down past there costs depth rather than buying it. "Reduced sharpness past f/x"
    means diffraction alone has spent the whole circle of confusion. Both walls are
    placed by the circle of confusion, so both move if the viewing target asks for less.

    FOCUS STACKING

    The stacked-squares button in the header turns it on. The distance column then
    counts the frames needed to cover everything from the closest sharp point out to
    infinity; tap that count to list where to focus each one. How far the frames overlap
    is set in Settings.

    DETAILS

    The eye beside the hyperfocal reading opens the full figures: the limits and the
    depth between them, field of view, magnification, the circle of confusion and the
    aperture at which diffraction reaches it.

    SETTINGS

    Theme, metric or imperial, and aperture increments. Within either system the unit
    suits the distance, so metres give way to centimetres as the subject comes closer.

    The three equipment lists live here too, and all three can be edited, added to or
    trimmed. Long-press an entry to select several at once and delete them. A camera can
    also be looked up by name, which fills in its real sensor size and resolution.

    Depth of field accounts for both focus blur and diffraction. Equations follow the
    DoF 4.0 reference manual by Jonathan M. Sachs, Digital Light & Color.
""".asParagraphs()

/**
 * Joins hard-wrapped source lines back into paragraphs, keeping blank lines as breaks.
 *
 * So the text above can be written at a readable width in the source and still wrap to
 * whatever width it is shown at. Without it every source line break survives into the
 * dialog and the paragraphs come out ragged, broken mid-sentence at whatever column the
 * editor happened to end on.
 */
private fun String.asParagraphs(): String =
    trimIndent().trim().split("\n\n").joinToString("\n\n") { paragraph ->
        paragraph.replace(Regex("""\s*\n\s*"""), " ").trim()
    }
