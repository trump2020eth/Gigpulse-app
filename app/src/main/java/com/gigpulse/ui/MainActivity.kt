package com.gigpulse.ui
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gigpulse.ui.theme.GigTheme
import com.gigpulse.data.DI
import com.gigpulse.model.*
import com.gigpulse.notifications.HotspotWorker
import com.gigpulse.tracking.MileageService
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DI.init(applicationContext)

        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        // Schedule hotspot worker
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hotspots", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HotspotWorker>(15, TimeUnit.MINUTES).build()
        )

        setContent {
            GigTheme {
                val nav = rememberNavController()
                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf("Dashboard","Hotspots","Trips","Earnings","Settings").forEach { label ->
                            NavigationBarItem(selected = false, onClick = { nav.navigate(label) },
                                label = { Text(label) }, icon = { Icon(Icons.Filled.Circle, null) })
                        }
                    }
                }) { pad ->
                    NavHost(nav, "Dashboard", Modifier.padding(pad)) {
                        composable("Dashboard") { DashboardScreen() }
                        composable("Hotspots") { HotspotsScreen() }
                        composable("Trips") { TripsScreen(onStart = {
                            startForegroundService(Intent(this@MainActivity, MileageService::class.java))
                        }, onStop = {
                            stopService(Intent(this@MainActivity, MileageService::class.java))
                        }) }
                        composable("Earnings") { EarningsScreen() }
                        composable("Settings") { SettingsScreen() }
                    }
                }
            }
        }
    }
}

@Composable fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    var earnings by remember { mutableStateOf(0) }
    var miles by remember { mutableStateOf(0.0) }
    var expenses by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        scope.launch {
            val now = System.currentTimeMillis()
            val dayStart = now - 24*60*60*1000
            earnings = (DI.db.tripDao().sumCents(dayStart, now) ?: 0)
            miles = (DI.db.mileageDao().sumMiles(dayStart, now) ?: 0.0)
            expenses = (DI.db.expenseDao().sumCents(dayStart, now) ?: 0)
        }
    }
    val net = (earnings - expenses) / 100.0
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GigPulse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Earnings", "$%.2f".format(earnings/100.0), Modifier.weight(1f))
            Stat("Miles", "%.1f mi".format(miles), Modifier.weight(1f))
            Stat("Net", "$%.2f".format(net), Modifier.weight(1f))
        }
        Text("Recent Hotspots")
        val (hotspots, setHotspots) = remember { mutableStateOf<List<Hotspot>>(emptyList()) }
        LaunchedEffect(Unit) { setHotspots(DI.db.hotspotDao().all()) }
        LazyColumn {
            items(hotspots) { h ->
                ListItem(headlineContent={ Text(h.name) },
                    supportingContent={ Text("${h.platform} • intensity ${h.intensity}") },
                    trailingContent={ if (h.intensity>=80) Text("RED") else Text("OK") })
                Divider()
            }
        }
    }
}
@Composable fun Stat(title:String, value:String, modifier: Modifier=Modifier){
    Card(modifier){ Column(Modifier.padding(12.dp)){ Text(title, fontWeight=FontWeight.SemiBold); Text(value, style=MaterialTheme.typography.headlineSmall) } }
}

@Composable fun HotspotsScreen() {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("DoorDash") }
    var intensity by remember { mutableStateOf(50) }
    var lat by remember { mutableStateOf(36.2077) }
    var lng by remember { mutableStateOf(-119.3473) }
    var list by remember { mutableStateOf<List<Hotspot>>(emptyList()) }
    LaunchedEffect(Unit) { list = DI.db.hotspotDao().all() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hotspots", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, {name=it}, label={Text("Name")}, modifier=Modifier.weight(1f))
            FilterChip(selected = platform=="DoorDash", onClick={platform="DoorDash"}, label={Text("DoorDash")})
            FilterChip(selected = platform=="UberEats", onClick={platform="UberEats"}, label={Text("UberEats")})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat.toString(), { lat = it.toDoubleOrNull()?:lat }, label={Text("Lat")}, modifier=Modifier.weight(1f))
            OutlinedTextField(lng.toString(), { lng = it.toDoubleOrNull()?:lng }, label={Text("Lng")}, modifier=Modifier.weight(1f))
            OutlinedTextField(intensity.toString(), { intensity = it.toIntOrNull()?.coerceIn(0,100)?:intensity }, label={Text("Intensity 0-100")})
        }
        Button(onClick = {
            scope.launch {
                DI.db.hotspotDao().upsert(Hotspot("${name}-${lat}-${lng}", name, lat, lng, intensity, platform))
                list = DI.db.hotspotDao().all()
            }
        }) { Text("Save Hotspot") }
        Divider()
        LazyColumn {
            items(list) { h ->
                ListItem(headlineContent={ Text(h.name) },
                    supportingContent={ Text("${h.platform} • ${"%.4f".format(h.lat)}, ${"%.4f".format(h.lng)}") },
                    trailingContent={ Text(if(h.intensity>=80) "RED" else h.intensity.toString()) })
                Divider()
            }
        }
    }
}

@Composable fun TripsScreen(onStart:()->Unit, onStop:()->Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var miles by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("DoorDash") }
    var list by remember { mutableStateOf<List<Trip>>(emptyList()) }
    LaunchedEffect(Unit) { scope.launch { list = DI.db.tripDao().all() } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Trips / Mileage", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart) { Text("Start Tracking") }
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = platform=="DoorDash", onClick={platform="DoorDash"}, label={Text("DoorDash")})
            FilterChip(selected = platform=="UberEats", onClick={platform="UberEats"}, label={Text("UberEats")})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, {amount=it}, label={Text("Payout $")}, modifier=Modifier.weight(1f))
            OutlinedTextField(miles, {miles=it}, label={Text("Miles")}, modifier=Modifier.weight(1f))
        }
        Button(onClick = {
            val cents = (amount.toDoubleOrNull()?.times(100))?.toInt() ?: 0
            val dist = miles.toDoubleOrNull() ?: 0.0
            scope.launch {
                DI.db.tripDao().insert(Trip(platform=platform, payoutCents=cents, distanceMiles=dist,
                    startedAt=System.currentTimeMillis(), endedAt=System.currentTimeMillis()))
                list = DI.db.tripDao().all()
                amount=""; miles=""
            }
        }) { Text("Add Trip") }
        Divider()
        LazyColumn {
            items(list) { t ->
                ListItem(headlineContent={ Text("${t.platform}  $%.2f".format(t.payoutCents/100.0)) },
                    supportingContent={ Text("%.1f mi".format(t.distanceMiles)) })
                Divider()
            }
        }
    }
}

@Composable fun EarningsScreen() {
    val scope = rememberCoroutineScope()
    var gas by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var sumTrips by remember { mutableStateOf(0) }
    var sumExpenses by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        scope.launch {
            val now = System.currentTimeMillis()
            val day = now - 24*60*60*1000
            sumTrips = DI.db.tripDao().sumCents(day, now) ?: 0
            sumExpenses = DI.db.expenseDao().sumCents(day, now) ?: 0
            list = DI.db.expenseDao().all()
        }
    }
    val net = (sumTrips - sumExpenses)/100.0
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Earnings", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Gross", "$%.2f".format(sumTrips/100.0), Modifier.weight(1f))
            Stat("Expenses", "$%.2f".format(sumExpenses/100.0), Modifier.weight(1f))
            Stat("Net", "$%.2f".format(net), Modifier.weight(1f))
        }
        OutlinedTextField(gas, {gas=it}, label={Text("Add Gas $")})
        Button(onClick = {
            val cents = (gas.toDoubleOrNull()?.times(100))?.toInt() ?: 0
            scope.launch {
                DI.db.expenseDao().insert(Expense(category="Gas", amountCents=cents))
                val now = System.currentTimeMillis(); val day = now - 24*60*60*1000
                sumExpenses = DI.db.expenseDao().sumCents(day, now) ?: 0
                gas = ""
                list = DI.db.expenseDao().all()
            }
        }) { Text("Add Gas Expense") }
        Divider()
        LazyColumn {
            items(list) { e ->
                ListItem(headlineContent={ Text("${e.category}") },
                    supportingContent={ Text("$%.2f".format(e.amountCents/100.0)) })
                Divider()
            }
        }
    }
}

@Composable fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text("• Enable Notification Access for GigPulse to auto-mark hotspots when DoorDash/UberEats send 'busy' alerts.")
        Text("• Import CSV payouts (Profile → Import) to sync historical earnings.")
        Text("• Toggle Online in Trips to start/stop auto mileage.")
    }
}