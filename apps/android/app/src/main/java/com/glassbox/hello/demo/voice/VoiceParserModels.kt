package com.glassbox.hello.demo.voice

internal data class DemoContactTarget(
    val id: String,
    val displayName: String,
    val aliases: List<String>
)

internal enum class VoiceCommandConfidence {
    High,
    Medium,
    Low
}

internal enum class VoiceResolutionStatus {
    Exact,
    Ambiguous,
    Missing,
    Unknown,
    ExactByClarification
}

internal data class ParsedVoiceCommand(
    val rawTranscript: String,
    val normalizedTranscript: String,
    val intent: String,
    val skillMatched: String,
    val route: String,
    val targetAlias: String,
    val resolvedTarget: DemoContactTarget?,
    val resolutionCandidates: List<DemoContactTarget>,
    val resolutionStatus: VoiceResolutionStatus,
    val confidence: VoiceCommandConfidence,
    val needsConfirmation: Boolean,
    val wouldDo: String
)

internal data class RankedVoiceCommandCandidate(
    val transcript: String,
    val parsedCommand: ParsedVoiceCommand,
    val score: Int
)

internal data class VoiceParseResult(
    val candidates: List<RankedVoiceCommandCandidate>,
    val chosen: ParsedVoiceCommand?
)

internal data class VoiceParseFeedback(
    val rating: Int,
    val route: String,
    val targetAlias: String,
    val resolvedTarget: String?,
    val timestampMillis: Long
)

internal data class GeminiVoiceReparseResult(
    val englishTranscript: String,
    val englishCommand: String,
    val intent: String,
    val route: String,
    val targetAliasEnglish: String?,
    val resolvedTargetEnglish: String?,
    val candidateTargetsEnglish: List<String>,
    val resolutionStatus: String,
    val confidence: String,
    val needsConfirmation: Boolean,
    val clarificationQuestionEnglish: String?,
    val wouldDoEnglish: String,
    val model: String,
    val keyIndex: Int
)
