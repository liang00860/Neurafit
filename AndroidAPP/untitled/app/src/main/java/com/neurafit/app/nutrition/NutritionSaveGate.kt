package com.neurafit.app.nutrition

class NutritionSaveGate {
    private var lastSubmittedScanId: String? = null

    fun shouldSubmit(scanId: String, force: Boolean = false): Boolean {
        if (scanId.isBlank()) return false
        if (!force && lastSubmittedScanId == scanId) return false

        lastSubmittedScanId = scanId
        return true
    }
}
