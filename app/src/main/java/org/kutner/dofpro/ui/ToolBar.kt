package org.kutner.dofpro.ui

import androidx.compose.ui.unit.dp

/**
 * How much height the control above a scale takes, including its margins.
 *
 * Only the distance scale and the lens scale put anything there — the hyperfocal read-out
 * and the teleconverter chip. The other columns reserve the same height and leave it
 * empty, so all four scales start on the same line.
 */
val TOOL_BUTTON_ROW_HEIGHT = 36.dp
