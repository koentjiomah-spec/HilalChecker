package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FalakReportExporter
import com.example.domain.falak.FalakReportGenerator
import com.example.domain.falak.FalakReportInput
import com.example.domain.falak.ReportScope
import com.example.ui.HilalUiState
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportBottomSheet(
    state: HilalUiState,
    scope: ReportScope,
    onSelectScope: (ReportScope) -> Unit,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isExporting by remember { mutableStateOf(false) }

    val reportInput = remember(
        state.markaz,
        state.selectedCriteria,
        state.hilalResult,
        state.mabimsPrediction,
        state.lunarPhaseData,
        state.trueNoonResult,
        state.istiwakResult,
        scope
    ) {
        FalakReportInput(
            markaz = state.markaz,
            selectedCriteria = state.selectedCriteria,
            hilalResult = state.hilalResult,
            mabimsPrediction = state.mabimsPrediction,
            lunarPhaseData = state.lunarPhaseData,
            trueNoonResult = state.trueNoonResult,
            istiwakResult = state.istiwakResult,
            scope = scope
        )
    }

    val previewText = remember(reportInput) {
        FalakReportGenerator.buildTextReport(reportInput)
    }
    val suggestedBaseName = remember(reportInput) {
        FalakReportGenerator.suggestedFileName(reportInput)
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            FalakReportExporter.writePdfToOutputStream(reportInput, out)
                        } ?: error("Output stream tidak tersedia")
                    }.isSuccess
                }
                isExporting = false
                if (ok) {
                    onShowMessage("Laporan PDF berhasil disimpan")
                    onDismiss()
                } else {
                    onShowMessage("Gagal menyimpan file PDF")
                }
            }
        }
    }

    val createTextLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            FalakReportExporter.writeTextToOutputStream(reportInput, out)
                        } ?: error("Output stream tidak tersedia")
                    }.isSuccess
                }
                isExporting = false
                if (ok) {
                    onShowMessage("Laporan teks (.txt) berhasil disimpan")
                    onDismiss()
                } else {
                    onShowMessage("Gagal menyimpan file teks")
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.export_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.markaz.name} • ${state.markaz.region}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scope selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportScope.entries.forEach { item ->
                    FilterChip(
                        selected = item == scope,
                        onClick = { onSelectScope(item) },
                        label = { Text(item.label) },
                        modifier = Modifier.testTag("export_scope_${item.name}")
                    )
                }
            }

            // Preview box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 230.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("export_preview_text")
                    )
                }
            }

            if (isExporting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menyusun dokumen laporan...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary PDF Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { createPdfLauncher.launch("$suggestedBaseName.pdf") },
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_save_pdf_button")
                ) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simpan PDF")
                }

                OutlinedButton(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            val uriResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    FalakReportExporter.createShareablePdfUri(context, reportInput)
                                }
                            }
                            isExporting = false
                            uriResult.onSuccess { uri ->
                                FalakReportExporter.launchSharePdfIntent(
                                    context = context,
                                    pdfUri = uri,
                                    title = "Laporan Hilal & Istiwak — ${state.markaz.name}"
                                )
                            }.onFailure {
                                onShowMessage("Gagal membuat PDF untuk dibagikan")
                            }
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_share_pdf_button")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bagikan PDF")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Secondary Text Report Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { createTextLauncher.launch("$suggestedBaseName.txt") },
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_save_txt_button")
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan TXT")
                }

                OutlinedButton(
                    onClick = {
                        FalakReportExporter.launchShareTextIntent(
                            context = context,
                            textReport = previewText,
                            subject = "Laporan Falak — ${state.markaz.name}"
                        )
                    },
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("export_share_txt_button")
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bagikan Teks")
                }
            }

            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Laporan Hilal & Istiwak", previewText))
                    onShowMessage("Laporan teks disalin ke clipboard")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("export_copy_text_button")
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salin Semua ke Clipboard")
            }
        }
    }
}
