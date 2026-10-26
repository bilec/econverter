package com.econverter.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConverterTheme {
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
    val canEdit = !vm.isConverting && !vm.isSaving && !vm.pendingSave

    LaunchedEffect(Unit) {
        vm.loadBackendPreference(context)
        vm.purgeStaleTempFiles(context)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val existingUriSet = vm.inputUris.toSet()
            val newUris = uris.distinct().filter { it !in existingUriSet }

            if (newUris.isNotEmpty()) {
                vm.status = "Loading selection..."
                vm.addSelectedUris(context, newUris)
            }
        }
    }

    val dirSavePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.saveToUri(context, uri)
        } else {
            vm.discardPendingSave()
        }
    }

    LaunchedEffect(vm.pendingSave) {
        if (vm.pendingSave) {
            dirSavePicker.launch(null)
        }
    }

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        BackendSettingsDialog(vm.backend, { vm.selectBackend(it, context) }, { showSettings = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("eBook Converter") },
                actions = {
                    IconButton(onClick = { showSettings = true }, enabled = canEdit) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier.navigationBarsPadding().imePadding()
                        .fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.isConverting || vm.isSaving || vm.pendingSave) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Button(
                            onClick = { if (vm.isConverting) vm.cancelConversion() else vm.convert(context) },
                            enabled = (vm.isConverting || vm.canConvert) && !vm.isCancelling && !vm.isSaving && !vm.pendingSave,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(if (vm.isConverting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    vm.isCancelling -> "Cancelling..."
                                    vm.isConverting -> "Cancel"
                                    vm.isSaving -> "Saving..."
                                    vm.pendingSave -> "Choose save folder"
                                    else -> "Convert"
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your books", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (vm.inputFileNames.isNotEmpty()) {
                        TextButton(onClick = vm::clearSelection, enabled = canEdit) { Text("Clear all") }
                    }
                }
                if (vm.inputFileNames.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("No books selected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        "${vm.inputFileNames.size} ${if (vm.isBatch) "books" else "book"} selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    vm.inputUris.zip(vm.inputFileNames).forEach { (uri, name) ->
                        key(uri) {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue, canEdit) {
                                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    if (canEdit) vm.removeSelectedUri(uri) else dismissState.reset()
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                gesturesEnabled = canEdit,
                                modifier = Modifier.semantics(mergeDescendants = true) {
                                    customActions = if (canEdit) {
                                        listOf(
                                            CustomAccessibilityAction("Remove book") {
                                                vm.removeSelectedUri(uri)
                                                true
                                            },
                                        )
                                    } else {
                                        emptyList()
                                    }
                                },
                                backgroundContent = {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Box(
                                            Modifier.padding(horizontal = 12.dp),
                                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                },
                            ) {
                                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(8.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(name.substringAfterLast('.', "").uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = canEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.inputFileNames.isEmpty()) "Select eBooks" else "Add eBooks")
                }
                if (vm.unsupportedInputFileNames.isNotEmpty()) {
                    Text("Not supported by ${vm.backend.label}: ${vm.unsupportedInputFileNames.joinToString(", ")}", color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Output", style = MaterialTheme.typography.titleLarge)
                FormatDropdown("Output Format", vm.outputFormat, vm.outputFormats, enabled = canEdit) { vm.outputFormat = it }
                Text("Backend: ${vm.backend.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                ConversionOptions(vm, enabled = canEdit)

                if (vm.status.isNotEmpty()) {
                    val isError = vm.status.startsWith("Error:")
                    Surface(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(
                                when {
                                    isError -> Icons.Default.Warning
                                    vm.status.startsWith("Done") -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                            )
                            Text(vm.status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (vm.status.startsWith("Error:")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { reportOnGitHub(context, vm) }) {
                        Text("Report Issue")
                    }
                }

                if (vm.history.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    var showHistory by remember { mutableStateOf(false) }
                    TextButton(onClick = { showHistory = !showHistory }, contentPadding = PaddingValues(0.dp)) {
                        Icon(if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("History (${vm.history.size})")
                    }
                    if (showHistory) {
                        vm.history.asReversed().forEach { entry ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = canEdit, onClickLabel = "Select books") { vm.selectHistory(entry) }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text("${entry.fileName} → ${entry.outputFormat} (${entry.backend.label})", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))} — ${entry.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String {
    var name = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                name = cursor.getString(idx) ?: ""
            }
        }
    } catch (_: Exception) {}

    if (name.isBlank()) {
        name = uri.lastPathSegment?.substringAfterLast("/") ?: ""
    }
    return name.ifBlank { "unknown" }
}
