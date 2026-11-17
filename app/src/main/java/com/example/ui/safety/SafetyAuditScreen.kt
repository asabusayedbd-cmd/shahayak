package com.example.ui.safety

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AuditLogEntry
import com.example.model.UserSettings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.shareAppApk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SafetyAuditScreen(
    settings: UserSettings,
    auditLogs: List<AuditLogEntry>,
    userEmail: String?,
    onUpdatePreferences: (languageMode: String, strictApprovalMode: Boolean) -> Unit,
    onSignOut: () -> Unit
) {
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.GppGood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Safety & Audit Log",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Text(
                    text = "Every tool call, Vault access, confirmation, and receipt is recorded here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Assistant Language", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = settings.languageMode == "en",
                                onClick = { onUpdatePreferences("en", settings.strictApprovalMode) },
                                label = { Text("English") },
                                modifier = Modifier.testTag("lang_en_chip")
                            )
                            FilterChip(
                                selected = settings.languageMode == "bn_en",
                                onClick = { onUpdatePreferences("bn_en", settings.strictApprovalMode) },
                                label = { Text("Bangla + English Mix") },
                                modifier = Modifier.testTag("lang_bn_chip")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Strict Approval Mode",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Always pause with an Approval Card before payments, bookings, or ledger writes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = settings.strictApprovalMode,
                                onCheckedChange = { onUpdatePreferences(settings.languageMode, it) },
                                modifier = Modifier.testTag("strict_approval_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Signed-in Account", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = userEmail ?: "Google Account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            item {
                val context = LocalContext.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Download APK to Your Phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Get the compiled Sahayak Android app package (.apk) to install directly on your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = { shareAppApk(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("download_apk_in_app_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download / Share APK File")
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Activity Audit Trail (${auditLogs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (auditLogs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No activity recorded yet. Start a task in Doer Chat to see real-time safety logs here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(auditLogs, key = { it.id }) { log ->
                    val timeStr = log.createdAt?.toDate()?.let {
                        SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(it)
                    } ?: SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date())

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = when (log.eventType) {
                                        "VAULT_ACCESS" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "USER_APPROVED", "ACTION_EXECUTED", "OTP_VERIFIED" -> MaterialTheme.colorScheme.primaryContainer
                                        "USER_CANCELLED" -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = log.eventType.replace("_", " "),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = log.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (log.details.isNotBlank()) {
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.vaultFieldsAccessed.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Vault fields: ${log.vaultFieldsAccessed.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            if (log.referenceId.isNotBlank()) {
                                Text(
                                    text = "Ref: ${log.referenceId}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
