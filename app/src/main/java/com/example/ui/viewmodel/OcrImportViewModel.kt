package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Factory
import com.example.data.model.Material
import com.example.data.ocr.OcrExtractedRow
import com.example.data.ocr.OcrMaterialParser
import com.example.data.repository.WmsRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OcrImportUiState(
    val activeFactoryId: String = "",
    val availableLines: List<String> = listOf("Volvo LFT", "CDPO", "Nissan"),
    val selectedImageUri: Uri? = null,
    val isProcessingOcr: Boolean = false,
    val isSaving: Boolean = false,
    val rawRecognizedText: String = "",
    val extractedRows: List<OcrExtractedRow> = emptyList(),
    val hasAttemptedScan: Boolean = false,
    val feedback: ActionFeedback? = null
)

class OcrImportViewModel(
    private val repository: WmsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrImportUiState())
    val uiState: StateFlow<OcrImportUiState> = _uiState.asStateFlow()

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun syncFactoryContext(factoryId: String, activeFactory: Factory?) {
        val lines = (activeFactory?.productionLines.orEmpty() + listOf("Volvo LFT", "CDPO", "Nissan"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        _uiState.update { state ->
            state.copy(
                activeFactoryId = factoryId,
                availableLines = lines
            )
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    isProcessingOcr = true,
                    hasAttemptedScan = true,
                    feedback = null
                )
            }
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val visionText = textRecognizer.process(inputImage).await()
                val rawLines = buildLogicalLines(visionText)
                val parsedRows = OcrMaterialParser.parseTextLines(
                    rawLines = rawLines,
                    knownLines = _uiState.value.availableLines
                )

                _uiState.update { state ->
                    state.copy(
                        isProcessingOcr = false,
                        rawRecognizedText = visionText.text,
                        extractedRows = parsedRows
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit OCR failed", e)
                _uiState.update { state ->
                    state.copy(
                        isProcessingOcr = false,
                        feedback = ActionFeedback(
                            message = e.localizedMessage ?: "Failed to process image with OCR",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    private fun buildLogicalLines(visionText: com.google.mlkit.vision.text.Text): List<String> {
        val allTextLines = visionText.textBlocks.flatMap { it.lines }
        if (allTextLines.isEmpty()) {
            return visionText.text.lines().filter { it.isNotBlank() }
        }
        // Group OCR lines sitting on the same horizontal sheet row using bounding-box vertical center
        val sortedByY = allTextLines.sortedBy { it.boundingBox?.centerY() ?: Int.MAX_VALUE }
        val groupedRows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()

        for (line in sortedByY) {
            val box = line.boundingBox
            if (box == null) {
                groupedRows.add(mutableListOf(line))
                continue
            }
            val lineCenterY = box.centerY()
            val tolerance = (box.height() * 0.6f).coerceAtLeast(18f)
            val matchingGroup = groupedRows.firstOrNull { group ->
                val refBox = group.first().boundingBox ?: return@firstOrNull false
                kotlin.math.abs(refBox.centerY() - lineCenterY) <= tolerance
            }
            if (matchingGroup != null) {
                matchingGroup.add(line)
            } else {
                groupedRows.add(mutableListOf(line))
            }
        }

        val joinedRows = groupedRows.map { rowLines ->
            rowLines
                .sortedBy { it.boundingBox?.left ?: 0 }
                .joinToString(" ") { it.text.trim() }
        }
        return (joinedRows + visionText.text.lines())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun updateRowPartNumber(rowId: String, value: String) {
        val digits = value.filter { it.isDigit() }.take(8)
        _uiState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        row.copy(
                            partNumber = digits,
                            lowConfidencePartNumber = !Material.isValidPartNumber(digits)
                        )
                    } else row
                }
            )
        }
    }

    fun updateRowLocation(rowId: String, value: String) {
        val upper = value.uppercase().take(24)
        _uiState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        row.copy(
                            location = upper,
                            lowConfidenceLocation = upper.isBlank()
                        )
                    } else row
                }
            )
        }
    }

    fun updateRowProductionLine(rowId: String, value: String) {
        _uiState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        row.copy(
                            productionLine = value,
                            lowConfidenceLine = value.isBlank()
                        )
                    } else row
                }
            )
        }
    }

    fun updateRowRequiredQty(rowId: String, value: String) {
        val digits = value.filter { it.isDigit() }.take(8)
        _uiState.update { state ->
            state.copy(
                extractedRows = state.extractedRows.map { row ->
                    if (row.rowId == rowId) {
                        row.copy(
                            requiredQty = digits,
                            lowConfidenceQty = digits.isBlank() || (digits.toLongOrNull() ?: -1L) < 0L
                        )
                    } else row
                }
            )
        }
    }

    fun addManualRow() {
        val defaultLine = _uiState.value.availableLines.firstOrNull() ?: "Volvo LFT"
        val newRow = OcrExtractedRow(
            rowId = "manual_${System.nanoTime()}",
            partNumber = "",
            location = "",
            productionLine = defaultLine,
            requiredQty = "0",
            lowConfidencePartNumber = true,
            lowConfidenceLocation = true,
            lowConfidenceLine = false,
            lowConfidenceQty = true
        )
        _uiState.update { state ->
            state.copy(extractedRows = state.extractedRows + newRow)
        }
    }

    fun removeRow(rowId: String) {
        _uiState.update { state ->
            state.copy(extractedRows = state.extractedRows.filterNot { it.rowId == rowId })
        }
    }

    fun confirmAndSaveToFirestore() {
        val current = _uiState.value
        val factoryId = current.activeFactoryId
        if (factoryId.isBlank()) {
            _uiState.update {
                it.copy(feedback = ActionFeedback("Select an active plant first.", isError = true))
            }
            return
        }
        val rows = current.extractedRows
        if (rows.isEmpty()) {
            _uiState.update {
                it.copy(feedback = ActionFeedback("Add or scan at least one row.", isError = true))
            }
            return
        }

        val invalidRows = rows.filterNot { OcrMaterialParser.isValidRowForCommit(it) }
        if (invalidRows.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    feedback = ActionFeedback(
                        "Fix invalid rows: 8-digit part number, location, line, and Req Qty >= 0 are required.",
                        isError = true
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, feedback = null) }
            var createdCount = 0
            var updatedCount = 0
            var lastError: String? = null

            for (row in rows) {
                val qty = row.requiredQty.toLongOrNull() ?: 0L
                repository.upsertMaterialFromOcr(
                    factoryId = factoryId,
                    partNumber = row.partNumber.trim(),
                    location = row.location.trim().uppercase(),
                    productionLine = row.productionLine.trim(),
                    requiredQty = qty,
                    materialName = row.materialName
                ).onSuccess { outcome ->
                    when (outcome) {
                        WmsRepository.OcrUpsertOutcome.CREATED -> createdCount++
                        WmsRepository.OcrUpsertOutcome.UPDATED -> updatedCount++
                    }
                }.onFailure { error ->
                    lastError = error.localizedMessage ?: "Failed saving ${row.partNumber}"
                }
            }

            _uiState.update { state ->
                val savedAny = createdCount > 0 || updatedCount > 0
                val summaryMsg = if (lastError == null) {
                    "OCR saved to $factoryId: $updatedCount updated, $createdCount created."
                } else {
                    "Saved $updatedCount updated, $createdCount created. Error: $lastError"
                }
                state.copy(
                    isSaving = false,
                    extractedRows = if (lastError == null && savedAny) emptyList() else state.extractedRows,
                    feedback = ActionFeedback(summaryMsg, isError = lastError != null)
                )
            }
        }
    }

    fun postError(message: String) {
        _uiState.update { it.copy(feedback = ActionFeedback(message, isError = true)) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { textRecognizer.close() }
    }

    private companion object {
        const val TAG = "OcrImportViewModel"
    }
}
