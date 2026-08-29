package org.kutner.dofpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * The parts the settings and equipment screens are built from.
 *
 * A settings screen is a list of decisions, and the useful thing to do with a list of
 * decisions is group the ones that belong together. Everything here exists to make that
 * grouping visible: a heading names a group, a card holds it, and the space between cards
 * says where one subject ends and the next begins — which a flat run of fields never does,
 * however carefully it is spaced.
 */

/** A named group of related settings, held in a card of its own. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    /** False when the content is list items, which bring their own padding and insets. */
    inset: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = if (inset) Modifier.padding(12.dp) else Modifier,
                verticalArrangement = Arrangement.spacedBy(if (inset) 10.dp else 0.dp),
                content = content,
            )
        }
    }
}

/**
 * One choice from a short list, as a segmented button row.
 *
 * Segmented buttons rather than a dropdown because the options are few and worth seeing
 * without a tap — which of them is chosen is part of what the screen is telling you.
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(option)) }
        }
    }
}

/** A quiet line under a control, saying what it does rather than naming it again. */
@Composable
fun SettingNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A row that leads to another screen. */
@Composable
fun NavigationRow(
    headline: String,
    supporting: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.rotate(180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Several related choices in one card, ruled apart.
 *
 * Denser than a card each: a card carries its own margins and its own heading, and three
 * of them in a row spend most of the screen on the gaps between them rather than on the
 * settings. Where the choices are short and self-evident, one card with rules says the
 * same thing in a third of the height.
 */
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** One labelled choice inside a [SettingsCard]. */
@Composable
fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        ChoiceRow(options, selected, label, onSelect)
    }
}

/**
 * A question mark beside a field, which explains it when pressed.
 *
 * The explanation used to sit under the field as permanent supporting text. That is the
 * right thing for a form you meet once, and the wrong thing for one you come back to: two
 * or three lines of prose under a number you already understand is most of the reason
 * these screens would not fit on a phone. A badge costs a corner of one row and says the
 * same thing to whoever still needs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBadge(text: String) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = state,
    ) {
        // A bare Icon rather than an IconButton: this rides on a text field's label,
        // where a button's 48dp target would push the label taller than the border it
        // is drawn on and distort the field around it.
        Icon(
            HelpIcon,
            contentDescription = "What is this?",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp)
                .clip(CircleShape)
                .clickable { scope.launch { state.show() } },
        )
    }
}
