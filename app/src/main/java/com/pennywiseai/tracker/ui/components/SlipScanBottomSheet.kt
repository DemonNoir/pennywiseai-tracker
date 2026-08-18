package com.pennywiseai.tracker.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.slip.ocr.SlipOcrEngine
import com.pennywiseai.tracker.slip.parser.ParsedSlip
import com.pennywiseai.tracker.slip.parser.SlipParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint to obtain the singleton [SlipOcrEngine] without re-creating it
 * per composition. [SlipOcrEngine] loads the ONNX models lazily in its init block,
 * so building it fresh in `remember {}` would re-initialise sessions every time the
 * bottom sheet opens.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SlipScanEntryPoint {
    fun slipOcrEngine(): SlipOcrEngine
}

data class BankFolderOption(
    val id: String,
    val bankName: String,
    val folderName: String,
    val accentColor: Color
)

val bankFolders = listOf(
    BankFolderOption("all", "ทั้งหมด (All Slips)", "Screenshots/Pictures", Color(0xFF6200EE)),
    BankFolderOption("kplus", "กสิกรไทย (K+)", "Pictures/KPlus", Color(0xFF138936)),
    BankFolderOption("scb", "ไทยพาณิชย์ (SCB)", "Pictures/SCB", Color(0xFF4E2A84)),
    BankFolderOption("ktb", "กรุงไทย (NEXT)", "Pictures/Krungthai", Color(0xFF1BA5E1)),
    BankFolderOption("bay", "กรุงศรี (KMA)", "Pictures/KMA", Color(0xFFFEC400)),
    BankFolderOption("ttb", "ทีทีบี (ttb touch)", "Pictures/ttb touch", Color(0xFF002D62)),
    BankFolderOption("gsb", "ออมสิน (GSB)", "Pictures/GSB", Color(0xFFEB008B)),
    BankFolderOption("baac", "ธ.ก.ส. (A-Mobile)", "Pictures/BAAC", Color(0xFF006837))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlipScanBottomSheet(
    onDismissRequest: () -> Unit,
    onSaveTransaction: (ParsedSlip) -> Unit = {}
) {
    val context = LocalContext.current
    // Reuse the singleton SlipOcrEngine from the Hilt graph instead of building a
    // fresh one per sheet opening (each construction would re-init ONNX sessions).
    val ocrEngine = remember {
        EntryPointAccessors
            .fromApplication(context.applicationContext, SlipScanEntryPoint::class.java)
            .slipOcrEngine()
    }
    
    val requiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    var hasRequiredPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRequiredPermissions = requiredPermissions.all { 
            permissions[it] == true || androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
        }
    }

    var showDisclosureDialog by remember { mutableStateOf(false) }

    if (showDisclosureDialog) {
        PermissionDisclosureDialog(
            onDismissRequest = { showDisclosureDialog = false },
            onConfirm = {
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            },
            title = "เข้าถึงรูปภาพเพื่อแสกนสลิป",
            description = "PennyWise จำเป็นต้องขอสิทธิ์เข้าถึงรูปภาพเพื่อตรวจหาและอ่านข้อมูลจากสลิปธนาคารโดยใช้ AI OCR ข้อมูลทั้งหมดจะถูกประมวลผลภายในเครื่องของคุณเท่านั้น และเราจะไม่อ่านรูปภาพอื่นที่ไม่ใช่สลิปธนาคาร"
        )
    }

    var selectedBankFolder by remember { mutableStateOf(bankFolders[0]) }
    var isScanning by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<ParsedSlip?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // ระบบรีวิวข้อมูลก่อนบันทึก — เปิด dialog ให้ตรวจ/แก้ไข ยอดเงิน/ผู้รับ/วันที่ ก่อนยืนยัน
    var showReviewDialog by remember { mutableStateOf(false) }

    // Launcher for selecting a single slip image
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            processSlipUri(ocrEngine, it, { isScanning = it }, { result -> parseResult = result }, { err -> errorMessage = err })
        }
    }

    // Launcher for picking images from specific bank folders
    val bankFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processSlipUri(ocrEngine, it, { isScanning = it }, { result -> parseResult = result }, { err -> errorMessage = err })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "สแกนสลิปโอนเงิน",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasRequiredPermissions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "จำเป็นต้องเข้าถึงรูปภาพเพื่อแสกนสลิป",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showDisclosureDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("อนุญาตสิทธิ์การเข้าถึง")
                        }
                    }
                }
            }

            // Bank Folder Selector Title
            Text(
                text = "เลือกตามโฟลเดอร์สลิปธนาคาร",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal Bank Folder Chips
            var folderImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

            LaunchedEffect(selectedBankFolder) {
                if (selectedBankFolder.id != "all") {
                    folderImages = queryImagesForBankFolder(context, selectedBankFolder.folderName)
                } else {
                    folderImages = emptyList()
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bankFolders) { folder ->
                    FilterChip(
                        selected = selectedBankFolder.id == folder.id,
                        onClick = {
                            selectedBankFolder = folder
                        },
                        label = { Text(text = folder.bankName, fontSize = 13.sp) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(folder.accentColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = folder.accentColor.copy(alpha = 0.25f),
                            selectedLabelColor = folder.accentColor
                        )
                    )
                }
            }

            if (selectedBankFolder.id != "all") {
                Spacer(modifier = Modifier.height(10.dp))
                if (folderImages.isNotEmpty()) {
                    Text(
                        text = "พบ ${folderImages.size} รูปในโฟลเดอร์ ${selectedBankFolder.bankName}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(folderImages) { uri ->
                            ElevatedCard(
                                onClick = {
                                    processSlipUri(ocrEngine, uri, { isScanning = it }, { result -> parseResult = result }, { err -> errorMessage = err })
                                },
                                modifier = Modifier.size(70.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = selectedBankFolder.accentColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "ไม่พบรูปสลิปในโฟลเดอร์ ${selectedBankFolder.folderName} (สามารถกดเลือกรูปจากแกลเลอรีหลักได้)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Action Button: Pick Slip Image
            Button(
                onClick = {
                    singlePhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "เลือกรูปสลิปจากแกลเลอรี", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Loading / Scanning Indicator
            if (isScanning) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("กำลังถอดข้อมูลสลิปด้วย AI OCR...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Error Message
            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                    }
                }
            }

            // Parsed Result Card
            parseResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "สแกนสำเร็จ!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = result.bankName ?: "สลิปธนาคาร",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Amount Display
                        val amountVal = result.amountBigDecimal?.toDouble() ?: result.amount ?: 0.0
                        Text(
                            text = "฿%.2f".format(amountVal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Receiver / Merchant
                        result.receiverName?.let { receiver ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ผู้รับ: $receiver",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Date Time
                        result.dateTimeIso?.let { dateTime ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "วันที่: $dateTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Save Button — เปิด dialog รีวิวก่อนบันทึก (ตรวจ/แก้ไขข้อมูล)
                        Button(
                            onClick = {
                                showReviewDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("บันทึกรายการนี้เข้าบัญชี")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Review-before-save dialog: ตรวจสอบ/แก้ไขข้อมูลจาก OCR ก่อนบันทึกลงบัญชี
    if (showReviewDialog) {
        parseResult?.let { result ->
            SlipReviewDialog(
                slip = result,
                onDismiss = { showReviewDialog = false },
                onConfirm = { editedSlip ->
                    showReviewDialog = false
                    onSaveTransaction(editedSlip)
                    onDismissRequest()
                }
            )
        }
    }
}


/**
 * Dialog รีวิวข้อมูลสลิปก่อนบันทึก — แสดงผล OCR ให้ตรวจสอบ
 * และเปิดโหมดแก้ไข ยอดเงิน/ผู้รับ/วันที่ ได้ (OCR อาจอ่านผิดได้)
 */
@Composable
private fun SlipReviewDialog(
    slip: ParsedSlip,
    onDismiss: () -> Unit,
    onConfirm: (ParsedSlip) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(
        slip.amountBigDecimal?.toPlainString() ?: slip.amount?.toString() ?: ""
    ) }
    var receiverText by remember { mutableStateOf(slip.receiverName ?: "") }
    var dateText by remember { mutableStateOf(slip.date ?: "") }
    var timeText by remember { mutableStateOf(slip.time?.replace("น.", "")?.trim() ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "แก้ไขข้อมูลสลิป" else "ตรวจสอบข้อมูลก่อนบันทึก")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isEditing) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("ยอดเงิน (บาท)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = receiverText,
                        onValueChange = { receiverText = it },
                        label = { Text("ผู้รับ / ร้านค้า") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("วันที่ (เช่น 11 ส.ค. 69)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("เวลา (เช่น 15:37)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ReviewRow("ยอดเงิน", "฿%.2f".format(slip.amountBigDecimal?.toDouble() ?: slip.amount ?: 0.0))
                    slip.receiverName?.let { ReviewRow("ผู้รับ", it) }
                    (slip.dateTimeIso ?: slip.date)?.let { ReviewRow("วันที่", it) }
                    if (slip.confidence != com.pennywiseai.tracker.slip.parser.SlipConfidence.CONFIRMED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ OCR อ่านข้อมูลไม่ครบถ้วน — ควรตรวจสอบก่อนบันทึก",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(onClick = {
                    // สร้าง ParsedSlip ใหม่จากค่าที่แก้ไข
                    val cleanedAmount = amountText.trim()
                    val amountBigDecimal = if (cleanedAmount.isEmpty()) {
                        slip.amountBigDecimal
                    } else {
                        try {
                            java.math.BigDecimal(cleanedAmount.replace(",", ""))
                        } catch (e: NumberFormatException) {
                            null
                        }
                    }
                    if (cleanedAmount.isNotEmpty() && amountBigDecimal == null) {
                        errorText = "กรุณากรอกยอดเงินให้ถูกต้อง"
                        return@TextButton
                    }

                    val cleanedDate = dateText.trim()
                    val cleanedTime = timeText.trim()
                    val localDateTime = if (cleanedDate.isEmpty() && cleanedTime.isEmpty()) {
                        null
                    } else {
                        SlipParser.parseToLocalDateTime(
                            cleanedDate.ifEmpty { null },
                            cleanedTime.ifEmpty { null }
                        )
                    }
                    if (cleanedDate.isNotEmpty() && localDateTime == null) {
                        errorText = "รูปแบบวันที่ไม่ถูกต้อง (เช่น 11 ส.ค. 69)"
                        return@TextButton
                    }

                    val edited = slip.copy(
                        amountBigDecimal = amountBigDecimal,
                        amount = amountBigDecimal?.toDouble(),
                        receiverName = receiverText.trim().ifEmpty { slip.receiverName },
                        date = cleanedDate.ifEmpty { null },
                        time = cleanedTime.ifEmpty { null },
                        dateTimeIso = localDateTime?.toString(),
                        timestampMillis = localDateTime
                            ?.atZone(java.time.ZoneId.systemDefault())
                            ?.toInstant()
                            ?.toEpochMilli()
                    )
                    onConfirm(edited)
                }) {
                    Text("บันทึก")
                }
            } else {
                Button(onClick = { onConfirm(slip) }) {
                    Text("ยืนยัน")
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = {
                    isEditing = false
                    errorText = null
                }) {
                    Text("ยกเลิก")
                }
            } else {
                TextButton(onClick = {
                    isEditing = true
                    errorText = null
                }) {
                    Text("แก้ไข")
                }
            }
        }
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}


private fun processSlipUri(
    ocrEngine: SlipOcrEngine,
    uri: Uri,
    setScanning: (Boolean) -> Unit,
    setResult: (ParsedSlip?) -> Unit,
    setError: (String?) -> Unit
) {
    setScanning(true)
    setError(null)
    setResult(null)

    ocrEngine.processImageUri(
        imageUri = uri,
        onSuccess = { parsed ->
            setScanning(false)
            val amt = parsed.amount ?: 0.0
            if (amt > 0.0) {
                setResult(parsed)
            } else {
                setError("ไม่สามารถถอดจำนวนเงินจากสลิปได้ กรุณาลองใช้สลิปรูปอื่น")
            }
        },
        onError = { ex ->
            setScanning(false)
            setError("เกิดข้อผิดพลาดในการอ่านรูปสลิป: ${ex.localizedMessage}")
        }
    )
}

private fun queryImagesForBankFolder(context: Context, folderPath: String): List<Uri> {
    if (folderPath.isEmpty() || folderPath == "Screenshots/Pictures") return emptyList()
    val folderName = folderPath.substringAfterLast("/")
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(
        android.provider.MediaStore.Images.Media._ID,
        android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val selection = "${android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.Images.Media.DATA} LIKE ?"
    val selectionArgs = arrayOf("%$folderName%", "%$folderPath%")
    val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && uris.size < 20) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                uris.add(uri)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("SlipScanBottomSheet", "Failed to query bank folder images: ${e.message}", e)
    }
    return uris
}

