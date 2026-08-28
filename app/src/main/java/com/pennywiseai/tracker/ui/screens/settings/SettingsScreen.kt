package com.pennywiseai.tracker.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.pennywiseai.tracker.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.amber_light
import com.pennywiseai.tracker.ui.theme.amber_dark
import com.pennywiseai.tracker.ui.theme.orange_light
import com.pennywiseai.tracker.ui.theme.orange_dark
import com.pennywiseai.tracker.ui.theme.green_light
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.red_light
import com.pennywiseai.tracker.ui.theme.red_dark
import com.pennywiseai.tracker.ui.theme.pink_light
import com.pennywiseai.tracker.ui.theme.pink_dark
import com.pennywiseai.tracker.ui.theme.purple_light
import com.pennywiseai.tracker.ui.theme.purple_dark
import com.pennywiseai.tracker.ui.theme.cyan_light
import com.pennywiseai.tracker.ui.theme.cyan_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.grey_light
import com.pennywiseai.tracker.ui.theme.grey_dark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.components.PermissionDisclosureDialog


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToUnrecognizedSms: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToTransactionGroups: () -> Unit = {},
    onNavigateToExchangeRates: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToImportStatement: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    appLockViewModel: com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel = hiltViewModel(),
    permissionViewModel: com.pennywiseai.tracker.ui.viewmodel.PermissionViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by settingsViewModel.downloadState.collectAsStateWithLifecycle()
    val downloadProgress by settingsViewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by settingsViewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by settingsViewModel.totalMB.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled by settingsViewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val smsScanMonths by settingsViewModel.smsScanMonths.collectAsStateWithLifecycle(initialValue = 3)
    val smsScanAllTime by settingsViewModel.smsScanAllTime.collectAsStateWithLifecycle(initialValue = false)
    val smsScanUseCustomDate by settingsViewModel.smsScanUseCustomDate.collectAsStateWithLifecycle(initialValue = false)
    val smsScanCustomDate by settingsViewModel.smsScanCustomDate.collectAsStateWithLifecycle(initialValue = null)
    val baseCurrency by settingsViewModel.baseCurrency.collectAsStateWithLifecycle(initialValue = "")
    val numberFormatStyle by settingsViewModel.numberFormatStyle.collectAsStateWithLifecycle(initialValue = NumberFormatStyle.AUTO)
    val budgetCycleStartDay by settingsViewModel.budgetCycleStartDay.collectAsStateWithLifecycle(initialValue = 1)
    val importExportMessage by settingsViewModel.importExportMessage.collectAsStateWithLifecycle()
    val exportedBackupFile by settingsViewModel.exportedBackupFile.collectAsStateWithLifecycle()
    val unifiedCurrencyMode by settingsViewModel.unifiedCurrencyMode.collectAsStateWithLifecycle(initialValue = false)
    val displayCurrency by settingsViewModel.displayCurrency.collectAsStateWithLifecycle(initialValue = "")
    val availableCurrencies by settingsViewModel.availableCurrencies.collectAsStateWithLifecycle()
    val accounts by settingsViewModel.accounts.collectAsStateWithLifecycle()
    val mainAccountKey by settingsViewModel.mainAccountKey.collectAsStateWithLifecycle()
    val appLanguage by settingsViewModel.appLanguage.collectAsStateWithLifecycle(initialValue = null)
    val useContactsForVpa by settingsViewModel.useContactsForVpa.collectAsStateWithLifecycle(initialValue = false)
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    val scheduledFolderBackupEnabled by settingsViewModel.scheduledFolderBackupEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scheduledFolderBackupLastTimestamp by settingsViewModel.scheduledFolderBackupLastTimestamp.collectAsStateWithLifecycle(initialValue = null)
    val requestFolderPicker by settingsViewModel.requestFolderPicker.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    // F-Droid builds have no Play billing, so they show a "Support development"
    // tip jar instead of the (un-buyable) Pro upsell. Play builds keep Pro.
    val isFdroidBuild = com.pennywiseai.tracker.BuildConfig.IS_FDROID_BUILD
    // Launches the runtime permission request. If granted, we flip the
    // preference on; if denied, leave the switch off so the user can try
    // again without us silently turning the feature on later.
    val readContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) settingsViewModel.setUseContactsForVpa(true)
    }
    var showSmsScanDialog by remember { mutableStateOf(false) }
    var showSmsScanDatePicker by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showDisplayCurrencyDialog by remember { mutableStateOf(false) }
    var showNumberFormatDialog by remember { mutableStateOf(false) }
    var showBudgetCycleDialog by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showMainAccountDropdown by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCleanUpDuplicatesDialog by remember { mutableStateOf(false) }
    var showClearMerchantsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionUiState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    val hasNotificationAccess = permissionUiState.hasNotificationAccess

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionViewModel.refreshPermissions()
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionViewModel.refreshPermissions()
    }

    var showSmsDisclosure by remember { mutableStateOf(false) }
    var showMediaDisclosure by remember { mutableStateOf(false) }

    if (showSmsDisclosure) {
        PermissionDisclosureDialog(
            onDismissRequest = { showSmsDisclosure = false },
            onConfirm = {
                val smsPermissions = mutableListOf(
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    smsPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                smsPermissionLauncher.launch(smsPermissions.toTypedArray())
            },
            title = "PennyWise Pro",
            description = "PennyWise reads SMS messages from your bank to automatically record transactions. We only process transaction-related messages; personal conversations are never read or stored."
        )
    }

    if (showMediaDisclosure) {
        PermissionDisclosureDialog(
            onDismissRequest = { showMediaDisclosure = false },
            onConfirm = {
                val mediaPermissions = mutableListOf<String>()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    mediaPermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                    mediaPermissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                    mediaPermissions.add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    mediaPermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                    mediaPermissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    mediaPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                mediaPermissionLauncher.launch(mediaPermissions.toTypedArray())
            },
            title = stringResource(R.string.settings_disclosure_media_title),
            description = stringResource(R.string.settings_disclosure_media_body)
        )
    }

    if (showCleanUpDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showCleanUpDuplicatesDialog = false },
            title = { Text(stringResource(R.string.settings_clean_duplicates_title)) },
            text = { Text(stringResource(R.string.settings_clean_duplicates_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanUpDuplicatesDialog = false
                        settingsViewModel.cleanUpLegacyDuplicates()
                    }
                ) {
                    Text(stringResource(R.string.settings_proceed_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanUpDuplicatesDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    var showRestoreMerchantsDialog by remember { mutableStateOf(false) }

    if (showRestoreMerchantsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreMerchantsDialog = false },
            title = { Text("กู้คืนชื่อผู้รับจากสลิป (Restore Slip Payee Names)") },
            text = { Text("ระบบจะอ่านชื่อผู้รับเงินจากข้อความสลิปเดิมของทุกรายการให้ใหม่โดยอัตโนมัติ โดยหมวดหมู่ ยอดเงิน และวันที่ที่คุณจัดไว้จะคงเดิมทั้งหมด") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreMerchantsDialog = false
                        settingsViewModel.restoreSlipMerchants { count ->
                            Toast.makeText(context, "กู้คืนชื่อผู้รับสำเร็จ $count รายการ", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreMerchantsDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showClearMerchantsDialog) {
        AlertDialog(
            onDismissRequest = { showClearMerchantsDialog = false },
            title = { Text("Reset Learned Merchants") },
            text = { Text("This will permanently delete all learned merchant name corrections (from slip scans). Are you sure you want to reset this history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.clearLearnedMerchants()
                        showClearMerchantsDialog = false
                        Toast.makeText(context, "Learned merchants reset successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMerchantsDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val notificationAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        permissionViewModel.refreshNotificationAccess()
    }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importBackup(it)
            }
        }
    )

    // File picker for CSV transaction import
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importCsv(it)
            }
        }
    )

    // File saver for export
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.saveBackupToFile(it)
            }
        }
    )

    val backupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let { settingsViewModel.onBackupFolderSelected(it) }
        }
    )

    LaunchedEffect(requestFolderPicker) {
        if (requestFolderPicker) {
            backupFolderLauncher.launch(null)
            settingsViewModel.onFolderPickerLaunched()
        }
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = scrollBehaviorSmall
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.settings_title),
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = { SettingsNavigationContent(onNavigateBack) },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // ── PennyWise Pro / Support development ──
            // Top of Settings on purpose: highest-discoverability slot.
            // F-Droid builds have no Play billing (everything is already
            // unlocked), so instead of an un-buyable Pro upsell they get a
            // "Support development" tip jar. Play builds keep the Pro upgrade.
            if (isFdroidBuild) {
                SectionHeaderV2(title = stringResource(R.string.support_title))
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Favorite,
                        iconBgColor = yellow_light,
                        iconTint = yellow_dark,
                        title = stringResource(R.string.support_title),
                        subtitle = stringResource(R.string.support_subtitle),
                        onClick = { showSupportDialog = true },
                        position = ListItemPosition.Single,
                    )
                }
            } else {
                // Row content adapts to entitlement state — paid users see
                // "Active" so the row reads as status, free users see "Upgrade"
                // so it reads as a call-to-action.
                SectionHeaderV2(title = stringResource(R.string.settings_pro_title))
                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.AutoAwesome,
                        iconBgColor = yellow_light,
                        iconTint = yellow_dark,
                        title = if (isProEntitled) {
                            stringResource(R.string.settings_pro_title)
                        } else {
                            stringResource(R.string.settings_pro_upgrade)
                        },
                        subtitle = if (isProEntitled) {
                            stringResource(R.string.settings_pro_active_subtitle)
                        } else {
                            stringResource(R.string.settings_pro_free_subtitle)
                        },
                        onClick = { showUpgradeSheet = true },
                        position = ListItemPosition.Single,
                    )
                }
            }

            // ── Required Permissions (Prominent Disclosure) ──
            val missingSms = !permissionUiState.hasPermission
            val missingMedia = !permissionUiState.hasMediaPermission
            val missingNotif = !hasNotificationAccess

            if (missingSms || missingMedia || missingNotif) {
                SectionHeaderV2(title = stringResource(R.string.settings_action_required))
                SettingsGroup {
                    if (missingSms) {
                        SettingsNavItem(
                            icon = Icons.Default.Sms,
                            iconBgColor = red_light,
                            iconTint = red_dark,
                            title = stringResource(R.string.settings_perm_sms_title),
                            subtitle = stringResource(R.string.settings_perm_sms_subtitle),
                            onClick = { showSmsDisclosure = true },
                            position = if (!missingMedia && !missingNotif) ListItemPosition.Single else ListItemPosition.Top
                        )
                    }
                    if (missingMedia) {
                        SettingsNavItem(
                            icon = Icons.Default.Image,
                            iconBgColor = red_light,
                            iconTint = red_dark,
                            title = stringResource(R.string.settings_perm_media_title),
                            subtitle = stringResource(R.string.settings_perm_media_subtitle),
                            onClick = { showMediaDisclosure = true },
                            position = if (missingSms && !missingNotif) ListItemPosition.Middle 
                                      else if (!missingSms && !missingNotif) ListItemPosition.Single 
                                      else if (missingSms) ListItemPosition.Middle 
                                      else ListItemPosition.Top
                        )
                    }
                    if (missingNotif) {
                        SettingsNavItem(
                            icon = Icons.Default.NotificationsActive,
                            iconBgColor = red_light,
                            iconTint = red_dark,
                            title = stringResource(R.string.settings_perm_notif_title),
                            subtitle = stringResource(R.string.settings_perm_notif_subtitle),
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                notificationAccessLauncher.launch(intent)
                            },
                            position = if (missingSms || missingMedia) ListItemPosition.Bottom else ListItemPosition.Single
                        )
                    }
                }
            }

            // ── Personalization ──
            SectionHeaderV2(title = stringResource(R.string.settings_personalization))
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.Translate,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = stringResource(R.string.settings_language),
                    subtitle = when (appLanguage) {
                        null -> stringResource(R.string.lang_system_default)
                        "en" -> stringResource(R.string.lang_english)
                        "th" -> stringResource(R.string.lang_thai)
                        else -> appLanguage ?: ""
                    },
                    onClick = { showLanguageDialog = true },
                    position = ListItemPosition.Top
                )
                SettingsNavItem(
                    icon = Icons.Default.Palette,
                    iconBgColor = orange_light,
                    iconTint = orange_dark,
                    title = stringResource(R.string.settings_appearance),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    onClick = onNavigateToAppearance,
                    position = ListItemPosition.Bottom
                )
            }

            // ── Currency ──
            SectionHeaderV2(title = stringResource(R.string.settings_currency))
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.CurrencyExchange,
                    iconBgColor = green_light,
                    iconTint = green_dark,
                    title = stringResource(R.string.settings_unified_mode_title),
                    subtitle = stringResource(R.string.settings_unified_mode_subtitle),
                    checked = unifiedCurrencyMode,
                    onCheckedChange = { settingsViewModel.setUnifiedCurrencyMode(it) },
                    position = ListItemPosition.Top
                )
                AnimatedVisibility(visible = unifiedCurrencyMode) {
                    SettingsNavItem(
                        icon = Icons.Default.AttachMoney,
                        iconBgColor = teal_light,
                        iconTint = teal_dark,
                        title = stringResource(R.string.settings_display_currency_title),
                        subtitle = stringResource(R.string.settings_display_currency_subtitle),
                        onClick = { showDisplayCurrencyDialog = true },
                        position = ListItemPosition.Middle,
                        trailingText = "${CurrencyFormatter.getCurrencySymbol(displayCurrency)} $displayCurrency"
                    )
                }
                SettingsNavItem(
                    icon = Icons.Default.SwapHoriz,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = stringResource(R.string.settings_exchange_rates_title),
                    subtitle = stringResource(R.string.settings_exchange_rates_subtitle),
                    onClick = onNavigateToExchangeRates,
                    position = ListItemPosition.Middle
                )
                SettingsDropdownItem(
                    icon = Icons.Default.Flag,
                    iconBgColor = indigo_light,
                    iconTint = indigo_dark,
                    title = stringResource(R.string.settings_base_currency_title),
                    subtitle = stringResource(R.string.settings_base_currency_subtitle),
                    currentValue = "${CurrencyFormatter.getCurrencySymbol(baseCurrency)} $baseCurrency",
                    expanded = showCurrencyDropdown,
                    onExpandedChange = { showCurrencyDropdown = it },
                    position = ListItemPosition.Middle
                ) {
                    availableCurrencies.forEach { currency ->
                        DropdownMenuItem(
                            text = {
                                Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency")
                            },
                            onClick = {
                                settingsViewModel.updateBaseCurrency(currency)
                                showCurrencyDropdown = false
                            },
                            leadingIcon = if (currency == baseCurrency) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }

                // Main account → sets the default currency (unless explicitly chosen above).
                if (accounts.isNotEmpty()) {
                    val mainAccount = accounts.firstOrNull {
                        "${it.bankName}_${it.accountLast4}" == mainAccountKey
                    }
                    SettingsDropdownItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        iconBgColor = purple_light,
                        iconTint = purple_dark,
                        title = stringResource(R.string.settings_main_account_title),
                        subtitle = stringResource(R.string.settings_main_account_subtitle),
                        currentValue = mainAccount?.let { acc ->
                            val name = acc.alias?.takeIf { it.isNotBlank() } ?: acc.bankName
                            AccountBalanceEntity.accountLabel(name, acc.accountLast4)
                        } ?: stringResource(R.string.settings_not_set),
                        expanded = showMainAccountDropdown,
                        onExpandedChange = { showMainAccountDropdown = it },
                        position = ListItemPosition.Middle
                    ) {
                        accounts.forEach { account ->
                            val name = account.alias?.takeIf { it.isNotBlank() } ?: account.bankName
                            val label = AccountBalanceEntity.accountLabel(name, account.accountLast4)
                            val key = "${account.bankName}_${account.accountLast4}"
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    settingsViewModel.setMainAccount(account)
                                    showMainAccountDropdown = false
                                },
                                leadingIcon = if (key == mainAccountKey) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                SettingsNavItem(
                    icon = Icons.Default.Numbers,
                    iconBgColor = green_light,
                    iconTint = green_dark,
                    title = stringResource(R.string.settings_number_format_title),
                    subtitle = stringResource(R.string.settings_number_format_subtitle),
                    onClick = { showNumberFormatDialog = true },
                    position = ListItemPosition.Bottom,
                    trailingText = stringResource(numberFormatStyleLabel(numberFormatStyle))
                )
            }

            // ── Budget ──
            // The cycle start day is a budgeting concept, but it changes how
            // Home / Analytics bucket transactions, so it lives up here next
            // to the other "display" knobs rather than buried in Data
            // Management with the budgets list.
            SectionHeaderV2(title = stringResource(R.string.settings_budget))
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.DateRange,
                    iconBgColor = teal_light,
                    iconTint = teal_dark,
                    title = stringResource(R.string.settings_budget_cycle_title),
                    subtitle = stringResource(R.string.settings_budget_cycle_subtitle),
                    onClick = { showBudgetCycleDialog = true },
                    position = ListItemPosition.Single,
                    trailingText = ordinalSuffix(budgetCycleStartDay)
                )
            }

            // ── Contacts ──
            SectionHeaderV2(title = stringResource(R.string.settings_contacts_section))
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.Contacts,
                    iconBgColor = teal_light,
                    iconTint = teal_dark,
                    title = stringResource(R.string.settings_contacts_vpa_title),
                    subtitle = stringResource(R.string.settings_contacts_vpa_subtitle),
                    checked = useContactsForVpa,
                    onCheckedChange = { wantsOn ->
                        if (wantsOn) {
                            val alreadyGranted = androidx.core.content.ContextCompat
                                .checkSelfPermission(
                                    context,
                                    android.Manifest.permission.READ_CONTACTS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (alreadyGranted) {
                                settingsViewModel.setUseContactsForVpa(true)
                            } else {
                                readContactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                            }
                        } else {
                            settingsViewModel.setUseContactsForVpa(false)
                        }
                    },
                    position = ListItemPosition.Single
                )
            }

            // ── Security ──
            SectionHeaderV2(title = stringResource(R.string.settings_security))
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.Lock,
                    iconBgColor = red_light,
                    iconTint = red_dark,
                    title = stringResource(R.string.settings_app_lock_title),
                    subtitle = if (appLockUiState.canUseBiometric) {
                        stringResource(R.string.settings_app_lock_subtitle)
                    } else {
                        appLockUiState.biometricCapability.getErrorMessage()
                    },
                    checked = appLockUiState.isLockEnabled,
                    onCheckedChange = { appLockViewModel.setAppLockEnabled(it) },
                    enabled = appLockUiState.canUseBiometric,
                    position = if (appLockUiState.isLockEnabled) ListItemPosition.Top else ListItemPosition.Single
                )
                AnimatedVisibility(visible = appLockUiState.isLockEnabled) {
                    SettingsNavItem(
                        icon = Icons.Default.Timer,
                        iconBgColor = pink_light,
                        iconTint = pink_dark,
                        title = stringResource(R.string.settings_lock_timeout_title),
                        subtitle = when (appLockUiState.timeoutMinutes) {
                            0 -> stringResource(R.string.settings_lock_timeout_immediate)
                            1 -> stringResource(R.string.settings_lock_timeout_1min)
                            else -> stringResource(R.string.settings_lock_timeout_minutes, appLockUiState.timeoutMinutes)
                        },
                        onClick = { showTimeoutDialog = true },
                        position = ListItemPosition.Bottom
                    )
                }
            }

            // ── Data Management ──
            SectionHeaderV2(title = stringResource(R.string.settings_data_management))
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.AccountBalance,
                    iconBgColor = red_light,
                    iconTint = red_dark,
                    title = stringResource(R.string.settings_manage_accounts_title),
                    subtitle = stringResource(R.string.settings_manage_accounts_subtitle),
                    onClick = onNavigateToManageAccounts,
                    position = ListItemPosition.Top
                )
                SettingsNavItem(
                    icon = Icons.Default.Category,
                    iconBgColor = purple_light,
                    iconTint = purple_dark,
                    title = stringResource(R.string.settings_categories_title),
                    subtitle = stringResource(R.string.settings_categories_subtitle),
                    onClick = onNavigateToCategories,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = orange_light,
                    iconTint = orange_dark,
                    title = stringResource(R.string.settings_smart_rules_title),
                    subtitle = stringResource(R.string.settings_smart_rules_subtitle),
                    onClick = onNavigateToRules,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBgColor = green_light,
                    iconTint = green_dark,
                    title = stringResource(R.string.settings_budgets_title),
                    subtitle = stringResource(R.string.settings_budgets_subtitle),
                    onClick = onNavigateToBudgets,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.SwapHoriz,
                    iconBgColor = amber_light,
                    iconTint = amber_dark,
                    title = stringResource(R.string.settings_loans_title),
                    subtitle = stringResource(R.string.settings_loans_subtitle),
                    onClick = onNavigateToLoans,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Folder,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.settings_transaction_groups_title),
                    subtitle = stringResource(R.string.settings_transaction_groups_subtitle),
                    onClick = onNavigateToTransactionGroups,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Upload,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = stringResource(R.string.settings_export_data_title),
                    subtitle = stringResource(R.string.settings_export_data_subtitle),
                    onClick = { settingsViewModel.exportBackup() },
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.DeleteSweep,
                    iconBgColor = red_light,
                    iconTint = red_dark,
                    title = "Reset Learned Merchants",
                    subtitle = "Clear all auto-correction history for merchant names",
                    onClick = { showClearMerchantsDialog = true },
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Refresh,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = "กู้คืนชื่อผู้รับจากสลิป (Restore Slip Payee Names)",
                    subtitle = "อ่านชื่อผู้รับเงินจากข้อความสลิปเดิมของทุกรายการใหม่ โดยไม่กระทบหมวดหมู่",
                    onClick = { showRestoreMerchantsDialog = true },
                    position = ListItemPosition.Middle
                )
                SettingsSwitchRow(
                    icon = Icons.Default.Backup,
                    iconBgColor = purple_light,
                    iconTint = purple_dark,
                    title = stringResource(R.string.settings_auto_backup_title),
                    subtitle = if (scheduledFolderBackupEnabled) {
                        stringResource(R.string.settings_auto_backup_active_subtitle)
                    } else if (!isProEntitled) {
                        stringResource(R.string.settings_auto_backup_pro_subtitle)
                    } else {
                        stringResource(R.string.settings_auto_backup_free_subtitle)
                    },
                    checked = scheduledFolderBackupEnabled,
                    // Scheduling daily backups is a Pro feature. Turning it ON while
                    // free routes to the paywall; turning it OFF is always allowed so
                    // a lapsed/downgraded user can still stop scheduled backups.
                    onCheckedChange = { enabled ->
                        if (enabled && !isProEntitled) {
                            showUpgradeSheet = true
                        } else {
                            settingsViewModel.setScheduledFolderBackupEnabled(enabled)
                        }
                    },
                    position = ListItemPosition.Middle
                )
                if (scheduledFolderBackupEnabled) {
                    SettingsNavItem(
                        icon = Icons.Default.SaveAlt,
                        iconBgColor = green_light,
                        iconTint = green_dark,
                        title = stringResource(R.string.settings_backup_now_title),
                        subtitle = scheduledFolderBackupLastTimestamp?.let { timestamp ->
                            val formatted = java.time.Instant.ofEpochMilli(timestamp)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                            stringResource(R.string.settings_backup_last_timestamp, formatted)
                        } ?: stringResource(R.string.settings_backup_now_subtitle),
                        onClick = { settingsViewModel.backupToFolderNow() },
                        position = ListItemPosition.Middle
                    )
                    SettingsNavItem(
                        icon = Icons.Default.FolderOpen,
                        iconBgColor = amber_light,
                        iconTint = amber_dark,
                        title = stringResource(R.string.settings_change_backup_folder_title),
                        subtitle = stringResource(R.string.settings_change_backup_folder_subtitle),
                        onClick = { settingsViewModel.requestChangeBackupFolder() },
                        position = ListItemPosition.Middle
                    )
                }
                SettingsNavItem(
                    icon = Icons.Default.Download,
                    iconBgColor = cyan_light,
                    iconTint = cyan_dark,
                    title = stringResource(R.string.settings_import_data_title),
                    subtitle = stringResource(R.string.settings_import_data_subtitle),
                    onClick = { importLauncher.launch("*/*") },
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Download,
                    iconBgColor = cyan_light,
                    iconTint = cyan_dark,
                    title = stringResource(R.string.settings_import_csv_title),
                    subtitle = stringResource(R.string.settings_import_csv_subtitle),
                    onClick = { csvImportLauncher.launch("*/*") },
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.CleaningServices,
                    iconBgColor = pink_light,
                    iconTint = pink_dark,
                    title = stringResource(R.string.settings_clean_duplicates_title),
                    subtitle = stringResource(R.string.settings_clean_duplicates_subtitle),
                    onClick = { showCleanUpDuplicatesDialog = true },
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Description,
                    iconBgColor = indigo_light,
                    iconTint = indigo_dark,
                    title = stringResource(R.string.settings_import_statement_title),
                    subtitle = stringResource(R.string.settings_import_statement_subtitle),
                    onClick = onNavigateToImportStatement,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.Sms,
                    iconBgColor = orange_light,
                    iconTint = orange_dark,
                    title = stringResource(R.string.settings_unrecognized_sms_title),
                    subtitle = stringResource(R.string.settings_unrecognized_sms_subtitle),
                    onClick = onNavigateToUnrecognizedSms,
                    position = ListItemPosition.Middle
                )
                SettingsNavItem(
                    icon = Icons.Default.CalendarMonth,
                    iconBgColor = teal_light,
                    iconTint = teal_dark,
                    title = stringResource(R.string.settings_sms_scan_period_title),
                    subtitle = when {
                        smsScanAllTime -> stringResource(R.string.settings_sms_scan_all_time)
                        smsScanUseCustomDate -> {
                            val formattedDate = smsScanCustomDate?.let { formatSmsScanCustomDate(it) }
                            if (formattedDate != null) {
                                stringResource(R.string.settings_sms_scan_custom_date_range, formattedDate)
                            } else {
                                stringResource(R.string.settings_sms_scan_custom_date_placeholder)
                            }
                        }
                        else -> stringResource(R.string.settings_sms_scan_months_range, smsScanMonths)
                    },
                    onClick = { showSmsScanDialog = true },
                    position = ListItemPosition.Bottom,
                    trailingText = when {
                        smsScanAllTime -> stringResource(R.string.settings_sms_scan_all_time_label)
                        smsScanUseCustomDate -> smsScanCustomDate?.let { formatSmsScanCustomDateShort(it) } ?: stringResource(R.string.settings_sms_scan_custom_label)
                        else -> stringResource(R.string.settings_sms_scan_months_short, smsScanMonths)
                    }
                )
            }

            // ── Notifications ──
            SectionHeaderV2(title = stringResource(R.string.settings_notifications))
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.Notifications,
                    iconBgColor = indigo_light,
                    iconTint = indigo_dark,
                    title = stringResource(R.string.settings_bank_notif_access_title),
                    subtitle = if (hasNotificationAccess) {
                        stringResource(R.string.settings_bank_notif_enabled)
                    } else {
                        stringResource(R.string.settings_bank_notif_disabled_subtitle)
                    },
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        notificationAccessLauncher.launch(intent)
                    },
                    position = ListItemPosition.Single,
                    trailingText = if (hasNotificationAccess) {
                        stringResource(R.string.settings_on)
                    } else {
                        stringResource(R.string.settings_off)
                    }
                )
            }

            // ── AI Features ──
            SectionHeaderV2(title = stringResource(R.string.settings_ai_features))
            SettingsGroup {
                AiChatSettingsItem(
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
                    downloadedMB = downloadedMB,
                    totalMB = totalMB,
                    onDownload = { settingsViewModel.startModelDownload() },
                    onCancel = { settingsViewModel.cancelDownload() },
                    onDelete = { settingsViewModel.deleteModel() }
                )
            }

            // ── Developer ──
            SectionHeaderV2(title = stringResource(R.string.settings_developer))
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.Code,
                    iconBgColor = grey_light,
                    iconTint = grey_dark,
                    title = stringResource(R.string.settings_developer_mode_title),
                    subtitle = stringResource(R.string.settings_developer_mode_subtitle),
                    checked = isDeveloperModeEnabled,
                    onCheckedChange = { settingsViewModel.toggleDeveloperMode(it) },
                    position = ListItemPosition.Single
                )
            }

            // ── Support & Community ──
            SectionHeaderV2(title = stringResource(R.string.settings_support_community))
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    iconBgColor = pink_light,
                    iconTint = pink_dark,
                    title = stringResource(R.string.support_faq_title),
                    subtitle = stringResource(R.string.settings_faq_subtitle),
                    onClick = onNavigateToFaq,
                    position = ListItemPosition.Top
                )
                SettingsNavItem(
                    icon = Icons.Default.BugReport,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = stringResource(R.string.settings_report_issue_title),
                    subtitle = stringResource(R.string.settings_report_issue_subtitle),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarim2000/pennywiseai-tracker/issues/new/choose"))
                        context.startActivity(intent)
                    },
                    position = ListItemPosition.Bottom,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
            }

            // App Version
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.settings_version_label, com.pennywiseai.tracker.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }

    // ── Dialogs ──

    // Display Currency Dialog
    if (showDisplayCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showDisplayCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_display_currency_title)) },
            text = {
                // Scrollable: the full currency list overflows the dialog's max
                // height, so without this the entries below the fold (e.g. MXN)
                // are unreachable. (#615)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    availableCurrencies.forEach { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currency == displayCurrency,
                                    onClick = {
                                        settingsViewModel.setDisplayCurrency(currency)
                                        showDisplayCurrencyDialog = false
                                    }
                                )
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currency == displayCurrency,
                                onClick = {
                                    settingsViewModel.setDisplayCurrency(currency)
                                    showDisplayCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "${CurrencyFormatter.getCurrencySymbol(currency)} $currency",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayCurrencyDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Number Format Dialog
    if (showNumberFormatDialog) {
        AlertDialog(
            onDismissRequest = { showNumberFormatDialog = false },
            title = { Text(stringResource(R.string.settings_number_format_title)) },
            text = {
                Column {
                    NumberFormatStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = style == numberFormatStyle,
                                    onClick = {
                                        settingsViewModel.updateNumberFormatStyle(style)
                                        showNumberFormatDialog = false
                                    }
                                )
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = style == numberFormatStyle,
                                onClick = {
                                    settingsViewModel.updateNumberFormatStyle(style)
                                    showNumberFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column {
                                Text(
                                    text = stringResource(numberFormatStyleLabel(style)),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(numberFormatStyleExample(style)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNumberFormatDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Budget Cycle Start Day Dialog
    if (showBudgetCycleDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetCycleDialog = false },
            title = { Text(stringResource(R.string.settings_budget_cycle_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.settings_budget_cycle_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    (1..31).forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = day == budgetCycleStartDay,
                                    onClick = {
                                        settingsViewModel.updateBudgetCycleStartDay(day)
                                        showBudgetCycleDialog = false
                                    }
                                )
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = day == budgetCycleStartDay,
                                onClick = {
                                    settingsViewModel.updateBudgetCycleStartDay(day)
                                    showBudgetCycleDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = ordinalSuffix(day),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBudgetCycleDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // SMS Scan Period Dialog
    if (showSmsScanDialog) {
        AlertDialog(
            onDismissRequest = { showSmsScanDialog = false },
            title = { Text(stringResource(R.string.settings_sms_scan_period_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_sms_scan_period_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    val options = listOf(-1, -2) + listOf(1, 2, 3, 6, 12, 24)
                    options.forEach { months ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (months) {
                                        -1 -> {
                                            settingsViewModel.updateSmsScanAllTime(true)
                                            showSmsScanDialog = false
                                        }
                                        -2 -> {
                                            showSmsScanDialog = false
                                            showSmsScanDatePicker = true
                                        }
                                        else -> {
                                            settingsViewModel.updateSmsScanMonths(months)
                                            settingsViewModel.updateSmsScanAllTime(false)
                                            showSmsScanDialog = false
                                        }
                                    }
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSelected = when (months) {
                                -1 -> smsScanAllTime
                                -2 -> smsScanUseCustomDate && !smsScanAllTime
                                else -> smsScanMonths == months && !smsScanAllTime && !smsScanUseCustomDate
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    when (months) {
                                        -1 -> {
                                            settingsViewModel.updateSmsScanAllTime(true)
                                            showSmsScanDialog = false
                                        }
                                        -2 -> {
                                            showSmsScanDialog = false
                                            showSmsScanDatePicker = true
                                        }
                                        else -> {
                                            settingsViewModel.updateSmsScanMonths(months)
                                            settingsViewModel.updateSmsScanAllTime(false)
                                            showSmsScanDialog = false
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Text(
                                text = when (months) {
                                    -1 -> stringResource(R.string.settings_sms_scan_all_time_label)
                                    -2 -> {
                                        val formattedDate = smsScanCustomDate?.let { formatSmsScanCustomDate(it) }
                                        if (formattedDate != null) {
                                            stringResource(R.string.settings_sms_scan_custom_date_val, formattedDate)
                                        } else {
                                            stringResource(R.string.settings_sms_scan_custom_date_generic)
                                        }
                                    }
                                    1 -> stringResource(R.string.settings_sms_scan_1month)
                                    24 -> stringResource(R.string.settings_sms_scan_2years)
                                    else -> stringResource(R.string.settings_sms_scan_n_months, months)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmsScanDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showSmsScanDatePicker) {
        val todayMillis = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val initialSelectedDateMillis = smsScanCustomDate
            ?: java.time.LocalDate.now()
                .minusMonths(3)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayMillis
                }
            }
        )

        // Cancelling/dismissing the picker returns to the scan-period dialog rather than
        // silently dropping the user back to Settings (they came here to change the period).
        fun reopenScanDialog() {
            showSmsScanDatePicker = false
            showSmsScanDialog = true
        }

        DatePickerDialog(
            onDismissRequest = { reopenScanDialog() },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            settingsViewModel.updateSmsScanCustomDate(millis)
                        }
                        showSmsScanDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { reopenScanDialog() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Show import/export message
    importExportMessage?.let { message ->
        if (exportedBackupFile != null && message.contains("successfully! Choose")) {
            showExportOptionsDialog = true
        } else {
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(5000)
                settingsViewModel.clearImportExportMessage()
            }

            AlertDialog(
                onDismissRequest = { settingsViewModel.clearImportExportMessage() },
                title = { Text(stringResource(R.string.settings_backup_status_title)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.clearImportExportMessage() }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            )
        }
    }

    // Export options dialog
    if (showExportOptionsDialog && exportedBackupFile != null) {
        val timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss")
        )
        val fileName = "PennyWise_Backup_$timestamp.pennywisebackup"

        AlertDialog(
            onDismissRequest = {
                showExportOptionsDialog = false
                settingsViewModel.clearImportExportMessage()
            },
            title = { Text(stringResource(R.string.settings_save_backup_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_backup_success))
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.settings_save_backup_desc), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            exportSaveLauncher.launch(fileName)
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        }
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_save_to_files))
                    }

                    TextButton(
                        onClick = {
                            settingsViewModel.shareBackup()
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_share))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportOptionsDialog = false
                        settingsViewModel.clearImportExportMessage()
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Lock Timeout Dialog
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(stringResource(R.string.settings_lock_timeout_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_lock_timeout_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    val timeoutOptions = listOf(
                        0 to stringResource(R.string.settings_lock_timeout_immediate),
                        1 to stringResource(R.string.settings_lock_timeout_1min),
                        5 to stringResource(R.string.settings_lock_timeout_5min),
                        15 to stringResource(R.string.settings_lock_timeout_15min)
                    )

                    timeoutOptions.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLockViewModel.setTimeoutMinutes(minutes)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockUiState.timeoutMinutes == minutes,
                                onClick = {
                                    appLockViewModel.setTimeoutMinutes(minutes)
                                    showTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }

    if (showSupportDialog) {
        SupportDevelopmentDialog(onDismiss = { showSupportDialog = false })
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    val languages = listOf(
                        null to stringResource(R.string.lang_system_default),
                        "en" to stringResource(R.string.lang_english),
                        "th" to stringResource(R.string.lang_thai)
                    )
                    languages.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = appLanguage == code,
                                    onClick = {
                                        settingsViewModel.updateLanguage(code)
                                        showLanguageDialog = false
                                    }
                                )
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLanguage == code,
                                onClick = {
                                    settingsViewModel.updateLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// ── Reusable Settings Components ──
//
// Row chrome — tonal surface, grouped-corner shape, padding, minimum height,
// the tinted icon circle, title/subtitle typography — lives in the shared
// `GroupedList` / `GroupedRow` / `IconTile` / `RowLabels` primitives, so a
// settings row and a grouped row on any other screen are literally the same
// object. These wrappers only add the settings-specific trailing affordance.

@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    GroupedList(content = content)
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ListItemPosition,
    trailingText: String? = null,
    trailingIcon: ImageVector = Icons.Default.ChevronRight
) {
    GroupedRow(position = position, onClick = onClick) {
        IconTile(icon = icon, containerColor = iconBgColor, contentColor = iconTint)
        RowLabels(title = title, subtitle = subtitle)
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // The chevron is a hint, not a control — at 20dp it stops competing
        // with the leading icon for attention the way a 24dp one did.
        Icon(
            trailingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.Icon.inline)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ListItemPosition,
    enabled: Boolean = true
) {
    GroupedRow(
        position = position,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) }
    ) {
        IconTile(icon = icon, containerColor = iconBgColor, contentColor = iconTint)
        RowLabels(
            title = title,
            subtitle = subtitle,
            subtitleColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    currentValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    position: ListItemPosition,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    GroupedColumn(position = position) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon = icon, containerColor = iconBgColor, contentColor = iconTint)
            RowLabels(title = title, subtitle = subtitle)
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            TextField(
                value = currentValue,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_currency)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    // A field nested inside an already-tonal row needs a step
                    // of contrast against it, otherwise the input boundary
                    // disappears.
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                content = dropdownContent
            )
        }
    }
}

@Composable
private fun AiChatSettingsItem(
    downloadState: DownloadState,
    downloadProgress: Int,
    downloadedMB: Long,
    totalMB: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    GroupedColumn(
        position = ListItemPosition.Single,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = Icons.Default.AutoAwesome,
                containerColor = yellow_light,
                contentColor = yellow_dark
            )
            RowLabels(
                title = stringResource(R.string.settings_ai_chat_title),
                subtitle = when (downloadState) {
                    DownloadState.NOT_DOWNLOADED -> stringResource(R.string.settings_ai_model_download_label, Constants.ModelDownload.MODEL_SIZE_MB)
                    DownloadState.DOWNLOADING -> stringResource(R.string.settings_ai_model_downloading)
                    DownloadState.PAUSED -> stringResource(R.string.settings_ai_model_interrupted)
                    DownloadState.COMPLETED -> stringResource(R.string.settings_ai_model_ready)
                    DownloadState.FAILED -> stringResource(R.string.settings_ai_model_failed)
                    DownloadState.ERROR_INSUFFICIENT_SPACE -> stringResource(R.string.settings_ai_model_no_space)
                }
            )

            when (downloadState) {
                DownloadState.NOT_DOWNLOADED -> {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_download_btn))
                    }
                }
                DownloadState.DOWNLOADING -> {
                    Text(
                        text = "$downloadProgress%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DownloadState.PAUSED -> {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_retry_btn))
                    }
                }
                DownloadState.COMPLETED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.settings_ai_downloaded_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.settings_ai_delete_btn))
                        }
                    }
                }
                DownloadState.FAILED -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_retry_btn))
                    }
                }
                DownloadState.ERROR_INSUFFICIENT_SPACE -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = stringResource(R.string.settings_ai_error_label),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
            }
        }

        // Progress details during download
        AnimatedVisibility(
            visible = downloadState == DownloadState.DOWNLOADING,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "$downloadedMB MB / $totalMB MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.settings_ai_cancel_download_btn))
                }
            }
        }

        // Info about AI features
        if (downloadState == DownloadState.NOT_DOWNLOADED ||
            downloadState == DownloadState.ERROR_INSUFFICIENT_SPACE
        ) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_ai_chat_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsNavigationContent(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .animateContentSize()
            .padding(start = Spacing.md)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigateBack,
            ),
    ) {
        IconButton(
            onClick = onNavigateBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back_desc),
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}

private fun numberFormatStyleLabel(style: NumberFormatStyle): Int = when (style) {
    NumberFormatStyle.AUTO -> R.string.settings_num_format_auto_label
    NumberFormatStyle.INDIAN -> R.string.settings_num_format_indian_label
    NumberFormatStyle.INTERNATIONAL -> R.string.settings_num_format_intl_label
}

private fun numberFormatStyleExample(style: NumberFormatStyle): Int = when (style) {
    NumberFormatStyle.AUTO -> R.string.settings_num_format_matches
    NumberFormatStyle.INDIAN -> R.string.settings_num_format_indian
    NumberFormatStyle.INTERNATIONAL -> R.string.settings_num_format_intl
}

/**
 * English ordinal suffix for the budget cycle start day — "1st", "2nd", "3rd",
 * "4th"… "11th", "12th", "13th" follow the standard rule that the last two
 * digits decide the suffix (the 11/12/13 teens are always "th").
 */
private fun ordinalSuffix(day: Int): String {
    val safe = day.coerceIn(1, 31)
    val suffix = when {
        safe in 11..13 -> "th"
        safe % 10 == 1 -> "st"
        safe % 10 == 2 -> "nd"
        safe % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$safe$suffix"
}

private fun formatSmsScanCustomDate(dateMillis: Long): String {
    return java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun formatSmsScanCustomDateShort(dateMillis: Long): String {
    return java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}
