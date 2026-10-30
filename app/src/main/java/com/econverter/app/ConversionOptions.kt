package com.econverter.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

private const val PDF_CLEANUP_ARGS = "--enable-heuristics --unwrap-lines --delete-blank-paragraphs " +
    "--remove-paragraph-spacing --dehyphenate --change-justification left"

@Composable
internal fun ConversionOptions(vm: ConverterViewModel, enabled: Boolean) {
    var showOptions by remember { mutableStateOf(false) }
    if (vm.backend == ConversionBackend.BOKO) return
    TextButton(onClick = { showOptions = !showOptions }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
        Icon(if (showOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.conversion_options))
    }
    if (showOptions) {
        FormatDropdown(stringResource(R.string.output_profile), vm.outputProfile, vm.outputProfiles) { vm.outputProfile = it }
        if (vm.inputFileNames.any { it.endsWith(".pdf", ignoreCase = true) }) {
            OutlinedButton(
                onClick = {
                    if (!vm.extraArgs.contains(PDF_CLEANUP_ARGS)) {
                        vm.extraArgs = listOf(vm.extraArgs, PDF_CLEANUP_ARGS).filter { it.isNotBlank() }.joinToString(" ")
                    }
                },
            ) {
                Text(stringResource(R.string.apply_pdf_cleanup))
            }
        }
        CheckboxRow(stringResource(R.string.smarten_punctuation), vm.smartenPunctuation) { vm.smartenPunctuation = it }
        if (vm.outputFormat == "epub") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.epub_version_label))
                listOf("2", "3").forEach { version ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.epubVersion == version, onClick = { vm.epubVersion = version })
                        Text(version)
                    }
                }
            }
        }

        OptionGroup(stringResource(R.string.metadata), enabled = enabled) {
            OptionField(
                label = stringResource(if (vm.isBatch) R.string.title_field_batch_disabled else R.string.title_field),
                value = vm.title,
                enabled = !vm.isBatch,
                onValueChange = { vm.title = it },
            )
            OptionField(stringResource(R.string.authors), vm.authors) { vm.authors = it }
            OptionField(stringResource(R.string.publisher), vm.publisher) { vm.publisher = it }
            OptionField(stringResource(R.string.comments), vm.comments) { vm.comments = it }
        }
        OptionGroup(stringResource(R.string.typography), enabled = enabled) {
            OptionField(stringResource(R.string.base_font_size), vm.baseFontSize) { vm.baseFontSize = it }
            CheckboxRow(stringResource(R.string.disable_font_rescaling), vm.disableFontRescaling) { vm.disableFontRescaling = it }
        }
        OptionGroup(stringResource(R.string.margins_pt), enabled = enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionField(stringResource(R.string.margin_top), vm.marginTop, modifier = Modifier.weight(1f)) { vm.marginTop = it }
                OptionField(stringResource(R.string.margin_bottom), vm.marginBottom, modifier = Modifier.weight(1f)) { vm.marginBottom = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionField(stringResource(R.string.margin_left), vm.marginLeft, modifier = Modifier.weight(1f)) { vm.marginLeft = it }
                OptionField(stringResource(R.string.margin_right), vm.marginRight, modifier = Modifier.weight(1f)) { vm.marginRight = it }
            }
        }
        OptionGroup(stringResource(R.string.advanced), enabled = enabled) {
            Text(stringResource(R.string.extra_arguments), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = vm.extraArgs,
                onValueChange = { vm.extraArgs = it },
                label = { Text(stringResource(R.string.extra_arguments_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.remove_exact_text), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = vm.removeText,
                onValueChange = { vm.removeText = it },
                label = { Text(stringResource(R.string.remove_exact_text_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormatDropdown(label: String, value: String, options: List<String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(modifier = Modifier.fillMaxWidth(), expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun OptionGroup(title: String, enabled: Boolean, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expansionDescription = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        TextButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = expansionDescription },
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) content()
    }
}

@Composable
private fun OptionField(label: String, value: String, enabled: Boolean = true, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
    )
}
