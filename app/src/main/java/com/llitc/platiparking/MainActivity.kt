package com.llitc.platiparking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.llitc.platiparking.ui.theme.ParkirajLakoTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParkirajLakoTheme {
                AppNavigator(mainViewModel)
            }
        }
        // 'intent' in onCreate is guaranteed non-null
        handleIntent(intent)
    }

    // THIS IS THE FINAL VERSION MATCHING YOUR IDE'S REQUIREMENT
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // No need for null checks because 'intent' is non-nullable here
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData = intent.data
        if (Intent.ACTION_VIEW == appLinkAction && appLinkData != null) {
            val city = appLinkData.getQueryParameter("city")
            val zone = appLinkData.getQueryParameter("zone")
            Toast.makeText(this, "Assistant request: City=$city, Zone=$zone", Toast.LENGTH_SHORT).show()
            mainViewModel.payFromVoiceCommand(city, zone)
        }
    }
}

// --- Navigation ---
@Composable
fun AppNavigator(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, mainViewModel) }
        composable("settings") { SettingsScreen(navController, mainViewModel) }
    }
}

// --- Helper function to trigger SMS payment flow ---
fun processPayment(
    context: Context,
    config: ParkingConfig,
    registration: String,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (registration.isBlank()) {
        Toast.makeText(context, "Please set registration number in settings", Toast.LENGTH_SHORT).show()
        return
    }
    when (PackageManager.PERMISSION_GRANTED) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) -> {
            sendSms(context, config.smsNumber, registration)
        }
        else -> {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }
}


// --- Screens ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MainViewModel) {
    val context = LocalContext.current
    val registration by vm.registrationNumber.collectAsState()
    val configs by vm.parkingConfigs.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permission denied. Cannot send SMS.", Toast.LENGTH_SHORT).show()
        }
    }

    // This listens for payment requests from the ViewModel (triggered by the Assistant)
    LaunchedEffect(Unit) {
        vm.paymentTrigger.collect { configToPay ->
            processPayment(context, configToPay, registration, permissionLauncher)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parkiraj Lako") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Configuration")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Current Registration: ${if (registration.isNotBlank()) registration else "Not Set"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (configs.isEmpty()) {
                Text("No configurations added. Tap the '+' button to add one.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(configs, key = { it.id }) { config ->
                        ConfigItem(
                            config = config,
                            onPay = { processPayment(context, config, registration, permissionLauncher) },
                            onDelete = { vm.removeParkingConfig(config) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddConfigDialog(
            onDismiss = { showDialog = false },
            onConfirm = { city, zone, number ->
                vm.addParkingConfig(ParkingConfig(cityName = city, zoneName = zone, smsNumber = number))
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, vm: MainViewModel) {
    val currentRegistration by vm.registrationNumber.collectAsState()
    var text by remember { mutableStateOf(currentRegistration) }
    val context = LocalContext.current

    LaunchedEffect(currentRegistration) {
        text = currentRegistration
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.uppercase() },
                label = { Text("Vehicle Registration Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    vm.saveRegistration(text)
                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

// --- UI Components ---
@Composable
fun ConfigItem(config: ParkingConfig, onPay: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${config.cityName} - ${config.zoneName}", fontWeight = FontWeight.Bold)
                Text("SMS to: ${config.smsNumber}", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onPay) {
                Text("PAY")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConfigDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var city by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") })
                OutlinedTextField(value = zone, onValueChange = { zone = it }, label = { Text("Zone") })
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("SMS Number") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if(city.isNotBlank() && zone.isNotBlank() && number.isNotBlank()) {
                        onConfirm(city, zone, number)
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- SMS Logic ---
fun sendSms(context: Context, number: String, message: String) {
    try {
        val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(number, null, message, null, null)
        Toast.makeText(context, "Parking payment SMS sent!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to send SMS. Error: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}