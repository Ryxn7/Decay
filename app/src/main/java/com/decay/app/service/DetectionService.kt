package com.decay.app.service

import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.decay.app.DecayApp
import com.decay.app.R
import com.decay.app.camera.FaceAnalyzer
import com.decay.app.core.BlinkClassifier
import com.decay.app.core.BlinkEvent
import com.decay.app.core.DecayCore
import com.decay.app.core.FaceSignal
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that owns the camera while a whitelisted app is open.
 * Started/stopped by [com.decay.app.accessibility.DecayAccessibilityService].
 *
 * It is a LifecycleService so CameraX can bind to it directly with no Activity
 * and no preview Surface — detection runs headless in the background.
 */
class DetectionService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: FaceAnalyzer? = null
    private lateinit var analysisExecutor: ExecutorService
    private val classifier = BlinkClassifier()
    private var lastLogMs = 0L

    override fun onCreate() {
        super.onCreate()
        analysisExecutor = Executors.newSingleThreadExecutor()
        startAsForeground()
        DecayCore.detectionActive.value = true
        startCamera()
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, DecayApp.CHANNEL_ID)
            .setContentTitle("Decay is watching for blinks")
            .setContentText("Camera active in this app. Frames stay on your phone.")
            .setSmallIcon(R.drawable.ic_eye)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val az = FaceAnalyzer { signal -> onSignal(signal) }
                analyzer = az

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, az) }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                classifier.reset()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onSignal(signal: FaceSignal) {
        DecayCore.eyeOpen.value = (signal.leftEyeOpen + signal.rightEyeOpen) / 2f
        // Apply the user's current sensitivity live (cheap; runs on the analysis thread).
        classifier.longBlinkMs = DecayCore.longBlinkMs.value

        // Throttled diagnostic logging — read via: adb logcat -s DecayDetect
        if (signal.timestampMs - lastLogMs >= 250L) {
            lastLogMs = signal.timestampMs
            Log.d(
                "DecayDetect",
                "face=${signal.faceFound} L=%.2f R=%.2f w=%.2f yaw=%.0f hold=${DecayCore.longBlinkMs.value}"
                    .format(signal.leftEyeOpen, signal.rightEyeOpen, signal.faceWidthFraction, signal.yawDegrees)
            )
        }

        when (classifier.update(signal)) {
            is BlinkEvent.LongBlink -> {
                Log.d("DecayDetect", "*** LONG BLINK ***")
                DecayCore.onLongBlink()
            }
            else -> Unit
        }
    }

    override fun onDestroy() {
        DecayCore.detectionActive.value = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbind failed", e)
        }
        analyzer?.close()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 1001
        private const val TAG = "DetectionService"
    }
}
