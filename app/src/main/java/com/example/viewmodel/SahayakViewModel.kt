package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SahayakAgentService
import com.example.data.SahayakRepository
import com.example.model.AgentStepState
import com.example.model.AppointmentBooking
import com.example.model.AuditLogEntry
import com.example.model.ChatMessage
import com.example.model.SahayakTab
import com.example.model.ShopInventoryItem
import com.example.model.ShopTransaction
import com.example.model.UserSettings
import com.example.model.UtilityBill
import com.example.model.VaultField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

class SahayakViewModel(
    private val repository: SahayakRepository,
    private val agentService: SahayakAgentService,
    private val currentUserId: String,
    private val userDisplayName: String?,
    private val userEmail: String?
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(SahayakTab.CHAT)
    val selectedTab: StateFlow<SahayakTab> = _selectedTab.asStateFlow()

    private val _currentStep = MutableStateFlow(AgentStepState.IDLE)
    val currentStep: StateFlow<AgentStepState> = _currentStep.asStateFlow()

    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    val settings: StateFlow<UserSettings> = repository.observeSettings(currentUserId)
        .catch { Log.w(TAG, "Error observing settings", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserSettings(userId = currentUserId))

    val vaultFields: StateFlow<List<VaultField>> = repository.observeVault(currentUserId)
        .catch { Log.w(TAG, "Error observing vault", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val messages: StateFlow<List<ChatMessage>> = repository.observeMessages(currentUserId)
        .catch { Log.w(TAG, "Error observing messages", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val bills: StateFlow<List<UtilityBill>> = repository.observeBills(currentUserId)
        .catch { Log.w(TAG, "Error observing bills", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val appointments: StateFlow<List<AppointmentBooking>> = repository.observeAppointments(currentUserId)
        .catch { Log.w(TAG, "Error observing appointments", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val shopTransactions: StateFlow<List<ShopTransaction>> = repository.observeShopTransactions(currentUserId)
        .catch { Log.w(TAG, "Error observing shop tx", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val shopInventory: StateFlow<List<ShopInventoryItem>> = repository.observeShopInventory(currentUserId)
        .catch { Log.w(TAG, "Error observing shop inventory", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntry>> = repository.observeAuditLogs(currentUserId)
        .catch { Log.w(TAG, "Error observing audit logs", it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureUserSeeded(userDisplayName, userEmail)
                .onFailure { err ->
                    Log.e(TAG, "Failed seeding user profile", err)
                }
        }
    }

    fun selectTab(tab: SahayakTab) {
        _selectedTab.value = tab
    }

    fun dismissErrorBanner() {
        _errorBanner.value = null
    }

    fun sendUserTask(
        text: String,
        bitmap: Bitmap? = null,
        audioBytes: ByteArray? = null,
        audioMimeType: String? = null,
        attachmentLabel: String = ""
    ) {
        if (_isAgentBusy.value) return
        if (text.isBlank() && bitmap == null && audioBytes == null) return

        viewModelScope.launch {
            _isAgentBusy.value = true
            _errorBanner.value = null
            _currentStep.value = AgentStepState.INTAKE

            val displayText = when {
                text.isNotBlank() -> text.trim()
                audioBytes != null -> "Sent a voice message"
                bitmap != null -> "Sent a photo for review"
                else -> ""
            }

            repository.addChatMessage(
                ChatMessage(
                    userId = currentUserId,
                    sender = "user",
                    text = displayText,
                    attachmentLabel = attachmentLabel,
                    stepState = AgentStepState.INTAKE.name,
                    cardType = "NONE",
                    status = "completed",
                    clientTimestampMs = System.currentTimeMillis()
                )
            )

            try {
                val history = messages.value
                    .filter { it.sender == "user" || it.sender == "assistant" }
                    .map { it.sender to it.text }

                val result = agentService.runAgentTurn(
                    userId = currentUserId,
                    userText = text,
                    bitmap = bitmap,
                    audioBytes = audioBytes,
                    audioMimeType = audioMimeType,
                    languageMode = settings.value.languageMode,
                    conversationSummary = history,
                    onStepChanged = { step -> _currentStep.value = step }
                )

                val draft = result.pendingDraft
                if (draft != null) {
                    repository.addChatMessage(
                        ChatMessage(
                            userId = currentUserId,
                            sender = "assistant",
                            text = result.replyText,
                            stepState = AgentStepState.AWAITING_APPROVAL.name,
                            cardType = "APPROVAL",
                            actionType = draft.actionType,
                            portalTitle = draft.portalTitle,
                            draftFields = draft.fields,
                            maskedKeys = draft.maskedKeys,
                            requiresOtp = draft.requiresOtp,
                            status = "active",
                            clientTimestampMs = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.addChatMessage(
                        ChatMessage(
                            userId = currentUserId,
                            sender = "assistant",
                            text = result.replyText,
                            stepState = result.finalStepState.name,
                            cardType = if (result.needsClarification) "CLARIFICATION" else "NONE",
                            status = "completed",
                            clientTimestampMs = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI turn timed out", e)
                _currentStep.value = AgentStepState.PAUSED_ERROR
                _errorBanner.value = "Sahayak took too long to respond. Please try again."
                repository.addChatMessage(
                    ChatMessage(
                        userId = currentUserId,
                        sender = "assistant",
                        text = "That took a bit longer than expected. Please try sending your request again.",
                        stepState = AgentStepState.PAUSED_ERROR.name,
                        cardType = "CLARIFICATION",
                        status = "error",
                        clientTimestampMs = System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI turn failed", e)
                _currentStep.value = AgentStepState.PAUSED_ERROR
                val msg = e.localizedMessage ?: "Unable to process request right now."
                _errorBanner.value = msg
                repository.addChatMessage(
                    ChatMessage(
                        userId = currentUserId,
                        sender = "assistant",
                        text = "I ran into an issue reading that request ($msg). Please check your connection and try again.",
                        stepState = AgentStepState.PAUSED_ERROR.name,
                        cardType = "CLARIFICATION",
                        status = "error",
                        clientTimestampMs = System.currentTimeMillis()
                    )
                )
            } finally {
                _isAgentBusy.value = false
            }
        }
    }

    fun confirmApproval(message: ChatMessage, updatedFields: Map<String, String>? = null) {
        val fields = updatedFields ?: message.draftFields
        viewModelScope.launch {
            repository.appendAuditLog(
                AuditLogEntry(
                    userId = currentUserId,
                    eventType = "USER_APPROVED",
                    title = "User Confirmed: ${message.portalTitle}",
                    details = "Explicit approval clicked for ${message.actionType}",
                    vaultFieldsAccessed = emptyList(),
                    referenceId = "APR-${UUID.randomUUID().toString().take(6).uppercase()}"
                )
            )

            if (message.requiresOtp) {
                _currentStep.value = AgentStepState.AWAITING_OTP
                repository.updateChatMessageCard(
                    messageId = message.id,
                    status = "approved",
                    stepState = AgentStepState.AWAITING_OTP.name,
                    cardType = "OTP",
                    draftFields = fields,
                    referenceId = "",
                    text = "The portal sent a 6-digit verification code to your phone. Enter code 482910 below to finish submitting."
                )
                _toastEvents.tryEmit("Portal OTP sent! Use demo code: 482910")
            } else {
                finalizeActionAndEmitReceipt(message.copy(draftFields = fields))
            }
        }
    }

    fun updateDraftFieldsOnly(message: ChatMessage, updatedFields: Map<String, String>) {
        viewModelScope.launch {
            repository.updateChatMessageCard(
                messageId = message.id,
                status = message.status,
                stepState = message.stepState,
                cardType = message.cardType,
                draftFields = updatedFields,
                referenceId = message.referenceId,
                text = message.text
            )
            _toastEvents.tryEmit("Draft details updated")
        }
    }

    fun cancelApproval(message: ChatMessage) {
        viewModelScope.launch {
            _currentStep.value = AgentStepState.CANCELLED
            repository.updateChatMessageCard(
                messageId = message.id,
                status = "cancelled",
                stepState = AgentStepState.CANCELLED.name,
                cardType = "NONE",
                draftFields = message.draftFields,
                referenceId = "",
                text = "Cancelled safely: \"${message.portalTitle}\" was not submitted, and no records were changed."
            )
            repository.appendAuditLog(
                AuditLogEntry(
                    userId = currentUserId,
                    eventType = "USER_CANCELLED",
                    title = "Cancelled: ${message.portalTitle}",
                    details = "User cancelled action before submission",
                    vaultFieldsAccessed = emptyList(),
                    referenceId = "CXL-${UUID.randomUUID().toString().take(6).uppercase()}"
                )
            )
            _toastEvents.tryEmit("Action cancelled safely")
        }
    }

    fun verifyOtpAndComplete(message: ChatMessage, enteredCode: String) {
        if (enteredCode.trim() != DEMO_OTP_CODE) {
            _toastEvents.tryEmit("Incorrect code. Please enter demo code $DEMO_OTP_CODE.")
            return
        }
        viewModelScope.launch {
            repository.appendAuditLog(
                AuditLogEntry(
                    userId = currentUserId,
                    eventType = "OTP_VERIFIED",
                    title = "OTP Verified (${message.portalTitle})",
                    details = "6-digit verification code confirmed by user",
                    vaultFieldsAccessed = emptyList(),
                    referenceId = "OTP-${UUID.randomUUID().toString().take(6).uppercase()}"
                )
            )
            finalizeActionAndEmitReceipt(message)
        }
    }

    private suspend fun finalizeActionAndEmitReceipt(message: ChatMessage) {
        val fields = message.draftFields
        val refId = "SAH-${UUID.randomUUID().toString().take(8).uppercase()}"
        val summary: String

        when (message.actionType) {
            "UTILITY_PAYMENT" -> {
                val provider = findField(fields, "Provider", "Utility", "Biller") ?: "DESCO Electricity"
                val account = findField(fields, "Account", "Meter") ?: "48219044"
                val month = findField(fields, "Month", "Billing") ?: "November 2026"
                val units = parseNumber(findField(fields, "Units", "Reading", "kWh")) ?: 312.0
                val amount = parseNumber(findField(fields, "Amount", "Total", "Due")) ?: 2450.0
                val dueDate = findField(fields, "Due Date", "Due") ?: "2026-11-25"
                val note = findField(fields, "Summary Note", "Comparison", "Note") ?: "Paid via Sahayak"

                repository.commitBill(
                    UtilityBill(
                        userId = currentUserId,
                        provider = provider,
                        accountNo = account,
                        billingMonth = month,
                        unitsKwh = units,
                        amount = amount,
                        currency = "BDT",
                        dueDate = dueDate,
                        previousAmount = 2150.0,
                        comparisonNote = note,
                        status = "Paid",
                        referenceId = refId
                    )
                )
                summary = "$provider ($month) • ${amount.roundToInt()} BDT marked Paid"
            }

            "CLINIC_APPOINTMENT", "DOCTOR_APPOINTMENT" -> {
                val doctor = findField(fields, "Doctor", "Clinic", "Office") ?: "Green Care Clinic"
                val dept = findField(fields, "Department", "Specialty") ?: "General Medicine"
                val date = findField(fields, "Date") ?: "2026-11-18"
                val time = findField(fields, "Time") ?: "10:30 AM"
                val patient = findField(fields, "Patient", "Name", "Applicant") ?: (userDisplayName ?: "Patient")
                val notes = findField(fields, "Notes", "Reason") ?: "Confirmed via Sahayak"

                repository.commitAppointment(
                    AppointmentBooking(
                        userId = currentUserId,
                        doctorOrOffice = doctor,
                        department = dept,
                        date = date,
                        time = time,
                        patientName = patient,
                        notes = notes,
                        status = "Confirmed",
                        referenceId = refId
                    )
                )
                summary = "$doctor on $date at $time • Confirmed"
            }

            "SHOP_TRANSACTION", "SHOP_LEDGER" -> {
                val txTypeRaw = (findField(fields, "Entry Type", "Type") ?: "SALE").uppercase()
                val txType = if (txTypeRaw in listOf("SALE", "EXPENSE", "RESTOCK")) txTypeRaw else "SALE"
                val items = findField(fields, "Items", "Summary", "Description") ?: "Shop Receipt"
                val total = parseNumber(findField(fields, "Total Amount", "Amount", "Total")) ?: 0.0
                val invName = findField(fields, "Inventory Item")
                val qtyDelta = parseNumber(findField(fields, "Stock Adjustment"))?.toLong() ?: 0L
                val notes = findField(fields, "Notes") ?: ""

                repository.commitShopTransaction(
                    ShopTransaction(
                        userId = currentUserId,
                        txType = txType,
                        itemsSummary = items,
                        totalAmount = total,
                        currency = "BDT",
                        notes = notes,
                        referenceId = refId
                    ),
                    inventoryItemName = invName,
                    quantityDelta = qtyDelta
                )
                summary = "$txType: $items • ${total.roundToInt()} BDT saved to Cashbook"
            }

            else -> {
                // SERVICE_APPLICATION: Log as a confirmed office appointment / submission record too
                val serviceTitle = message.portalTitle.ifBlank { "Service Application" }
                val applicant = findField(fields, "Applicant", "Full Name", "Name") ?: (userDisplayName ?: "Applicant")
                val date = findField(fields, "Date", "Submitted") ?: "2026-11-15"
                repository.commitAppointment(
                    AppointmentBooking(
                        userId = currentUserId,
                        doctorOrOffice = serviceTitle,
                        department = "Submitted Application",
                        date = date,
                        time = "Submitted",
                        patientName = applicant,
                        notes = "Ref $refId",
                        status = "Confirmed",
                        referenceId = refId
                    )
                )
                summary = "$serviceTitle submitted for $applicant"
            }
        }

        _currentStep.value = AgentStepState.COMPLETED
        repository.updateChatMessageCard(
            messageId = message.id,
            status = "completed",
            stepState = AgentStepState.COMPLETED.name,
            cardType = "RECEIPT",
            draftFields = fields,
            referenceId = refId,
            text = summary
        )
        repository.appendAuditLog(
            AuditLogEntry(
                userId = currentUserId,
                eventType = "ACTION_EXECUTED",
                title = "Receipt Issued: $refId",
                details = summary,
                vaultFieldsAccessed = emptyList(),
                referenceId = refId
            )
        )
        _toastEvents.tryEmit("Completed! Receipt $refId saved.")
    }

    fun clearChatAndReset() {
        viewModelScope.launch {
            _currentStep.value = AgentStepState.IDLE
            repository.clearChatMessages()
            repository.addChatMessage(
                ChatMessage(
                    userId = currentUserId,
                    sender = "assistant",
                    text = "Fresh task started. Upload a bill or receipt photo, record a voice message, or tap a starter task above.",
                    stepState = AgentStepState.IDLE.name,
                    cardType = "NONE",
                    status = "completed",
                    clientTimestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleVaultAutoFill(field: VaultField, allow: Boolean) {
        viewModelScope.launch {
            repository.saveVaultField(field.copy(allowAutoFill = allow))
        }
    }

    fun updateVaultFieldValue(field: VaultField, newValue: String) {
        viewModelScope.launch {
            repository.saveVaultField(field.copy(value = newValue.trim()))
            _toastEvents.tryEmit("${field.label} updated in Vault")
        }
    }

    fun updateAppointment(appointment: AppointmentBooking, status: String, newDate: String? = null, newTime: String? = null) {
        viewModelScope.launch {
            repository.updateAppointmentStatus(appointment.id, status, newDate, newTime)
            _toastEvents.tryEmit("Appointment $status")
        }
    }

    fun adjustStock(item: ShopInventoryItem, delta: Long) {
        viewModelScope.launch {
            repository.adjustInventoryQuantity(item, delta)
        }
    }

    fun addShopItem(name: String, qty: Long, unit: String, threshold: Long, price: Double) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addInventoryItem(
                ShopInventoryItem(
                    userId = currentUserId,
                    itemName = name.trim(),
                    quantity = qty,
                    unit = unit.ifBlank { "pcs" },
                    lowStockThreshold = threshold,
                    unitPrice = price
                )
            )
            _toastEvents.tryEmit("$name added to inventory")
        }
    }

    fun updateSafetyPreferences(languageMode: String, strictApprovalMode: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(languageMode, strictApprovalMode)
        }
    }

    private fun findField(fields: Map<String, String>, vararg needles: String): String? {
        for (needle in needles) {
            val hit = fields.entries.firstOrNull { (k, _) ->
                k.contains(needle, ignoreCase = true)
            }?.value
            if (!hit.isNullOrBlank()) return hit
        }
        return null
    }

    private fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = Regex("""-?\d+(\.\d+)?""").find(raw.replace(",", ""))?.value
        return cleaned?.toDoubleOrNull()
    }

    companion object {
        private const val TAG = "SahayakVM"
        const val DEMO_OTP_CODE = "482910"
    }
}
