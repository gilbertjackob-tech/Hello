package com.glassbox.hello.demo.voice

import java.util.Locale

internal object VoiceCommandParser {
    private const val INTENT_START_CALL = "start_call"
    private const val INTENT_UNKNOWN = "unknown"
    private const val ROUTE_HELLO_CALL = "hello_call"
    private const val ROUTE_DIRECT_MOBILE_CALL = "direct_mobile_call"
    private const val ROUTE_UNKNOWN = "unknown"

    val contacts = listOf(
        DemoContactTarget(
            id = "hasnat_pc",
            displayName = "Hasnat PC",
            aliases = listOf("hasnat pc", "hasnat computer", "হাসনাত পিসি")
        ),
        DemoContactTarget(
            id = "hasnat_ios",
            displayName = "Hasnat IOS",
            aliases = listOf("hasnat ios", "hasnat iphone", "হাসনাত আইওএস", "হাসনাত আইফোন")
        ),
        DemoContactTarget(
            id = "nowshin",
            displayName = "Nowshin",
            aliases = listOf("nowshin", "নওশিন", "নওশীন")
        ),
        DemoContactTarget(
            id = "bihi",
            displayName = "Bihi",
            aliases = listOf("bihi", "বিহি")
        )
    )

    private val ambiguousGroups = mapOf(
        "hasnat" to contacts.filter { it.id == "hasnat_pc" || it.id == "hasnat_ios" },
        "হাসনাত" to contacts.filter { it.id == "hasnat_pc" || it.id == "hasnat_ios" }
    )

    private val normalizationRules = listOf(
        "নওশীন" to "নওশিন",
        "ডিরেক্ট" to "direct",
        "সরাসরি" to "direct",
        "ফোন" to "phone",
        "মোবাইল" to "mobile",
        "কল" to "call",
        "হ্যালো" to "hello",
        "হেলো" to "hello",
        "হ্যাল" to "hello",
        "দিয়ে" to "দিয়ে",
        "দিয়া" to "দিয়ে",
        "দাও" to "dao",
        "করো" to "koro"
    )

    private val directMarkers = listOf("direct", "phone", "mobile")
    private val helloMarkers = listOf("hello", "hello app", "call on hello", "কল অন হেলো")
    private val callMarkers = listOf("call")
    private val targetStopWords = setOf(
        "call",
        "direct",
        "phone",
        "mobile",
        "hello",
        "app",
        "on",
        "কে",
        "এর",
        "dao",
        "koro",
        "দিয়ে",
        "অন"
    )

    fun parseCandidates(transcripts: List<String>): VoiceParseResult {
        val rankedCandidates = transcripts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { transcript ->
                val parsed = parse(transcript)
                RankedVoiceCommandCandidate(
                    transcript = transcript,
                    parsedCommand = parsed,
                    score = parsed.rankScore()
                )
            }
            .sortedWith(
                compareByDescending<RankedVoiceCommandCandidate> { it.score }
                    .thenBy { it.transcript.length }
            )

        return VoiceParseResult(
            candidates = rankedCandidates,
            chosen = rankedCandidates.firstOrNull()?.parsedCommand
        )
    }

    fun parse(rawTranscript: String): ParsedVoiceCommand {
        val normalizedTranscript = normalizeTranscript(rawTranscript)
        val callIntent = callMarkers.any { normalizedTranscript.hasToken(it) }
        val route = detectRoute(normalizedTranscript, callIntent)
        val contactResolution = resolveContact(normalizedTranscript)
        val intent = if (callIntent) INTENT_START_CALL else INTENT_UNKNOWN
        val skillMatched = when {
            route == ROUTE_DIRECT_MOBILE_CALL -> ROUTE_DIRECT_MOBILE_CALL
            route == ROUTE_HELLO_CALL -> ROUTE_HELLO_CALL
            callIntent -> INTENT_START_CALL
            else -> "none"
        }
        val confidence = when {
            callIntent && contactResolution.status == VoiceResolutionStatus.Exact && route != ROUTE_UNKNOWN -> VoiceCommandConfidence.High
            callIntent && contactResolution.status == VoiceResolutionStatus.Ambiguous -> VoiceCommandConfidence.Medium
            callIntent && contactResolution.status == VoiceResolutionStatus.Missing -> VoiceCommandConfidence.Medium
            else -> VoiceCommandConfidence.Low
        }
        val needsConfirmation = when {
            !callIntent -> false
            contactResolution.status == VoiceResolutionStatus.Ambiguous -> true
            contactResolution.status == VoiceResolutionStatus.Missing -> true
            route == ROUTE_HELLO_CALL -> true
            else -> false
        }

        return ParsedVoiceCommand(
            rawTranscript = rawTranscript,
            normalizedTranscript = normalizedTranscript,
            intent = intent,
            skillMatched = skillMatched,
            route = route,
            targetAlias = contactResolution.targetAlias,
            resolvedTarget = contactResolution.resolvedTarget,
            resolutionCandidates = contactResolution.candidates,
            resolutionStatus = contactResolution.status,
            confidence = confidence,
            needsConfirmation = needsConfirmation,
            wouldDo = wouldDo(
                callIntent = callIntent,
                route = route,
                status = contactResolution.status,
                target = contactResolution.resolvedTarget
            )
        )
    }

    fun clarify(parsedCommand: ParsedVoiceCommand, target: DemoContactTarget): ParsedVoiceCommand {
        return parsedCommand.copy(
            resolvedTarget = target,
            resolutionCandidates = emptyList(),
            resolutionStatus = VoiceResolutionStatus.ExactByClarification,
            confidence = VoiceCommandConfidence.High,
            needsConfirmation = parsedCommand.route == ROUTE_HELLO_CALL,
            wouldDo = wouldDo(
                callIntent = parsedCommand.intent == INTENT_START_CALL,
                route = parsedCommand.route,
                status = VoiceResolutionStatus.ExactByClarification,
                target = target
            )
        )
    }

    fun normalizeTranscript(text: String): String {
        var normalized = text.cleanForMatching()
        normalizationRules.forEach { (from, to) ->
            normalized = normalized.replace(from.cleanForMatching(), to.cleanForMatching())
        }
        return normalized.normalizeSpaces()
    }

    private fun detectRoute(normalizedTranscript: String, callIntent: Boolean): String {
        return when {
            !callIntent -> ROUTE_UNKNOWN
            directMarkers.any { normalizedTranscript.hasToken(it) } -> ROUTE_DIRECT_MOBILE_CALL
            helloMarkers.any { marker -> normalizedTranscript.contains(normalizeTranscript(marker)) } -> ROUTE_HELLO_CALL
            callIntent -> ROUTE_HELLO_CALL
            else -> ROUTE_UNKNOWN
        }
    }

    private fun resolveContact(normalizedTranscript: String): ContactResolution {
        contacts
            .flatMap { contact -> contact.aliases.map { alias -> contact to normalizeTranscript(alias) } }
            .sortedByDescending { (_, alias) -> alias.length }
            .forEach { (contact, alias) ->
                if (normalizedTranscript.containsPhrase(alias)) {
                    return ContactResolution(
                        targetAlias = alias,
                        resolvedTarget = contact,
                        candidates = emptyList(),
                        status = VoiceResolutionStatus.Exact
                    )
                }
            }

        ambiguousGroups.forEach { (alias, candidates) ->
            if (normalizedTranscript.containsPhrase(normalizeTranscript(alias))) {
                return ContactResolution(
                    targetAlias = alias,
                    resolvedTarget = null,
                    candidates = candidates,
                    status = VoiceResolutionStatus.Ambiguous
                )
            }
        }

        val possibleAlias = normalizedTranscript
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in targetStopWords }
            .joinToString(" ")
            .normalizeSpaces()

        return ContactResolution(
            targetAlias = possibleAlias,
            resolvedTarget = null,
            candidates = emptyList(),
            status = if (possibleAlias.isBlank()) VoiceResolutionStatus.Missing else VoiceResolutionStatus.Unknown
        )
    }

    private fun wouldDo(
        callIntent: Boolean,
        route: String,
        status: VoiceResolutionStatus,
        target: DemoContactTarget?
    ): String {
        return when {
            !callIntent -> "Would log transcript only"
            status == VoiceResolutionStatus.Ambiguous -> "Would ask which Hasnat to call"
            status == VoiceResolutionStatus.Missing -> "Would ask which user to call"
            target == null -> "Would ask which user to call"
            route == ROUTE_DIRECT_MOBILE_CALL -> "Would start direct mobile call to ${target.displayName}"
            else -> "Would start Hello app call with ${target.displayName} after confirmation"
        }
    }

    private fun ParsedVoiceCommand.rankScore(): Int {
        val confidenceScore = when (confidence) {
            VoiceCommandConfidence.High -> 300
            VoiceCommandConfidence.Medium -> 200
            VoiceCommandConfidence.Low -> 100
        }
        val resolutionScore = when (resolutionStatus) {
            VoiceResolutionStatus.ExactByClarification -> 70
            VoiceResolutionStatus.Exact -> 60
            VoiceResolutionStatus.Ambiguous -> 35
            VoiceResolutionStatus.Missing -> 15
            VoiceResolutionStatus.Unknown -> 0
        }
        val routeScore = when (route) {
            ROUTE_DIRECT_MOBILE_CALL -> 20
            ROUTE_HELLO_CALL -> 12
            else -> 0
        }
        val resolvedScore = if (resolvedTarget != null) 30 else 0
        val intentScore = if (intent == INTENT_START_CALL) 20 else 0
        return confidenceScore + resolutionScore + routeScore + resolvedScore + intentScore
    }

    private data class ContactResolution(
        val targetAlias: String,
        val resolvedTarget: DemoContactTarget?,
        val candidates: List<DemoContactTarget>,
        val status: VoiceResolutionStatus
    )
}

internal fun String.cleanForMatching(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("[,।.!?;:()\\[\\]{}]"), " ")
        .normalizeSpaces()
}

internal fun String.normalizeSpaces(): String {
    return trim().replace(Regex("\\s+"), " ")
}

private fun String.hasToken(token: String): Boolean {
    return split(" ").contains(token)
}

private fun String.containsPhrase(phrase: String): Boolean {
    val phraseTokens = phrase.split(" ").filter { it.isNotBlank() }
    if (phraseTokens.isEmpty()) return false
    val tokens = split(" ").filter { it.isNotBlank() }
    if (phraseTokens.size > tokens.size) return false
    return tokens.windowed(phraseTokens.size).any { it == phraseTokens }
}
