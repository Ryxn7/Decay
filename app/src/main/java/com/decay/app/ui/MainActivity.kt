package com.decay.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.decay.app.accessibility.DecayAccessibilityService
import com.decay.app.core.DecayCore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DecayCore.loadEnabled(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = DecayDarkColors) {
                Scaffold { padding ->
                    DecayHomeScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

private val DecayDarkColors = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF16161D),
)

@Composable
private fun DecayHomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Bump this on every ON_RESUME to re-read settings-screen-granted permissions.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraGranted = remember(refresh) { hasCameraPermission(context) }
    val notificationsGranted = remember(refresh) { hasNotificationPermission(context) }
    val accessibilityOn = remember(refresh) { isAccessibilityEnabled(context) }
    val overlayOn = remember(refresh) { Settings.canDrawOverlays(context) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val enabled by DecayCore.enabled.collectAsState()
    val foreground by DecayCore.foregroundPackage.collectAsState()
    val detectionActive by DecayCore.detectionActive.collectAsState()
    val eyeOpen by DecayCore.eyeOpen.collectAsState()
    val lastGesture by DecayCore.lastGesture.collectAsState()
    val scrolls by DecayCore.scrollsToday.collectAsState()

    val ready = cameraGranted && accessibilityOn

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Decay", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Scroll without lifting a finger. Blink long to advance the feed.",
            color = Color(0xFFB5B5C2)
        )

        // --- Master toggle ---
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "Detection ON" else "Detection OFF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        if (detectionActive) "Camera active — watching for blinks"
                        else "Idle — open a whitelisted app to start",
                        color = Color(0xFFB5B5C2),
                        fontSize = 13.sp
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = ready,
                    onCheckedChange = { DecayCore.setEnabled(context, it) }
                )
            }
        }
        if (!ready) {
            Text(
                "Finish setup below to turn detection on.",
                color = Color(0xFFE0A45C),
                fontSize = 13.sp
            )
        }

        // --- Setup checklist ---
        Text("Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        SetupRow(
            title = "Camera",
            rationale = "To see your blinks. Frames never leave your phone.",
            done = cameraGranted,
            actionLabel = "Grant",
            onAction = { cameraLauncher.launch(Manifest.permission.CAMERA) }
        )

        SetupRow(
            title = "Accessibility service",
            rationale = "Lets Decay send a scroll to apps like Instagram.",
            done = accessibilityOn,
            actionLabel = "Open settings",
            onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        SetupRow(
            title = "Notifications",
            rationale = "Android shows you a badge whenever the camera is watching.",
            done = notificationsGranted,
            actionLabel = "Grant",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        SetupRow(
            title = "Display over other apps",
            rationale = "Needed so detection can start while a feed is open.",
            done = overlayOn,
            actionLabel = "Allow",
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        // --- Live status ---
        Spacer(Modifier.height(4.dp))
        Text("Live status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLine("Foreground app", foreground ?: "—")
                StatusLine("Camera running", if (detectionActive) "yes" else "no")
                StatusLine("Eyes open", "%.2f".format(eyeOpen))
                StatusLine("Last gesture", lastGesture ?: "—")
                StatusLine("Scrolls today", scrolls.toString())
            }
        }

        Text(
            "Tip: leave detection ON, then switch to Instagram, TikTok or YouTube. " +
                "Hold a blink for ~0.7s to advance.",
            color = Color(0xFFB5B5C2),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupRow(
    title: String,
    rationale: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (done) "✓ " else "") + title,
                    fontWeight = FontWeight.Bold,
                    color = if (done) Color(0xFF74D99F) else Color.White
                )
                Text(rationale, color = Color(0xFFB5B5C2), fontSize = 13.sp)
            }
            if (!done) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFB5B5C2))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

// --- helpers ---

private fun hasCameraPermission(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun isAccessibilityEnabled(ctx: Context): Boolean {
    val expected = ComponentName(ctx, DecayAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        ctx.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    for (component in splitter) {
        if (ComponentName.unflattenFromString(component) == expected) return true
    }
    return false
}
