package com.glassbox.hello.demo.voice

import java.util.Locale

internal object VoiceCommandParser {
    private const val INTENT_START_CALL = "start_call"
    private const val INTENT_SEND_MESSAGE = "send_message"
    private const val INTENT_READ_MESSAGE = "read_message"
    private const val INTENT_UNKNOWN = "unknown"
    private const val ROUTE_HELLO_CALL = "hello_call"
    private const val ROUTE_DIRECT_MOBILE_CALL = "direct_mobile_call"
    private const val ROUTE_HELLO_MESSAGE = "hello_message"
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
    private val sendMessageMarkers = listOf("message", "msg", "sms", "text", "chat")
    private val readMessageMarkers = listOf("read message", "read msg", "message read", "messages")
    private val targetStopWords = setOf(
        "call",
        "message",
        "msg",
        "sms",
        "text",
        "chat",
        "read",
        "send",
        "pathao",
        "bolo",
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
        val messageIntent = sendMessageMarkers.any { marker -> normalizedTranscript.containsPhrase(marker) }
        val readMessageIntent = readMessageMarkers.any { marker -> normalizedTranscript.containsPhrase(marker) }
        val intent = when {
            callIntent -> INTENT_START_CALL
            readMessageIntent -> INTENT_READ_MESSAGE
            messageIntent -> INTENT_SEND_MESSAGE
            else -> INTENT_UNKNOWN
        }
        val route = detectRoute(normalizedTranscript, intent)
        val contactResolution = resolveContact(normalizedTranscript)
        val skillMatched = when {
            route == ROUTE_DIRECT_MOBILE_CALL -> ROUTE_DIRECT_MOBILE_CALL
            route == ROUTE_HELLO_CALL -> ROUTE_HELLO_CALL
            route == ROUTE_HELLO_MESSAGE -> ROUTE_HELLO_MESSAGE
            intent != INTENT_UNKNOWN -> intent
            else -> "none"
        }
        val confidence = when {
            intent != INTENT_UNKNOWN && contactResolution.status == VoiceResolutionStatus.Exact && route != ROUTE_UNKNOWN -> VoiceCommandConfidence.High
            intent != INTENT_UNKNOWN && contactResolution.status == VoiceResolutionStatus.Ambiguous -> VoiceCommandConfidence.Medium
            intent != INTENT_UNKNOWN && contactResolution.status == VoiceResolutionStatus.Missing -> VoiceCommandConfidence.Medium
            else -> VoiceCommandConfidence.Low
        }
        val needsConfirmation = when {
            intent == INTENT_UNKNOWN -> false
            contactResolution.status == VoiceResolutionStatus.Ambiguous -> true
            contactResolution.status == VoiceResolutionStatus.Missing -> true
            route == ROUTE_HELLO_CALL || route == ROUTE_HELLO_MESSAGE -> true
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
                intent = intent,
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
            needsConfirmation = parsedCommand.route == ROUTE_HELLO_CALL || parsedCommand.route == ROUTE_HELLO_MESSAGE,
            wouldDo = wouldDo(
                intent = parsedCommand.intent,
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

    private fun detectRoute(normalizedTranscript: String, intent: String): String {
        return when {
            intent == INTENT_UNKNOWN -> ROUTE_UNKNOWN
            intent == INTENT_SEND_MESSAGE || intent == INTENT_READ_MESSAGE -> ROUTE_HELLO_MESSAGE
            directMarkers.any { normalizedTranscript.hasToken(it) } -> ROUTE_DIRECT_MOBILE_CALL
            helloMarkers.any { marker -> normalizedTranscript.contains(normalizeTranscript(marker)) } -> ROUTE_HELLO_CALL
            intent == INTENT_START_CALL -> ROUTE_HELLO_CALL
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
        intent: String,
        route: String,
        status: VoiceResolutionStatus,
        target: DemoContactTarget?
    ): String {
        return when {
            intent == INTENT_UNKNOWN -> "Would log transcript only"
            status == VoiceResolutionStatus.Ambiguous -> "Would ask which user you meant"
            status == VoiceResolutionStatus.Missing -> "Would ask which user to use"
            target == null -> "Would ask which user to use"
            route == ROUTE_DIRECT_MOBILE_CALL -> "Would start direct mobile call to ${target.displayName}"
            intent == INTENT_SEND_MESSAGE -> "Would prepare a Hello message to ${target.displayName} after confirmation"
            intent == INTENT_READ_MESSAGE -> "Would read Hello messages from ${target.displayName} after confirmation"
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
            ROUTE_HELLO_MESSAGE -> 12
            else -> 0
        }
        val resolvedScore = if (resolvedTarget != null) 30 else 0
        val intentScore = if (intent != INTENT_UNKNOWN) 20 else 0
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
