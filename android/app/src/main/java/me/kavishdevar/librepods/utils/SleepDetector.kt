/*
    LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.utils

import kotlin.math.abs

class SleepDetector {
    companion object {
        private const val WARMUP_SAMPLES = 12
        private const val BASELINE_SAMPLES = 40

        private const val PITCH_DEVIATION_START_DEGREES = 10f
        private const val PITCH_DEVIATION_CANCEL_DEGREES = 6f

        private const val LOW_MOTION_THRESHOLD = 1.2f
        private const val HIGH_MOTION_RESET_THRESHOLD = 3.5f

        private const val SUSPECTED_CONFIRMATION_MS = 25_000L
        private const val BASELINE_ADAPTATION = 0.02f
    }

    private var warmupSamplesSeen = 0
    private val baselinePitchSamples = mutableListOf<Float>()
    private val baselineYawSamples = mutableListOf<Float>()

    private var baselinePitch = 0f
    private var baselineYaw = 0f
    private var baselineReady = false

    private var lastOrientation: Orientation? = null
    private var suspectedSince: Long? = null
    private var triggered = false

    fun reset() {
        warmupSamplesSeen = 0
        baselinePitchSamples.clear()
        baselineYawSamples.clear()
        baselinePitch = 0f
        baselineYaw = 0f
        baselineReady = false
        lastOrientation = null
        suspectedSince = null
        triggered = false
    }

    fun processSample(
        orientation: Orientation,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (triggered) return false

        val previous = lastOrientation
        lastOrientation = orientation

        if (warmupSamplesSeen < WARMUP_SAMPLES) {
            warmupSamplesSeen++
            return false
        }

        if (!baselineReady) {
            baselinePitchSamples.add(orientation.pitch)
            baselineYawSamples.add(orientation.yaw)
            if (baselinePitchSamples.size >= BASELINE_SAMPLES) {
                baselinePitch = baselinePitchSamples.average().toFloat()
                baselineYaw = baselineYawSamples.average().toFloat()
                baselineReady = true
            }
            return false
        }

        if (previous == null) return false

        val motion = abs(orientation.pitch - previous.pitch) + abs(orientation.yaw - previous.yaw)
        val pitchDeviation = abs(orientation.pitch - baselinePitch)
        val yawDeviation = abs(orientation.yaw - baselineYaw)

        if (pitchDeviation < PITCH_DEVIATION_CANCEL_DEGREES && motion <= LOW_MOTION_THRESHOLD) {
            baselinePitch += (orientation.pitch - baselinePitch) * BASELINE_ADAPTATION
            baselineYaw += (orientation.yaw - baselineYaw) * BASELINE_ADAPTATION
        }

        val likelySleepPose =
            pitchDeviation >= PITCH_DEVIATION_START_DEGREES &&
                yawDeviation <= PITCH_DEVIATION_START_DEGREES &&
                motion <= LOW_MOTION_THRESHOLD

        if (likelySleepPose) {
            if (suspectedSince == null) {
                suspectedSince = now
            }

            if (now - (suspectedSince ?: now) >= SUSPECTED_CONFIRMATION_MS) {
                triggered = true
                return true
            }
            return false
        }

        if (motion >= HIGH_MOTION_RESET_THRESHOLD || pitchDeviation < PITCH_DEVIATION_CANCEL_DEGREES) {
            suspectedSince = null
        }

        return false
    }
}
