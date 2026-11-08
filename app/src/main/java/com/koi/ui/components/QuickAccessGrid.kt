package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.model.QuickAccessItem
import com.koi.ui.theme.*

@Composable
fun QuickAccessGrid(
    items: List<QuickAccessItem>,
    onItemClick: (QuickAccessItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chunked = items.chunked(2)
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    QuickAccessCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun QuickAccessCard(
    item: QuickAccessItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(RogSurfaceDark)
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expressive Rounded Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RogSurfaceMedium),
            contentAlignment = Alignment.Center
        ) {
            if (!item.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(
                        id = if (item.isLikedSongs) R.drawable.ic_heart_filled else R.drawable.ic_soundwave
                    ),
                    contentDescription = null,
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title
        Text(
            text = item.title,
            fontSize = 12.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Medium,
            color = RogTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
}
