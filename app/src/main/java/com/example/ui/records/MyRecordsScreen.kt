package com.example.ui.records

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AppointmentBooking
import com.example.model.ShopInventoryItem
import com.example.model.ShopTransaction
import com.example.model.UtilityBill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.buildRecordsCsv
import com.example.util.maskSensitiveValue
import com.example.util.shareCsvReport
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRecordsScreen(
    bills: List<UtilityBill>,
    appointments: List<AppointmentBooking>,
    transactions: List<ShopTransaction>,
    inventory: List<ShopInventoryItem>,
    onUpdateAppointment: (AppointmentBooking, String, String?, String?) -> Unit,
    onAdjustStock: (ShopInventoryItem, Long) -> Unit,
    onAddShopItem: (String, Long, String, Long, Double) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) }
    var reschedulingAppt by remember { mutableStateOf<AppointmentBooking?>(null) }
    var showAddInventoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "My Records",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Bills, bookings & small-shop cashbook",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val csv = buildRecordsCsv(bills, appointments, transactions, inventory)
                            val ok = shareCsvReport(context, csv)
                            if (ok) {
                                onShowMessage("Exported CSV (${bills.size} bills, ${appointments.size} bookings, ${transactions.size} shop entries)")
                            } else {
                                onShowMessage("Could not open share sheet for CSV export.")
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                }

                PrimaryTabRow(selectedTabIndex = subTab) {
                    Tab(
                        selected = subTab == 0,
                        onClick = { subTab = 0 },
                        text = { Text("Bills (${bills.size})") },
                        modifier = Modifier.testTag("records_tab_bills")
                    )
                    Tab(
                        selected = subTab == 1,
                        onClick = { subTab = 1 },
                        text = { Text("Bookings (${appointments.size})") },
                        modifier = Modifier.testTag("records_tab_appointments")
                    )
                    Tab(
                        selected = subTab == 2,
                        onClick = { subTab = 2 },
                        text = { Text("Shop (${transactions.size})") },
                        modifier = Modifier.testTag("records_tab_shop")
                    )
                }
            }
        }

        when (subTab) {
            0 -> BillsSection(bills = bills)
            1 -> AppointmentsSection(
                appointments = appointments,
                onReschedule = { reschedulingAppt = it },
                onCancel = { appt -> onUpdateAppointment(appt, "Cancelled", null, null) }
            )
            2 -> ShopSection(
                transactions = transactions,
                inventory = inventory,
                onAdjustStock = onAdjustStock,
                onOpenAddItem = { showAddInventoryDialog = true }
            )
        }
    }

    reschedulingAppt?.let { appt ->
        var newDate by remember(appt.id) { mutableStateOf(appt.date) }
        var newTime by remember(appt.id) { mutableStateOf(appt.time) }
        AlertDialog(
            onDismissRequest = { reschedulingAppt = null },
            title = { Text("Reschedule Appointment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(appt.doctorOrOffice, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = newDate,
                        onValueChange = { newDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTime,
                        onValueChange = { newTime = it },
                        label = { Text("Time") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateAppointment(appt, "Rescheduled", newDate, newTime)
                    reschedulingAppt = null
                }) {
                    Text("Save New Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { reschedulingAppt = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddInventoryDialog) {
        var name by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("10") }
        var unit by remember { mutableStateOf("pcs") }
        var threshold by remember { mutableStateOf("4") }
        var price by remember { mutableStateOf("120") }
        AlertDialog(
            onDismissRequest = { showAddInventoryDialog = false },
            title = { Text("Add Inventory Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = qty,
                            onValueChange = { qty = it.filter(Char::isDigit) },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it.filter(Char::isDigit) },
                            label = { Text("Low Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Price (BDT)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddShopItem(
                            name,
                            qty.toLongOrNull() ?: 0L,
                            unit,
                            threshold.toLongOrNull() ?: 4L,
                            price.toDoubleOrNull() ?: 0.0
                        )
                        showAddInventoryDialog = false
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Add Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddInventoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BillsSection(bills: List<UtilityBill>) {
    if (bills.isEmpty()) {
        EmptyRecordsState(
            icon = Icons.Filled.Bolt,
            title = "No bills logged yet",
            tip = "Tap \"Check & Log Electricity Bill\" in Doer Chat or snap a photo of any paper utility bill."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(bills, key = { it.id }) { bill ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${bill.provider} • ${bill.billingMonth}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Acct ${maskSensitiveValue(bill.accountNo)} • ${bill.unitsKwh.roundToInt()} kWh • Due ${bill.dueDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = bill.status,
                            isPositive = bill.status == "Paid"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${bill.amount.roundToInt()} ${bill.currency}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (bill.comparisonNote.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = bill.comparisonNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (bill.referenceId.isNotBlank()) {
                        Text(
                            text = "Receipt Ref: ${bill.referenceId}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentsSection(
    appointments: List<AppointmentBooking>,
    onReschedule: (AppointmentBooking) -> Unit,
    onCancel: (AppointmentBooking) -> Unit
) {
    if (appointments.isEmpty()) {
        EmptyRecordsState(
            icon = Icons.Filled.CalendarMonth,
            title = "No appointments booked yet",
            tip = "Ask Sahayak to book a doctor visit or service counter slot in Doer Chat."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(appointments, key = { it.id }) { appt ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appt.doctorOrOffice,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${appt.department} • ${appt.patientName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = appt.status,
                            isPositive = appt.status != "Cancelled",
                            isError = appt.status == "Cancelled"
                        )
                    }

                    Text(
                        text = "${appt.date} at ${appt.time}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (appt.notes.isNotBlank()) {
                        Text(
                            text = appt.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (appt.status != "Cancelled") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onReschedule(appt) },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Reschedule")
                            }
                            OutlinedButton(
                                onClick = { onCancel(appt) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopSection(
    transactions: List<ShopTransaction>,
    inventory: List<ShopInventoryItem>,
    onAdjustStock: (ShopInventoryItem, Long) -> Unit,
    onOpenAddItem: () -> Unit
) {
    val totalIncome = transactions.filter { it.txType == "SALE" }.sumOf { it.totalAmount }
    val totalExpense = transactions.filter { it.txType != "SALE" }.sumOf { it.totalAmount }
    val lowStockItems = inventory.filter { it.quantity <= it.lowStockThreshold }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Sales Income", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${totalIncome.roundToInt()} BDT",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Expenses & Restock", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${totalExpense.roundToInt()} BDT",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (lowStockItems.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Low stock alert",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Low Stock Alert: ${lowStockItems.joinToString(", ") { "${it.itemName} (${it.quantity} ${it.unit})" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shop Inventory (${inventory.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenAddItem) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item")
                }
            }
        }

        items(inventory, key = { it.id }) { item ->
            val isLow = item.quantity <= item.lowStockThreshold
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (isLow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.itemName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${item.unitPrice.roundToInt()} BDT/${item.unit} • Min ${item.lowStockThreshold}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onAdjustStock(item, -1L) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease ${item.itemName}")
                        }
                        Text(
                            text = "${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        IconButton(onClick = { onAdjustStock(item, 1L) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase ${item.itemName}")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("Cashbook Entries (${transactions.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "No sales or expenses logged yet. Try \"Log Shop Receipt / Sale\" in Doer Chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(transactions, key = { it.id }) { tx ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.itemsSummary, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${tx.txType} • Ref ${tx.referenceId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${if (tx.txType == "SALE") "+" else "-"}${tx.totalAmount.roundToInt()} ${tx.currency}",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = if (tx.txType == "SALE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    isPositive: Boolean,
    isError: Boolean = false
) {
    val bg = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isPositive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val fg = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isPositive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyRecordsState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tip: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
