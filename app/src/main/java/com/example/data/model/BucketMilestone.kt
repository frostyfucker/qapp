package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bucket_milestones")
data class BucketMilestone(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bucketItemId: Int,
    val title: String,
    val isCompleted: Boolean = false
)
