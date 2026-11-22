package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firebase.CloudObservationsUiState
import com.example.data.firebase.RukyatObservation
import com.example.domain.falak.HilalResult
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun RukyatObservationsCard(
    currentHilal: HilalResult,
    observationsState: CloudObservationsUiState,
    isSavingObservation: Boolean,
    onSaveObservation: (hilalSighted: Boolean, notes: String) -> Unit,
    onUpdateObservation: (observationId: String, hilalSighted: Boolean, notes: String) -> Unit,
    onDeleteObservation: (observationId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingObservation by remember { mutableStateOf<RukyatObservation?>(null) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("cloud_observations_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Log Rukyatul Hilal (Cloud)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Simpan & sinkronkan catatan pengamatan hisab/rukyat Anda",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                enabled = !isSavingObservation,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("save_observation_button")
            ) {
                if (isSavingObservation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Catat Pengamatan ${currentHilal.hijriMonthName} ${currentHilal.hijriYear} H")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (observationsState) {
                is CloudObservationsUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Menyinkronkan log rukyat dari Cloud...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is CloudObservationsUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Sinkronisasi Cloud terganggu: ${observationsState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                is CloudObservationsUiState.Success -> {
                    val items = observationsState.items
                    if (items.isEmpty()) {
                        Text(
                            text = "Belum ada log rukyat tersimpan. Tekan tombol di atas untuk mencatat hasil pengamatan bulan ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEach { item ->
                                ObservationRowItem(
                                    item = item,
                                    onEdit = { editingObservation = item },
                                    onDelete = { onDeleteObservation(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ObservationEditorDialog(
            title = "Simpan Log Rukyat ${currentHilal.hijriMonthName} ${currentHilal.hijriYear} H",
            subtitle = "${currentHilal.markaz.name} • Tinggi ${String.format(Locale.US, "%.2f°", currentHilal.moonAltitudeMariDeg)} • Elongasi ${String.format(Locale.US, "%.2f°", currentHilal.elongationTopoDeg)}",
            initialSighted = false,
            initialNotes = "",
            confirmLabel = "Simpan ke Cloud",
            onDismiss = { showCreateDialog = false },
            onConfirm = { sighted, notes ->
                showCreateDialog = false
                onSaveObservation(sighted, notes)
            }
        )
    }

    val editing = editingObservation
    if (editing != null) {
        ObservationEditorDialog(
            title = "Perbarui Catatan ${editing.hijriMonthName} ${editing.hijriYear} H",
            subtitle = "${editing.markazName} (${editing.observationDateLabel})",
            initialSighted = editing.hilalSighted,
            initialNotes = editing.notes,
            confirmLabel = "Simpan Perubahan",
            onDismiss = { editingObservation = null },
            onConfirm = { sighted, notes ->
                val targetId = editing.id
                editingObservation = null
                onUpdateObservation(targetId, sighted, notes)
            }
        )
    }
}

@Composable
private fun ObservationRowItem(
    item: RukyatObservation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("observation_row_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.hijriMonthName} ${item.hijriYear} H — ${item.verdictSummary}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.markazName} • ${item.observationDateLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_observation_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Ubah catatan rukyat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_observation_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Hapus log rukyat",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (item.hilalSighted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (item.hilalSighted) Icons.Outlined.CheckCircle else Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (item.hilalSighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (item.hilalSighted) "Hilal Terlihat" else "Belum Teramati",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.hilalSighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = String.format(
                        Locale.US,
                        "h %.2f° • el %.2f° • %s",
                        item.moonAltitudeMariDeg,
                        item.elongationTopoDeg,
                        item.criteriaCode
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ObservationEditorDialog(
    title: String,
    subtitle: String,
    initialSighted: Boolean,
    initialNotes: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String) -> Unit
) {
    var hilalSighted by remember { mutableStateOf(initialSighted) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hilalSighted,
                        onClick = { hilalSighted = true },
                        label = { Text("Hilal Terlihat") },
                        modifier = Modifier.testTag("dialog_sighted_true")
                    )
                    FilterChip(
                        selected = !hilalSighted,
                        onClick = { hilalSighted = false },
                        label = { Text("Tidak Terlihat") },
                        modifier = Modifier.testTag("dialog_sighted_false")
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 500) notes = it },
                    label = { Text("Catatan Lapangan (opsional, maks 500 karakter)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_observation_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hilalSighted, notes) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_observation_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Batal")
            }
        }
    )
}
