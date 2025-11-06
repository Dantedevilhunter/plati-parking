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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.llitc.platiparking.ui.theme.PlatiParkingTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlatiParkingTheme {
                AppNavigator(mainViewModel)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
        Toast.makeText(context, "Molimo podesite registarski broj u podešavanjima", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Dozvola odbijena. Slanje SMS poruke nije moguće.", Toast.LENGTH_SHORT).show()
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
            CenterAlignedTopAppBar(
                title = { Text("Plati Parking", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Podešavanja")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj Konfiguraciju")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CurrentRegistrationCard(
                registration = registration,
                onCardClick = { navController.navigate("settings") }
            )

            if (configs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nema sačuvanih konfiguracija.\nDodirnite '+' da dodate novu.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
        topBar = {
            TopAppBar(
                title = { Text("Podešavanja") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Nazad")
                    }
                }
            )
        }
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
                onValueChange = { text = it.uppercase().filter { char -> char.isLetterOrDigit() } },
                label = { Text("Registarski Broj Vozila") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.CarCrash, contentDescription = null) }
            )
            Button(
                onClick = {
                    vm.saveRegistration(text)
                    Toast.makeText(context, "Sačuvano!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Sačuvaj")
            }
        }
    }
}

// --- UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentRegistrationCard(registration: String, onCardClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = onCardClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "TRENUTNA REGISTRACIJA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (registration.isNotBlank()) registration else "Nije podešeno",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ConfigItem(config: ParkingConfig, onPay: () -> Unit, onDelete: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationCity,
                contentDescription = "Grad",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${config.cityName} - ${config.zoneName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "SMS na broj: ${config.smsNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPay,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.PriceChange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("PLATI")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Obriši", tint = MaterialTheme.colorScheme.error)
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
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        title = { Text("Dodaj Novu Konfiguraciju") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Grad") })
                OutlinedTextField(value = zone, onValueChange = { zone = it }, label = { Text("Zona") })
                OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("SMS Broj") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (city.isNotBlank() && zone.isNotBlank() && number.isNotBlank()) {
                    onConfirm(city, zone, number)
                }
            }) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži") }
        }
    )
}

// --- SMS Logic ---
fun sendSms(context: Context, number: String, message: String) {
    try {
        val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(number, null, message, null, null)
        Toast.makeText(context, "SMS za plaćanje parkinga je poslat!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Slanje SMS poruke nije uspelo. Greška: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}