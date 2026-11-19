package com.neurafit.app.nutrition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NutritionRepository(application.applicationContext)
    private val recipeRepository = RecipeSelectionRepository(application.applicationContext)
    private val saveGate = NutritionSaveGate()
    private val recipeSaveGate = NutritionSaveGate()
    private val _saveState = MutableStateFlow<NutritionSaveUiState>(NutritionSaveUiState.Idle)
    private val _recipeSaveState = MutableStateFlow<RecipeSelectionUiState>(RecipeSelectionUiState.Idle)

    val saveState: StateFlow<NutritionSaveUiState> = _saveState.asStateFlow()
    val recipeSaveState: StateFlow<RecipeSelectionUiState> = _recipeSaveState.asStateFlow()

    fun saveScan(record: NutritionScanRecord, force: Boolean = false) {
        if (!saveGate.shouldSubmit(record.scanId, force)) return

        _saveState.value = NutritionSaveUiState.Saving
        viewModelScope.launch {
            _saveState.value = when (val result = repository.save(record)) {
                is NutritionSaveResult.Success -> NutritionSaveUiState.Saved(result.recordId)
                NutritionSaveResult.NeedsLogin -> NutritionSaveUiState.NeedsLogin
                is NutritionSaveResult.Error -> NutritionSaveUiState.Error(result.message)
            }
        }
    }

    fun saveSelectedRecipe(record: SelectedRecipeRecord, force: Boolean = false) {
        if (!recipeSaveGate.shouldSubmit(record.recipeId, force)) return

        _recipeSaveState.value = RecipeSelectionUiState.Saving
        viewModelScope.launch {
            _recipeSaveState.value = when (val result = recipeRepository.save(record)) {
                is RecipeSelectionResult.Success -> RecipeSelectionUiState.Saved(result.recordId)
                RecipeSelectionResult.NeedsLogin -> RecipeSelectionUiState.NeedsLogin
                is RecipeSelectionResult.Error -> RecipeSelectionUiState.Error(result.message)
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = NutritionSaveUiState.Idle
    }

    fun resetRecipeSaveState() {
        _recipeSaveState.value = RecipeSelectionUiState.Idle
    }
}
