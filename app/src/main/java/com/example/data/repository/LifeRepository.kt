package com.example.data.repository

import com.example.data.local.LifeDao
import com.example.data.model.BucketItem
import com.example.data.model.QuarterPlan
import com.example.data.model.CustomCategory
import com.example.data.model.QuarterlyReview
import kotlinx.coroutines.flow.Flow

class LifeRepository(private val lifeDao: LifeDao) {
    // Bucket Items
    val allBucketItems: Flow<List<BucketItem>> = lifeDao.getAllBucketItems()

    fun getBucketItemsForQuarter(quarter: String): Flow<List<BucketItem>> =
        lifeDao.getBucketItemsForQuarter(quarter)

    suspend fun insertBucketItem(item: BucketItem) = lifeDao.insertBucketItem(item)

    suspend fun updateBucketItem(item: BucketItem) = lifeDao.updateBucketItem(item)

    suspend fun deleteBucketItemById(id: Int) = lifeDao.deleteBucketItemById(id)

    // Quarterly Plans
    val allQuarterPlans: Flow<List<QuarterPlan>> = lifeDao.getAllQuarterPlans()

    fun getQuarterPlan(quarter: String): Flow<QuarterPlan?> =
        lifeDao.getQuarterPlanByQuarter(quarter)

    suspend fun getQuarterPlanOneShot(quarter: String): QuarterPlan? =
        lifeDao.getQuarterPlanByQuarterOneShot(quarter)

    suspend fun insertQuarterPlan(plan: QuarterPlan) = lifeDao.insertQuarterPlan(plan)

    suspend fun deleteQuarterPlan(plan: QuarterPlan) = lifeDao.deleteQuarterPlan(plan)

    // Custom Categories
    val allCustomCategories: Flow<List<CustomCategory>> = lifeDao.getAllCustomCategories()

    suspend fun insertCustomCategory(category: CustomCategory) =
        lifeDao.insertCustomCategory(category)

    suspend fun deleteCustomCategoryById(id: Int) =
        lifeDao.deleteCustomCategoryById(id)

    // Quarterly Reviews
    val allQuarterlyReviews: Flow<List<QuarterlyReview>> = lifeDao.getAllQuarterlyReviews()

    fun getQuarterlyReview(quarter: String): Flow<QuarterlyReview?> =
        lifeDao.getQuarterlyReviewByQuarter(quarter)

    suspend fun getQuarterlyReviewOneShot(quarter: String): QuarterlyReview? =
        lifeDao.getQuarterlyReviewByQuarterOneShot(quarter)

    suspend fun insertQuarterlyReview(review: QuarterlyReview) =
        lifeDao.insertQuarterlyReview(review)

    suspend fun deleteQuarterlyReviewByQuarter(quarter: String) =
        lifeDao.deleteQuarterlyReviewByQuarter(quarter)
}
