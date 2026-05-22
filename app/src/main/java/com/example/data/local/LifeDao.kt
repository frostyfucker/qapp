package com.example.data.local

import androidx.room.*
import com.example.data.model.BucketItem
import com.example.data.model.QuarterPlan
import com.example.data.model.CustomCategory
import com.example.data.model.QuarterlyReview
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeDao {
    // --- Bucket Items ---
    @Query("SELECT * FROM bucket_items ORDER BY createdAt DESC")
    fun getAllBucketItems(): Flow<List<BucketItem>>

    @Query("SELECT * FROM bucket_items WHERE targetQuarter = :quarter")
    fun getBucketItemsForQuarter(quarter: String): Flow<List<BucketItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBucketItem(item: BucketItem)

    @Update
    suspend fun updateBucketItem(item: BucketItem)

    @Query("DELETE FROM bucket_items WHERE id = :id")
    suspend fun deleteBucketItemById(id: Int)

    // --- Quarterly Plans ---
    @Query("SELECT * FROM quarter_plans ORDER BY quarter DESC")
    fun getAllQuarterPlans(): Flow<List<QuarterPlan>>

    @Query("SELECT * FROM quarter_plans WHERE quarter = :quarter LIMIT 1")
    fun getQuarterPlanByQuarter(quarter: String): Flow<QuarterPlan?>

    @Query("SELECT * FROM quarter_plans WHERE quarter = :quarter LIMIT 1")
    suspend fun getQuarterPlanByQuarterOneShot(quarter: String): QuarterPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuarterPlan(plan: QuarterPlan)

    @Delete
    suspend fun deleteQuarterPlan(plan: QuarterPlan)

    // --- Custom Categories ---
    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    fun getAllCustomCategories(): Flow<List<CustomCategory>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomCategory(category: CustomCategory)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCustomCategoryById(id: Int)

    // --- Quarterly Reviews ---
    @Query("SELECT * FROM quarterly_reviews ORDER BY quarter DESC")
    fun getAllQuarterlyReviews(): Flow<List<QuarterlyReview>>

    @Query("SELECT * FROM quarterly_reviews WHERE quarter = :quarter LIMIT 1")
    fun getQuarterlyReviewByQuarter(quarter: String): Flow<QuarterlyReview?>

    @Query("SELECT * FROM quarterly_reviews WHERE quarter = :quarter LIMIT 1")
    suspend fun getQuarterlyReviewByQuarterOneShot(quarter: String): QuarterlyReview?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuarterlyReview(review: QuarterlyReview)

    @Query("DELETE FROM quarterly_reviews WHERE quarter = :quarter")
    suspend fun deleteQuarterlyReviewByQuarter(quarter: String)
}
