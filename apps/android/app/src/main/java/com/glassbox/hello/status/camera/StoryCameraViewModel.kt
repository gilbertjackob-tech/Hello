package com.glassbox.hello.status.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StoryCameraViewModel : ViewModel() {
    private val _state = MutableStateFlow(StoryCameraState())
    val state: StateFlow<StoryCameraState> = _state.asStateFlow()

    fun setPermission(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted, error = if (granted) null else it.error) }
    }

    fun setCameraReady(ready: Boolean) {
        _state.update { it.copy(cameraReady = ready) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(error = message) }
    }

    fun toggleTools() {
        _state.update { it.copy(expandedTools = !it.expandedTools) }
    }

    fun selectCategory(category: StoryEffectCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun selectEffect(effect: StoryEffect) {
        _state.update { current ->
            current.copy(
                selectedEffect = effect,
                draft = current.draft?.copy(selectedEffect = effect),
                selectedCategory = effect.category
            )
        }
    }

    fun updateCaption(text: String) {
        _state.update { current -> current.copy(draft = current.draft?.copy(caption = text)) }
    }

    fun updateSticker(sticker: String?) {
        _state.update { current -> current.copy(draft = current.draft?.copy(sticker = sticker)) }
    }

    fun flipCamera() {
        _state.update {
            it.copy(
                lensFacing = if (it.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            )
        }
    }

    fun toggleFlash() {
        _state.update {
            it.copy(
                flashMode = if (it.flashMode == ImageCapture.FLASH_MODE_OFF) {
                    ImageCapture.FLASH_MODE_ON
                } else {
                    ImageCapture.FLASH_MODE_OFF
                }
            )
        }
    }

    fun cycleTimer() {
        _state.update {
            val next = when (it.timerSeconds) {
                0 -> 3
                3 -> 10
                else -> 0
            }
            it.copy(timerSeconds = next)
        }
    }

    fun toggleGrid() {
        _state.update { it.copy(gridEnabled = !it.gridEnabled) }
    }

    fun toggleHd() {
        _state.update { it.copy(hdMode = !it.hdMode) }
    }

    fun toggleGreenScreen() {
        _state.update { current ->
            val enabled = !current.greenScreen
            val effect = if (enabled) StoryEffects.all.first { it.id == "mint_bg" } else current.selectedEffect
            current.copy(greenScreen = enabled, selectedEffect = effect, draft = current.draft?.copy(selectedEffect = effect))
        }
    }

    fun openDraft(uri: Uri, bytes: ByteArray, name: String = "hello-story.jpg") {
        _state.update {
            it.copy(
                error = null,
                draft = StoryDraft(sourceUri = uri, sourceBytes = bytes, sourceName = name, selectedEffect = it.selectedEffect)
            )
        }
    }

    fun retake() {
        _state.update { it.copy(draft = null, error = null) }
    }

    fun post(context: Context, currentUserId: String, onPosted: () -> Unit) {
        val draft = _state.value.draft ?: return
        _state.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { StoryExportRenderer.render(context, draft) }
                    .fold(
                        onSuccess = { bytes -> StoryCloudPublisher(context).publishPhoto(currentUserId, bytes) },
                        onFailure = { Result.failure(it) }
                    )
            }
            _state.update { it.copy(posting = false, error = result.exceptionOrNull()?.message) }
            if (result.isSuccess) onPosted()
        }
    }

    fun saveDraft(context: Context) {
        val draft = _state.value.draft ?: return
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = StoryExportRenderer.render(context, draft)
                    val dir = File(context.cacheDir, "story-drafts").apply { mkdirs() }
                    val file = File(dir, "hello-story-draft-${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    file.name
                }
            }
            _state.update {
                it.copy(error = result.fold(
                    onSuccess = { name -> "Saved draft: $name" },
                    onFailure = { error -> error.message ?: "Could not save draft" }
                ))
            }
        }
    }
}
