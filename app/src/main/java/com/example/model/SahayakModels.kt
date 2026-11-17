package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SahayakTab(val label: String) {
    CHAT("Doer Chat"),
    VAULT("My Vault"),
    RECORDS("My Records"),
    SAFETY("Safety & Audit")
}

enum class AgentStepState(val label: String, val bnLabel: String) {
    IDLE("Ready to help", "সাহায্যের জন্য প্রস্তুত"),
    INTAKE("Reading your input", "আপনার তথ্য পড়া হচ্ছে"),
    VAULT_LOOKUP("Checking consented Vault", "ভল্ট থেকে অনুমতিপ্রাপ্ত তথ্য নেওয়া হচ্ছে"),
    DRAFTING_ACTION("Preparing safe draft", "খসড়া তৈরি হচ্ছে"),
    AWAITING_APPROVAL("Waiting for your approval", "আপনার অনুমোদনের অপেক্ষায়"),
    AWAITING_OTP("Waiting for OTP verification", "ওটিপি যাচাইয়ের অপেক্ষায়"),
    COMPLETED("Completed with receipt", "রসিদসহ সম্পন্ন হয়েছে"),
    PAUSED_ERROR("Needs clarification", "আরও পরিষ্কার তথ্য প্রয়োজন"),
    CANCELLED("Cancelled safely", "নিরাপদভাবে বাতিল করা হয়েছে");

    companion object {
        fun fromString(raw: String): AgentStepState =
            entries.firstOrNull { it.name == raw } ?: IDLE
    }
}

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val languageMode: String = "en",
    val strictApprovalMode: Boolean = true,
    val seededDefaults: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VaultField(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fieldKey: String = "",
    val label: String = "",
    val value: String = "",
    val isSensitive: Boolean = false,
    val allowAutoFill: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "assistant",
    val text: String = "",
    val attachmentLabel: String = "",
    val stepState: String = "NONE",
    val cardType: String = "NONE",
    val actionType: String = "",
    val portalTitle: String = "",
    val draftFields: Map<String, String> = emptyMap(),
    val maskedKeys: List<String> = emptyList(),
    val requiresOtp: Boolean = false,
    val referenceId: String = "",
    val status: String = "active",
    val clientTimestampMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UtilityBill(
    @DocumentId val id: String = "",
    val userId: String = "",
    val provider: String = "",
    val accountNo: String = "",
    val billingMonth: String = "",
    val unitsKwh: Double = 0.0,
    val amount: Double = 0.0,
    val currency: String = "BDT",
    val dueDate: String = "",
    val previousAmount: Double = 0.0,
    val comparisonNote: String = "",
    val status: String = "Logged",
    val referenceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppointmentBooking(
    @DocumentId val id: String = "",
    val userId: String = "",
    val doctorOrOffice: String = "",
    val department: String = "",
    val date: String = "",
    val time: String = "",
    val patientName: String = "",
    val notes: String = "",
    val status: String = "Confirmed",
    val referenceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = "SALE",
    val itemsSummary: String = "",
    val totalAmount: Double = 0.0,
    val currency: String = "BDT",
    val notes: String = "",
    val referenceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopInventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemName: String = "",
    val quantity: Long = 0L,
    val unit: String = "pcs",
    val lowStockThreshold: Long = 5L,
    val unitPrice: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "",
    val title: String = "",
    val details: String = "",
    val vaultFieldsAccessed: List<String> = emptyList(),
    val referenceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
