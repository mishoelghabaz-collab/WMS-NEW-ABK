package com.example.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Material
import com.example.data.model.TransactionType
import com.example.ui.theme.WmsMonoTypography
import com.example.ui.theme.wmsColors

@Composable
fun CreateFactoryDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (code: String, name: String, linesCsv: String, zonesCsv: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("Volvo LFT, CDPO") }
    var zones by remember { mutableStateOf("A-01, B-01, C-01") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.dialog_create_plant_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = { Text(stringResource(R.string.dialog_plant_code)) },
                    textStyle = WmsMonoTypography.partNumber,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_plant_code_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_plant_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_plant_name_input")
                )
                OutlinedTextField(
                    value = lines,
                    onValueChange = { lines = it },
                    label = { Text(stringResource(R.string.dialog_plant_lines)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = zones,
                    onValueChange = { zones = it },
                    label = { Text(stringResource(R.string.dialog_plant_zones)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code, name, lines, zones) },
                enabled = !isSubmitting && code.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.testTag("dialog_create_plant_confirm")
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun QuickStockMovementDialog(
    initialType: TransactionType,
    canRegisterMaterial: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSwitchToRegisterSku: () -> Unit,
    onConfirm: (partNumber: String, type: TransactionType, qty: Long, targetLocation: String, notes: String) -> Unit
) {
    var partNumber by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var targetLocation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val partError = stringResource(R.string.error_part_number_8_digits)
    val qtyError = stringResource(R.string.error_positive_qty)
    val wmsColors = MaterialTheme.wmsColors

    val (accentColor, onAccentColor, typeLabel) = when (initialType) {
        TransactionType.RECEIVE -> Triple(wmsColors.receiveGreen, wmsColors.onReceiveGreen, stringResource(R.string.tx_receive))
        TransactionType.ISSUE -> Triple(wmsColors.issueRed, wmsColors.onIssueRed, stringResource(R.string.tx_issue))
        TransactionType.TRANSFER -> Triple(wmsColors.transferAmber, wmsColors.onTransferAmber, stringResource(R.string.tx_transfer))
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.dialog_stock_tx_title)} · $typeLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = partNumber,
                    onValueChange = { input ->
                        partNumber = input.filter { it.isDigit() }.take(8)
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.dialog_part_number)) },
                    textStyle = WmsMonoTypography.partNumber,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_part_number_input")
                )

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { input ->
                        qtyText = input.filter { it.isDigit() }
                        validationError = null
                    },
                    label = { Text(stringResource(R.string.dialog_quantity)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_qty_input")
                )

                if (initialType == TransactionType.TRANSFER) {
                    OutlinedTextField(
                        value = targetLocation,
                        onValueChange = {
                            targetLocation = it.uppercase()
                            validationError = null
                        },
                        label = { Text(stringResource(R.string.dialog_target_location)) },
                        textStyle = WmsMonoTypography.location,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tx_target_location_input")
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.dialog_notes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (canRegisterMaterial && initialType == TransactionType.RECEIVE) {
                    Spacer(modifier = Modifier.height(2.dp))
                    TextButton(
                        onClick = onSwitchToRegisterSku,
                        modifier = Modifier.testTag("switch_register_sku_button")
                    ) {
                        Text(stringResource(R.string.dialog_new_sku_switch))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = qtyText.toLongOrNull() ?: 0L
                    when {
                        !Material.isValidPartNumber(partNumber) -> validationError = partError
                        qty <= 0L -> validationError = qtyError
                        initialType == TransactionType.TRANSFER && targetLocation.isBlank() ->
                            validationError = "Target location is required."
                        else -> onConfirm(partNumber, initialType, qty, targetLocation, notes)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor
                ),
                modifier = Modifier.testTag("tx_confirm_button")
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun RegisterMaterialDialog(
    defaultProductionLine: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        partNumber: String,
        name: String,
        initialQty: Long,
        minThreshold: Long,
        location: String,
        productionLine: String
    ) -> Unit
) {
    var partNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("0") }
    var thresholdText by remember { mutableStateOf("10") }
    var location by remember { mutableStateOf("A-01-01") }
    var line by remember { mutableStateOf(defaultProductionLine.ifBlank { "Volvo LFT" }) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val partError = stringResource(R.string.error_part_number_8_digits)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.dialog_add_material_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = partNumber,
                    onValueChange = {
                        partNumber = it.filter { ch -> ch.isDigit() }.take(8)
                        errorText = null
                    },
                    label = { Text(stringResource(R.string.dialog_part_number)) },
                    textStyle = WmsMonoTypography.partNumber,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_sku_part_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_material_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_sku_name_input")
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.dialog_quantity)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.dialog_min_threshold)) },
                    textStyle = WmsMonoTypography.quantityMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.uppercase() },
                    label = { Text(stringResource(R.string.dialog_location)) },
                    textStyle = WmsMonoTypography.location,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    label = { Text(stringResource(R.string.dialog_production_line)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!Material.isValidPartNumber(partNumber)) {
                        errorText = partError
                    } else if (name.isBlank() || location.isBlank() || line.isBlank()) {
                        errorText = "Name, location, and line are required."
                    } else {
                        onConfirm(
                            partNumber,
                            name,
                            qtyText.toLongOrNull() ?: 0L,
                            thresholdText.toLongOrNull() ?: 10L,
                            location,
                            line
                        )
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.wmsColors.receiveGreen,
                    contentColor = MaterialTheme.wmsColors.onReceiveGreen
                ),
                modifier = Modifier.testTag("register_sku_confirm_button")
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
