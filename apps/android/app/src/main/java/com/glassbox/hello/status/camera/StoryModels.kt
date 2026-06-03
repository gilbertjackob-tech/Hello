package com.glassbox.hello.status.camera

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.graphics.Color

enum class StoryMediaType { Photo, Video }

enum class StoryEffectCategory(val label: String) {
    Favorites("Favorites"),
    ForYou("For You"),
    Aesthetic("Aesthetic"),
    Games("Games"),
    Backgrounds("Backgrounds"),
    Face("Face")
}

enum class StoryTool { Flip, Flash, Hd, Selfie, Timer, GreenScreen, Grid }

data class StoryEffect(
    val id: String,
    val label: String,
    val category: StoryEffectCategory,
    val accent: Color,
    val faceAware: Boolean = false,
    val backgroundAware: Boolean = false
)

data class StoryDraft(
    val mediaType: StoryMediaType = StoryMediaType.Photo,
    val sourceUri: Uri? = null,
    val sourceBytes: ByteArray? = null,
    val sourceName: String = "hello-story.jpg",
    val caption: String = "",
    val selectedEffect: StoryEffect = StoryEffects.default,
    val textColor: Int = android.graphics.Color.WHITE,
    val textOffsetX: Float = 0f,
    val textOffsetY: Float = 0.24f,
    val sticker: String? = null
) {
    val hasMedia: Boolean get() = sourceUri != null || sourceBytes != null
}

data class StoryCameraState(
    val permissionGranted: Boolean = false,
    val cameraReady: Boolean = false,
    val posting: Boolean = false,
    val error: String? = null,
    val lensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val timerSeconds: Int = 0,
    val gridEnabled: Boolean = false,
    val hdMode: Boolean = true,
    val greenScreen: Boolean = false,
    val expandedTools: Boolean = false,
    val selectedCategory: StoryEffectCategory = StoryEffectCategory.ForYou,
    val selectedEffect: StoryEffect = StoryEffects.default,
    val draft: StoryDraft? = null
)

object StoryEffects {
    val all = listOf(
        StoryEffect("natural", "Natural", StoryEffectCategory.Favorites, Color(0xFFFFFFFF)),
        StoryEffect("aesthetic", "Aesthetic", StoryEffectCategory.ForYou, Color(0xFFFFD166)),
        StoryEffect("vivid", "Vivid", StoryEffectCategory.ForYou, Color(0xFF00D4AA)),
        StoryEffect("film", "Film", StoryEffectCategory.Aesthetic, Color(0xFFFFA8C7)),
        StoryEffect("noir", "Noir", StoryEffectCategory.Aesthetic, Color(0xFFD1D5DB)),
        StoryEffect("sunset", "Sunset BG", StoryEffectCategory.Backgrounds, Color(0xFFFF8A3D), backgroundAware = true),
        StoryEffect("mint_bg", "Mint BG", StoryEffectCategory.Backgrounds, Color(0xFF5EEAD4), backgroundAware = true),
        StoryEffect("face_glow", "Face Glow", StoryEffectCategory.Face, Color(0xFFFFF176), faceAware = true),
        StoryEffect("neon_mask", "Neon Mask", StoryEffectCategory.Face, Color(0xFF9B7CFF), faceAware = true),
        StoryEffect("game_pop", "Pop", StoryEffectCategory.Games, Color(0xFF38BDF8), faceAware = true)
    )

    val default: StoryEffect = all.first { it.id == "aesthetic" }
}
