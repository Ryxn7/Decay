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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.decay.app.R
import com.decay.app.accessibility.DecayAccessibilityService
import com.decay.app.core.DecayCore

// ---- palette ----
private val Bg0 = Color(0xFF05060B)
private val Bg1 = Color(0xFF0A0E1A)
private val Stroke1 = Color(0xFF26304A)
private val TextDim = Color(0xFF8A93B3)
private val Cyan = Color(0xFF34E5FF)
private val Accent = Color(0xFF7C8CFF)
private val AccentA = Color(0xFF5B7CFF)
private val AccentB = Color(0xFF9B6BFF)
private val Good = Color(0xFF4ADE80)

private val GlassTop = Color(0xFF161B2E)
private val GlassBot = Color(0xFF0D1120)

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
                    surface = GlassTop,
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

    val enabled by DecayCore.enabled.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Box(Modifier.fillMaxSize().background(Bg0)) {
        // Animated ambient background, brighter while detection is armed.
        DecayBackground(active = enabled && ready, modifier = Modifier.fillMaxSize())

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == "settings") {
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 4 } +
                            fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it } +
                        fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeOut(tween(220)))
                }
            },
            label = "screen"
        ) { target ->
            when (target) {
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
}

// ---------------- Animated background ----------------

@Composable
private fun DecayBackground(active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    val intensity by animateFloatAsState(
        targetValue = if (active) 1f else 0.55f,
        animationSpec = tween(900),
        label = "intensity"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Bg0, Bg1, Bg0)))

        // cyan orb, drifts top-left
        val c1 = Offset(w * (0.18f + 0.18f * a), h * (0.20f + 0.05f * b))
        drawRect(
            brush = Brush.radialGradient(
                listOf(Cyan.copy(alpha = 0.20f * intensity), Color.Transparent),
                center = c1, radius = w * 0.75f
            )
        )
        // violet orb, drifts bottom-right
        val c2 = Offset(w * (0.85f - 0.18f * b), h * (0.78f - 0.05f * a))
        drawRect(
            brush = Brush.radialGradient(
                listOf(AccentB.copy(alpha = 0.22f * intensity), Color.Transparent),
                center = c2, radius = w * 0.8f
            )
        )
        // indigo glow center
        val c3 = Offset(w * (0.5f + 0.08f * (a - b)), h * (0.5f))
        drawRect(
            brush = Brush.radialGradient(
                listOf(AccentA.copy(alpha = 0.12f * intensity), Color.Transparent),
                center = c3, radius = w * 0.65f
            )
        )
    }
}

// ---------------- Glass card ----------------

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color = Accent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(GlassTop.copy(alpha = 0.85f), GlassBot.copy(alpha = 0.85f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        glow.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.04f),
                        glow.copy(alpha = 0.18f),
                    )
                ),
                RoundedCornerShape(20.dp)
            )
    ) { content() }
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
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Decay",
                modifier = Modifier.height(40.dp)
            )
            Spacer(Modifier.weight(1f))
            SettingsButton(onClick = onOpenSettings)
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

        Spacer(Modifier.height(32.dp))

        val statusTitle: String
        val statusSub: String
        val statusColor: Color
        when {
            !ready -> {
                statusTitle = "Setup needed"
                statusSub = "Grant camera & accessibility to start"
                statusColor = AccentB
            }
            enabled && detectionActive -> {
                statusTitle = "Scrolling hands-free"
                statusSub = "Watching in ${appLabel(foreground)} — blink to advance"
                statusColor = Cyan
            }
            enabled -> {
                statusTitle = "Armed"
                statusSub = "Open Instagram, TikTok or YouTube and blink"
                statusColor = Good
            }
            else -> {
                statusTitle = "Tap to start"
                statusSub = "Turn on blink-to-scroll"
                statusColor = TextDim
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(9.dp).clip(CircleShape).background(statusColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                statusTitle,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            statusSub,
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 19.dp),
        )

        AnimatedVisibility(
            visible = !ready,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentA),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Finish setup", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }

        Spacer(Modifier.weight(1.4f))

        // scrolls-today stat chip
        GlassCard(
            modifier = Modifier.padding(bottom = 24.dp),
            glow = Cyan,
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    scrolls.toString(),
                    color = Cyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "scrolls today",
                    color = TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rot by animateFloatAsState(
        targetValue = if (pressed) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gearRot"
    )
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gearScale"
    )
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassTop.copy(alpha = 0.6f))
            .border(1.dp, Stroke1, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = Accent,
            modifier = Modifier.size(22.dp).rotate(rot).scale(sc)
        )
    }
}

@Composable
private fun PowerButton(on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "press"
    )

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )
    val spin by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "spin"
    )
    // smooth on/off colour + glow transition
    val onAmt by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "onAmt"
    )

    Box(
        Modifier.size(280.dp).scale(press),
        contentAlignment = Alignment.Center
    ) {
        // outer soft glow (fades in with on state)
        Box(
            Modifier
                .size(264.dp)
                .scale(if (on) ring else 1f)
                .alpha(onAmt)
                .background(
                    Brush.radialGradient(listOf(Accent.copy(alpha = 0.30f), Color.Transparent)),
                    CircleShape
                )
        )

        // rotating dashed tech ring
        Canvas(Modifier.size(244.dp).rotate(spin)) {
            val stroke = 2.dp.toPx()
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Cyan.copy(alpha = 0.05f + 0.45f * onAmt),
                        AccentB.copy(alpha = 0.05f + 0.45f * onAmt),
                        Cyan.copy(alpha = 0.05f + 0.45f * onAmt),
                    )
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 22f), 0f)
                ),
            )
        }

        // inner static ring
        Box(
            Modifier
                .size(214.dp)
                .border(1.dp, Stroke1.copy(alpha = 0.8f), CircleShape)
        )

        val circle = Modifier
            .size(190.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerpColor(Color(0xFF161A28), AccentA, onAmt),
                        lerpColor(Color(0xFF12131F), AccentB, onAmt),
                    )
                ),
                CircleShape
            )
            .border(
                1.dp,
                if (on) Color.White.copy(alpha = 0.25f) else Stroke1,
                CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )

        Box(circle, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerGlyph(
                    color = lerpColor(Color(0xFF6E6E84), Color.White, onAmt),
                    size = 62.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (on) "ON" else "OFF",
                    color = lerpColor(TextDim, Color.White, onAmt),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)

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
            Spacer(Modifier.width(4.dp))
            Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
        GlassCard(Modifier.fillMaxWidth(), glow = Cyan) {
            Column(Modifier.padding(18.dp)) {
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
                        thumbColor = Cyan,
                        activeTrackColor = Cyan,
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
        GlassCard(Modifier.fillMaxWidth(), glow = AccentB) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        GlassCard(Modifier.fillMaxWidth(), glow = Accent) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            modifier = Modifier.padding(start = 4.dp, bottom = 28.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(Cyan, AccentB)))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
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
    GlassCard(
        Modifier.fillMaxWidth(),
        glow = if (done) Good else Accent,
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
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Good.copy(alpha = 0.15f))
                        .border(1.dp, Good.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = Good,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentA),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(actionLabel, color = Color.White, fontWeight = FontWeight.SemiBold) }
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
