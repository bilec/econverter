package com.econverter.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConverterScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(vm: ConverterViewModel = viewModel()) {
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = getFileName(context, it)
            if (vm.isInputSupported(name)) {
                vm.inputUri = it
                vm.inputFileName = name
                vm.status = ""
            } else {
                val ext = name.substringAfterLast(".", "")
                vm.status = "Unsupported format: ${ext.ifEmpty { "(no extension)" }}"
            }
        }
    }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            vm.saveToUri(context, uri)
        } else {
            vm.pendingSave = false
            vm.status = "Save cancelled"
        }
    }

    LaunchedEffect(vm.pendingSave) {
        if (vm.pendingSave) {
            savePicker.launch(vm.getOutputFileName())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("eBook Converter", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
            Text("Select eBook")
        }

        if (vm.inputFileName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Input: ${vm.inputFileName}", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        FormatDropdown("Output Format", vm.outputFormat, vm.outputFormats) { vm.outputFormat = it }

        Spacer(modifier = Modifier.height(16.dp))

        var showOptions by remember { mutableStateOf(false) }
        TextButton(onClick = { showOptions = !showOptions }) {
            Text(if (showOptions) "▲ Hide Options" else "▼ Options")
        }
        if (showOptions) {
            FormatDropdown("Output Profile", vm.outputProfile, vm.outputProfiles) { vm.outputProfile = it }
            Spacer(modifier = Modifier.height(8.dp))
            CheckboxRow("Smarten punctuation", vm.smartenPunctuation) { vm.smartenPunctuation = it }
            if (vm.outputFormat == "epub") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("EPUB version: ")
                    listOf("2", "3").forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = vm.epubVersion == v, onClick = { vm.epubVersion = v })
                            Text(v)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Metadata", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OptionField("Title", vm.title) { vm.title = it }
            OptionField("Authors", vm.authors) { vm.authors = it }
            OptionField("Publisher", vm.publisher) { vm.publisher = it }
            OptionField("Comments", vm.comments) { vm.comments = it }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Font", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OptionField("Base font size (pt)", vm.baseFontSize) { vm.baseFontSize = it }
            CheckboxRow("Disable font rescaling", vm.disableFontRescaling) { vm.disableFontRescaling = it }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Margins (pt)", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = vm.marginTop, onValueChange = { vm.marginTop = it }, label = { Text("Top") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = vm.marginBottom, onValueChange = { vm.marginBottom = it }, label = { Text("Bottom") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = vm.marginLeft, onValueChange = { vm.marginLeft = it }, label = { Text("Left") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = vm.marginRight, onValueChange = { vm.marginRight = it }, label = { Text("Right") }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Extra arguments", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = vm.extraArgs,
                onValueChange = { vm.extraArgs = it },
                label = { Text("e.g. --chapter-mark pagebreak") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { vm.convert(context) },
            enabled = vm.inputUri != null && !vm.isConverting,
        ) {
            Text(if (vm.isConverting) "Converting..." else "Convert")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (vm.status.isNotEmpty()) {
            Text(vm.status, style = MaterialTheme.typography.bodyLarge)
        }

        if (vm.status.startsWith("Error:")) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { reportOnGitHub(context, vm) }) {
                Text("Report Issue")
            }
        }
    }
}

// ponytail: tiny composable helpers to reduce repetition in the options section
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun OptionField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

private fun reportOnGitHub(context: android.content.Context, vm: ConverterViewModel) {
    val body = buildString {
        appendLine("**Error:** ${vm.status}")
        appendLine("**Input file:** ${vm.inputFileName}")
        appendLine("**Output format:** ${vm.outputFormat}")
        appendLine("**Output profile:** ${vm.outputProfile}")
        appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("**Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        if (vm.extraArgs.isNotBlank()) appendLine("**Extra args:** ${vm.extraArgs}")
    }
    val title = "Conversion failed: ${vm.inputFileName.substringAfterLast(".")} → ${vm.outputFormat}"
    val url = "https://github.com/bilec/econverter/issues/new" +
        "?title=${Uri.encode(title)}" +
        "&body=${Uri.encode(body)}" +
        "&labels=bug"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var name = "unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) {
            name = cursor.getString(idx)
        }
    }
    return name
}
