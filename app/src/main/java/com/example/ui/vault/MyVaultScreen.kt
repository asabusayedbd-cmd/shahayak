package com.example.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.VaultField
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.maskSensitiveValue

@Composable
fun MyVaultScreen(
    vaultFields: List<VaultField>,
    onToggleAutoFill: (VaultField, Boolean) -> Unit,
    onUpdateValue: (VaultField, String) -> Unit
) {
    val revealedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var editingField by remember { mutableStateOf<VaultField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "My Personal Vault",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Text(
                    text = "Store your reusable details once. Only fields with \"Allow Agent Auto-Fill\" turned ON can be read by Sahayak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vaultFields.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Loading your Personal Vault...", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vaultFields, key = { it.id.ifBlank { it.fieldKey } }) { field ->
                    val isRevealed = revealedKeys[field.fieldKey] ?: !field.isSensitive
                    val displayValue = if (field.isSensitive && !isRevealed) {
                        maskSensitiveValue(field.value)
                    } else {
                        field.value
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (field.allowAutoFill) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_card_${field.fieldKey}")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = field.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = displayValue.ifBlank { "Not set" },
                                        style = if (field.isSensitive) {
                                            MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily)
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row {
                                    if (field.isSensitive) {
                                        IconButton(
                                            onClick = { revealedKeys[field.fieldKey] = !isRevealed },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("reveal_toggle_${field.fieldKey}")
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (isRevealed) "Hide ${field.label}" else "Reveal ${field.label}",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { editingField = field },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_vault_${field.fieldKey}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit ${field.label}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = if (field.allowAutoFill) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Allow Agent Auto-Fill",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = field.allowAutoFill,
                                        onCheckedChange = { onToggleAutoFill(field, it) },
                                        modifier = Modifier.testTag("consent_switch_${field.fieldKey}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingField?.let { field ->
        var textValue by remember(field.fieldKey) { mutableStateOf(field.value) }
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text("Update ${field.label}") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text(field.label) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateValue(field, textValue)
                    editingField = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
