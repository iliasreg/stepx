package com.example.stepx

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.stepx.ui.theme.StepXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { PreferencesManager(this) }
            val darkTheme by prefs.darkThemeEnabled.collectAsState(initial = false)
            StepXTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val needsARPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { }
                )
            val arPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { }
            )
                if (needsNotifPermission) {
                    LaunchedEffect(Unit) {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            if (needsARPermission) {
                LaunchedEffect(Unit) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        arPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
            }
                Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = {
                    val route = navController.currentBackStackEntryAsState().value?.destination?.route
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == "dashboard",
                            onClick = { navController.navigate("dashboard") },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") }
                        )
                        
                        NavigationBarItem(
                            selected = route == "settings",
                            onClick = { navController.navigate("settings") },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                }) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(snackbarHostState = snackbarHostState)
                        }
                        
                        composable("settings") { SettingsScreen() }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun DashboardScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val counter = remember { StepCounterManager.getInstance(context) }
    val prefs = remember { PreferencesManager(context) }
    val dailyGoal by prefs.dailyGoal.collectAsState(initial = 10000)
    val lightReminderEnabled by prefs.lightReminderEnabled.collectAsState(initial = true)
    val persistedSteps by prefs.totalSteps.collectAsState(initial = 0)

    val speed by counter.speedMps.collectAsState()
    val activityState by counter.activityState.collectAsState()
    val calories by counter.caloriesBurned.collectAsState()
    val distance by counter.distanceMeters.collectAsState()

    val ambientLux by counter.ambientLux.collectAsState()
    val isCovered by counter.isCovered.collectAsState()
    var stepsToday by remember { mutableIntStateOf(persistedSteps) }
    var firstDimAtMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(counter) {
        counter.totalSteps.collect { steps -> stepsToday = steps }
    }

    // Sensor registration handled by StepCounterManager; no per-screen listeners
    // Light reminder: poll periodically so it triggers even if lux doesn't change
    LaunchedEffect(lightReminderEnabled) {
        val dimThreshold = 200f
        val durationMs = 10_000L
        while (true) {
            if (!lightReminderEnabled) {
                firstDimAtMs = 0L
            } else {
                val now = System.currentTimeMillis()
                if (ambientLux < dimThreshold) {
                    if (firstDimAtMs == 0L) firstDimAtMs = now
                    val elapsed = now - firstDimAtMs
                    if (elapsed > durationMs) {
                        snackbarHostState.showSnackbar("It's been dim for a while. Consider better lighting.")
                        firstDimAtMs = now // reset so it doesn't spam
                    }
                } else {
                    firstDimAtMs = 0L
                }
            }
            delay(1000)
        }
    }

    // Detect date change every minute and reset only local counters for new day
    // No date-bound reset anymore

    val clampedSteps = stepsToday.coerceAtMost(dailyGoal)
    val animatedProgress by animateFloatAsState(
        targetValue = (clampedSteps.toFloat() / dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 400f)
    )
    val animatedSteps by animateIntAsState(
        targetValue = clampedSteps,
        animationSpec = spring(stiffness = 400f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "HAITAM SIMULATOR",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))

        CircularProgressIndicator(
            progress = { animatedProgress },
            color = ProgressIndicatorDefaults.circularColor,
            strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth,
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
            strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
        )
        Text(
            text = "$animatedSteps",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        val pct = (animatedProgress * 100).toInt()
        Text(
            text = "$pct% of goal",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Goal: $dailyGoal",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Activity State: $activityState", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(text = String.format("Speed: %.2f m/s", speed), style = MaterialTheme.typography.bodyMedium)
            Text(text = String.format("Calories: %.1f kcal", calories), style = MaterialTheme.typography.bodyMedium)
            Text(text = String.format("Distance: %.2f m", distance), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = ambientLux >= 1000f,
                onClick = {},
                label = { Text(if (ambientLux >= 1000f) "Bright" else "Dim") },
                leadingIcon = {
                    Icon(
                        imageVector = if (ambientLux >= 1000f) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null
                    )
                }
            )
            FilterChip(
                selected = !isCovered,
                onClick = {},
                label = { Text(if (isCovered) "In Pocket" else "Active") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhoneIphone,
                        contentDescription = null
                    )
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        // Extra small stats row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(tonalElevation = 2.dp) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = "Lux: ${ambientLux.toInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Surface(tonalElevation = 2.dp) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = if (isCovered) "Prox: Pocket" else "Prox: Clear",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val goal by prefs.dailyGoal.collectAsState(initial = 10000)
    val lightEnabled by prefs.lightReminderEnabled.collectAsState(initial = true)
    val pocketEnabled by prefs.pocketDetectionEnabled.collectAsState(initial = true)
    val darkThemeEnabled by prefs.darkThemeEnabled.collectAsState(initial = false)
    val bgTrackingEnabled by prefs.backgroundTrackingEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var isGoalDialogVisible by remember { mutableStateOf(false) }
    var goalInput by remember { mutableStateOf(goal.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(text = "Goal", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("Daily Goal") },
            supportingContent = { Text("$goal steps") },
            trailingContent = {
                OutlinedButton(onClick = {
                    goalInput = goal.toString()
                    isGoalDialogVisible = true
                }) { Text("Change") }
            }
        )
        if (isGoalDialogVisible) {
            GoalDialog(
                currentGoal = goal,
                initialText = goalInput,
                onDismiss = { isGoalDialogVisible = false },
                onSave = { newGoal ->
                    scope.launch { prefs.setDailyGoal(newGoal) }
                    isGoalDialogVisible = false
                },
                onTextChange = { goalInput = it }
            )
        }
        Divider()
        Spacer(Modifier.height(12.dp))
        Text(text = "Sensors", style = MaterialTheme.typography.titleMedium)
        ListItem(
            leadingContent = { Icon(Icons.Default.LightMode, contentDescription = null) },
            headlineContent = { Text("Light Reminder") },
            supportingContent = { Text("Notify when it's dim for long") },
            trailingContent = {
                Switch(checked = lightEnabled, onCheckedChange = {
                    scope.launch { prefs.setLightReminderEnabled(it) }
                })
            }
        )
        ListItem(
            leadingContent = { Icon(Icons.Default.PhoneIphone, contentDescription = null) },
            headlineContent = { Text("Pocket Detection") },
            supportingContent = { Text("Pause steps when in pocket") },
            trailingContent = {
                Switch(checked = pocketEnabled, onCheckedChange = {
                    scope.launch { prefs.setPocketDetectionEnabled(it) }
                })
            }
        )
        Divider()
        Spacer(Modifier.height(12.dp))
        Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
        ListItem(
            leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
            headlineContent = { Text("Dark Theme") },
            supportingContent = { Text("Use dark color scheme") },
            trailingContent = {
                Switch(checked = darkThemeEnabled, onCheckedChange = {
                    scope.launch { prefs.setDarkThemeEnabled(it) }
                })
            }
        )
        Divider()
        Spacer(Modifier.height(12.dp))
        Text(text = "Background", style = MaterialTheme.typography.titleMedium)
        ListItem(
            leadingContent = { Icon(Icons.Default.DirectionsWalk, contentDescription = null) },
            headlineContent = { Text("Background tracking") },
            supportingContent = { Text("Continue counting with screen off") },
            trailingContent = {
                Switch(checked = bgTrackingEnabled, onCheckedChange = { checked ->
                    val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    if (checked && needsNotifPermission) {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            Toast.makeText(context, "Enable notifications to use background tracking", Toast.LENGTH_LONG).show()
                            return@Switch
                        }
                    }

                    scope.launch { prefs.setBackgroundTrackingEnabled(checked) }
                    val svc = Intent(context, StepTrackingService::class.java)
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc)
                    } else {
                        context.stopService(svc)
                    }
                })
            }
        )
        Spacer(Modifier.weight(1f))
        Divider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Developers: i24reguig@enib.fr, y24elmesb@enib.fr",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GoalDialog(
    currentGoal: Int,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onTextChange: (String) -> Unit
) {
    var localText by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val parsed = localText.filter { it.isDigit() }.toIntOrNull()
                if (parsed != null && parsed > 0) onSave(parsed)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Set Daily Goal") },
        text = {
            OutlinedTextField(
                value = localText,
                onValueChange = { localText = it; onTextChange(it) },
                label = { Text("Steps") }
            )
        }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun HistoryScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val last7 by prefs.lastNDaysSteps(7).collectAsState(initial = List(7) { 0 })
    val days = remember(last7) {
        val today = LocalDate.now()
        (6 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "History", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        if (last7.any { it > 0 }) {
            SimpleBarChart(days = days, values = last7)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null
                )
                Spacer(Modifier.height(8.dp))
    Text(
                    text = "No steps yet — start moving to build your history",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SimpleBarChart(days: List<String>, values: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    val barCount = values.size
    val barSpacing = 16.dp
    val barWidth = 24.dp
    val chartHeight = 200.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .height(chartHeight)
        ) {
            val totalSpacingPx = (barSpacing.toPx() * (barCount + 1))
            val availableWidth = size.width - totalSpacingPx
            val barWidthPx = barWidth.toPx().coerceAtMost(availableWidth / barCount)
            var x = barSpacing.toPx()
            values.forEach { v ->
                val ratio = v.toFloat() / maxValue.toFloat()
                val barHeight = ratio * size.height
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidthPx, barHeight)
                )
                x += barWidthPx + barSpacing.toPx()
            }
        }
        Spacer(Modifier.height(8.dp))
        // Simple labels row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            days.forEach { day ->
                Text(text = day, color = labelColor)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    StepXTheme { DashboardScreen(snackbarHostState = SnackbarHostState()) }
}