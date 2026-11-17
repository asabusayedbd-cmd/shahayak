package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AppointmentBooking
import com.example.model.AuditLogEntry
import com.example.model.ChatMessage
import com.example.model.ShopInventoryItem
import com.example.model.ShopTransaction
import com.example.model.UserSettings
import com.example.model.UtilityBill
import com.example.model.VaultField
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SahayakRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeSettings(uid: String): Flow<UserSettings> = flow {
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: UserSettings(userId = uid)
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    fun observeVault(uid: String): Flow<List<VaultField>> = flow {
        val path = "users/$uid/vault"
        emitAll(
            userDoc(uid).collection("vault")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        VaultField::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { vaultSortOrder(it.fieldKey) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeMessages(uid: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/messages"
        emitAll(
            userDoc(uid).collection("messages")
                .orderBy("clientTimestampMs", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeBills(uid: String): Flow<List<UtilityBill>> = flow {
        val path = "users/$uid/bills"
        emitAll(
            userDoc(uid).collection("bills")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UtilityBill::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeAppointments(uid: String): Flow<List<AppointmentBooking>> = flow {
        val path = "users/$uid/appointments"
        emitAll(
            userDoc(uid).collection("appointments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AppointmentBooking::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeShopTransactions(uid: String): Flow<List<ShopTransaction>> = flow {
        val path = "users/$uid/shop_transactions"
        emitAll(
            userDoc(uid).collection("shop_transactions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ShopTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeShopInventory(uid: String): Flow<List<ShopInventoryItem>> = flow {
        val path = "users/$uid/shop_inventory"
        emitAll(
            userDoc(uid).collection("shop_inventory")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ShopInventoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.itemName }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeAuditLogs(uid: String): Flow<List<AuditLogEntry>> = flow {
        val path = "users/$uid/audit_logs"
        emitAll(
            userDoc(uid).collection("audit_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AuditLogEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    private suspend fun handleStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserSeeded(displayName: String?, email: String?): Result<Unit> = runCatching {
        val uid = requireUserId()
        val settingsSnap = try {
            userDoc(uid).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            throw e
        }

        if (settingsSnap.exists() && settingsSnap.getBoolean("seededDefaults") == true) {
            return@runCatching
        }

        val batch = db.batch()
        if (!settingsSnap.exists()) {
            batch.set(
                userDoc(uid),
                mapOf(
                    "userId" to uid,
                    "languageMode" to "en",
                    "strictApprovalMode" to true,
                    "seededDefaults" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.update(
                userDoc(uid),
                mapOf(
                    "seededDefaults" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: "Rahim Uddin"
        val resolvedEmail = email?.takeIf { it.isNotBlank() } ?: "rahim.uddin@example.com"

        val defaultVault = listOf(
            VaultSeed("full_name", "Full Name", resolvedName, isSensitive = false, allowAutoFill = true),
            VaultSeed("phone", "Phone Number", "+880 1712-454821", isSensitive = true, allowAutoFill = true),
            VaultSeed("email", "Email Address", resolvedEmail, isSensitive = false, allowAutoFill = true),
            VaultSeed("address", "Home Address", "House 14, Road 6, Mirpur-10, Dhaka", isSensitive = false, allowAutoFill = true),
            VaultSeed("national_id", "National ID", "1991269482104", isSensitive = true, allowAutoFill = false),
            VaultSeed("meter_id", "Electricity Meter ID", "48219044", isSensitive = true, allowAutoFill = true),
            VaultSeed("preferred_clinic", "Preferred Clinic", "Green Care Clinic, Mirpur", isSensitive = false, allowAutoFill = true),
            VaultSeed("emergency_contact", "Emergency Contact", "Fatema Begum (+880 1811-903312)", isSensitive = true, allowAutoFill = false)
        )

        defaultVault.forEach { item ->
            val ref = userDoc(uid).collection("vault").document(item.key)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "fieldKey" to item.key,
                    "label" to item.label,
                    "value" to item.value,
                    "isSensitive" to item.isSensitive,
                    "allowAutoFill" to item.allowAutoFill,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val historicalBills = listOf(
            mapOf(
                "userId" to uid,
                "provider" to "DESCO Electricity",
                "accountNo" to "48219044",
                "billingMonth" to "October 2026",
                "unitsKwh" to 274.0,
                "amount" to 2150.0,
                "currency" to "BDT",
                "dueDate" to "2026-10-25",
                "previousAmount" to 1980.0,
                "comparisonNote" to "+8.6% vs Sep (AC evening usage)",
                "status" to "Paid",
                "referenceId" to "SAH-BILL-1026",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            mapOf(
                "userId" to uid,
                "provider" to "DESCO Electricity",
                "accountNo" to "48219044",
                "billingMonth" to "September 2026",
                "unitsKwh" to 252.0,
                "amount" to 1980.0,
                "currency" to "BDT",
                "dueDate" to "2026-09-25",
                "previousAmount" to 1980.0,
                "comparisonNote" to "Baseline billing month",
                "status" to "Paid",
                "referenceId" to "SAH-BILL-0926",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        historicalBills.forEachIndexed { idx, payload ->
            val ref = userDoc(uid).collection("bills").document("seed_bill_$idx")
            batch.set(ref, payload)
        }

        val starterInventory = listOf(
            Triple("inv_rice", "Miniket Rice (5kg)", Triple(14L, "bags", 480.0) to 5L),
            Triple("inv_oil", "Soybean Oil (2L)", Triple(3L, "bottles", 360.0) to 5L),
            Triple("inv_dal", "Masoor Dal (1kg)", Triple(18L, "packs", 145.0) to 6L),
            Triple("inv_sugar", "Refined Sugar (1kg)", Triple(4L, "packs", 130.0) to 5L)
        )
        starterInventory.forEach { (id, name, details) ->
            val (qtyUnitPrice, threshold) = details
            val (qty, unit, price) = qtyUnitPrice
            val ref = userDoc(uid).collection("shop_inventory").document(id)
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "itemName" to name,
                    "quantity" to qty,
                    "unit" to unit,
                    "lowStockThreshold" to threshold,
                    "unitPrice" to price,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val welcomeMsgRef = userDoc(uid).collection("messages").document("welcome_msg")
        batch.set(
            welcomeMsgRef,
            mapOf(
                "userId" to uid,
                "sender" to "assistant",
                "text" to "Assalamu Alaikum! I am Sahayak, your everyday practical helper.\n\nSend a photo of a bill or receipt, record a voice note in English or Bangla, or tap a quick task above. I will always pause for your approval before submitting or saving any final action.",
                "attachmentLabel" to "",
                "stepState" to "IDLE",
                "cardType" to "NONE",
                "actionType" to "",
                "portalTitle" to "",
                "draftFields" to emptyMap<String, String>(),
                "maskedKeys" to emptyList<String>(),
                "requiresOtp" to false,
                "referenceId" to "",
                "status" to "completed",
                "clientTimestampMs" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            throw e
        }
    }

    suspend fun updateSettings(languageMode: String, strictApprovalMode: Boolean): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            val ref = userDoc(uid)
            try {
                ref.update(
                    mapOf(
                        "languageMode" to languageMode,
                        "strictApprovalMode" to strictApprovalMode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, ref.path)
                throw e
            }
        }

    suspend fun saveVaultField(field: VaultField): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docId = field.id.ifBlank { field.fieldKey }
        val ref = userDoc(uid).collection("vault").document(docId)
        try {
            if (field.createdAt != null) {
                ref.update(
                    mapOf(
                        "label" to field.label,
                        "value" to field.value,
                        "isSensitive" to field.isSensitive,
                        "allowAutoFill" to field.allowAutoFill,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "fieldKey" to field.fieldKey,
                        "label" to field.label,
                        "value" to field.value,
                        "isSensitive" to field.isSensitive,
                        "allowAutoFill" to field.allowAutoFill,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun getVaultFieldsSnapshot(): List<VaultField> {
        val uid = requireUserId()
        val col = userDoc(uid).collection("vault")
        return try {
            col.get().await().toObjects(
                VaultField::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, col.path)
            throw e
        }
    }

    suspend fun getBillsSnapshot(): List<UtilityBill> {
        val uid = requireUserId()
        val col = userDoc(uid).collection("bills")
        return try {
            col.get().await().toObjects(
                UtilityBill::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, col.path)
            throw e
        }
    }

    suspend fun addChatMessage(message: ChatMessage): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = message.id.ifBlank { "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val ref = userDoc(uid).collection("messages").document(docId)
        val payload = mapOf(
            "userId" to uid,
            "sender" to message.sender,
            "text" to message.text.take(6000),
            "attachmentLabel" to message.attachmentLabel.take(200),
            "stepState" to message.stepState.ifBlank { "NONE" },
            "cardType" to message.cardType.ifBlank { "NONE" },
            "actionType" to message.actionType.take(50),
            "portalTitle" to message.portalTitle.take(120),
            "draftFields" to message.draftFields.entries.take(20).associate { it.key to it.value },
            "maskedKeys" to message.maskedKeys.take(15),
            "requiresOtp" to message.requiresOtp,
            "referenceId" to message.referenceId.take(80),
            "status" to message.status.ifBlank { "active" },
            "clientTimestampMs" to if (message.clientTimestampMs > 0L) message.clientTimestampMs else System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updateChatMessageCard(
        messageId: String,
        status: String,
        stepState: String,
        cardType: String,
        draftFields: Map<String, String>,
        referenceId: String,
        text: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("messages").document(messageId)
        try {
            ref.update(
                mapOf(
                    "status" to status,
                    "stepState" to stepState,
                    "cardType" to cardType,
                    "draftFields" to draftFields.entries.take(20).associate { it.key to it.value },
                    "referenceId" to referenceId.take(80),
                    "text" to text.take(6000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun clearChatMessages(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val col = userDoc(uid).collection("messages")
        try {
            val docs = col.get().await().documents
            val batch = db.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, col.path)
            throw e
        }
    }

    suspend fun commitBill(bill: UtilityBill): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = bill.id.ifBlank { "bill_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val ref = userDoc(uid).collection("bills").document(docId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "provider" to bill.provider.take(120),
                    "accountNo" to bill.accountNo.take(80),
                    "billingMonth" to bill.billingMonth.take(60),
                    "unitsKwh" to bill.unitsKwh.coerceAtLeast(0.0),
                    "amount" to bill.amount.coerceAtLeast(0.0),
                    "currency" to bill.currency.ifBlank { "BDT" }.take(12),
                    "dueDate" to bill.dueDate.take(40),
                    "previousAmount" to bill.previousAmount.coerceAtLeast(0.0),
                    "comparisonNote" to bill.comparisonNote.take(240),
                    "status" to bill.status.ifBlank { "Paid" },
                    "referenceId" to bill.referenceId.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun commitAppointment(appointment: AppointmentBooking): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = appointment.id.ifBlank { "appt_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val ref = userDoc(uid).collection("appointments").document(docId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "doctorOrOffice" to appointment.doctorOrOffice.take(140),
                    "department" to appointment.department.take(100),
                    "date" to appointment.date.take(40),
                    "time" to appointment.time.take(40),
                    "patientName" to appointment.patientName.take(120),
                    "notes" to appointment.notes.take(400),
                    "status" to appointment.status.ifBlank { "Confirmed" },
                    "referenceId" to appointment.referenceId.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updateAppointmentStatus(
        appointmentId: String,
        status: String,
        newDate: String? = null,
        newTime: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = userDoc(uid).collection("appointments").document(appointmentId)
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!newDate.isNullOrBlank()) updates["date"] = newDate.take(40)
        if (!newTime.isNullOrBlank()) updates["time"] = newTime.take(40)
        try {
            ref.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun commitShopTransaction(
        tx: ShopTransaction,
        inventoryItemName: String? = null,
        quantityDelta: Long = 0L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = tx.id.ifBlank { "tx_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val ref = userDoc(uid).collection("shop_transactions").document(docId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "txType" to tx.txType.ifBlank { "SALE" },
                    "itemsSummary" to tx.itemsSummary.take(400),
                    "totalAmount" to tx.totalAmount.coerceAtLeast(0.0),
                    "currency" to tx.currency.ifBlank { "BDT" }.take(12),
                    "notes" to tx.notes.take(300),
                    "referenceId" to tx.referenceId.take(80),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (!inventoryItemName.isNullOrBlank() && quantityDelta != 0L) {
                val items = userDoc(uid).collection("shop_inventory").get().await()
                    .toObjects(ShopInventoryItem::class.java)
                val matched = items.firstOrNull {
                    it.itemName.contains(inventoryItemName, ignoreCase = true) ||
                        inventoryItemName.contains(it.itemName, ignoreCase = true)
                }
                if (matched != null) {
                    val newQty = (matched.quantity + quantityDelta).coerceAtLeast(0L)
                    userDoc(uid).collection("shop_inventory").document(matched.id)
                        .update(
                            mapOf(
                                "quantity" to newQty,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                }
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun adjustInventoryQuantity(item: ShopInventoryItem, delta: Long): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("shop_inventory").document(item.id)
            val updatedQty = (item.quantity + delta).coerceAtLeast(0L)
            try {
                ref.update(
                    mapOf(
                        "quantity" to updatedQty,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, ref.path)
                throw e
            }
        }

    suspend fun addInventoryItem(item: ShopInventoryItem): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = item.id.ifBlank { "inv_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val ref = userDoc(uid).collection("shop_inventory").document(docId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "itemName" to item.itemName.take(120),
                    "quantity" to item.quantity.coerceAtLeast(0L),
                    "unit" to item.unit.ifBlank { "pcs" }.take(24),
                    "lowStockThreshold" to item.lowStockThreshold.coerceAtLeast(0L),
                    "unitPrice" to item.unitPrice.coerceAtLeast(0.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun appendAuditLog(entry: AuditLogEntry): Result<String> = runCatching {
        val uid = requireUserId()
        val docId = entry.id.ifBlank { "log_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val ref = userDoc(uid).collection("audit_logs").document(docId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "eventType" to entry.eventType.take(60),
                    "title" to entry.title.take(140),
                    "details" to entry.details.take(600),
                    "vaultFieldsAccessed" to entry.vaultFieldsAccessed.take(15),
                    "referenceId" to entry.referenceId.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    private data class VaultSeed(
        val key: String,
        val label: String,
        val value: String,
        val isSensitive: Boolean,
        val allowAutoFill: Boolean
    )

    private fun vaultSortOrder(key: String): Int = when (key) {
        "full_name" -> 0
        "phone" -> 1
        "email" -> 2
        "address" -> 3
        "national_id" -> 4
        "meter_id" -> 5
        "preferred_clinic" -> 6
        "emergency_contact" -> 7
        else -> 99
    }
}
