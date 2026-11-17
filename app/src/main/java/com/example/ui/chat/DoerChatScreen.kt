package com.example.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AgentStepState
import com.example.model.ChatMessage
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.VoiceNoteRecorder
import com.example.util.createTempCameraUri
import com.example.util.loadDrawableBitmap
import com.example.util.loadScaledBitmap
import com.example.util.maskSensitiveValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoerChatScreen(
    messages: List<ChatMessage>,
    currentStep: AgentStepState,
    isAgentBusy: Boolean,
    languageMode: String,
    errorBanner: String?,
    onDismissError: () -> Unit,
    onSendTask: (text: String, bitmap: Bitmap?, audioBytes: ByteArray?, audioMime: String?, attachmentLabel: String) -> Unit,
    onConfirmApproval: (ChatMessage, Map<String, String>?) -> Unit,
    onUpdateDraft: (ChatMessage, Map<String, String>) -> Unit,
    onCancelApproval: (ChatMessage) -> Unit,
    onVerifyOtp: (ChatMessage, String) -> Unit,
    onViewInRecords: () -> Unit,
    onClearChat: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedLabel by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.stopAndDiscard() }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = loadScaledBitmap(context, uri)
            if (bmp != null) {
                attachedBitmap = bmp
                attachedLabel = "Uploaded Photo"
            } else {
                onShowMessage("Could not read selected image.")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val bmp = loadScaledBitmap(context, uri)
            if (bmp != null) {
                attachedBitmap = bmp
                attachedLabel = "Camera Photo"
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.startRecording()) {
                isRecording = true
            } else {
                onShowMessage("Could not start microphone recording on this device.")
            }
        } else {
            onShowMessage("Microphone permission is needed for voice notes. You can still type or upload photos.")
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header + Demo Starter Chips
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Sahayak Safe Assistant",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sahayak",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (languageMode == "bn_en") "Bangla + English • Approval First" else "Safe Action Doer • Approval First",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearChat,
                        enabled = !isAgentBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Start new task",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 4 One-Tap Demo Starter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StarterChip(
                        label = "Check & Log Electricity Bill",
                        icon = Icons.Filled.Bolt,
                        tag = "starter_electricity_bill",
                        enabled = !isAgentBusy
                    ) {
                        val sampleBmp = loadDrawableBitmap(context, R.drawable.img_sample_bill)
                        onSendTask(
                            "Please check this electricity bill photo, compare it with my previous months, and prepare the payment draft.",
                            sampleBmp,
                            null,
                            null,
                            "Sample DESCO Bill Photo"
                        )
                    }

                    StarterChip(
                        label = "Book Doctor Appointment",
                        icon = Icons.Filled.CalendarMonth,
                        tag = "starter_doctor_appointment",
                        enabled = !isAgentBusy
                    ) {
                        onSendTask(
                            "Book a general medicine doctor appointment at my preferred clinic for tomorrow at 10:30 AM using my Vault details.",
                            null,
                            null,
                            null,
                            ""
                        )
                    }

                    StarterChip(
                        label = "Log Shop Receipt / Sale",
                        icon = Icons.Filled.Storefront,
                        tag = "starter_shop_receipt",
                        enabled = !isAgentBusy
                    ) {
                        onSendTask(
                            "Log a shop sale: sold 2 bags of Miniket Rice (5kg) and 1 bottle of Soybean Oil (2L) for 1320 BDT total.",
                            null,
                            null,
                            null,
                            ""
                        )
                    }

                    StarterChip(
                        label = "Fill Service Application Form",
                        icon = Icons.Filled.Description,
                        tag = "starter_service_form",
                        enabled = !isAgentBusy
                    ) {
                        onSendTask(
                            "Help me fill out a municipal Trade License Renewal service application form using my saved Vault details.",
                            null,
                            null,
                            null,
                            ""
                        )
                    }
                }

                // Live Step Progress Pill
                Spacer(Modifier.height(8.dp))
                LiveStepProgressPill(
                    currentStep = currentStep,
                    isBusy = isAgentBusy,
                    languageMode = languageMode,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Calm Error Recovery Banner
        AnimatedVisibility(visible = !errorBanner.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Conversation Timeline
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id.ifBlank { "${it.clientTimestampMs}_${it.sender}" } }) { msg ->
                ChatTimelineItem(
                    message = msg,
                    onConfirmApproval = { onConfirmApproval(msg, null) },
                    onEditApproval = { editingMessage = msg },
                    onCancelApproval = { onCancelApproval(msg) },
                    onVerifyOtp = { code -> onVerifyOtp(msg, code) },
                    onViewInRecords = onViewInRecords
                )
            }
        }

        // Attached Photo Preview Chip
        AnimatedVisibility(visible = attachedBitmap != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached photo preview",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachedLabel.ifBlank { "Photo ready" },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Sahayak will read only clearly visible text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        attachedBitmap = null
                        attachedLabel = ""
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attached photo")
                    }
                }
            }
        }

        // Sticky Bottom Input Bar (Voice / Camera / Text)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp
        ) {
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Listening... ${recordingSeconds}s (English / বাংলা)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isRecording = false
                                voiceRecorder.stopAndDiscard()
                            }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                isRecording = false
                                val bytes = voiceRecorder.stopAndReadBytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    onSendTask(
                                        inputText.trim(),
                                        attachedBitmap,
                                        bytes,
                                        "audio/mp4",
                                        "Voice Note (${recordingSeconds}s)"
                                    )
                                    inputText = ""
                                    attachedBitmap = null
                                    attachedLabel = ""
                                } else {
                                    onShowMessage("No voice audio captured. Please try speaking closer or type your request.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        enabled = !isAgentBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Attach or snap a photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                if (voiceRecorder.startRecording()) {
                                    isRecording = true
                                } else {
                                    onShowMessage("Could not start microphone recording on this device.")
                                }
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !isAgentBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("voice_record_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Record voice message",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (languageMode == "bn_en") "বাংলা বা English এ লিখুন..." else "Ask Sahayak to do something...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_text_input")
                    )

                    Spacer(Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            val bmp = attachedBitmap
                            val lbl = attachedLabel
                            val txt = inputText.trim()
                            inputText = ""
                            attachedBitmap = null
                            attachedLabel = ""
                            onSendTask(txt, bmp, null, null, lbl)
                        },
                        enabled = !isAgentBusy && (inputText.isNotBlank() || attachedBitmap != null),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send task"
                        )
                    }
                }
            }
        }
    }

    // Photo / Camera Attachment Bottom Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add Bill, Receipt, or Form Photo",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Sahayak extracts readable details and pauses for your confirmation before doing anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                AttachmentOptionRow(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "Choose from Photo Gallery",
                    subtitle = "Pick an image without granting full storage access"
                ) {
                    showAttachmentSheet = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                AttachmentOptionRow(
                    icon = Icons.Filled.CameraAlt,
                    title = "Take a Photo with Camera",
                    subtitle = "Snap a utility bill, clinic slip, or receipt"
                ) {
                    showAttachmentSheet = false
                    val uri = createTempCameraUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }
                AttachmentOptionRow(
                    icon = Icons.Filled.AttachFile,
                    title = "Attach Built-in Sample DESCO Bill",
                    subtitle = "Quick test with realistic November 2026 bill"
                ) {
                    showAttachmentSheet = false
                    val bmp = loadDrawableBitmap(context, R.drawable.img_sample_bill)
                    if (bmp != null) {
                        attachedBitmap = bmp
                        attachedLabel = "Sample DESCO Bill Photo"
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Inline Field Editor Dialog for Approval Cards
    editingMessage?.let { msgToEdit ->
        EditDraftFieldsDialog(
            message = msgToEdit,
            onDismiss = { editingMessage = null },
            onSaveOnly = { updated ->
                editingMessage = null
                onUpdateDraft(msgToEdit, updated)
            },
            onSaveAndConfirm = { updated ->
                editingMessage = null
                onConfirmApproval(msgToEdit, updated)
            }
        )
    }
}

@Composable
private fun StarterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(tag)
    )
}

@Composable
private fun LiveStepProgressPill(
    currentStep: AgentStepState,
    isBusy: Boolean,
    languageMode: String,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "step_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val (bgColor, fgColor) = when (currentStep) {
        AgentStepState.AWAITING_APPROVAL, AgentStepState.AWAITING_OTP ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AgentStepState.COMPLETED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AgentStepState.PAUSED_ERROR, AgentStepState.CANCELLED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val text = if (languageMode == "bn_en") currentStep.bnLabel else currentStep.label
    val pipelineHint = when (currentStep) {
        AgentStepState.INTAKE -> "Step 1/4 • $text"
        AgentStepState.VAULT_LOOKUP -> "Step 2/4 • $text"
        AgentStepState.DRAFTING_ACTION -> "Step 3/4 • $text"
        AgentStepState.AWAITING_APPROVAL -> "Step 4/4 • $text"
        AgentStepState.AWAITING_OTP -> "Verification • $text"
        else -> text
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .alpha(if (isBusy) pulseAlpha else 1f)
                    .clip(CircleShape)
                    .background(fgColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = pipelineHint,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor
            )
        }
    }
}

@Composable
private fun ChatTimelineItem(
    message: ChatMessage,
    onConfirmApproval: () -> Unit,
    onEditApproval: () -> Unit,
    onCancelApproval: () -> Unit,
    onVerifyOtp: (String) -> Unit,
    onViewInRecords: () -> Unit
) {
    val isUser = message.sender == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Text Bubble
        if (message.text.isNotBlank()) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                tonalElevation = if (isUser) 0.dp else 1.dp,
                border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.attachmentLabel.isNotBlank()) {
                        Surface(
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = message.attachmentLabel,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Interactive Cards rendered inline for Assistant
        if (!isUser && message.cardType == "APPROVAL" && message.status == "active") {
            Spacer(Modifier.height(8.dp))
            ApprovalCard(
                message = message,
                onConfirm = onConfirmApproval,
                onEdit = onEditApproval,
                onCancel = onCancelApproval
            )
        } else if (!isUser && message.cardType == "OTP" && message.status == "approved") {
            Spacer(Modifier.height(8.dp))
            OtpVerificationCard(
                portalTitle = message.portalTitle,
                onVerify = onVerifyOtp,
                onCancel = onCancelApproval
            )
        } else if (!isUser && message.cardType == "RECEIPT") {
            Spacer(Modifier.height(8.dp))
            AuditReceiptCard(
                message = message,
                onViewInRecords = onViewInRecords
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    message: ChatMessage,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .testTag("approval_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Approval Required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = message.portalTitle.ifBlank { "Draft Action" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (message.requiresOtp) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "OTP Step",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Draft fields table
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.draftFields.forEach { (key, value) ->
                        val isMasked = message.maskedKeys.any { it.equals(key, ignoreCase = true) } ||
                            key.contains("ID", ignoreCase = true) ||
                            key.contains("Account", ignoreCase = true) ||
                            key.contains("Meter", ignoreCase = true) ||
                            key.contains("Phone", ignoreCase = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isMasked) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Protected field",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isMasked) maskSensitiveValue(value) else value,
                                style = if (isMasked) {
                                    MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Three clear action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("confirm_submit_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirm & Submit", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onEdit,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("edit_details_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cancel_approval_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun OtpVerificationCard(
    portalTitle: String,
    onVerify: (String) -> Unit,
    onCancel: () -> Unit
) {
    var otpCode by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .testTag("otp_verification_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OTP Verification • $portalTitle",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enter the 6-digit code sent to your phone (demo code: 482910).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            OutlinedTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6) otpCode = it.filter(Char::isDigit) },
                label = { Text("6-digit OTP Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("otp_code_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { otpCode = "482910" },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Use 482910")
                }
                Button(
                    onClick = { onVerify(otpCode) },
                    enabled = otpCode.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("verify_otp_button")
                ) {
                    Text("Verify & Complete")
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AuditReceiptCard(
    message: ChatMessage,
    onViewInRecords: () -> Unit
) {
    val timeLabel = remember(message.updatedAt, message.clientTimestampMs) {
        val date = message.updatedAt?.toDate() ?: Date(
            if (message.clientTimestampMs > 0L) message.clientTimestampMs else System.currentTimeMillis()
        )
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(date)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .testTag("audit_receipt_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Receipt verified",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Official Proof Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ref ID",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = message.referenceId.ifBlank { "SAH-DONE" },
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(
                onClick = onViewInRecords,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("view_in_records_button")
            ) {
                Text("View in Records")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditDraftFieldsDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSaveOnly: (Map<String, String>) -> Unit,
    onSaveAndConfirm: (Map<String, String>) -> Unit
) {
    val editableState = remember(message.id) {
        mutableStateMapOf<String, String>().apply {
            putAll(message.draftFields)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Details • ${message.portalTitle}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                editableState.keys.toList().forEach { key ->
                    OutlinedTextField(
                        value = editableState[key].orEmpty(),
                        onValueChange = { editableState[key] = it },
                        label = { Text(key) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveAndConfirm(editableState.toMap()) }) {
                Text("Save & Confirm")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = { onSaveOnly(editableState.toMap()) }) {
                    Text("Save Draft")
                }
            }
        }
    )
}
