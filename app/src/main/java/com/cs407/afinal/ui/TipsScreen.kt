package com.cs407.afinal.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

data class SleepTip(
    val id: String,
    val title: String,
    val summary: String,
    val details: String,
    val icon: ImageVector,
    val category: TipCategory
)

enum class TipCategory(
    val displayName: String,
    val icon: ImageVector,
    val color: Color
) {
    ALL("All", Icons.Default.Lightbulb, Color(0xFF5C6BC0)),
    BEDTIME("Bedtime", Icons.Default.Bedtime, Color(0xFF7C4DFF)),
    ENVIRONMENT("Environment", Icons.Default.Hotel, Color(0xFF26A69A)),
    LIFESTYLE("Lifestyle", Icons.Default.FitnessCenter, Color(0xFF66BB6A)),
    NUTRITION("Nutrition", Icons.Default.Fastfood, Color(0xFFFF7043)),
    RELAXATION("Relaxation", Icons.Default.SelfImprovement, Color(0xFFEC407A))
}

val sleepTips = listOf(
    SleepTip(
        id = "consistent_schedule",
        title = "Keep a Consistent Sleep Schedule",
        summary = "Go to bed and wake up at the same time every day",
        details = "Your body has an internal clock called the circadian rhythm. Going to bed and waking up at consistent times helps regulate this clock, making it easier to fall asleep and wake up naturally. Try to maintain your schedule even on weekends - sleeping in can disrupt your rhythm and make Monday mornings harder.",
        icon = Icons.Default.Schedule,
        category = TipCategory.BEDTIME
    ),
    SleepTip(
        id = "dark_room",
        title = "Sleep in Complete Darkness",
        summary = "Use blackout curtains or a sleep mask",
        details = "Light exposure suppresses melatonin production, the hormone that makes you sleepy. Even small amounts of light from electronics or street lamps can interfere with your sleep quality. Use blackout curtains, cover LED lights on devices, and consider a comfortable sleep mask for optimal darkness.",
        icon = Icons.Default.NightsStay,
        category = TipCategory.ENVIRONMENT
    ),
    SleepTip(
        id = "cool_temperature",
        title = "Keep Your Room Cool",
        summary = "Optimal sleep temperature is 60-67°F (15-19°C)",
        details = "Your body temperature naturally drops when you sleep. A cool room helps facilitate this process and promotes deeper sleep. If you wake up sweating or shivering, adjust your thermostat or bedding. Many people sleep best around 65°F (18°C).",
        icon = Icons.Default.Thermostat,
        category = TipCategory.ENVIRONMENT
    ),
    SleepTip(
        id = "no_screens",
        title = "Avoid Screens Before Bed",
        summary = "Put away devices 1-2 hours before sleep",
        details = "Blue light from phones, tablets, and computers tricks your brain into thinking it's daytime. This suppresses melatonin and makes it harder to fall asleep. Try reading a physical book, listening to calming music, or practicing relaxation techniques instead of scrolling before bed.",
        icon = Icons.Default.PhoneAndroid,
        category = TipCategory.BEDTIME
    ),
    SleepTip(
        id = "limit_caffeine",
        title = "Limit Caffeine After Noon",
        summary = "Caffeine stays in your system for 6-8 hours",
        details = "That afternoon coffee might be affecting your sleep more than you realize. Caffeine has a half-life of about 5-6 hours, meaning half of it is still in your system hours later. Try switching to decaf or herbal tea after lunch, and avoid energy drinks in the evening.",
        icon = Icons.Default.LocalCafe,
        category = TipCategory.NUTRITION
    ),
    SleepTip(
        id = "exercise_timing",
        title = "Exercise Earlier in the Day",
        summary = "Finish workouts at least 3 hours before bed",
        details = "Regular exercise improves sleep quality, but timing matters. Working out raises your body temperature and releases stimulating hormones. Give your body at least 3 hours to wind down after intense exercise. Morning or afternoon workouts are ideal for promoting better sleep.",
        icon = Icons.Default.FitnessCenter,
        category = TipCategory.LIFESTYLE
    ),
    SleepTip(
        id = "wind_down",
        title = "Create a Wind-Down Routine",
        summary = "Spend 30-60 minutes relaxing before bed",
        details = "A consistent pre-sleep routine signals to your body that it's time to sleep. This could include taking a warm bath, reading, gentle stretching, or meditation. Avoid stimulating activities, arguments, or stressful work. Your routine should be calming and enjoyable.",
        icon = Icons.Default.SelfImprovement,
        category = TipCategory.RELAXATION
    ),
    SleepTip(
        id = "no_late_meals",
        title = "Avoid Heavy Meals Before Bed",
        summary = "Finish eating 2-3 hours before sleep",
        details = "Eating a large meal close to bedtime can cause discomfort and indigestion that interferes with sleep. Your digestive system slows down during sleep, so late meals can sit in your stomach longer. If you're hungry, opt for a light snack like a banana or small handful of nuts.",
        icon = Icons.Default.Fastfood,
        category = TipCategory.NUTRITION
    ),
    SleepTip(
        id = "morning_light",
        title = "Get Morning Sunlight",
        summary = "Expose yourself to bright light upon waking",
        details = "Morning light exposure helps reset your circadian rhythm and improves nighttime sleep. Try to get 10-30 minutes of natural sunlight within an hour of waking. If natural light isn't available, consider a light therapy lamp. This is especially important in winter months.",
        icon = Icons.Default.WbSunny,
        category = TipCategory.LIFESTYLE
    ),
    SleepTip(
        id = "comfortable_mattress",
        title = "Invest in Quality Bedding",
        summary = "Your mattress and pillows affect sleep quality",
        details = "You spend about a third of your life in bed, so comfort matters. A mattress that's too old (over 7-10 years) or doesn't support your body properly can cause pain and poor sleep. Choose pillows that keep your neck aligned with your spine. Consider your sleep position when selecting bedding.",
        icon = Icons.Default.Hotel,
        category = TipCategory.ENVIRONMENT
    ),
    SleepTip(
        id = "manage_stress",
        title = "Manage Daily Stress",
        summary = "Process worries before they follow you to bed",
        details = "Racing thoughts are a common cause of insomnia. Try journaling before bed to get worries out of your head. Practice relaxation techniques like deep breathing or progressive muscle relaxation. If anxiety persists, consider speaking with a mental health professional.",
        icon = Icons.Default.Psychology,
        category = TipCategory.RELAXATION
    ),
    SleepTip(
        id = "limit_naps",
        title = "Limit Daytime Naps",
        summary = "Keep naps under 30 minutes and before 3pm",
        details = "While short naps can be refreshing, long or late naps can interfere with nighttime sleep. If you need to nap, keep it to 20-30 minutes and do it before 3pm. This gives you time to build up enough sleep pressure for bedtime. Avoid napping if you have insomnia.",
        icon = Icons.Default.Brightness5,
        category = TipCategory.LIFESTYLE
    ),
    SleepTip(
        id = "bed_for_sleep",
        title = "Use Your Bed Only for Sleep",
        summary = "Strengthen the association between bed and sleep",
        details = "Working, watching TV, or scrolling your phone in bed weakens your brain's association between bed and sleep. Reserve your bed for sleep (and intimacy) only. If you can't fall asleep after 20 minutes, get up and do something relaxing until you feel sleepy.",
        icon = Icons.Default.Bedtime,
        category = TipCategory.BEDTIME
    ),
    SleepTip(
        id = "deep_breathing",
        title = "Practice Deep Breathing",
        summary = "Use the 4-7-8 technique to relax",
        details = "The 4-7-8 breathing technique can help calm your nervous system: Inhale through your nose for 4 seconds, hold for 7 seconds, then exhale slowly through your mouth for 8 seconds. Repeat 3-4 times. This activates your parasympathetic nervous system and promotes relaxation.",
        icon = Icons.Default.SelfImprovement,
        category = TipCategory.RELAXATION
    ),
    SleepTip(
        id = "avoid_alcohol",
        title = "Limit Alcohol Before Bed",
        summary = "Alcohol disrupts REM sleep quality",
        details = "While alcohol might help you fall asleep faster, it actually reduces sleep quality. It suppresses REM sleep (the restorative stage), causes more frequent awakenings, and can worsen snoring and sleep apnea. If you drink, try to finish at least 3 hours before bed.",
        icon = Icons.Default.NightsStay,
        category = TipCategory.NUTRITION
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf(TipCategory.ALL) }
    val bookmarkedTips = remember { mutableStateListOf<String>().also { it.addAll(loadBookmarks(context)) } }
    var showBookmarksOnly by remember { mutableStateOf(false) }

    LaunchedEffect(bookmarkedTips.toList()) {
        saveBookmarks(context, bookmarkedTips.toList())
    }

    val filteredTips = remember(selectedCategory, showBookmarksOnly, bookmarkedTips.toList()) {
        sleepTips.filter { tip ->
            val categoryMatch = selectedCategory == TipCategory.ALL || tip.category == selectedCategory
            val bookmarkMatch = !showBookmarksOnly || bookmarkedTips.contains(tip.id)
            categoryMatch && bookmarkMatch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Sleep Tips", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBookmarksOnly = !showBookmarksOnly
                    }) {
                        Icon(
                            imageVector = if (showBookmarksOnly) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmarks",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            TipsHeader(bookmarkCount = bookmarkedTips.size, totalTips = sleepTips.size)

            CategoryFilterRow(
                selectedCategory = selectedCategory,
                onCategorySelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedCategory = it
                }
            )

            if (filteredTips.isEmpty()) {
                EmptyTipsPlaceholder(showBookmarksOnly = showBookmarksOnly)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTips, key = { it.id }) { tip ->
                        TipCard(
                            tip = tip,
                            isBookmarked = bookmarkedTips.contains(tip.id),
                            onBookmarkToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (bookmarkedTips.contains(tip.id)) {
                                    bookmarkedTips.remove(tip.id)
                                } else {
                                    bookmarkedTips.add(tip.id)
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TipsHeader(bookmarkCount: Int, totalTips: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF5C6BC0), Color(0xFF7C4DFF))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Better Sleep Starts Here",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$totalTips tips to improve your sleep",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "$bookmarkCount saved",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: TipCategory,
    onCategorySelected: (TipCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TipCategory.entries) { category ->
            val isSelected = category == selectedCategory
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(category.displayName, fontSize = 13.sp)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.color.copy(alpha = 0.2f),
                    selectedLabelColor = category.color
                )
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EmptyTipsPlaceholder(showBookmarksOnly: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (showBookmarksOnly) Icons.Default.BookmarkBorder else Icons.Default.Lightbulb,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (showBookmarksOnly) "No Bookmarked Tips" else "No Tips Found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (showBookmarksOnly) "Tap the bookmark icon on tips to save them" else "Try selecting a different category",
            fontSize = 14.sp,
            color = Color.Gray.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TipCard(
    tip: SleepTip,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rotation"
    )

    val bookmarkScale by animateFloatAsState(
        targetValue = if (isBookmarked) 1.2f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "bookmark"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMedium))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isExpanded = !isExpanded
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(tip.category.color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tip.icon,
                            contentDescription = null,
                            tint = tip.category.color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tip.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tip.summary,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            lineHeight = 18.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Color(0xFFFFD54F) else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.scale(bookmarkScale)
                        )
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExpanded = !isExpanded
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color.Gray,
                            modifier = Modifier.rotate(rotationAngle)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                tip.category.color.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = tip.details,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 22.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        tip.category.color.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tip.category.displayName,
                                    fontSize = 11.sp,
                                    color = tip.category.color,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Science-backed",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadBookmarks(context: Context): List<String> {
    val prefs = context.getSharedPreferences("sleep_tips", Context.MODE_PRIVATE)
    val bookmarksString = prefs.getString("bookmarks", "") ?: ""
    return if (bookmarksString.isEmpty()) emptyList() else bookmarksString.split(",")
}

private fun saveBookmarks(context: Context, bookmarks: List<String>) {
    val prefs = context.getSharedPreferences("sleep_tips", Context.MODE_PRIVATE)
    prefs.edit { putString("bookmarks", bookmarks.joinToString(",")) }
}

