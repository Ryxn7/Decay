package com.decay.app.core

import kotlin.math.abs

/**
 * Turns a stream of [FaceSignal]s into long-blink trigger events.
 *
 * Logic (per the spec):
 *  - A frame counts as "closed" when BOTH eye-open probabilities fall below
 *    [closedThreshold]. Requiring both eyes distinguishes a blink from a wink.
 *  - We measure how long the eyes stay continuously closed. Natural blinks
 *    (~100-400ms) never reach [longBlinkMs] and are ignored. A deliberate hold
 *    past [longBlinkMs] fires exactly one [BlinkEvent.LongBlink].
 *  - After a trigger, [cooldownMs] suppresses further triggers so one long blink
 *    can't fire multiple scrolls and detection is quiet during the scroll animation.
 *  - Frames where the face is too far ([minFaceWidthFraction]) or the head is
 *    turned away ([maxYawDegrees]) are treated as "not looking" and reset state.
 *
 * Not thread-safe; feed it from a single thread (the camera analysis executor).
 */
class BlinkClassifier(
    var closedThreshold: Float = 0.4f,
    var longBlinkMs: Long = 700L,
    var cooldownMs: Long = 1200L,
    var minFaceWidthFraction: Float = 0.18f,
    var maxYawDegrees: Float = 25f,
) {
    private var eyesClosed = false
    private var closedStartMs = 0L
    private var triggeredThisClosure = false
    private var lastTriggerMs = -100_000L

    fun reset() {
        eyesClosed = false
        triggeredThisClosure = false
    }

    fun update(s: FaceSignal): BlinkEvent {
        // Guard: no usable face → behave as if eyes opened, don't accumulate a hold.
        if (!s.faceFound ||
            s.faceWidthFraction < minFaceWidthFraction ||
            abs(s.yawDegrees) > maxYawDegrees
        ) {
            return if (eyesClosed) {
                eyesClosed = false
                triggeredThisClosure = false
                BlinkEvent.EyeState(open = true)
            } else {
                BlinkEvent.None
            }
        }

        val closedNow = s.leftEyeOpen < closedThreshold && s.rightEyeOpen < closedThreshold

        // Transition: open -> closed
        if (closedNow && !eyesClosed) {
            eyesClosed = true
            closedStartMs = s.timestampMs
            triggeredThisClosure = false
            return BlinkEvent.EyeState(open = false)
        }

        // Transition: closed -> open
        if (!closedNow && eyesClosed) {
            eyesClosed = false
            triggeredThisClosure = false
            return BlinkEvent.EyeState(open = true)
        }

        // Sustained closure: check whether it has become a long blink.
        if (closedNow && eyesClosed && !triggeredThisClosure) {
            val held = s.timestampMs - closedStartMs
            val sinceLastTrigger = s.timestampMs - lastTriggerMs
            if (held >= longBlinkMs && sinceLastTrigger >= cooldownMs) {
                triggeredThisClosure = true
                lastTriggerMs = s.timestampMs
                return BlinkEvent.LongBlink
            }
        }

        return BlinkEvent.None
    }
}
