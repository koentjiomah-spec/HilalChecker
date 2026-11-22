package com.example.data.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserFalakProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val preferredMarkazId: String = "pelabuhan_ratu",
    val preferredMarkazName: String = "POS Observasi Pelabuhan Ratu",
    val preferredCriteria: String = "MABIMS_BARU",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RukyatObservation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val hijriYear: Long = 1448L,
    val hijriMonth: Long = 1L,
    val hijriMonthName: String = "Muharram",
    val observationDateLabel: String = "",
    val markazName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val moonAltitudeMariDeg: Double = 0.0,
    val elongationTopoDeg: Double = 0.0,
    val criteriaCode: String = "MABIMS Baru",
    val verdictSummary: String = "",
    val visibilityStatus: String = "TIDAK_MEMENUHI",
    val hilalSighted: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface CloudObservationsUiState {
    data object Loading : CloudObservationsUiState
    data class Success(val items: List<RukyatObservation>) : CloudObservationsUiState
    data class Error(val message: String) : CloudObservationsUiState
}
