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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.decay.app.accessibility.DecayAccessibilityService
import com.decay.app.core.DecayCore

// ---- palette ----
private val Bg0 = Color(0xFF0A0A0F)
private val Bg1 = Color(0xFF14141F)
private val CardBg = Color(0xFF16161F)
private val Stroke1 = Color(0xFF2A2A38)
private val TextDim = Color(0xFF9A9AB0)
private val Accent = Color(0xFF7C8CFF)
private val AccentA = Color(0xFF6D5BFF)
private val AccentB = Color(0xFF9B7CFF)
private val Good = Color(0xFF4ADE80)
private val Warn = Color(0xFFE0A45C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DecayCore.load(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg0,
                    surface = CardBg,
                )
            ) {
                DecayApp()
            }
        }
    }
}

@Composable
private fun DecayApp() {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf("home") }

    // Re-read settings-screen-granted permissions on every resume.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val cameraGranted = remember(refresh) { hasCameraPermission(context) }
    val notifGranted = remember(refresh) { hasNotificationPermission(context) }
    val accessibilityOn = remember(refresh) { isAccessibilityEnabled(context) }
    val overlayOn = remember(refresh) { Settings.canDrawOverlays(context) }
    val ready = cameraGranted && accessibilityOn

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg0, Bg1)))
    ) {
        when (screen) {
            "settings" -> SettingsScreen(
                onBack = { screen = "home" },
                cameraGranted = cameraGranted,
                notifGranted = notifGranted,
                accessibilityOn = accessibilityOn,
                overlayOn = overlayOn,
                onGrantCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                onGrantNotif = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onAllowOverlay = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
            )

            else -> HomeScreen(
                ready = ready,
                onOpenSettings = { screen = "settings" },
            )
        }
    }
}

// ---------------- Home ----------------

@Composable
private fun HomeScreen(ready: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val enabled by DecayCore.enabled.collectAsState()
    val detectionActive by DecayCore.detectionActive.collectAsState()
    val foreground by DecayCore.foregroundPackage.collectAsState()
    val scrolls by DecayCore.scrollsToday.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "decay",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextDim)
            }
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PowerButton(
                on = enabled,
                enabled = ready,
                onClick = {
                    if (ready) DecayCore.setEnabled(context, !enabled) else onOpenSettings()
                }
            )
        }

        Spacer(Modifier.height(28.dp))

        val statusTitle: String
        val statusSub: String
        when {
            !ready -> {
                statusTitle = "Setup needed"
                statusSub = "Grant camera & accessibility to start"
            }
            enabled && detectionActive -> {
                statusTitle = "Scrolling hands-free"
                statusSub = "Watching in ${appLabel(foreground)} — blink to advance"
            }
            enabled -> {
                statusTitle = "Armed"
                statusSub = "Open Instagram, TikTok or YouTube and blink"
            }
            else -> {
                statusTitle = "Tap to start"
                statusSub = "Turn on blink-to-scroll"
            }
        }
        Text(
            statusTitle,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            statusSub,
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!ready) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Finish setup", color = Color.White) }
        }

        Spacer(Modifier.weight(1.4f))

        Text(
            "$scrolls scrolls today",
            color = TextDim,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun PowerButton(on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "ring"
    )

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        if (on) {
            Box(
                Modifier
                    .size(248.dp)
                    .scale(ring)
                    .background(
                        Brush.radialGradient(listOf(Accent.copy(alpha = 0.28f), Color.Transparent)),
                        CircleShape
                    )
            )
        }
        val circle = Modifier
            .size(196.dp)
            .clip(CircleShape)
            .then(
                if (on) Modifier.background(Brush.linearGradient(listOf(AccentA, AccentB)), CircleShape)
                else Modifier.background(Color(0xFF1B1B26), CircleShape).border(1.dp, Stroke1, CircleShape)
            )
            .clickable(enabled = enabled, onClick = onClick)

        Box(circle, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerGlyph(
                    color = if (on) Color.White else Color(0xFF6E6E84),
                    size = 60.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (on) "ON" else "OFF",
                    color = if (on) Color.White else TextDim,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
            }
        }
    }
}

@Composable
private fun PowerGlyph(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val s = w * 0.09f
        val r = w * 0.30f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = s, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(cx, cy - r * 0.15f),
            end = Offset(cx, cy - r * 1.2f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------- Settings ----------------

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    cameraGranted: Boolean,
    notifGranted: Boolean,
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    onGrantCamera: () -> Unit,
    onGrantNotif: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onAllowOverlay: () -> Unit,
) {
    val context = LocalContext.current
    val foreground by DecayCore.foregroundPackage.collectAsState()
    val detectionActive by DecayCore.detectionActive.collectAsState()
    val eyeOpen by DecayCore.eyeOpen.collectAsState()
    val lastGesture by DecayCore.lastGesture.collectAsState()
    val scrolls by DecayCore.scrollsToday.collectAsState()
    val longBlink by DecayCore.longBlinkMs.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        SectionTitle("Setup")
        SetupRow("Camera", "Sees your blinks. Frames never leave your phone.",
            cameraGranted, "Grant", onGrantCamera)
        SetupRow("Accessibility service", "Lets Decay send the scroll to other apps.",
            accessibilityOn, "Open", onOpenAccessibility)
        SetupRow("Notifications", "Shows the camera-active badge while watching.",
            notifGranted, "Grant", onGrantNotif)
        SetupRow("Display over other apps", "Lets detection start while a feed is open.",
            overlayOn, "Allow", onAllowOverlay)

        Spacer(Modifier.height(6.dp))
        SectionTitle("Blink sensitivity")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Hold a blink for ~$longBlink ms to scroll",
                    color = Color.White, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                var pos by remember(longBlink) {
                    mutableFloatStateOf(
                        ((longBlink - DecayCore.MIN_LONG_BLINK_MS).toFloat() /
                            (DecayCore.MAX_LONG_BLINK_MS - DecayCore.MIN_LONG_BLINK_MS))
                            .coerceIn(0f, 1f)
                    )
                }
                Slider(
                    value = pos,
                    onValueChange = { pos = it },
                    onValueChangeFinished = {
                        val ms = (DecayCore.MIN_LONG_BLINK_MS +
                            pos * (DecayCore.MAX_LONG_BLINK_MS - DecayCore.MIN_LONG_BLINK_MS)).toLong()
                        DecayCore.setLongBlinkMs(context, ms)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Looser", color = TextDim, fontSize = 12.sp)
                    Text("Stricter", color = TextDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionTitle("Active in these apps")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Decay only watches your face while one of these is open.",
                    color = TextDim, fontSize = 13.sp
                )
                AppRow("Instagram")
                AppRow("TikTok")
                AppRow("YouTube")
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionTitle("Live status")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLine("Foreground app", appLabel(foreground))
                StatusLine("Camera running", if (detectionActive) "yes" else "no")
                StatusLine("Eyes open", "%.2f".format(eyeOpen))
                StatusLine("Last gesture", lastGesture ?: "—")
                StatusLine("Scrolls today", scrolls.toString())
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionTitle("Privacy")
        Text(
            "All face detection runs on-device. Camera frames are analyzed in memory " +
                "and discarded immediately — never stored, never uploaded.",
            color = TextDim, fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SetupRow(
    title: String,
    rationale: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(rationale, color = TextDim, fontSize = 13.sp)
            }
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = Good)
            } else {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(actionLabel, color = Color.White) }
            }
        }
    }
}

@Composable
private fun AppRow(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(Good)
        )
        Spacer(Modifier.width(10.dp))
        Text(name, color = Color.White)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

// ---------------- helpers ----------------

private fun appLabel(pkg: String?): String = when (pkg) {
    null -> "—"
    "com.instagram.android" -> "Instagram"
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
    "com.google.android.youtube" -> "YouTube"
    else -> pkg.substringAfterLast('.')
}

private fun hasCameraPermission(ctx: Context): Boolean =
    ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun hasNotificationPermission(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true

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
