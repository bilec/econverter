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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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
    val removeBookLabel = stringResource(R.string.remove_book)
    val canEdit = !vm.isConverting && !vm.isSaving && !vm.pendingSave

    LaunchedEffect(Unit) {
        vm.loadBackendPreference(context)
        vm.purgeStaleTempFiles(context)
    }

    LaunchedEffect(vm.status, vm.shouldAutoDismissStatus) {
        if (vm.shouldAutoDismissStatus) {
            delay(3_000)
            vm.status = StatusMessage()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val existingUriSet = vm.inputUris.toSet()
            val newUris = uris.distinct().filter { it !in existingUriSet }

            if (newUris.isNotEmpty()) {
                vm.addSelectedUris(context, newUris)
            }
        }
    }

    val dirSavePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.saveToUri(context, uri)
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
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = { showSettings = true }, enabled = canEdit) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
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
                            val progress = vm.conversionProgress
                            if (progress != null) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        if (vm.pendingSave && !vm.isSaving) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { dirSavePicker.launch(null) },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.save))
                                }
                                OutlinedButton(
                                    onClick = { vm.createShareIntent(context)?.let(context::startActivity) },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.share))
                                }
                                IconButton(onClick = vm::discardPendingSave) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                                }
                            }
                        } else {
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
                                        vm.isCancelling -> stringResource(R.string.cancelling)
                                        vm.isConverting -> stringResource(R.string.cancel)
                                        vm.isSaving -> stringResource(R.string.saving)
                                        else -> stringResource(R.string.convert)
                                    },
                                )
                            }
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
                    Text(stringResource(R.string.your_books), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (vm.inputFileNames.isNotEmpty()) {
                        TextButton(onClick = vm::clearSelection, enabled = canEdit) { Text(stringResource(R.string.clear_all)) }
                    }
                }
                if (vm.inputFileNames.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.no_books_selected), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        stringResource(R.string.books_selected, vm.inputFileNames.size),
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
                                            CustomAccessibilityAction(removeBookLabel) {
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
                    Text(stringResource(if (vm.inputFileNames.isEmpty()) R.string.select_ebooks else R.string.add_ebooks))
                }
                if (vm.unsupportedInputFileNames.isNotEmpty()) {
                    Text(stringResource(R.string.not_supported_by, stringResource(vm.backend.labelRes), vm.unsupportedInputFileNames.joinToString(", ")), color = MaterialTheme.colorScheme.error)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text(stringResource(R.string.output), style = MaterialTheme.typography.titleLarge)
                FormatDropdown(stringResource(R.string.output_format), vm.outputFormat, vm.outputFormats, enabled = canEdit) { vm.outputFormat = it }
                Text(stringResource(R.string.backend_label, stringResource(vm.backend.labelRes)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                ConversionOptions(vm, enabled = canEdit)

                if (vm.status.resourceId != 0) {
                    val isError = vm.status.isError
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
                                    vm.status.isSuccess -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                            )
                            Text(vm.status.text(context), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (vm.status.isError) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { reportOnGitHub(context, vm) }) {
                        Text(stringResource(R.string.report_issue))
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
                        Text(stringResource(R.string.history, vm.history.size))
                    }
                    if (showHistory) {
                        vm.history.asReversed().forEach { entry ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = canEdit, onClickLabel = stringResource(R.string.select_books)) { vm.selectHistory(entry) }
                                    .padding(vertical = 12.dp),
                            ) {
                                val entryName = if (entry.fileNames.size > 1) stringResource(R.string.books_selected, entry.fileNames.size) else entry.fileNames.firstOrNull().orEmpty()
                                Text("$entryName → ${entry.outputFormat} (${stringResource(entry.backend.labelRes)})", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp))} — ${entry.status.text(context)}",
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
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: ""
            }
        }
    } catch (_: Exception) {}

    if (name.isBlank()) {
        name = uri.lastPathSegment?.substringAfterLast("/") ?: ""
    }
    return name.ifBlank { "unknown" }
}
