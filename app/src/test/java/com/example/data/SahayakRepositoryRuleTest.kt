package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AppointmentBooking
import com.example.model.AuditLogEntry
import com.example.model.ChatMessage
import com.example.model.ShopTransaction
import com.example.model.UtilityBill
import com.example.model.VaultField
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SahayakRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserSeeded_and_commitRecords_succeedsForOwner() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = SahayakRepository(firestore)

        val seedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserSeeded("Alice Tester", "alice@test.com")
        }
        assertTrue("Seed failed: ${seedRes.exceptionOrNull()}", seedRes.isSuccess)

        val vaultList = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVault(uid).first { it.isNotEmpty() }
        }
        assertTrue(vaultList.any { it.fieldKey == "full_name" })

        val billId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.commitBill(
                UtilityBill(
                    userId = uid,
                    provider = "DESCO",
                    accountNo = "48219044",
                    billingMonth = "November 2026",
                    unitsKwh = 312.0,
                    amount = 2450.0,
                    currency = "BDT",
                    dueDate = "2026-11-25",
                    previousAmount = 2150.0,
                    comparisonNote = "+14% vs Oct",
                    status = "Paid",
                    referenceId = "SAH-BILL-${UUID.randomUUID().toString().take(6)}"
                )
            ).getOrThrow()
        }
        assertTrue(billId.isNotEmpty())

        val apptId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.commitAppointment(
                AppointmentBooking(
                    userId = uid,
                    doctorOrOffice = "Dr. Nusrat",
                    department = "Medicine",
                    date = "2026-11-20",
                    time = "11:00 AM",
                    patientName = "Alice",
                    notes = "Checkup",
                    status = "Confirmed",
                    referenceId = "SAH-APT-${UUID.randomUUID().toString().take(6)}"
                )
            ).getOrThrow()
        }
        assertTrue(apptId.isNotEmpty())

        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.commitShopTransaction(
                ShopTransaction(
                    userId = uid,
                    txType = "SALE",
                    itemsSummary = "Miniket Rice x2",
                    totalAmount = 960.0,
                    currency = "BDT",
                    notes = "Cash",
                    referenceId = "SAH-SHP-${UUID.randomUUID().toString().take(6)}"
                ),
                inventoryItemName = "Miniket Rice",
                quantityDelta = -2L
            ).getOrThrow()
        }
        assertTrue(txId.isNotEmpty())

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addChatMessage(
                ChatMessage(
                    userId = uid,
                    sender = "assistant",
                    text = "Approval ready",
                    stepState = "AWAITING_APPROVAL",
                    cardType = "APPROVAL",
                    actionType = "UTILITY_PAYMENT",
                    portalTitle = "DESCO",
                    draftFields = mapOf("Amount" to "2450 BDT"),
                    maskedKeys = listOf("Account"),
                    requiresOtp = true,
                    status = "active"
                )
            ).getOrThrow()
        }
        assertTrue(msgId.isNotEmpty())

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.appendAuditLog(
                AuditLogEntry(
                    userId = uid,
                    eventType = "TOOL_CALL",
                    title = "Vault Read",
                    details = "Read Full Name",
                    vaultFieldsAccessed = listOf("full_name"),
                    referenceId = "SAH-LOG-${UUID.randomUUID().toString().take(6)}"
                )
            ).getOrThrow()
        }
        assertTrue(logId.isNotEmpty())
    }

    @Test
    fun observeVault_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SahayakRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeVault("some_uid_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                firestoreException.code
            )
        }
    }

    @Test
    fun crossUserVaultAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
        val aliceRepo = SahayakRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveVaultField(
                VaultField(
                    id = "phone",
                    userId = aliceUid,
                    fieldKey = "phone",
                    label = "Phone",
                    value = "+8801712000000",
                    isSensitive = true,
                    allowAutoFill = true
                )
            ).getOrThrow()
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
        val bobRepo = SahayakRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeVault(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user vault read")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                firestoreException.code
            )
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
