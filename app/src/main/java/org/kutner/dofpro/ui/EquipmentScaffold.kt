package org.kutner.dofpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kutner.dofpro.model.formatSig

/**
 * The shape both equipment managers take: a list of what you own with the one in use
 * marked, a button to add another, and a form behind each one.
 *
 * This replaces a Prev/Next/New/Delete button row, which is a desktop dialog idiom — on a
 * phone the collection itself should be the screen, and editing a member of it a place you
 * navigate to.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun <T> EquipmentManager(
    title: String,
    items: List<T>,
    selectedIndex: Int,
    nameOf: (T) -> String,
    /** What distinguishes one from another, or null where the name is the whole story. */
    summaryOf: ((T) -> String)?,
    addLabel: String,
    onSelect: (Int) -> Unit,
    /** A fresh member to start editing. Not added to anything: see [onSave]. */
    newItem: () -> T,
    /** Commits an edit. A null index means it is new and should be appended. */
    onSave: (index: Int?, item: T) -> Unit,
    /** Removes several at once. Indices, and never all of them. */
    onRemove: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    /**
     * The form for one member, working on a copy.
     *
     * It is handed a value rather than an index, and hands one back, so nothing
     * reaches the collection until [onSave]. That is what makes Cancel, Back and the
     * X the same act — none of them has anything to undo — and what stops a new
     * member existing before anyone has agreed to it.
     */
    editor: @Composable (
        item: T,
        canDelete: Boolean,
        onSave: (T) -> Unit,
        onDelete: () -> Unit,
        onCancel: () -> Unit,
    ) -> Unit,
) {
    /** The member being edited: its place in the list, or null if it is new. */
    var editing by remember { mutableStateOf<Pair<Int?, T>?>(null) }
    // Long press starts a selection, and while one is running a tap adds to it rather than
    // choosing a camera to shoot with. Two jobs for one gesture, told apart by whether a
    // selection is already under way — which is how every list on the phone behaves.
    var marked by remember { mutableStateOf(emptySet<Int>()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val selecting = marked.isNotEmpty()

    val open = editing
    if (open != null) {
        val (at, item) = open
        marked = emptySet()
        editor(
            item,
            // Nothing to delete on something not in the list yet, and the last
            // member cannot go: the app would have nothing to compute for.
            at != null && items.size > 1,
            { saved ->
                onSave(at, saved)
                editing = null
            },
            {
                at?.let { onRemove(setOf(it)) }
                editing = null
            },
            { editing = null },
        )
        return
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${marked.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { marked = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        // A collection with nothing in it would leave the app with no
                        // camera to compute for, so the last one cannot be deleted.
                        IconButton(
                            enabled = marked.size < items.size,
                            onClick = { confirmRemove = true },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            } else {
                LargeTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null to newItem() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(addLabel) },
                )
            }
        },
    ) { padding ->
        // Cards rather than a run of identical rows, because these are things you own
        // rather than settings you set, and which one is in use is worth seeing at a
        // glance. A card in the accent colour says that across the room; a small radio
        // button in a column of look-alike rows has to be hunted for.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items) { index, item ->
                val chosen = index == selectedIndex && !selecting
                val ticked = index in marked
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = when {
                        ticked -> MaterialTheme.colorScheme.tertiaryContainer
                        chosen -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = when {
                        ticked -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                        chosen -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        else -> null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selecting) {
                                    marked = if (ticked) marked - index else marked + index
                                } else {
                                    onSelect(index)
                                }
                            },
                            onLongClick = {
                                marked = if (ticked) marked - index else marked + index
                            },
                        ),
                ) {
                    Row(
                        Modifier.padding(
                            start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selecting) {
                            Checkbox(
                                checked = ticked,
                                onCheckedChange = {
                                    marked = if (ticked) marked - index else marked + index
                                },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                nameOf(item),
                                style = MaterialTheme.typography.titleMedium,
                                color = when {
                                    ticked -> MaterialTheme.colorScheme.onTertiaryContainer
                                    chosen -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            summaryOf?.let {
                                Text(
                                    it(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (chosen) {
                                Text(
                                    "In use",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        if (!selecting) {
                            IconButton(onClick = { editing = index to items[index] }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        val names = marked.sorted().mapNotNull { items.getOrNull(it)?.let(nameOf) }
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Delete ${marked.size}?") },
            text = { Text(names.joinToString("\n")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove(marked)
                    marked = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            },
        )
    }
}

/** The form behind one piece of equipment: an app bar that can delete it, then fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentEditor(
    title: String,
    canDelete: Boolean,
    /** Removes the item *and* closes: the restore Cancel performs must not run. */
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    // Back means cancel. Without this it falls through to whatever hosts the panel
    // and closes the lot, which counts as neither keeping the edit nor discarding
    // it — and left a half-filled new camera in the list.
    BackHandler { onCancel() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // A small bar, not a large one. The large title collapses on scroll, which is a
        // fine thing on a screen you scroll and pure overhead on one meant to be read
        // whole: it was spending nearly a sixth of the height saying a word already in
        // the field below it.
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(enabled = canDelete, onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        // Pinned rather than scrolled to: whether the edit is kept is the one decision
        // this screen exists to take, and it should never be somewhere you have to go
        // looking for it.
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("OK") }
                }
            }
        },
    ) { padding ->
        // Still scrollable. Everything here is sized to fit an ordinary phone without it,
        // but a small screen, a large system font or a keyboard over half the display all
        // make a scroll the difference between awkward and unusable.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $title?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** A read-only panel of derived figures, shown under the fields that produce them. */
@Composable
fun OutputCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A number field that keeps half-typed input and only commits what parses. */
@Composable
fun NumberField(
    label: String,
    value: Double,
    resetKey: Any,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    /** An explanation, offered as a question mark beside the label. */
    help: String? = null,
    onValue: (Double) -> Unit,
) {
    var text by remember(resetKey, label) { mutableStateOf(formatSig(value, 6)) }

    // The field keeps its own text so half-typed input survives — "1." must not be
    // rewritten to "1" between keystrokes. That also means a value changed from outside,
    // by a camera lookup filling the form in, would never appear. So it re-reads, but only
    // when the new value is not the one this text already says: typing sets the value and
    // would otherwise fight itself here.
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = formatSig(value, 6)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered
            entered.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onValue)
        },
        // The badge sits on the label, so it names the field it explains without
        // costing a row of its own or a second line under the number.
        label = {
            if (help == null) {
                Text(label)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    HelpBadge(help)
                }
            }
        },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
