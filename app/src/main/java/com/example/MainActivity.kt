package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.SahayakAgentService
import com.example.data.SahayakRepository
import com.example.firebase.configureAppCheck
import com.example.model.SahayakTab
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.chat.DoerChatScreen
import com.example.ui.records.MyRecordsScreen
import com.example.ui.safety.SafetyAuditScreen
import com.example.ui.theme.SahayakTheme
import com.example.ui.vault.MyVaultScreen
import com.example.viewmodel.SahayakViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure App Check with launch intent before setContent, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SahayakTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        key(user.uid) {
            SahayakShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName,
                userEmail = user.email
            )
        }
    }
}

@Composable
fun SahayakShell(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SahayakViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repository = SahayakRepository(db)
                val agentService = SahayakAgentService(repository)
                SahayakViewModel(
                    repository = repository,
                    agentService = agentService,
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val isAgentBusy by viewModel.isAgentBusy.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vaultFields by viewModel.vaultFields.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val shopTransactions by viewModel.shopTransactions.collectAsStateWithLifecycle()
    val shopInventory by viewModel.shopInventory.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (selectedTab != SahayakTab.CHAT) {
        BackHandler {
            viewModel.selectTab(SahayakTab.CHAT)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        SahayakTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                if (useRail) {
                    NavigationRail {
                        SahayakTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        SahayakTab.CHAT -> DoerChatScreen(
                            messages = messages,
                            currentStep = currentStep,
                            isAgentBusy = isAgentBusy,
                            languageMode = settings.languageMode,
                            errorBanner = errorBanner,
                            onDismissError = viewModel::dismissErrorBanner,
                            onSendTask = viewModel::sendUserTask,
                            onConfirmApproval = viewModel::confirmApproval,
                            onUpdateDraft = viewModel::updateDraftFieldsOnly,
                            onCancelApproval = viewModel::cancelApproval,
                            onVerifyOtp = viewModel::verifyOtpAndComplete,
                            onViewInRecords = { viewModel.selectTab(SahayakTab.RECORDS) },
                            onClearChat = viewModel::clearChatAndReset,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )

                        SahayakTab.VAULT -> MyVaultScreen(
                            vaultFields = vaultFields,
                            onToggleAutoFill = viewModel::toggleVaultAutoFill,
                            onUpdateValue = viewModel::updateVaultFieldValue
                        )

                        SahayakTab.RECORDS -> MyRecordsScreen(
                            bills = bills,
                            appointments = appointments,
                            transactions = shopTransactions,
                            inventory = shopInventory,
                            onUpdateAppointment = viewModel::updateAppointment,
                            onAdjustStock = viewModel::adjustStock,
                            onAddShopItem = viewModel::addShopItem,
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )

                        SahayakTab.SAFETY -> SafetyAuditScreen(
                            settings = settings,
                            auditLogs = auditLogs,
                            userEmail = userEmail,
                            onUpdatePreferences = viewModel::updateSafetyPreferences,
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: SahayakTab, selected: Boolean) = when (tab) {
    SahayakTab.CHAT -> if (selected) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline
    SahayakTab.VAULT -> if (selected) Icons.Filled.Lock else Icons.Outlined.Lock
    SahayakTab.RECORDS -> if (selected) Icons.Filled.FolderCopy else Icons.Outlined.FolderCopy
    SahayakTab.SAFETY -> if (selected) Icons.Filled.GppGood else Icons.Outlined.GppGood
}
