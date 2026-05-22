package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.model.BucketItem
import com.example.data.model.QuarterPlan
import com.example.data.model.CustomCategory
import com.example.data.model.QuarterlyReview
import com.example.data.repository.LifeRepository
import com.example.ui.theme.AccentSuccess
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LifeViewModel
import com.example.ui.viewmodel.LifeViewModelFactory
import com.example.ui.viewmodel.MeetingStep

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = LifeRepository(database.lifeDao())
        val viewModel = ViewModelProvider(
            this,
            LifeViewModelFactory(application, repository)
        )[LifeViewModel::class.java]

        setContent {
            // Read theme selection from ViewModel if desired, or keep direct toggle
            var isDarkTheme by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainContainerScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onDarkThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    viewModel: LifeViewModel,
    isDarkTheme: Boolean,
    onDarkThemeToggle: () -> Unit
) {
    val selectedQuarter by viewModel.selectedQuarter.collectAsStateWithLifecycle()
    val bucketItems by viewModel.bucketItems.collectAsStateWithLifecycle()
    val currentPlan by viewModel.currentQuarterPlan.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val quarterlyReviews by viewModel.allQuarterlyReviews.collectAsStateWithLifecycle()
    val meetingStep by viewModel.meetingStep.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) } // 0 = Bucket List, 1 = Focus & OKRs, 2 = AI review

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToComplete by remember { mutableStateOf<BucketItem?>(null) }
    var isManualEditOpen by remember { mutableStateOf(false) }
    var showCategoryMgmtDialog by remember { mutableStateOf(false) }
    var showQuarterReviewWizard by remember { mutableStateOf(false) }

    val quartersList = listOf("2026-Q1", "2026-Q2", "2026-Q3", "2026-Q4", "2027-Q1")
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quarterly Horizon",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        // Dark Mode explicitly requested button
                        IconButton(
                            onClick = onDarkThemeToggle,
                            modifier = Modifier.testTag("dark_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box {
                            FilterPill(
                                label = selectedQuarter,
                                selected = true,
                                icon = Icons.Default.KeyboardArrowDown,
                                onClick = { isDropdownExpanded = true },
                                modifier = Modifier.testTag("quarter_selector_trigger")
                            )
                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false }
                            ) {
                                quartersList.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q, fontWeight = FontWeight.SemiBold) },
                                        onClick = {
                                            viewModel.selectQuarter(q)
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.testTag("app_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Bucket List") },
                    label = { Text("Bucket List", fontWeight = FontWeight.Medium) },
                    modifier = Modifier.testTag("nav_bucket_list")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Focus & OKRs") },
                    label = { Text("Focus & OKRs", fontWeight = FontWeight.Medium) },
                    modifier = Modifier.testTag("nav_focus")
                )
                NavigationBarItem(
                    selected = currentTab == 2 || meetingStep != MeetingStep.OFFLINE,
                    onClick = { currentTab = 2 },
                    icon = {
                        val reviewIcon = if (meetingStep != MeetingStep.OFFLINE) Icons.Default.CheckCircle else Icons.Default.PlayArrow
                        Icon(reviewIcon, contentDescription = "AI Meeting Room")
                    },
                    label = {
                        Text(
                            if (meetingStep != MeetingStep.OFFLINE) "Meeting (Live)" else "AI Review",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("nav_ai_meeting")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = { showCategoryMgmtDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp).testTag("category_mgmt_fab")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Categories Manager")
                    }

                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .testTag("add_item_fab")
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Bucket Item")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> BucketListTabContent(
                    viewModel = viewModel,
                    bucketItems = bucketItems,
                    customCategories = customCategories,
                    selectedQuarter = selectedQuarter,
                    onOpenCompleteRequest = { itemToComplete = it }
                )
                1 -> QuarterFocusTabContent(
                    selectedQuarter = selectedQuarter,
                    plan = currentPlan,
                    quarterlyReviews = quarterlyReviews,
                    onManualEditRequest = { isManualEditOpen = true },
                    onLaunchReviewWizard = { showQuarterReviewWizard = true }
                )
                2 -> AiReviewRoomContent(
                    viewModel = viewModel,
                    selectedQuarter = selectedQuarter
                )
            }

            if (showAddDialog) {
                AddBucketItemDialog(
                    customCategories = customCategories,
                    onDismiss = { showAddDialog = false },
                    onConfirm = { title, desc, catString, qtr, targetDate, progress ->
                        viewModel.addBucketItem(
                            title = title,
                            description = desc,
                            category = catString,
                            targetQuarter = qtr,
                            targetDate = targetDate,
                            progressPercentage = progress
                        )
                        showAddDialog = false
                    },
                    currentSelectedQuarter = selectedQuarter,
                    quartersList = quartersList
                )
            }

            itemToComplete?.let { item ->
                CompleteBucketItemDialog(
                    item = item,
                    onDismiss = { itemToComplete = null },
                    onConfirm = { reflection ->
                        viewModel.completeBucketItem(item, reflection)
                        itemToComplete = null
                    }
                )
            }

            if (isManualEditOpen) {
                ManualEditPlanDialog(
                    quarter = selectedQuarter,
                    plan = currentPlan,
                    onDismiss = { isManualEditOpen = false },
                    onConfirm = { focus1, focus2, focus3, wins, learnings ->
                        viewModel.updateManualQuarterGoals(focus1, focus2, focus3, wins, learnings)
                        isManualEditOpen = false
                    }
                )
            }

            if (showCategoryMgmtDialog) {
                CategoryManagementDialog(
                    categories = customCategories,
                    onDismiss = { showCategoryMgmtDialog = false },
                    onAddCategory = { name ->
                        viewModel.addCustomCategory(name)
                    },
                    onDeleteCategory = { id ->
                        viewModel.deleteCustomCategory(id)
                    }
                )
            }

            if (showQuarterReviewWizard) {
                QuarterlyReviewWizardDialog(
                    quarter = selectedQuarter,
                    currentReview = quarterlyReviews.find { it.quarter == selectedQuarter },
                    onDismiss = { showQuarterReviewWizard = false },
                    onSaveReview = { reflection, newGoals, priorityUp ->
                        viewModel.saveQuarterlyReviewNotes(
                            quarter = selectedQuarter,
                            reflectionAndProgress = reflection,
                            newGoalsNotes = newGoals,
                            upcomingPriorities = priorityUp
                        )
                        showQuarterReviewWizard = false
                    }
                )
            }
        }
    }
}

// ==========================================
// COMPOSABLE VISUAL DECORATIONS
// ==========================================

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    backgroundColor: Color? = null
) {
    val containerColor = when {
        selected && backgroundColor != null -> backgroundColor
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        if (backgroundColor != null) Color.White else MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (icon != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ==========================================
// TAB CONTENT: Bucket List board
// ==========================================

@Composable
fun BucketListTabContent(
    viewModel: LifeViewModel,
    bucketItems: List<BucketItem>,
    customCategories: List<CustomCategory>,
    selectedQuarter: String,
    onOpenCompleteRequest: (BucketItem) -> Unit
) {
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var displayGroupedByCategory by remember { mutableStateOf(false) }

    val filterCategories = listOf("All") + customCategories.map { it.name }
    val filterStatuses = listOf("All", "Planned", "Active", "Completed")

    val filteredItems = bucketItems.filter { item ->
        val matchesCategory = if (selectedCategoryFilter == "All") {
            true
        } else {
            item.category.split(",").map { it.trim() }.contains(selectedCategoryFilter)
        }
        val matchesStatus = when (selectedStatusFilter) {
            "All" -> true
            "Planned" -> item.status == "PLANNED"
            "Active" -> item.status == "ACTIVE"
            "Completed" -> item.status == "COMPLETED"
            else -> true
        }
        matchesCategory && matchesStatus
    }

    val totalCount = filteredItems.size
    val completedCount = filteredItems.count { it.status == "COMPLETED" }
    val progressPercentValue = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Life Alignment Progress",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Turning bucket goals into reality.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "$completedCount/$totalCount Goals Completed",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercentValue)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Viewing Mode:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Group by Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (displayGroupedByCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = displayGroupedByCategory,
                    onCheckedChange = { displayGroupedByCategory = it },
                    modifier = Modifier.testTag("grouping_switch")
                )
            }
        }

        if (!displayGroupedByCategory) {
            Text(
                text = "Categories Filter:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filterCategories.forEach { cat ->
                    val colorHexVal = customCategories.find { it.name == cat }?.let { "#10B981" }
                    val color = colorHexVal?.let { parseColorOrDefault(it) }
                    FilterPill(
                        label = cat,
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        backgroundColor = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Status Filters:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filterStatuses.forEach { stat ->
                    FilterPill(
                        label = stat,
                        selected = selectedStatusFilter == stat,
                        onClick = { selectedStatusFilter = stat }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your Horizon is clear",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add bucket items or define custom categories to start aligning your goals.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            if (displayGroupedByCategory) {
                // Group goals by active categories
                val activeCategoryNames = customCategories.map { it.name }.distinct()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    activeCategoryNames.forEach { catName ->
                        val itemsInCat = filteredItems.filter { item ->
                            item.category.split(",").map { it.trim() }.contains(catName)
                        }

                        if (itemsInCat.isNotEmpty()) {
                            val catColor = parseColorOrDefault("#38BDF8")

                            item(key = "hdr_$catName") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$catName (${itemsInCat.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            items(itemsInCat, key = { "item_${catName}_${it.id}" }) { item ->
                                BucketItemRow(
                                    item = item,
                                    customCategories = customCategories,
                                    viewModel = viewModel,
                                    onActivate = { viewModel.startBucketItem(item) },
                                    onCompleteRequest = { onOpenCompleteRequest(item) },
                                    onUpdateProgress = { progress ->
                                        viewModel.updateBucketItemProgress(item, progress)
                                    },
                                    onDelete = { viewModel.deleteBucketItem(item.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        BucketItemRow(
                            item = item,
                            customCategories = customCategories,
                            viewModel = viewModel,
                            onActivate = { viewModel.startBucketItem(item) },
                            onCompleteRequest = { onOpenCompleteRequest(item) },
                            onUpdateProgress = { progress ->
                                viewModel.updateBucketItemProgress(item, progress)
                            },
                            onDelete = { viewModel.deleteBucketItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BucketItemRow(
    item: BucketItem,
    customCategories: List<CustomCategory>,
    viewModel: LifeViewModel,
    onActivate: () -> Unit,
    onCompleteRequest: () -> Unit,
    onUpdateProgress: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (item.status) {
        "COMPLETED" -> AccentSuccess
        "ACTIVE" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("bucket_goal_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (item.status == "COMPLETED") AccentSuccess.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Split categories nicely
                val categoriesList = item.category.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categoriesList.take(3).forEach { catName ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = catName.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (item.targetQuarter.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = item.targetQuarter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp).testTag("delete_item_btn_${item.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (!item.targetDate.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendar",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Target Date: ${item.targetDate}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when(item.status) {
                            "PLANNED" -> "Planned"
                            "ACTIVE" -> "Active"
                            "COMPLETED" -> "Completed"
                            else -> item.status
                        },
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${item.progressPercentage}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.progressPercentage.toFloat() / 100f)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Refine Progress Level",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Slider(
                    value = item.progressPercentage.toFloat(),
                    onValueChange = { percentValue ->
                        onUpdateProgress(percentValue.toInt())
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth().testTag("percent_slider_${item.id}")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Planned" to 0, "Active" to 50, "Complete" to 100).forEach { (lbl, prg) ->
                        OutlinedButton(
                            onClick = { onUpdateProgress(prg) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (item.status == "COMPLETED" && !item.reflection.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = AccentSuccess.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, AccentSuccess.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Success",
                                tint = AccentSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Growth Reflection Journal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = AccentSuccess
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"${item.reflection}\"",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (item.status == "PLANNED") {
                        Button(
                            onClick = onActivate,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp).testTag("activate_btn_${item.id}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Activate Goal", fontSize = 12.sp)
                        }
                    } else if (item.status == "ACTIVE") {
                        Button(
                            onClick = onCompleteRequest,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess),
                            modifier = Modifier.height(36.dp).testTag("complete_btn_${item.id}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark Completed", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB CONTENT: Quarterly Plan Focus (OKRs)
// ==========================================

@Composable
fun QuarterFocusTabContent(
    selectedQuarter: String,
    plan: QuarterPlan?,
    quarterlyReviews: List<QuarterlyReview>,
    onManualEditRequest: () -> Unit,
    onLaunchReviewWizard: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Check if review completed for active quarter
    val matchedReview = quarterlyReviews.find { it.quarter == selectedQuarter }
    val isReviewCompleted = matchedReview != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Quarter Focus Strategy",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Define and master $selectedQuarter focal objectives. Review plans to ensure progress alignments.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onManualEditRequest,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Okrs", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onLaunchReviewWizard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("review_wizard_btn")
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Prompt Review", fontSize = 11.sp)
                    }
                }
            }
        }

        // Prompt user for quarterly review alerts (beginning of quarter prompt)
        if (!isReviewCompleted) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("review_prompt_banner"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Alert",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quarterly Plan Alignment Pending",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Reflect on accomplishments, explore new goals, and set priorities for upcoming segments.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onLaunchReviewWizard,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Initiate Quarter Review Note", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "THE CORE FOCUS OBJECTIVES (THE BIG 3)",
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        listOf(
            "Primary Goal" to (plan?.focus1 ?: "Define Strategy Focus"),
            "Secondary Goal" to (plan?.focus2 ?: "Execute Alignment Objectives"),
            "Tertiary Goal" to (plan?.focus3 ?: "Sustain Habit Consistency")
        ).forEachIndexed { idx, item ->
            QuarterFocusCard(order = idx + 1, header = item.first, content = item.second)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isReviewCompleted && matchedReview != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PERSISTED QUARTERLY REVIEW NOTES",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("persisted_review_notes_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentSuccess)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stored Reflection & Planning",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Last Review Logged: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(matchedReview.reviewDate)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Previous Segment Progress & Wins Reflection:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(matchedReview.reflectionAndProgress.ifEmpty { "None recorded." }, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

                    Text("New Goals & Opportunity Observations:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(matchedReview.newGoalsNotes.ifEmpty { "None recorded." }, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))

                    Text("Focal Priorities Identified:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(matchedReview.upcomingPriorities.ifEmpty { "None recorded." }, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "RETROSPECTIVE LOGS",
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wins & Milestones",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (plan?.wins.isNullOrEmpty()) "Unlock your milestones or run AI assessment to log achievements." else plan!!.wins,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Challenges & Bottlenecks",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (plan?.learnings.isNullOrEmpty()) "Audit friction points in reviews to optimize alignment pathways." else plan!!.learnings,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun QuarterFocusCard(order: Int, header: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.toString(),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = header,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==========================================
// TAB CONTENT: AI Review Room
// ==========================================

@Composable
fun AiReviewRoomContent(
    viewModel: LifeViewModel,
    selectedQuarter: String
) {
    val step by viewModel.meetingStep.collectAsStateWithLifecycle()
    val messages = viewModel.meetingMessages
    val isLoading by viewModel.isGeminiLoading.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberScrollState()

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollTo(listState.maxValue)
        }
    }

    if (step == MeetingStep.OFFLINE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Launch AI Alignment Assessment",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Gemini acts as your high-momentum life coach. Initiate a strategic session to examine wins, troubleshoot blockers, and refine OKRs based on your active bucket items.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.startQuarterlyMeeting(selectedQuarter) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_meeting_btn")
            ) {
                Text(
                    text = "Begin Diagnostic Session",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Coach Facilitator ($selectedQuarter)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Surface(
                            onClick = { viewModel.endMeetingAndSave() },
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                            modifier = Modifier.testTag("exit_meeting_btn")
                        ) {
                            Text(
                                "Cancel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val stepDescription = when (step) {
                        MeetingStep.WELCOME_AI -> "Reflection Stage: Assess wins"
                        MeetingStep.DISCUSS_WINS -> "Challenge Stage: Review bottlenecks"
                        MeetingStep.DISCUSS_CHALLENGES -> "Vision Stage: Calibrate Big 3"
                        MeetingStep.SET_BIG_THREE -> "Synthesizing alignment feedback report..."
                        MeetingStep.SUMMARY_AI -> "Aignment Report Compiled Successfully."
                        else -> ""
                    }
                    Text(
                        text = stepDescription,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(listState)
                ) {
                    messages.forEach { msg ->
                        ChatBubbleCard(message = msg)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (step == MeetingStep.SUMMARY_AI) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.endMeetingAndSave() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("conclude_meeting_btn")
                        ) {
                            Text("Conclude & Save To OKR Board")
                        }
                    }
                }
            }

            if (step != MeetingStep.SUMMARY_AI) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    when (step) {
                                        MeetingStep.WELCOME_AI -> "List your accomplishments..."
                                        MeetingStep.DISCUSS_WINS -> "Describe your friction points..."
                                        MeetingStep.DISCUSS_CHALLENGES -> "Enter future intention goals..."
                                        else -> "Type response..."
                                    },
                                    fontSize = 13.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text")
                                .padding(vertical = 4.dp),
                            singleLine = false,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (textInput.trim().isNotEmpty()) {
                                    viewModel.sendMeetingMessage(textInput)
                                    textInput = ""
                                }
                            },
                            enabled = textInput.trim().isNotEmpty() && !isLoading,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("chat_send_button"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleCard(message: com.example.ui.viewmodel.ChatMessage) {
    val isAi = message.sender == "AI"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAi) 4.dp else 16.dp,
                bottomEnd = if (isAi) 16.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isAi) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag("chat_bubble_${message.sender}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isAi) "AI Strategic Guide" else "You",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = if (isAi) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// ==========================================
// DATA DIALOGS AND POPUPS
// ==========================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBucketItemDialog(
    customCategories: List<CustomCategory>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String?, Int) -> Unit,
    currentSelectedQuarter: String,
    quartersList: List<String>
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedQuarter by remember { mutableStateOf(currentSelectedQuarter) }
    var selectedCategories = remember { mutableStateListOf<String>() }
    var targetDateText by remember { mutableStateOf("") }
    var progressValueVal by remember { mutableStateOf(0f) }

    var isQuarterDropdownOpen by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add Bucket Goal",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is your goal?") },
                    placeholder = { Text("e.g. Master Jetpack Compose") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_title_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / Segment details") },
                    placeholder = { Text("e.g. Develop complete client architectures.") },
                    modifier = Modifier.fillMaxWidth().testTag("add_desc_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetDateText,
                    onValueChange = { targetDateText = it },
                    label = { Text("Target Completion Date") },
                    placeholder = { Text("e.g. 2026-06-30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_target_date_field")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Assign to Categories (Multi-select)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    customCategories.forEach { cat ->
                        val isChecked = selectedCategories.contains(cat.name)
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                if (isChecked) selectedCategories.remove(cat.name)
                                else selectedCategories.add(cat.name)
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Initial Progress: ${progressValueVal.toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Slider(
                    value = progressValueVal,
                    onValueChange = { progressValueVal = it },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Target Alignment Quarter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { isQuarterDropdownOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("quarter_dropdown_trigger")
                    ) {
                        Text(selectedQuarter)
                    }
                    DropdownMenu(
                        expanded = isQuarterDropdownOpen,
                        onDismissRequest = { isQuarterDropdownOpen = false }
                    ) {
                        val fullQuarterList = quartersList + "Someday"
                        fullQuarterList.forEach { qtr ->
                            DropdownMenuItem(
                                text = { Text(qtr) },
                                onClick = {
                                    selectedQuarter = qtr
                                    isQuarterDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val joinedCats = if (selectedCategories.isEmpty()) "Other" else selectedCategories.joinToString(", ")
                            onConfirm(
                                title,
                                desc,
                                joinedCats,
                                selectedQuarter,
                                targetDateText.ifEmpty { null },
                                progressValueVal.toInt()
                            )
                        },
                        enabled = title.trim().isNotEmpty()
                    ) {
                        Text("Save Goal")
                    }
                }
            }
        }
    }
}

@Composable
fun CompleteBucketItemDialog(
    item: BucketItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var rawReflection by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Celebrate Your Success!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentSuccess
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completing \"${item.title}\" is a key win. Record a strategic reflection to preserve this learning.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = rawReflection,
                    onValueChange = { rawReflection = it },
                    label = { Text("What did completing this teach you?") },
                    placeholder = { Text("e.g. Concentrated energy yields incredible results.") },
                    modifier = Modifier.fillMaxWidth().testTag("reflection_field")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(rawReflection) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess)
                    ) {
                        Text("Save Reflection", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ManualEditPlanDialog(
    quarter: String,
    plan: QuarterPlan?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var foc1 by remember { mutableStateOf(plan?.focus1 ?: "") }
    var foc2 by remember { mutableStateOf(plan?.focus2 ?: "") }
    var foc3 by remember { mutableStateOf(plan?.focus3 ?: "") }
    var wins by remember { mutableStateOf(plan?.wins ?: "") }
    var learnings by remember { mutableStateOf(plan?.learnings ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit $quarter Core OKRs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = foc1,
                    onValueChange = { foc1 = it },
                    label = { Text("Objective Focus 1") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = foc2,
                    onValueChange = { foc2 = it },
                    label = { Text("Objective Focus 2") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = foc3,
                    onValueChange = { foc3 = it },
                    label = { Text("Objective Focus 3") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wins,
                    onValueChange = { wins = it },
                    label = { Text("Manual Wins Summary") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = learnings,
                    onValueChange = { learnings = it },
                    label = { Text("Obstacles & Growth Lessons") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(foc1, foc2, foc3, wins, learnings) }) {
                        Text("Update OKRs")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryManagementDialog(
    categories: List<CustomCategory>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (Int) -> Unit
) {
    var newCatName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxHeight(0.6f)
            ) {
                Text(
                    text = "Manage Categories",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            IconButton(
                                onClick = { onDeleteCategory(cat.id) },
                                modifier = Modifier.size(32.dp).testTag("delete_category_${cat.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text("Create New Category", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Title") },
                    placeholder = { Text("e.g. Career") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_category_field")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Finished")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCatName.trim().isNotEmpty()) {
                                onAddCategory(newCatName)
                                newCatName = ""
                            }
                        },
                        enabled = newCatName.trim().isNotEmpty()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun QuarterlyReviewWizardDialog(
    quarter: String,
    currentReview: QuarterlyReview?,
    onDismiss: () -> Unit,
    onSaveReview: (String, String, String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    var reflectionPrv by remember { mutableStateOf(currentReview?.reflectionAndProgress ?: "") }
    var newGoalsId by remember { mutableStateOf(currentReview?.newGoalsNotes ?: "") }
    var prioritySet by remember { mutableStateOf(currentReview?.upcomingPriorities ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "$quarter Strategic Wizard",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Step $step of 3: " + when(step) {
                        1 -> "Reflect on Previous Quarter"
                        2 -> "Identify Potential New Goals"
                        else -> "Set Strategic Focus Priorities"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                when (step) {
                    1 -> {
                        Text("Reflect: What wins and progress did you discover recently?", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = reflectionPrv,
                            onValueChange = { reflectionPrv = it },
                            placeholder = { Text("e.g. Cleared 2 major learning targets; did meditation consistently.") },
                            modifier = Modifier.fillMaxWidth().height(140.dp).testTag("wizard_step1_field")
                        )
                    }

                    2 -> {
                        Text("Goals: What new bucket list opportunities do you identify?", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newGoalsId,
                            onValueChange = { newGoalsId = it },
                            placeholder = { Text("e.g. Initiate training, explore masterclasses etc.") },
                            modifier = Modifier.fillMaxWidth().height(140.dp).testTag("wizard_step2_field")
                        )
                    }

                    3 -> {
                        Text("Priorities: Set upcoming core objectives for priorities list", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = prioritySet,
                            onValueChange = { prioritySet = it },
                            placeholder = { Text("Line 1: High focus priority\nLine 2: Supporting focal point\nLine 3: Base core habit") },
                            modifier = Modifier.fillMaxWidth().height(140.dp).testTag("wizard_step3_field")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        OutlinedButton(onClick = { step-- }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        if (step < 3) {
                            Button(onClick = { step++ }) {
                                Text("Continue")
                            }
                        } else {
                            Button(
                                onClick = {
                                    onSaveReview(reflectionPrv, newGoalsId, prioritySet)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("Save Note", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COLOR CONVERTER UTILITY
// ==========================================

fun parseColorOrDefault(hexString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexString))
    } catch (e: Exception) {
        Color(0xFF38BDF8)
    }
}
