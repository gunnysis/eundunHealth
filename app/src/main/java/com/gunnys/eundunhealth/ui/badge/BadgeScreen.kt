package com.gunnys.eundunhealth.ui.badge

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gunnys.eundunhealth.ui.components.EmptyContent
import com.gunnys.eundunhealth.ui.components.ErrorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeScreen(
    onBack: () -> Unit,
    viewModel: BadgeViewModel = hiltViewModel()
) {
    val badges by viewModel.badges.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("챌린지 배지") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        when {
            error != null && badges.isEmpty() -> {
                ErrorContent(
                    error = error!!,
                    modifier = Modifier.padding(padding),
                    onRetry = {
                        viewModel.clearError()
                        viewModel.loadBadges()
                    },
                )
            }
            badges.isEmpty() -> {
                EmptyContent(
                    message = "아직 배지가 없습니다",
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                    items(badges, key = { it.key }) { badge ->
                        BadgeItem(badge)
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeItem(badge: BadgeDisplayItem) {
    val containerColor by animateColorAsState(
        if (badge.earned) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "badgeColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (badge.earned) Icons.Default.EmojiEvents else Icons.Outlined.Lock,
                badge.name,
                tint = if (badge.earned) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    badge.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (badge.earned) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(badge.description, style = MaterialTheme.typography.bodySmall)
                if (badge.earned && badge.earnedAt != null) {
                    Text(
                        "획득: ${badge.earnedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
