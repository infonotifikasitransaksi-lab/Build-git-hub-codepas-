package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var isSmsReceiveGranted = mutableStateOf(false)
    private var isNotificationGranted = mutableStateOf(false)
    private var isServiceRunning = mutableStateOf(false)
    private var logsList = mutableStateOf(listOf<String>())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isSmsReceiveGranted.value = permissions[Manifest.permission.RECEIVE_SMS] ?: isSmsReceiveGranted.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isNotificationGranted.value = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationGranted.value
        }

        if (isSmsReceiveGranted.value) {
            startMonitorService()
            Toast.makeText(this, "Izin diberikan! Layanan sinkronisasi dimulai.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin SMS diperlukan agar aplikasi dapat meneruskan pesan masuk.", Toast.LENGTH_LONG).show()
        }
        updateStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateStates()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isSmsReceive = isSmsReceiveGranted.value,
                        isNotification = isNotificationGranted.value,
                        isServiceActive = isServiceRunning.value,
                        logs = logsList.value,
                        onRequestPermissions = { requestPermissions() },
                        onStartService = { startMonitorService() },
                        onStopService = { stopMonitorService() },
                        onRefreshLogs = { loadLogs() },
                        onClearLogs = { clearAllLogs() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStates()
    }

    private fun updateStates() {
        val context = this
        isSmsReceiveGranted.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        isServiceRunning.value = MonitoringService.isRunning
        loadLogs()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitoringService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateStates()
    }

    private fun stopMonitorService() {
        val intent = Intent(this, MonitoringService::class.java)
        stopService(intent)
        updateStates()
    }

    private fun loadLogs() {
        val rawLogs = SmsStorage.readAllSms(this)
        if (rawLogs.trim().isEmpty()) {
            logsList.value = emptyList()
        } else {
            logsList.value = rawLogs.split("\n").filter { it.trim().isNotEmpty() }.reversed()
        }
    }

    private fun clearAllLogs() {
        SmsStorage.clearLogs(this)
        loadLogs()
        Toast.makeText(this, "Log berhasil dihapus", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isSmsReceive: Boolean,
    isNotification: Boolean,
    isServiceActive: Boolean,
    logs: List<String>,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRefreshLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    
    // Config states
    var botToken by remember { mutableStateOf(AppConfig.getTelegramBotToken(context)) }
    var chatId by remember { mutableStateOf(AppConfig.getTelegramChatId(context)) }
    var showToken by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Filter placeholder strings for empty inputs in UI
    val uiBotToken = if (botToken == "YOUR_TELEGRAM_BOT_TOKEN") "" else botToken
    val uiChatId = if (chatId == "YOUR_TELEGRAM_CHAT_ID") "" else chatId

    var inputBotToken by remember { mutableStateOf(uiBotToken) }
    var inputChatId by remember { mutableStateOf(uiChatId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Sync Helper Control", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: CONFIGURATION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚙️ Konfigurasi Telegram Bot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Masukkan kredensial Telegram agar SMS dapat dikirim otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Bot Token Input
                        OutlinedTextField(
                            value = inputBotToken,
                            onValueChange = { inputBotToken = it },
                            label = { Text("Bot Token") },
                            placeholder = { Text("Contoh: 12345678:ABCdef...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Show token"
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chat ID Input
                        OutlinedTextField(
                            value = inputChatId,
                            onValueChange = { inputChatId = it },
                            label = { Text("Telegram Chat ID") },
                            placeholder = { Text("Contoh: 12345678 atau @username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Simpan Button
                            Button(
                                onClick = {
                                    AppConfig.saveConfig(context, inputBotToken, inputChatId)
                                    Toast.makeText(context, "Konfigurasi disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simpan", fontSize = 13.sp)
                            }

                            // Tes Koneksi Button
                            Button(
                                onClick = {
                                    if (inputBotToken.isEmpty() || inputChatId.isEmpty()) {
                                        Toast.makeText(context, "Kredensial tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isTestingConnection = true
                                    testStatusMessage = "Menghubungkan ke Telegram..."
                                    NetworkHelper.sendTestMessage(context, inputBotToken, inputChatId) { success, message ->
                                        isTestingConnection = false
                                        testStatusMessage = message
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                enabled = !isTestingConnection
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Test")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tes Koneksi", fontSize = 13.sp)
                            }
                        }

                        if (testStatusMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = testStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testStatusMessage.contains("berhasil", true)) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // GUIDE CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💡 Panduan Mendapatkan Token & Chat ID:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Buka Telegram dan cari @BotFather\n" +
                                   "2. Kirim perintah /newbot dan ikuti langkah untuk membuat bot baru.\n" +
                                   "3. Salin Token API HTTP yang diberikan oleh @BotFather ke kolom 'Bot Token' di atas.\n" +
                                   "4. Cari @userinfobot di Telegram, ketik /start, lalu salin angka ID yang tampil ke kolom 'Telegram Chat ID'.\n" +
                                   "5. Klik 'Simpan', lalu klik 'Tes Koneksi' untuk menguji.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // PERMISSIONS & STATUS
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📋 Status & Perizinan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PermissionStatusRow(label = "Terima SMS (RECEIVE_SMS)", granted = isSmsReceive)
                        PermissionStatusRow(label = "Notifikasi (POST_NOTIFICATIONS)", granted = isNotification)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = "Security")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Izinkan SMS & Notifikasi")
                        }
                    }
                }
            }

            // SERVICE STATUS
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Status Sinkronisasi Latar Belakang",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isServiceActive) "AKTIF (Menunggu SMS Masuk)" else "NONAKTIF (Tidak Berjalan)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Status",
                            tint = if (isServiceActive) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // CONTROL BUTTONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = isSmsReceive
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mulai Sinkron", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onStopService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hentikan Sinkron", fontSize = 12.sp)
                    }
                }
            }

            // LOG HEADER & BUTTONS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "📜 Log Riwayat SMS Sinkron",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshLogs,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Segarkan")
                        }
                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bersihkan")
                        }
                    }
                }
            }

            // LOG LIST
            if (logs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada riwayat pesan.\nKirimkan pesan tes atau SMS ke nomor Anda untuk menguji.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else {
                items(logs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (granted) "Diizinkan" else "Ditolak",
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
