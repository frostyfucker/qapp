package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quarterly_reviews")
data class QuarterlyReview(
    @PrimaryKey val quarter: String, // e.g., "2026-Q2"
    val reflectionAndProgress: String = "", // Reflection/wins from previous quarter
    val newGoalsNotes: String = "", // Identified new goals/notes
    val upcomingPriorities: String = "", // Strategic priorities notes
    val reviewDate: Long = System.currentTimeMillis()
)
