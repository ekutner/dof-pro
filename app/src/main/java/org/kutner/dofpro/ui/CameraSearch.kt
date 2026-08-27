package org.kutner.dofpro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kutner.dofpro.model.CameraLookup
import org.kutner.dofpro.model.CameraMatch
import org.kutner.dofpro.model.CameraSpecs
import org.kutner.dofpro.model.formatSig

/**
 * Looking a camera up by name instead of copying its sensor off a spec sheet.
 *
 * The results are a list to choose from rather than one answer taken on trust. Search is
 * good but not certain — ask it for "sony a7r v" and the second and third suggestions are
 * the α7R III and the plain α7 — and a calculator that silently adopted the wrong sensor
 * would put every distance on screen slightly out with nothing to show for it. Choosing
 * takes one tap and removes the whole class of error.
 */
@Composable
fun CameraSearch(onAdopt: (CameraSpecs) -> Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<CameraMatch>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun find() {
        if (query.isBlank() || busy) return
        busy = true
        message = null
        matches = emptyList()
        scope.launch {
            val found = CameraLookup.search(query)
            busy = false
            matches = found
            if (found.isEmpty()) message = "Nothing found. Check the spelling, or type the specs in below."
        }
    }

    fun adopt(match: CameraMatch) {
        busy = true
        message = null
        scope.launch {
            val specs = CameraLookup.specs(match.title)
            busy = false
            when {
                specs == null ->
                    message = "Could not reach Wikipedia. Try again, or type the specs in below."
                !specs.hasFrame ->
                    // The article named a sensor format rather than measuring one. Formats
                    // are families, not measurements, and the families disagree by enough
                    // to matter, so nothing is filled in from a family name.
                    message = "${match.title} does not give its sensor in millimetres. " +
                        "Type the frame size in below."
                else -> {
                    onAdopt(specs)
                    matches = emptyList()
                    query = ""
                    message = if (specs.hasPixels) {
                        "Filled in from ${specs.name}: " +
                            "${formatSig(specs.widthMm!!, 4)} × ${formatSig(specs.heightMm!!, 4)} mm, " +
                            "${specs.widthPx} × ${specs.heightPx} pixels."
                    } else {
                        "Frame size filled in from ${specs.name}. Its resolution was not " +
                            "listed — set the pixel dimensions below."
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Find a camera by name") },
            placeholder = { Text("e.g. Sony a7R V") },
            singleLine = true,
            trailingIcon = {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { find() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { find() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (matches.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column {
                    Text(
                        "Which one?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
                    )
                    matches.forEachIndexed { i, match ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { adopt(match) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                match.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
