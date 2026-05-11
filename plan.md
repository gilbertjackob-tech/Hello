Scope:
Only modify apps/android.
Keep everything isolated inside:
apps/android/app/src/main/java/com/glassbox/hello/demo/voice/

Do not execute real actions.
Do not call backend.
Do not start real calls.
Only parse, clarify, rate, and log.

Current file:
VoiceAssistantDemoScreen.kt

Goal:
Upgrade the current voice demo into Voice Skill Learning Demo v1.

==================================================
PART 1 — FIX RECOGNITION TIMING
==================================================

Current code uses SESSION_TIMEOUT_MS = 10000L but recognizer intent has:
EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 1000

Fix:
- SESSION_TIMEOUT_MS = 10000L
- EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 10000L
- EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 4000L
- EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 3000L or 4000L
- EXTRA_MAX_RESULTS = 5

Log these:
Recognition language = bn-BD
Timeout = 10000ms
Max silence = 4000ms

==================================================
PART 2 — USE ALL SPEECH CANDIDATES
==================================================

Current bestSpeechResult() returns only first result.

Change it to return:
List<String>

Parse all candidates:
- normalize each
- parse each
- score each
- choose best

Show in UI:
Candidates:
1. ...
2. ...
3. ...

Chosen transcript:
"..."

Scoring:
High = 3
Medium = 2
Low = 1

Prefer:
- non-unknown
- target resolved
- exact contact over ambiguous
- route detected over default

==================================================
PART 3 — CONTACT RESOLUTION WITH AMBIGUITY
==================================================

Replace simple contactAliases pair list with structured model.

Create:
data class DemoContactTarget(
    val canonicalGroup: String,
    val targetName: String,
    val aliases: List<String>
)

Targets:
1. Hasnat PC
canonicalGroup = "Hasnat"
aliases:
- "হাসনাত পিসি"
- "hasnat pc"
- "pc"

2. Hasnat IOS
canonicalGroup = "Hasnat"
aliases:
- "হাসনাত আইওএস"
- "হাসনাত ios"
- "hasnat ios"
- "ios"

3. Nowshin
canonicalGroup = "Nowshin"
aliases:
- "নওশিন"
- "নওশীন"
- "nowshin"

4. Bihi
canonicalGroup = "Bihi"
aliases:
- "বিহি"
- "bihi"

Also support group alias:
"হাসনাত" / "hasnat" should return multiple candidates:
- Hasnat PC
- Hasnat IOS

Resolution states:
- Exact
- Ambiguous
- NotFound

If command says:
"হাসনাত কে কল দাও"

Output:
resolution = Ambiguous
candidates = ["Hasnat PC", "Hasnat IOS"]
wouldDo = "Would ask which Hasnat to call"

If command says:
"হাসনাত পিসি কে কল দাও"

Output:
resolution = Exact
resolvedTarget = "Hasnat PC"

==================================================
PART 4 — UPDATE PARSED MODEL
==================================================

Replace/extend ParsedVoiceCommand with:

data class ParsedVoiceCommand(
    val rawTranscript: String,
    val chosenTranscript: String,
    val candidates: List<String>,
    val normalizedTranscript: String,
    val skillMatched: String,
    val intent: String,
    val route: String,
    val targetAlias: String,
    val resolvedTarget: String?,
    val resolutionStatus: String,
    val resolutionCandidates: List<String>,
    val confidence: DemoConfidence,
    val needsConfirmation: Boolean,
    val wouldDo: String,
    val reason: String? = null
)

==================================================
PART 5 — CALL PARSER RULES
==================================================

Route:
- direct/direct call/ডিরেক্ট/phone/mobile/সরাসরি => direct_mobile_call
- hello/হেলো/hello app/কল অন হেলো => hello_call
- if call intent but no route => hello_call default

Intent:
- if contains call/কল => start_call

Examples:

"বিহি কে direct কল দাও"
=> start_call, direct_mobile_call, Bihi, High

"নওশীন কে ডিরেক্ট কল দাও"
=> start_call, direct_mobile_call, Nowshin, High

"নওশিন কে hello app দিয়ে কল দাও"
=> start_call, hello_call, Nowshin, High

"হাসনাত কে কল দাও"
=> start_call, hello_call, Ambiguous, candidates Hasnat PC/Hasnat IOS, Medium

"হাসনাত পিসি কে কল দাও"
=> start_call, hello_call, Hasnat PC, High

==================================================
PART 6 — CLARIFICATION BUTTONS
==================================================

If resolutionStatus = Ambiguous:
Show candidate buttons:
- Hasnat PC
- Hasnat IOS

When user taps one:
- update parser result resolvedTarget
- resolutionStatus = ExactByClarification
- log:
User clarified target = Hasnat PC
- then show rating prompt

Still no real action.

==================================================
PART 7 — STAR RATING FEEDBACK
==================================================

After each parsed command or clarification, show:

Parsing ঠিক ছিল?
★ ★ ★ ★ ★

Rating behavior:
5 = strong positive
4 = positive
3 = neutral
2 = negative
1 = wrong

Create:
data class VoiceParseFeedback(
    val rawTranscript: String,
    val normalizedTranscript: String,
    val intent: String,
    val route: String,
    val targetAlias: String,
    val resolvedTarget: String?,
    val resolutionStatus: String,
    val rating: Int,
    val timestamp: Long
)

Store feedback in local Compose state/memory only for now.

Log:
User feedback = 5 stars
Vote updated locally for route + target alias

Do not persist to backend yet.

==================================================
PART 8 — UI RESULT CARD
==================================================

Update DemoParserLogPanel to show:

- Raw transcript
- Candidates
- Chosen transcript
- Normalized
- Intent
- Skill matched
- Route
- Target alias
- Resolved target
- Candidates if ambiguous
- Resolution status
- Confidence
- Needs confirmation
- Would do
- Executed = No real action executed in demo mode

If ambiguous, show candidate buttons below result card.

If parsed, show rating stars below result card.

==================================================
PART 9 — FUTURE SERVER SYNC TODO
==================================================

Add TODO only, no backend call:

Future endpoint:
POST /hello/api/voice/feedback

Payload:
rawTranscript
normalizedTranscript
intent
route
targetAlias
resolvedTarget
resolutionCandidates
rating
timestamp

Future behavior:
- 5/4 star increases vote
- 3 neutral
- 1/2 lowers vote
- zero/negative vote disables phrase
- top voted mapping becomes accepted globally

==================================================
PART 10 — NO REAL ACTION
==================================================

Do not:
- start phone call
- start Hello call
- open chat
- call backend
- set alarm
- open map

Only parse, clarify, rate, and log.

==================================================
BUILD
==================================================

Run:
cd apps/android
.\gradlew.bat clean build

Return:
- files changed
- timing fix
- candidate parsing
- ambiguity handling
- rating behavior
- sample logs
- build result