package com.neurafit.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurafit.app.data.TrainingRecord
import com.neurafit.app.data.TrainingRepository
import com.neurafit.app.learning.UserActionProfile
import com.neurafit.app.learning.UserProfileBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrainingHistoryUiState(
    val isLoading: Boolean = true,
    val records: List<TrainingRecord> = emptyList(),
    val actionProfile: UserActionProfile? = null,
    val selectedRecordId: Long? = null,
    val errorMessage: String? = null,
) {
    val selectedRecord: TrainingRecord?
        get() = records.firstOrNull { it.id == selectedRecordId }
}

class TrainingHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrainingRepository.create(application)
    private val _uiState = MutableStateFlow(TrainingHistoryUiState())
    val uiState: StateFlow<TrainingHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.getTrainingRecords()
                }
            }.onSuccess { records ->
                val currentSelection = _uiState.value.selectedRecordId
                val profileActionName = records.firstOrNull()?.actionName
                _uiState.value = TrainingHistoryUiState(
                    isLoading = false,
                    records = records,
                    actionProfile = profileActionName?.let { UserProfileBuilder.build(records, actionName = it) },
                    selectedRecordId = currentSelection?.takeIf { id -> records.any { it.id == id } },
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.localizedMessage ?: "Unable to load training history.",
                )
            }
        }
    }

    fun selectRecord(record: TrainingRecord) {
        _uiState.value = _uiState.value.copy(selectedRecordId = record.id)
    }

    fun closeDetails() {
        _uiState.value = _uiState.value.copy(selectedRecordId = null)
    }
}
