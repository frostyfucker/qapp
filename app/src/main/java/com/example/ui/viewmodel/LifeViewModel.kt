package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.local.AppDatabase
import com.example.data.model.BucketItem
import com.example.data.model.QuarterPlan
import com.example.data.model.CustomCategory
import com.example.data.model.QuarterlyReview
import com.example.data.repository.LifeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MeetingStep {
    OFFLINE,
    WELCOME_AI,
    DISCUSS_WINS,
    DISCUSS_CHALLENGES,
    SET_BIG_THREE,
    SUMMARY_AI
}

data class ChatMessage(
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LifeViewModel(
    application: Application,
    private val repository: LifeRepository
) : AndroidViewModel(application) {

    // Selected View / State Tracker
    private val _selectedQuarter = MutableStateFlow("2026-Q2")
    val selectedQuarter: StateFlow<String> = _selectedQuarter.asStateFlow()

    // Dynamic User Theme Selection state (System, Light, Dark)
    private val _themeMode = MutableStateFlow("System")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Database flow bindings
    val bucketItems: StateFlow<List<BucketItem>> = repository.allBucketItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuarterPlans: StateFlow<List<QuarterPlan>> = repository.allQuarterPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategories: StateFlow<List<CustomCategory>> = repository.allCustomCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuarterlyReviews: StateFlow<List<QuarterlyReview>> = repository.allQuarterlyReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Prepopulate Custom Categories & Mock Data on first runs
            try {
                customCategories.first { true }.let { cats ->
                    if (cats.isEmpty()) {
                        val baselineCategories = listOf("Travel", "Learning", "Health", "Career", "Finance", "Social", "Other")
                        baselineCategories.forEach { name ->
                            repository.insertCustomCategory(CustomCategory(name = name))
                        }
                    }
                }

                bucketItems.first { true }.let { items ->
                    if (items.isEmpty()) {
                        repository.insertBucketItem(BucketItem(title = "Skydive in Switzerland", description = "Experience terminal velocity over the majestic Swiss Alps.", category = "Travel", targetQuarter = "2026-Q3", status = "PLANNED", targetDate = "2026-08-15", progressPercentage = 0))
                        repository.insertBucketItem(BucketItem(title = "Master Jetpack Compose", description = "Build premium, native Material 3 user interfaces independently.", category = "Learning, Career", targetQuarter = "2026-Q2", status = "ACTIVE", targetDate = "2026-06-25", progressPercentage = 65))
                        repository.insertBucketItem(BucketItem(title = "Read 50 Premium Books", description = "A yearly reading marathon across classic literature, design, and history.", category = "Learning", targetQuarter = "Someday", status = "PLANNED", targetDate = "", progressPercentage = 12))
                        repository.insertBucketItem(BucketItem(title = "Save Mortgage Downpayment", description = "Establish a structured monthly savings target for home equity.", category = "Finance", targetQuarter = "2026-Q4", status = "ACTIVE", targetDate = "2026-12-01", progressPercentage = 40))
                        repository.insertBucketItem(BucketItem(title = "Daily Morning Meditation", description = "A mindful 15-minute breath-anchor before launching daily coding tasks.", category = "Health", targetQuarter = "2026-Q2", status = "COMPLETED", completedAt = System.currentTimeMillis() - 172800000, reflection = "It completely anchored my morning routine! Focus level rose noticeably, and my screen anxiety decreased.", targetDate = "2026-05-10", progressPercentage = 100))
                        repository.insertBucketItem(BucketItem(title = "Run Route under 50m", description = "Shave off 5 minutes from standard 10km pace to reach endurance objectives.", category = "Health", targetQuarter = "2026-Q2", status = "PLANNED", targetDate = "2026-06-10", progressPercentage = 0))
                    }
                }

                allQuarterPlans.first { true }.let { plans ->
                    if (plans.isEmpty()) {
                        repository.insertQuarterPlan(QuarterPlan(
                            quarter = "2026-Q1",
                            focus1 = "Set Daily Health Habits",
                            focus2 = "Complete basic financial audit",
                            focus3 = "Run cardiovascular track weekly",
                            wins = "Established morning hydration, completed audit, cleared initial card balance.",
                            learnings = "Attempting to track 12 variables in parrallel resulted in mental exhaustion. Must practice strict containment.",
                            aiSummaryAndFeedback = "### Q1 Strategic Alignment Summary\n\n- **Validated Achievements**: Clearing debt establishes absolute baseline peace of mind. Excellent momentum.\n- **Course Corrections**: Adopt the 'Rule of Three' for Q2. Over-scheduling triggers paralysis. Containment is clarity.",
                            meetingLoggedAt = System.currentTimeMillis() - 600000000,
                            isMeetingCompleted = true
                        ))
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback fail
            }
        }
    }

    // Quarter Plan derived specifically for the selected quarter
    val currentQuarterPlan: StateFlow<QuarterPlan?> = _selectedQuarter
        .flatMapLatest { q -> repository.getQuarterPlan(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Interactive Meeting State Machine
    private val _meetingStep = MutableStateFlow(MeetingStep.OFFLINE)
    val meetingStep: StateFlow<MeetingStep> = _meetingStep.asStateFlow()

    val meetingMessages = mutableStateListOf<ChatMessage>()

    private val _isGeminiLoading = MutableStateFlow(false)
    val isGeminiLoading: StateFlow<Boolean> = _isGeminiLoading.asStateFlow()

    // Temporary storage during a meeting
    private var tempWins = ""
    private var tempChallenges = ""
    private var tempFocus1 = ""
    private var tempFocus2 = ""
    private var tempFocus3 = ""

    fun selectQuarter(quarter: String) {
        _selectedQuarter.value = quarter
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    // --- Custom Categories Actions ---
    fun addCustomCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.insertCustomCategory(CustomCategory(name = trimmed))
        }
    }

    fun deleteCustomCategory(id: Int) {
        viewModelScope.launch {
            repository.deleteCustomCategoryById(id)
        }
    }

    // --- Quarterly Review Actions ---
    fun getQuarterlyReviewStream(quarter: String): Flow<QuarterlyReview?> {
        return repository.getQuarterlyReview(quarter)
    }

    fun saveQuarterlyReviewNotes(
        quarter: String,
        reflectionAndProgress: String,
        newGoalsNotes: String,
        upcomingPriorities: String
    ) {
        viewModelScope.launch {
            val review = QuarterlyReview(
                quarter = quarter,
                reflectionAndProgress = reflectionAndProgress,
                newGoalsNotes = newGoalsNotes,
                upcomingPriorities = upcomingPriorities,
                reviewDate = System.currentTimeMillis()
            )
            repository.insertQuarterlyReview(review)

            // Also keep standard QuarterPlan fully synchronized!
            val lines = upcomingPriorities.lines()
                .map { it.replace(Regex("^[\\d.\\-*+ ]+"), "").trim() }
                .filter { it.isNotEmpty() }

            val f1 = lines.getOrNull(0) ?: "Prioritize Key Intentions"
            val f2 = lines.getOrNull(1) ?: "Build Habit Streaks"
            val f3 = lines.getOrNull(2) ?: "Reflect with AI Coach"

            val existingPlan = repository.getQuarterPlanOneShot(quarter) ?: QuarterPlan(quarter = quarter)
            val updatedPlan = existingPlan.copy(
                focus1 = f1,
                focus2 = f2,
                focus3 = f3,
                wins = reflectionAndProgress,
                learnings = "Guided Quarterly Review: $newGoalsNotes",
                meetingLoggedAt = System.currentTimeMillis()
            )
            repository.insertQuarterPlan(updatedPlan)
        }
    }

    // --- Bucket List Actions ---
    fun addBucketItem(
        title: String,
        description: String,
        category: String,
        targetQuarter: String,
        targetDate: String? = null,
        progressPercentage: Int = 0
    ) {
        viewModelScope.launch {
            val calculatedStatus = when {
                progressPercentage >= 100 -> "COMPLETED"
                progressPercentage > 0 -> "ACTIVE"
                else -> "PLANNED"
            }
            val newItem = BucketItem(
                title = title,
                description = description,
                category = category,
                targetQuarter = targetQuarter,
                status = calculatedStatus,
                targetDate = targetDate,
                progressPercentage = progressPercentage,
                completedAt = if (calculatedStatus == "COMPLETED") System.currentTimeMillis() else null
            )
            repository.insertBucketItem(newItem)
        }
    }

    fun updateBucketItem(
        item: BucketItem,
        title: String,
        description: String,
        category: String,
        targetQuarter: String,
        targetDate: String?,
        progressPercentage: Int,
        reflection: String? = null
    ) {
        viewModelScope.launch {
            val calculatedStatus = when {
                progressPercentage >= 100 -> "COMPLETED"
                progressPercentage > 0 -> "ACTIVE"
                else -> "PLANNED"
            }
            val updated = item.copy(
                title = title,
                description = description,
                category = category,
                targetQuarter = targetQuarter,
                status = calculatedStatus,
                targetDate = targetDate,
                progressPercentage = progressPercentage,
                reflection = reflection,
                completedAt = if (calculatedStatus == "COMPLETED") (item.completedAt ?: System.currentTimeMillis()) else null
            )
            repository.updateBucketItem(updated)
        }
    }

    fun startBucketItem(item: BucketItem) {
        viewModelScope.launch {
            repository.updateBucketItem(item.copy(status = "ACTIVE", progressPercentage = maxOf(item.progressPercentage, 10)))
        }
    }

    fun updateBucketItemProgress(item: BucketItem, percentage: Int) {
        viewModelScope.launch {
            val nextStatus = when {
                percentage >= 100 -> "COMPLETED"
                percentage > 0 -> "ACTIVE"
                else -> "PLANNED"
            }
            repository.updateBucketItem(item.copy(
                progressPercentage = percentage,
                status = nextStatus,
                completedAt = if (nextStatus == "COMPLETED") (item.completedAt ?: System.currentTimeMillis()) else null
            ))
        }
    }

    fun completeBucketItem(item: BucketItem, reflection: String) {
        viewModelScope.launch {
            repository.updateBucketItem(
                item.copy(
                    status = "COMPLETED",
                    progressPercentage = 100,
                    completedAt = System.currentTimeMillis(),
                    reflection = reflection
                )
            )
        }
    }

    fun deleteBucketItem(id: Int) {
        viewModelScope.launch {
            repository.deleteBucketItemById(id)
        }
    }

    // --- Quarterly Meeting State Machine Controller ---
    fun startQuarterlyMeeting(quarter: String) {
        viewModelScope.launch {
            _meetingStep.value = MeetingStep.WELCOME_AI
            meetingMessages.clear()
            tempWins = ""
            tempChallenges = ""
            tempFocus1 = ""
            tempFocus2 = ""
            tempFocus3 = ""

            _isGeminiLoading.value = true

            // Gather active bucket list items to make the context hyper-personalized!
            val currentBucketGoals = bucketItems.value.filter {
                it.status == "ACTIVE" || it.targetQuarter == quarter
            }
            val completedGoals = bucketItems.value.filter {
                it.status == "COMPLETED" && it.targetQuarter == quarter
            }

            val goalsContext = buildString {
                append("Active/Quarterly Goals: ")
                if (currentBucketGoals.isEmpty()) append("None.") else {
                    currentBucketGoals.forEach { append("[${it.category}] ${it.title} (${it.status}); ") }
                }
                append("\nCompleted goals this quarter: ")
                if (completedGoals.isEmpty()) append("None.") else {
                    completedGoals.forEach { append("${it.title}; ") }
                }
            }

            val systemPrompt = """
                You are a senior executive performance coach and intuitive AI Life Architect.
                You are conducting a formal 1-on-1 Quarterly Life Alignment and Goal-Setting Meeting.
                Keep your tone extremely professional, inspiring, warm, and highly structured.
                Avoid flowery conversational filler. Address the user directly as a co-creator of their life.
            """.trimIndent()

            val welcomePrompt = """
                Welcome the user to their Quarterly Alignment Review Meeting for $quarter.
                Here is their current bucket list and goal context for this quarter:
                $goalsContext

                Please trigger the meeting by:
                1. Stating the objective of this meeting (review past achievements, address blocks, set the Big 3 focal goals for next quarter).
                2. Genuinely validating them based on any completed items.
                3. Asking them: "Let's begin with Step 1: Wins. What were your most meaningful achievements, wins, or positive moments during this past quarter?"
            """.trimIndent()

            val aiResponse = GeminiClient.generateSpeechOrText(welcomePrompt, systemPrompt)
            _isGeminiLoading.value = false

            meetingMessages.add(ChatMessage("AI", aiResponse))
        }
    }

    fun sendMeetingMessage(userText: String) {
        if (userText.trim().isEmpty()) return
        meetingMessages.add(ChatMessage("USER", userText))

        viewModelScope.launch {
            val step = _meetingStep.value
            _isGeminiLoading.value = true

            val systemPrompt = """
                You are a senior executive life strategist and quarterly facilitator.
                Carry out the current step of the alignment meeting. Keep responses concise, high-potency, and encouraging.
            """.trimIndent()

            when (step) {
                MeetingStep.WELCOME_AI -> {
                    // User just inputted wins
                    tempWins = userText
                    _meetingStep.value = MeetingStep.DISCUSS_WINS

                    val prompt = """
                        The user just shared their wins:
                        "$userText"

                        Acknowledge their accomplishments with deep insights (highlighting personal growth or strategic value). 
                        Then, transition directly to the next stage: Challenges & Lessons.
                        Ask them: "Now, let's look at Step 2: Challenges & Lessons. What bottlenecks, friction, or challenges slowed you down, and what major lessons did you learn from them?"
                    """.trimIndent()

                    val aiResponse = GeminiClient.generateSpeechOrText(prompt, systemPrompt)
                    meetingMessages.add(ChatMessage("AI", aiResponse))
                }

                MeetingStep.DISCUSS_WINS -> {
                    // User just inputted challenges
                    tempChallenges = userText
                    _meetingStep.value = MeetingStep.DISCUSS_CHALLENGES

                    val prompt = """
                        The user just shared their challenges and learnings:
                        "$userText"

                        Validate their experiences, highlighting that setbacks are essential data points for alignment. 
                        Now transition to Step 3: Setting the Big Three Focus Areas for the upcoming quarter.
                        Suggest 1-2 directions based on their unresolved bucket list items:
                        "${bucketItems.value.filter { it.status == "PLANNED" }.take(3).map { it.title }}"
                        Ask them: "What are your Big Three Focus areas or primary objectives for this next quarter? Please specify them clearly, one by one."
                    """.trimIndent()

                    val aiResponse = GeminiClient.generateSpeechOrText(prompt, systemPrompt)
                    meetingMessages.add(ChatMessage("AI", aiResponse))
                }

                MeetingStep.DISCUSS_CHALLENGES -> {
                    // User just input next quarter's goals
                    val rawGoals = userText
                    _meetingStep.value = MeetingStep.SET_BIG_THREE

                    val finalPrompt = """
                        We have completed the reviews.
                        Wins of this Quarter: $tempWins
                        Challenges: $tempChallenges
                        Proposed Next Quarter Objectives: $rawGoals

                        Please generate a professional, polished and structured "Quarterly Alignment Report".
                        Start with a motivating headline.
                        Include nicely structured sections:
                        - Win Synthesis
                        - Growth Lessons
                        - The Big Three Objectives for Next Quarter (Refined with clear, actionable advice)
                        - Strategic Action Plan (Immediate next steps to build momentum)

                        Conclude with an empowering executive statement.
                    """.trimIndent()

                    val aiResponse = GeminiClient.generateSpeechOrText(finalPrompt, systemPrompt)
                    meetingMessages.add(ChatMessage("AI", aiResponse))

                    extractAndSaveQuarterPlan(aiResponse, rawGoals)
                    _meetingStep.value = MeetingStep.SUMMARY_AI
                }

                else -> {}
            }
            _isGeminiLoading.value = false
        }
    }

    private suspend fun extractAndSaveQuarterPlan(report: String, rawGoals: String) {
        val goalsList = rawGoals.lines()
            .map { it.replace(Regex("^[\\d.\\-*+ ]+"), "").trim() }
            .filter { it.isNotEmpty() }

        tempFocus1 = goalsList.getOrNull(0) ?: "Define Core Habits"
        tempFocus2 = goalsList.getOrNull(1) ?: "Execute Active Bucket Items"
        tempFocus3 = goalsList.getOrNull(2) ?: "Maintain Quarterly Reviews"

        val updatedPlan = QuarterPlan(
            quarter = _selectedQuarter.value,
            focus1 = tempFocus1,
            focus2 = tempFocus2,
            focus3 = tempFocus3,
            wins = tempWins,
            learnings = tempChallenges,
            aiSummaryAndFeedback = report,
            meetingLoggedAt = System.currentTimeMillis(),
            isMeetingCompleted = true
        )

        repository.insertQuarterPlan(updatedPlan)
    }

    fun endMeetingAndSave() {
        _meetingStep.value = MeetingStep.OFFLINE
        meetingMessages.clear()
    }

    fun updateManualQuarterGoals(focus1: String, focus2: String, focus3: String, wins: String, learnings: String) {
        viewModelScope.launch {
            val existing = repository.getQuarterPlanOneShot(_selectedQuarter.value) ?: QuarterPlan(quarter = _selectedQuarter.value)
            val updated = existing.copy(
                focus1 = focus1,
                focus2 = focus2,
                focus3 = focus3,
                wins = wins,
                learnings = learnings,
                meetingLoggedAt = System.currentTimeMillis()
            )
            repository.insertQuarterPlan(updated)
        }
    }
}

class LifeViewModelFactory(
    private val application: Application,
    private val repository: LifeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LifeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LifeViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
