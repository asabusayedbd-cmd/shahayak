package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.SahayakRepository
import com.example.model.AgentStepState
import com.example.model.AuditLogEntry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.math.roundToInt

data class PendingApprovalDraft(
    val actionType: String,
    val portalTitle: String,
    val fields: Map<String, String>,
    val maskedKeys: List<String>,
    val requiresOtp: Boolean,
    val notes: String = ""
)

data class AgentTurnResult(
    val replyText: String,
    val finalStepState: AgentStepState,
    val pendingDraft: PendingApprovalDraft?,
    val needsClarification: Boolean = false
)

class SahayakAgentService(
    private val repository: SahayakRepository
) {
    private val lookupVaultTool = FunctionDeclaration(
        name = "lookup_vault_fields",
        description = "Reads ONLY user-consented fields from the user's Personal Vault. Use this before drafting forms or bookings so the user does not have to re-type saved details.",
        parameters = mapOf(
            "requested_fields" to Schema.array(
                items = Schema.string(),
                description = "Field keys needed, chosen from: full_name, phone, email, address, national_id, meter_id, preferred_clinic, emergency_contact"
            )
        )
    )

    private val compareUtilityBillTool = FunctionDeclaration(
        name = "compare_utility_bill",
        description = "Compares current electricity or utility bill units and amount against previous months stored in the user's records and explains why it changed.",
        parameters = mapOf(
            "provider" to Schema.string("Utility provider name, e.g. DESCO Electricity"),
            "current_units" to Schema.double("Current billing units in kWh"),
            "current_amount" to Schema.double("Current bill amount in BDT"),
            "billing_month" to Schema.string("Billing month label, e.g. November 2026")
        )
    )

    private val draftPortalFormTool = FunctionDeclaration(
        name = "draft_portal_form",
        description = "Prepares an interactive Approval Card preview for Utility Payment, Clinic Appointment, Service Application, or Shop Ledger. Does NOT submit anything final.",
        parameters = mapOf(
            "portal_type" to Schema.enumeration(
                values = listOf("UTILITY_PAYMENT", "CLINIC_APPOINTMENT", "SERVICE_APPLICATION", "SHOP_LEDGER"),
                description = "Category of action being drafted"
            ),
            "portal_title" to Schema.string("Friendly portal or ledger title shown on the Approval Card"),
            "fields" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "label" to Schema.string("Human-readable field name"),
                        "value" to Schema.string("Extracted or vault-filled value"),
                        "is_sensitive" to Schema.boolean("True for IDs, meter numbers, or phone numbers that should be masked")
                    )
                ),
                description = "Ordered list of form fields to display for user review"
            ),
            "requires_otp" to Schema.boolean("True for UTILITY_PAYMENT and SERVICE_APPLICATION; false for appointments or shop logs"),
            "notes" to Schema.string("Concise summary or comparison insight for the user")
        ),
        optionalParameters = listOf("notes")
    )

    private val logShopTransactionTool = FunctionDeclaration(
        name = "log_shop_transaction",
        description = "Stages a small-shop sale, expense, or restock entry for user approval before saving to the Business Ledger.",
        parameters = mapOf(
            "tx_type" to Schema.enumeration(
                values = listOf("SALE", "EXPENSE", "RESTOCK"),
                description = "Type of shop cashbook entry"
            ),
            "items_summary" to Schema.string("Items and quantities sold or purchased"),
            "total_amount" to Schema.double("Total amount in BDT"),
            "inventory_item_name" to Schema.string("Matching inventory item name if stock changed, or empty string"),
            "quantity_delta" to Schema.integer("Stock change: negative for sales, positive for restocks, 0 otherwise"),
            "notes" to Schema.string("Optional note")
        ),
        optionalParameters = listOf("inventory_item_name", "quantity_delta", "notes")
    )

    private val createAppointmentTool = FunctionDeclaration(
        name = "create_appointment_booking",
        description = "Stages a doctor or office appointment booking for explicit user approval before confirming.",
        parameters = mapOf(
            "doctor_or_office" to Schema.string("Doctor name, clinic, or service office"),
            "department" to Schema.string("Specialty or service counter"),
            "date" to Schema.string("Appointment date e.g. 2026-11-18"),
            "time" to Schema.string("Preferred time slot e.g. 10:30 AM"),
            "patient_name" to Schema.string("Patient or visitor name"),
            "notes" to Schema.string("Visit reason or symptoms/notes")
        ),
        optionalParameters = listOf("department", "notes")
    )

    private fun buildModel(languageMode: String) = Firebase.ai(
        backend = GenerativeBackend.googleAI()
    ).generativeModel(
        modelName = "gemini-3.8-flash",
        tools = listOf(
            Tool.functionDeclarations(
                listOf(
                    lookupVaultTool,
                    compareUtilityBillTool,
                    draftPortalFormTool,
                    logShopTransactionTool,
                    createAppointmentTool
                )
            )
        ),
        systemInstruction = content {
            text(
                buildString {
                    appendLine("You are Sahayak, a practical, trustworthy everyday action assistant for non-technical people.")
                    appendLine("Today's date context: November 15, 2026. Default currency: BDT.")
                    appendLine()
                    appendLine("CRITICAL RULES:")
                    appendLine("1. NEVER use technical jargon like JSON, API, LLM, database, function, schema, or backend in your replies.")
                    appendLine("2. NEVER guess or invent unclear numbers, account IDs, dates, or amounts from blurry photos, unclear voice notes, or ambiguous requests. If any key detail is blurry or missing, ask the user concisely and calmly to clarify.")
                    appendLine("3. Always call lookup_vault_fields first when personal details (name, phone, address, meter_id, national_id, preferred_clinic, email) can save the user from re-typing. If a field is blocked by the user's consent toggle, respect that privacy choice and either leave it blank or ask the user.")
                    appendLine("4. When checking a utility bill, call compare_utility_bill to compare against prior months, then call draft_portal_form (with portal_type=UTILITY_PAYMENT and requires_otp=true) so the user gets an Approval Card before paying.")
                    appendLine("5. For appointments, call lookup_vault_fields then create_appointment_booking (which stages an Approval Card).")
                    appendLine("6. For shop sales/expenses/receipts, call log_shop_transaction (which stages an Approval Card).")
                    appendLine("7. For service/government application forms, call lookup_vault_fields then draft_portal_form (with portal_type=SERVICE_APPLICATION and requires_otp=true).")
                    appendLine("8. Keep replies warm, clear, short, and scannable.")
                    if (languageMode == "bn_en") {
                        appendLine("9. LANGUAGE MODE: Reply in friendly, everyday Bangla + English mix (Banglish / বাংলা) that feels natural to users in Bangladesh, while keeping numbers and labels super clear.")
                    } else {
                        appendLine("9. LANGUAGE MODE: Reply in clear, warm, simple English.")
                    }
                }
            )
        }
    )

    suspend fun runAgentTurn(
        userId: String,
        userText: String,
        bitmap: Bitmap?,
        audioBytes: ByteArray?,
        audioMimeType: String?,
        languageMode: String,
        conversationSummary: List<Pair<String, String>>,
        onStepChanged: (AgentStepState) -> Unit
    ): AgentTurnResult {
        onStepChanged(AgentStepState.INTAKE)

        return try {
            withTimeout(60_000L) {
                val model = buildModel(languageMode)
                val historyContents = conversationSummary.takeLast(8).map { (role, text) ->
                    content(if (role == "user") "user" else "model") { text(text) }
                }
                val chat = model.startChat(history = historyContents)

                val promptContent = content("user") {
                    if (bitmap != null) {
                        image(bitmap)
                    }
                    if (audioBytes != null && audioMimeType != null) {
                        inlineData(audioBytes, audioMimeType)
                    }
                    val effectivePrompt = when {
                        userText.isNotBlank() -> userText
                        audioBytes != null -> "Listen to this voice message (which may be English, Bangla, or mixed Banglish) and carry out the user's request safely using your tools."
                        bitmap != null -> "Examine this photo carefully. Extract only clearly legible fields. If anything critical is blurry or unreadable, ask for clarification without guessing."
                        else -> "Help the user."
                    }
                    text(effectivePrompt)
                }

                var response = chat.sendMessage(promptContent)
                var stagedDraft: PendingApprovalDraft? = null
                var rounds = 0

                while (response.functionCalls.isNotEmpty() && rounds < 6) {
                    rounds++
                    val responses = mutableListOf<FunctionResponsePart>()

                    for (call in response.functionCalls) {
                        val toolResult = when (call.name) {
                            "lookup_vault_fields" -> {
                                onStepChanged(AgentStepState.VAULT_LOOKUP)
                                handleLookupVault(userId, call.args)
                            }
                            "compare_utility_bill" -> {
                                onStepChanged(AgentStepState.DRAFTING_ACTION)
                                handleCompareUtilityBill(userId, call.args)
                            }
                            "draft_portal_form" -> {
                                onStepChanged(AgentStepState.DRAFTING_ACTION)
                                val (resultObj, draft) = handleDraftPortalForm(userId, call.args)
                                if (draft != null) stagedDraft = draft
                                resultObj
                            }
                            "log_shop_transaction" -> {
                                onStepChanged(AgentStepState.DRAFTING_ACTION)
                                val (resultObj, draft) = handleStageShopTransaction(userId, call.args)
                                if (draft != null) stagedDraft = draft
                                resultObj
                            }
                            "create_appointment_booking" -> {
                                onStepChanged(AgentStepState.DRAFTING_ACTION)
                                val (resultObj, draft) = handleStageAppointment(userId, call.args)
                                if (draft != null) stagedDraft = draft
                                resultObj
                            }
                            else -> JsonObject(mapOf("error" to JsonPrimitive("Unknown tool")))
                        }
                        responses.add(FunctionResponsePart(call.name, toolResult))
                    }

                    response = chat.sendMessage(
                        content("user") {
                            responses.forEach { part(it) }
                        }
                    )
                }

                val reply = response.text?.trim().orEmpty().ifBlank {
                    if (stagedDraft != null) {
                        "I have prepared the draft below. Please review the details and tap Confirm & Submit when ready."
                    } else {
                        "Could you share a bit more detail so I can help accurately?"
                    }
                }

                val finalState = when {
                    stagedDraft != null -> AgentStepState.AWAITING_APPROVAL
                    reply.contains("?") || reply.contains("clarif", ignoreCase = true) ||
                        reply.contains("unclear", ignoreCase = true) || reply.contains("blur", ignoreCase = true) ->
                        AgentStepState.PAUSED_ERROR
                    else -> AgentStepState.IDLE
                }

                onStepChanged(finalState)
                AgentTurnResult(
                    replyText = reply,
                    finalStepState = finalState,
                    pendingDraft = stagedDraft,
                    needsClarification = finalState == AgentStepState.PAUSED_ERROR
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Agent turn timed out", e)
            onStepChanged(AgentStepState.PAUSED_ERROR)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent turn failed", e)
            onStepChanged(AgentStepState.PAUSED_ERROR)
            throw e
        }
    }

    private suspend fun handleLookupVault(
        userId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>
    ): JsonObject {
        val requested = args["requested_fields"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content
        } ?: emptyList()

        val allVault = repository.getVaultFieldsSnapshot()
        val accessedKeys = mutableListOf<String>()
        val blockedKeys = mutableListOf<String>()
        val consentedMap = mutableMapOf<String, JsonPrimitive>()

        val targetFields = if (requested.isEmpty()) allVault else {
            allVault.filter { field -> requested.any { it.equals(field.fieldKey, ignoreCase = true) } }
        }

        for (field in targetFields) {
            if (field.allowAutoFill && field.value.isNotBlank()) {
                accessedKeys.add(field.fieldKey)
                consentedMap[field.fieldKey] = JsonPrimitive(field.value)
            } else if (!field.allowAutoFill) {
                blockedKeys.add(field.fieldKey)
            }
        }

        repository.appendAuditLog(
            AuditLogEntry(
                userId = userId,
                eventType = "VAULT_ACCESS",
                title = "Personal Vault Lookup",
                details = buildString {
                    append("Read ${accessedKeys.size} consented field(s)")
                    if (blockedKeys.isNotEmpty()) {
                        append("; skipped ${blockedKeys.joinToString(", ")} (Auto-Fill disabled)")
                    }
                },
                vaultFieldsAccessed = accessedKeys,
                referenceId = "VLT-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        return JsonObject(
            mapOf(
                "consented_fields" to JsonObject(consentedMap),
                "blocked_by_user_consent" to JsonArray(blockedKeys.map { JsonPrimitive(it) })
            )
        )
    }

    private suspend fun handleCompareUtilityBill(
        userId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>
    ): JsonObject {
        val provider = args["provider"]?.jsonPrimitive?.content ?: "Electricity"
        val currentUnits = args["current_units"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val currentAmount = args["current_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val billingMonth = args["billing_month"]?.jsonPrimitive?.content ?: "Current Month"

        val previousBills = repository.getBillsSnapshot()
            .filter { !it.billingMonth.equals(billingMonth, ignoreCase = true) }
        val latestPrev = previousBills.firstOrNull()

        val prevAmount = latestPrev?.amount ?: 2150.0
        val prevUnits = latestPrev?.unitsKwh ?: 274.0
        val prevMonth = latestPrev?.billingMonth ?: "October 2026"

        val amountDelta = currentAmount - prevAmount
        val unitDelta = currentUnits - prevUnits
        val pctChange = if (prevAmount > 0.0) ((amountDelta / prevAmount) * 100.0).roundToInt() else 0
        val sign = if (pctChange >= 0) "+" else ""
        val comparisonBadge = "$sign$pctChange% vs ${prevMonth.take(3)} (${prevAmount.roundToInt()} BDT)"

        val explanation = when {
            amountDelta > 0 ->
                "Your $billingMonth bill (${currentAmount.roundToInt()} BDT, ${currentUnits.roundToInt()} kWh) is ${amountDelta.roundToInt()} BDT ($sign$pctChange%) higher than $prevMonth (${prevAmount.roundToInt()} BDT, ${prevUnits.roundToInt()} kWh), mainly due to ${unitDelta.roundToInt()} extra kWh used."
            amountDelta < 0 ->
                "Good news: your $billingMonth bill is ${(-amountDelta).roundToInt()} BDT lower than $prevMonth."
            else ->
                "Your $billingMonth bill matches $prevMonth."
        }

        repository.appendAuditLog(
            AuditLogEntry(
                userId = userId,
                eventType = "TOOL_CALL",
                title = "Compared Utility Bill ($provider)",
                details = comparisonBadge,
                vaultFieldsAccessed = emptyList(),
                referenceId = "CMP-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        return JsonObject(
            mapOf(
                "previous_month" to JsonPrimitive(prevMonth),
                "previous_amount" to JsonPrimitive(prevAmount),
                "previous_units_kwh" to JsonPrimitive(prevUnits),
                "comparison_badge" to JsonPrimitive(comparisonBadge),
                "explanation" to JsonPrimitive(explanation)
            )
        )
    }

    private suspend fun handleDraftPortalForm(
        userId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>
    ): Pair<JsonObject, PendingApprovalDraft?> {
        val portalType = args["portal_type"]?.jsonPrimitive?.content ?: "SERVICE_APPLICATION"
        val portalTitle = args["portal_title"]?.jsonPrimitive?.content ?: "Portal Form Draft"
        val requiresOtp = args["requires_otp"]?.jsonPrimitive?.booleanOrNull
            ?: (portalType == "UTILITY_PAYMENT" || portalType == "SERVICE_APPLICATION")
        val notes = args["notes"]?.jsonPrimitive?.content.orEmpty()

        val fieldsMap = linkedMapOf<String, String>()
        val maskedKeys = mutableListOf<String>()

        val fieldsArray = args["fields"]?.jsonArray
        fieldsArray?.forEach { elem ->
            val obj = elem.jsonObject
            val label = obj["label"]?.jsonPrimitive?.content.orEmpty()
            val value = obj["value"]?.jsonPrimitive?.content.orEmpty()
            val isSensitive = obj["is_sensitive"]?.jsonPrimitive?.booleanOrNull ?: false
            if (label.isNotBlank()) {
                fieldsMap[label] = value
                if (isSensitive) maskedKeys.add(label)
            }
        }
        if (notes.isNotBlank() && !fieldsMap.containsKey("Summary Note")) {
            fieldsMap["Summary Note"] = notes
        }

        repository.appendAuditLog(
            AuditLogEntry(
                userId = userId,
                eventType = "TOOL_CALL",
                title = "Prepared Draft: $portalTitle",
                details = "Paused for explicit user approval (${fieldsMap.size} fields, OTP=${if (requiresOtp) "Yes" else "No"})",
                vaultFieldsAccessed = emptyList(),
                referenceId = "DRF-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        val draft = PendingApprovalDraft(
            actionType = portalType,
            portalTitle = portalTitle,
            fields = fieldsMap,
            maskedKeys = maskedKeys,
            requiresOtp = requiresOtp,
            notes = notes
        )

        return JsonObject(
            mapOf(
                "status" to JsonPrimitive("staged_for_user_approval"),
                "portal_title" to JsonPrimitive(portalTitle),
                "requires_otp" to JsonPrimitive(requiresOtp)
            )
        ) to draft
    }

    private suspend fun handleStageShopTransaction(
        userId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>
    ): Pair<JsonObject, PendingApprovalDraft> {
        val txType = args["tx_type"]?.jsonPrimitive?.content ?: "SALE"
        val itemsSummary = args["items_summary"]?.jsonPrimitive?.content ?: "Shop Items"
        val totalAmount = args["total_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val inventoryItem = args["inventory_item_name"]?.jsonPrimitive?.content.orEmpty()
        val qtyDelta = args["quantity_delta"]?.jsonPrimitive?.intOrNull ?: 0
        val notes = args["notes"]?.jsonPrimitive?.content.orEmpty()

        val fields = linkedMapOf(
            "Entry Type" to txType,
            "Items" to itemsSummary,
            "Total Amount" to "${totalAmount.roundToInt()} BDT"
        )
        if (inventoryItem.isNotBlank()) {
            fields["Inventory Item"] = inventoryItem
            fields["Stock Adjustment"] = if (qtyDelta >= 0) "+$qtyDelta" else "$qtyDelta"
        }
        if (notes.isNotBlank()) {
            fields["Notes"] = notes
        }

        repository.appendAuditLog(
            AuditLogEntry(
                userId = userId,
                eventType = "TOOL_CALL",
                title = "Staged Shop Ledger ($txType)",
                details = "Waiting for user approval before saving $itemsSummary (${totalAmount.roundToInt()} BDT)",
                vaultFieldsAccessed = emptyList(),
                referenceId = "SHP-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        val draft = PendingApprovalDraft(
            actionType = "SHOP_TRANSACTION",
            portalTitle = "Small-Shop Cashbook & Inventory",
            fields = fields,
            maskedKeys = emptyList(),
            requiresOtp = false,
            notes = notes
        )

        return JsonObject(
            mapOf(
                "status" to JsonPrimitive("staged_for_user_approval"),
                "message" to JsonPrimitive("Staged for user confirmation. Not committed yet.")
            )
        ) to draft
    }

    private suspend fun handleStageAppointment(
        userId: String,
        args: Map<String, kotlinx.serialization.json.JsonElement>
    ): Pair<JsonObject, PendingApprovalDraft> {
        val doctorOrOffice = args["doctor_or_office"]?.jsonPrimitive?.content ?: "Green Care Clinic"
        val department = args["department"]?.jsonPrimitive?.content ?: "General Medicine"
        val date = args["date"]?.jsonPrimitive?.content ?: "2026-11-18"
        val time = args["time"]?.jsonPrimitive?.content ?: "10:30 AM"
        val patientName = args["patient_name"]?.jsonPrimitive?.content ?: "Patient"
        val notes = args["notes"]?.jsonPrimitive?.content.orEmpty()

        val fields = linkedMapOf(
            "Doctor / Office" to doctorOrOffice,
            "Department" to department,
            "Date" to date,
            "Time" to time,
            "Patient Name" to patientName
        )
        if (notes.isNotBlank()) fields["Notes"] = notes

        repository.appendAuditLog(
            AuditLogEntry(
                userId = userId,
                eventType = "TOOL_CALL",
                title = "Staged Appointment ($doctorOrOffice)",
                details = "Paused for user confirmation for $date at $time",
                vaultFieldsAccessed = emptyList(),
                referenceId = "APT-${UUID.randomUUID().toString().take(6).uppercase()}"
            )
        )

        val draft = PendingApprovalDraft(
            actionType = "DOCTOR_APPOINTMENT",
            portalTitle = "Clinic & Service Appointment",
            fields = fields,
            maskedKeys = emptyList(),
            requiresOtp = false,
            notes = notes
        )

        return JsonObject(
            mapOf(
                "status" to JsonPrimitive("staged_for_user_approval"),
                "message" to JsonPrimitive("Appointment draft ready. Waiting for user to click Confirm & Submit.")
            )
        ) to draft
    }

    companion object {
        private const val TAG = "SahayakAgent"
    }
}
