package com.econverter.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

private const val REPOSITORY_URL = "https://github.com/bilec/econverter"

@Composable
internal fun MetadataDialog(original: BookMetadata, onApply: (BookMetadata) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(original) { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_metadata)) },
        text = {
            CenterDialogWindow()
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = { Text(stringResource(R.string.title_field)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = draft.title != original.title && draft.title.isBlank(),
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.authors), modifier = Modifier.weight(1f))
                    IconButton(onClick = { draft = draft.copy(authors = draft.authors + "") }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_author))
                    }
                }
                draft.authors.forEachIndexed { index, author ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = author,
                            onValueChange = { value -> draft = draft.copy(authors = draft.authors.mapIndexed { position, name -> if (position == index) value else name }) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = draft.authors != original.authors && author.isBlank(),
                        )
                        IconButton(onClick = { draft = draft.copy(authors = draft.authors.filterIndexed { position, _ -> position != index }) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_author))
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.publisher,
                    onValueChange = { draft = draft.copy(publisher = it) },
                    label = { Text(stringResource(R.string.publisher)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.comments,
                    onValueChange = { draft = draft.copy(comments = it) },
                    label = { Text(stringResource(R.string.comments)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }, enabled = draft.canApplyTo(original)) { Text(stringResource(R.string.apply_metadata)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun BackendSettingsDialog(backend: ConversionBackend, onSelect: (ConversionBackend) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversion_backend)) },
        text = {
            CenterDialogWindow()
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                ConversionBackend.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = backend == option, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(selected = backend == option, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(option.descRes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.input_formats_summary, backend.inputFormats.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.output_formats_summary, backend.outputFormats.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            CenterDialogWindow()
            Column {
                Text(stringResource(R.string.version_format, versionName))
                Text(stringResource(R.string.author))
                Text(stringResource(R.string.license))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)))
            }) {
                Text(stringResource(R.string.view_on_github))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun CenterDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setGravity(Gravity.CENTER) }
}

internal fun reportOnGitHub(context: Context, vm: ConverterViewModel) {
    val body = buildString {
        appendLine("## Bug Report")
        appendLine()
        appendLine("- **Steps to reproduce:**")
        appendLine("- **Expected result:**")
        appendLine("- **Error:** ${vm.status.text(context)}")
        appendLine("- **Input file:** ${vm.inputFileName}")
        appendLine("- **Output format:** ${vm.outputFormat}")
        appendLine("- **Backend:** ${vm.backend.label}")
        appendLine("- **Output profile:** ${vm.outputProfile}")
        appendLine("- **Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- **Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- **Extra args:** ${vm.extraArgs}")
        appendLine()
        appendLine("Remove personal information before submitting.")
    }
    val title = "Conversion failed: ${vm.inputFileName.substringAfterLast(".")} → ${vm.outputFormat}"
    val url = "$REPOSITORY_URL/issues/new" +
        "?title=${Uri.encode(title)}" +
        "&body=${Uri.encode(body)}" +
        "&labels=bug"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
