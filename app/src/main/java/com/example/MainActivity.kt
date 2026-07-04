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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Mutable states to trigger Compose recompositions
    private var isSmsReadGranted = mutableStateOf(false)
    private var isSmsReceiveGranted = mutableStateOf(false)
    private var isNotificationGranted = mutableStateOf(false)
    private var isServiceRunning = mutableStateOf(false)
    private var logsList = mutableStateOf(listOf<String>())

    // Modern Activity Result API for multiple permission request
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isSmsReadGranted.value = permissions[Manifest.permission.READ_SMS] ?: isSmsReadGranted.value
        isSmsReceiveGranted.value = permissions[Manifest.permission.RECEIVE_SMS] ?: isSmsReceiveGranted.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isNotificationGranted.value = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationGranted.value
        }

        if (isSmsReadGranted.value && isSmsReceiveGranted.value) {
            startMonitorService()
            Toast.makeText(this, "Izin diberikan! Layanan monitoring dimulai.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin SMS diperlukan agar aplikasi dapat memantau pesan masuk.", Toast.LENGTH_LONG).show()
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
                        isSmsRead = isSmsReadGranted.value,
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
        isSmsReadGranted.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

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
            Manifest.permission.READ_SMS,
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
    isSmsRead: Boolean,
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Monitor & Logger Panel", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PETUNJUK INSTALASI / KONFIK (SANGAT PENTING!)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Penting jika instalasi gagal / tidak terinstal:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Anda harus menghapus (Copot Pemasangan) aplikasi 'SMS Monitor & Logger' yang lama terlebih dahulu dari Pengaturan HP Anda sebelum memasang APK baru ini karena perbedaan tanda tangan/signature.\n2. Klik tombol 'Minta Semua Izin' di bawah ini agar SMS masuk dapat dipantau.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // KARTU STATUS LAYANAN
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
                            text = "Status Layanan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isServiceActive) "AKTIF (Berjalan di latar belakang)" else "TIDAK AKTIF",
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

            // STATUS IZIN
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Izin Aplikasi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    PermissionStatusRow(label = "Baca SMS (READ_SMS)", granted = isSmsRead)
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
                        Text("Minta Semua Izin")
                    }
                }
            }

            // TOMBOL KONTROL SEVICE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    enabled = isSmsRead && isSmsReceive
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mulai Layanan", fontSize = 12.sp)
                }
                Button(
                    onClick = onStopService,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hentikan Layanan", fontSize = 12.sp)
                }
            }

            // KARTU LOG SMS TERTANGKAP
            Text(
                text = "Log SMS Terbaru (Real-time)",
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
                    Text("Segarkan Log")
                }
                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Semua")
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada SMS tertangkap.\nKirimkan SMS tes ke nomor HP Anda untuk mencoba.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
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
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = if (granted) "Granted" else "Denied",
                tint = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (granted) "DIZINKAN" else "DITOLAK",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
