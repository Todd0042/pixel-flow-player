package com.pixelflow.player.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixelflow.player.data.FavoriteFilter
import com.pixelflow.player.data.VideoFolder
import com.pixelflow.player.ui.theme.PixelHeartRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFilterDialog(
    folder: VideoFolder,
    onFilterSelected: (FavoriteFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val nonFavCount = (folder.totalCount - folder.favoriteCount).coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Filter Videos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${folder.name} (${folder.totalCount} videos)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterOptionCard(
                        filter = FavoriteFilter.ALL,
                        icon = Icons.Default.VideoLibrary,
                        count = folder.totalCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(FavoriteFilter.ALL) }
                    )
                    FilterOptionCard(
                        filter = FavoriteFilter.FAVORITES_ONLY,
                        icon = Icons.Default.Favorite,
                        iconTint = PixelHeartRed,
                        count = folder.favoriteCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(FavoriteFilter.FAVORITES_ONLY) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterOptionCard(
                        filter = FavoriteFilter.NON_FAVORITES_ONLY,
                        icon = Icons.Default.FavoriteBorder,
                        count = nonFavCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(FavoriteFilter.NON_FAVORITES_ONLY) }
                    )
                    FilterOptionCard(
                        filter = FavoriteFilter.NON_FAVORITES_UNVIEWED,
                        icon = Icons.Default.VisibilityOff,
                        count = folder.unviewedNonFavoriteCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onFilterSelected(FavoriteFilter.NON_FAVORITES_UNVIEWED) }
                    )
                }
            } else {
                FilterOptionCard(
                    filter = FavoriteFilter.ALL,
                    icon = Icons.Default.VideoLibrary,
                    count = folder.totalCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onFilterSelected(FavoriteFilter.ALL) }
                )

                FilterOptionCard(
                    filter = FavoriteFilter.FAVORITES_ONLY,
                    icon = Icons.Default.Favorite,
                    iconTint = PixelHeartRed,
                    count = folder.favoriteCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onFilterSelected(FavoriteFilter.FAVORITES_ONLY) }
                )

                FilterOptionCard(
                    filter = FavoriteFilter.NON_FAVORITES_ONLY,
                    icon = Icons.Default.FavoriteBorder,
                    count = nonFavCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onFilterSelected(FavoriteFilter.NON_FAVORITES_ONLY) }
                )

                FilterOptionCard(
                    filter = FavoriteFilter.NON_FAVORITES_UNVIEWED,
                    icon = Icons.Default.VisibilityOff,
                    count = folder.unviewedNonFavoriteCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onFilterSelected(FavoriteFilter.NON_FAVORITES_UNVIEWED) }
                )
            }
        }
    }
}

@Composable
private fun FilterOptionCard(
    filter: FavoriteFilter,
    icon: ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    val isEnabled = count > 0

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = isEnabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isEnabled) {
                    iconTint ?: MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = filter.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )
        }
    }
}
