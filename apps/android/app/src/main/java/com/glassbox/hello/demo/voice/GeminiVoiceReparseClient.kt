package com.glassbox.hello.demo.voice

import com.glassbox.hello.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class GeminiVoiceReparseClient(
    private val apiKeys: List<String> = BuildConfig.GEMINI_API_KEYS
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() },
    private val model: String = "gemini-2.5-flash-lite",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun reparse(
        parseResult: VoiceParseResult,
        deviceContacts: List<DemoContactTarget>
    ): GeminiVoiceReparseResult = withContext(Dispatchers.IO) {
        if (apiKeys.isEmpty()) {
            throw IOException("Gemini API keys are not configured.")
        }

        val prompt = buildPrompt(parseResult, deviceContacts)
        val requestJson = gson.toJson(
            mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to prompt))
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to 0.0,
                    "topP" to 0.1,
                    "maxOutputTokens" to 520,
                    "responseMimeType" to "application/json"
                )
            )
        )

        var lastError: IOException? = null
        apiKeys.forEachIndexed { index, key ->
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext parseResponse(body, index)
                }

                val error = IOException("Gemini HTTP ${response.code}: ${body.take(220)}")
                if (response.code in retryableStatusCodes && index < apiKeys.lastIndex) {
                    lastError = error
                } else {
                    throw error
                }
            }
        }

        throw lastError ?: IOException("Gemini request failed.")
    }

    private fun buildPrompt(
        parseResult: VoiceParseResult,
        deviceContacts: List<DemoContactTarget>
    ): String {
        val chosen = parseResult.chosen
        val candidateText = parseResult.candidates.joinToString("\n") { candidate ->
            "- ${candidate.transcript}"
        }
        val canonicalContacts = (VoiceCommandParser.contacts + deviceContacts)
            .distinctBy { it.displayName.lowercase() }
            .take(260)
        val userDirectory = canonicalContacts.joinToString("\n") { contact ->
            "- ${contact.displayName}: aliases=${contact.aliases.take(8).joinToString(", ")}"
        }
        val localParse = if (chosen == null) {
            "none"
        } else {
            """
            rawTranscript: ${chosen.rawTranscript}
            normalizedTranscript: ${chosen.normalizedTranscript}
            intent: ${chosen.intent}
            route: ${chosen.route}
            targetAlias: ${chosen.targetAlias}
            resolvedTarget: ${chosen.resolvedTarget?.displayName ?: "null"}
            resolutionCandidates: ${chosen.resolutionCandidates.joinToString(", ") { it.displayName }}
            resolutionStatus: ${chosen.resolutionStatus.name}
            confidence: ${chosen.confidence.name}
            """.trimIndent()
        }

        return """
            You are a strict Bangla/Banglish voice-command normalizer for an Android demo.
            Rewrite the detected command in English and return only compact JSON. Do not include markdown or extra keys.

            Canonical user directory. Resolve targets only from this list:
            $userDirectory

            Allowed intents: start_call, send_message, read_message, unknown.
            Allowed routes: direct_mobile_call, hello_call, hello_message, unknown.
            Allowed resolutionStatus: Exact, Ambiguous, Missing, Unknown.
            Allowed confidence: High, Medium, Low.

            Rules:
            - Translate Bangla/Banglish words into a concise English command.
            - Keep all names in English form.
            - For call/message/read-message intents, resolve target only from the canonical user directory.
            - If the spoken target can refer to more than one user, set resolvedTargetEnglish=null, resolutionStatus=Ambiguous, needsConfirmation=true, and put all plausible users in candidateTargetsEnglish.
            - If a call/message/read-message intent is present but no target is spoken, set resolutionStatus=Missing, needsConfirmation=true, and put every canonical user in candidateTargetsEnglish.
            - If the spoken target is not in the directory, set resolutionStatus=Unknown and do not invent a user.
            - কল, call, phone/mobile/direct/সরাসরি/ডিরেক্ট imply start_call.
            - hello, হেলো, হ্যালো, hello app, or missing route with call means hello_call.
            - direct, phone, mobile, ডিরেক্ট, সরাসরি means direct_mobile_call.
            - message/msg/text/chat/মেসেজ/বার্তা/চ্যাট/পাঠাও imply send_message with hello_message route.
            - read message/মেসেজ পড়ো/মেসেজ শোনাও imply read_message with hello_message route.
            - hasnat or হাসনাত without PC/IOS is Ambiguous between Hasnat PC and Hasnat IOS.
            - clarificationQuestionEnglish must be a short question Hello can ask next when needsConfirmation is true.
            - This is demo parsing only. Never say an action was executed.

            Return exactly this JSON shape:
            {
              "englishTranscript": "string",
              "englishCommand": "string",
              "intent": "start_call|send_message|read_message|unknown",
              "route": "direct_mobile_call|hello_call|hello_message|unknown",
              "targetAliasEnglish": "string or null",
              "resolvedTargetEnglish": "exact name from directory or null",
              "candidateTargetsEnglish": ["exact names from directory"],
              "resolutionStatus": "Exact|Ambiguous|Missing|Unknown",
              "confidence": "High|Medium|Low",
              "needsConfirmation": true,
              "clarificationQuestionEnglish": "string or null",
              "wouldDoEnglish": "string"
            }

            Speech candidates:
            $candidateText

            Local parser output:
            $localParse
        """.trimIndent()
    }

    private fun parseResponse(body: String, keyIndex: Int): GeminiVoiceReparseResult {
        val root = JsonParser.parseString(body).asJsonObject
        val text = root
            .getAsJsonArray("candidates")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.firstOrNull()
            ?.asJsonObject
            ?.get("text")
            ?.asString
            .orEmpty()

        if (text.isBlank()) {
            throw IOException("Gemini returned an empty response.")
        }

        val parsed = JsonParser.parseString(text.stripJsonFence()).asJsonObject
        val intent = parsed.stringOrEmpty("intent").coerceToAllowed(
            allowed = setOf("start_call", "send_message", "read_message", "unknown"),
            fallback = "unknown"
        )
        val route = parsed.stringOrEmpty("route").coerceToAllowed(
            allowed = setOf("direct_mobile_call", "hello_call", "hello_message", "unknown"),
            fallback = "unknown"
        )
        val resolutionStatus = parsed.stringOrEmpty("resolutionStatus").coerceToAllowed(
            allowed = setOf("Exact", "Ambiguous", "Missing", "Unknown"),
            fallback = "Unknown"
        )
        val confidence = parsed.stringOrEmpty("confidence").coerceToAllowed(
            allowed = setOf("High", "Medium", "Low"),
            fallback = "Low"
        )
        return GeminiVoiceReparseResult(
            englishTranscript = parsed.stringOrEmpty("englishTranscript"),
            englishCommand = parsed.stringOrEmpty("englishCommand"),
            intent = intent,
            route = route,
            targetAliasEnglish = parsed.stringOrNull("targetAliasEnglish"),
            resolvedTargetEnglish = parsed.stringOrNull("resolvedTargetEnglish"),
            candidateTargetsEnglish = parsed.stringListOrEmpty("candidateTargetsEnglish"),
            resolutionStatus = resolutionStatus,
            confidence = confidence,
            needsConfirmation = parsed.booleanOrFalse("needsConfirmation"),
            clarificationQuestionEnglish = parsed.stringOrNull("clarificationQuestionEnglish"),
            wouldDoEnglish = parsed.stringOrEmpty("wouldDoEnglish"),
            model = model,
            keyIndex = keyIndex + 1
        )
    }

    private fun String.stripJsonFence(): String {
        return trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun JsonObject.stringOrEmpty(name: String): String {
        return stringOrNull(name).orEmpty()
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return value.asString
    }

    private fun JsonObject.booleanOrFalse(name: String): Boolean {
        val value = get(name) ?: return false
        if (value.isJsonNull) return false
        return value.asBoolean
    }

    private fun JsonObject.stringListOrEmpty(name: String): List<String> {
        val value = get(name) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray
            .mapNotNull { item -> if (item.isJsonNull) null else item.asString }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun String.coerceToAllowed(allowed: Set<String>, fallback: String): String {
        return if (this in allowed) this else fallback
    }

    private companion object {
        val retryableStatusCodes = setOf(403, 429, 500, 502, 503, 504)
    }
}
