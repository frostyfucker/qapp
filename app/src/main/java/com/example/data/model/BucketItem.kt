package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bucket_items")
data class BucketItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String, // Comma-separated list of categories, e.g., "Travel, Learning"
    val targetQuarter: String, // "Someday" or specific e.g., "2026-Q2"
    val status: String, // "PLANNED", "ACTIVE", "COMPLETED"
    val completedAt: Long? = null,
    val reflection: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val targetDate: String? = null, // Target completion date, e.g., "2026-06-30" or blank
    val progressPercentage: Int = 0 // Progress tracking percentage from 0 to 100
)
