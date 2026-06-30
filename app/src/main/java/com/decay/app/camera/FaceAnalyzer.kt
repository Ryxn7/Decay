package com.decay.app.camera

import android.media.Image
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.decay.app.core.FaceSignal
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * CameraX analyzer that runs ML Kit face detection on each (throttled) frame and
 * emits a [FaceSignal]. No frame is ever stored or copied off-thread — the
 * ImageProxy is closed as soon as detection completes.
 *
 * @param minIntervalMs lower bound between analyzed frames (~16 fps default) to
 *        keep the face pipeline light on battery.
 */
class FaceAnalyzer(
    private val minIntervalMs: Long = 60L,
    private val onSignal: (FaceSignal) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // eye-open probs
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    private var lastAnalyzedMs = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedMs < minIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedMs = now

        val media: Image? = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotation == 90 || rotation == 270) media.height else media.width
        val input = InputImage.fromMediaImage(media, rotation)

        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() }
                if (face == null) {
                    onSignal(FaceSignal(now, 1f, 1f, 0f, 0f, faceFound = false))
                } else {
                    val left = face.leftEyeOpenProbability ?: 1f
                    val right = face.rightEyeOpenProbability ?: 1f
                    val widthFraction =
                        if (imageWidth > 0) face.boundingBox.width().toFloat() / imageWidth else 0f
                    onSignal(
                        FaceSignal(
                            timestampMs = now,
                            leftEyeOpen = left,
                            rightEyeOpen = right,
                            faceWidthFraction = widthFraction,
                            yawDegrees = face.headEulerAngleY,
                            faceFound = true,
                        )
                    )
                }
            }
            .addOnFailureListener {
                onSignal(FaceSignal(now, 1f, 1f, 0f, 0f, faceFound = false))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        detector.close()
    }
}
