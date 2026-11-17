package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.AppointmentBooking
import com.example.model.ShopInventoryItem
import com.example.model.ShopTransaction
import com.example.model.UtilityBill
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

fun maskSensitiveValue(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= 4) return "••••"
    return "••••" + trimmed.takeLast(4)
}

fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int = 1400): Bitmap? {
    return try {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null
        scaleBitmapIfNeeded(raw, maxDim)
    } catch (e: Exception) {
        Log.e("MediaHelper", "Failed to decode bitmap from URI", e)
        null
    }
}

fun loadDrawableBitmap(context: Context, drawableResId: Int, maxDim: Int = 1400): Bitmap? {
    return try {
        val raw = BitmapFactory.decodeResource(context.resources, drawableResId) ?: return null
        scaleBitmapIfNeeded(raw, maxDim)
    } catch (e: Exception) {
        Log.e("MediaHelper", "Failed to decode drawable bitmap", e)
        null
    }
}

private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
    val largest = max(bitmap.width, bitmap.height)
    if (largest <= maxDim) return bitmap
    val ratio = maxDim.toFloat() / largest.toFloat()
    val w = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        return try {
            stopAndDiscard()
            val dir = File(context.cacheDir, "voice").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            true
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Could not start recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopAndReadBytes(): ByteArray? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val bytes = outputFile?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
            outputFile?.delete()
            outputFile = null
            bytes
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed stopping recording", e)
            stopAndDiscard()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

fun buildRecordsCsv(
    bills: List<UtilityBill>,
    appointments: List<AppointmentBooking>,
    transactions: List<ShopTransaction>,
    inventory: List<ShopInventoryItem>
): String = buildString {
    appendLine("SECTION,REFERENCE_ID,TITLE_OR_PROVIDER,DETAILS,AMOUNT_OR_QTY,DATE_OR_DUE,STATUS_OR_ALERT")
    bills.forEach { b ->
        appendLine(
            listOf(
                "Bill",
                csvCell(b.referenceId),
                csvCell(b.provider),
                csvCell("${b.billingMonth} (${b.unitsKwh.roundToInt()} kWh) - ${b.comparisonNote}"),
                csvCell("${b.amount.roundToInt()} ${b.currency}"),
                csvCell(b.dueDate),
                csvCell(b.status)
            ).joinToString(",")
        )
    }
    appointments.forEach { a ->
        appendLine(
            listOf(
                "Appointment",
                csvCell(a.referenceId),
                csvCell(a.doctorOrOffice),
                csvCell("${a.department} - ${a.patientName}"),
                csvCell("1 booking"),
                csvCell("${a.date} ${a.time}"),
                csvCell(a.status)
            ).joinToString(",")
        )
    }
    transactions.forEach { t ->
        appendLine(
            listOf(
                "ShopCashbook",
                csvCell(t.referenceId),
                csvCell(t.txType),
                csvCell(t.itemsSummary),
                csvCell("${t.totalAmount.roundToInt()} ${t.currency}"),
                csvCell("-"),
                csvCell("Logged")
            ).joinToString(",")
        )
    }
    inventory.forEach { i ->
        val alert = if (i.quantity <= i.lowStockThreshold) "LOW_STOCK" else "OK"
        appendLine(
            listOf(
                "ShopInventory",
                csvCell(i.id),
                csvCell(i.itemName),
                csvCell("${i.unitPrice.roundToInt()} BDT/${i.unit}"),
                csvCell("${i.quantity} ${i.unit}"),
                csvCell("Min ${i.lowStockThreshold}"),
                csvCell(alert)
            ).joinToString(",")
        )
    }
}

private fun csvCell(raw: String): String {
    val escaped = raw.replace("\"", "\"\"")
    return "\"$escaped\""
}

fun shareCsvReport(context: Context, csvContent: String): Boolean {
    return try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "sahayak_records_${System.currentTimeMillis()}.csv")
        file.writeText(csvContent)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Sahayak Records Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Sahayak Records CSV"))
        true
    } catch (e: Exception) {
        Log.e("CsvExport", "Failed to share CSV", e)
        false
    }
}

fun shareAppApk(context: Context): Boolean {
    return try {
        val apkFile = File(context.applicationContext.dataDir, "../app/build/outputs/apk/debug/app-debug.apk")
        val directFile = File("/app/applet/app/build/outputs/apk/debug/app-debug.apk")
        val fallbackFile = File("app/build/outputs/apk/debug/app-debug.apk")
        val target = when {
            directFile.exists() -> directFile
            fallbackFile.exists() -> fallbackFile
            else -> apkFile
        }
        if (!target.exists()) {
            Log.e("ApkExport", "APK not found at ${target.absolutePath}")
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_SUBJECT, "Sahayak APK Installer")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Sahayak APK"))
        true
    } catch (e: Exception) {
        Log.e("ApkExport", "Failed to share APK", e)
        false
    }
}

