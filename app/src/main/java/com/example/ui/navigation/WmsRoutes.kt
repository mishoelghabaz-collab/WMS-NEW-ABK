package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface WmsRoute

@Serializable
data object DashboardRoute : WmsRoute

@Serializable
data object InventoryRoute : WmsRoute

@Serializable
data object HistoryRoute : WmsRoute

@Serializable
data object MapRoute : WmsRoute

@Serializable
data object AdminRoute : WmsRoute

@Serializable
data object ScannerRoute : WmsRoute

@Serializable
data object OcrImportRoute : WmsRoute

@Serializable
data class CycleCountRoute(val prefillPartNumber: String = "") : WmsRoute

@Serializable
data object VoiceCommandRoute : WmsRoute
