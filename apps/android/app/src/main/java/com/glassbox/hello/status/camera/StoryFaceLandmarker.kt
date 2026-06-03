package com.glassbox.hello.status.camera

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

class StoryFaceLandmarker private constructor(private val landmarker: FaceLandmarker?) : AutoCloseable {
    val available: Boolean get() = landmarker != null

    override fun close() {
        runCatching { landmarker?.close() }
    }

    companion object {
        private const val MODEL_ASSET = "face_landmarker.task"

        fun create(context: Context): StoryFaceLandmarker {
            val hasAsset = runCatching {
                context.assets.open(MODEL_ASSET).close()
                true
            }.getOrDefault(false)
            if (!hasAsset) return StoryFaceLandmarker(null)
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(4)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            return StoryFaceLandmarker(runCatching { FaceLandmarker.createFromOptions(context, options) }.getOrNull())
        }
    }
}
