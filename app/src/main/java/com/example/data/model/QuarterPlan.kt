package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quarter_plans")
data class QuarterPlan(
    @PrimaryKey val quarter: String, // e.g. "2026-Q2"
    val focus1: String = "",
    val focus2: String = "",
    val focus3: String = "",
    val wins: String = "",
    val learnings: String = "",
    val aiSummaryAndFeedback: String = "",
    val meetingLoggedAt: Long? = null,
    val isMeetingCompleted: Boolean = false,
    
    // Quarterly review notes and prompts
    val reflectionPrevQuarter: String = "",
    val newGoalsIdentified: String = "",
    val prioritiesUpcomingQuarter: String = "",
    val storedReviewNotes: String = "",
    val isReviewCompleted: Boolean = false,
    val reviewLoggedAt: Long? = null
)
