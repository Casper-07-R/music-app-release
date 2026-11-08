package com.koi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koi.ui.R
import com.koi.ui.theme.*

data class ShelfCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val gradientStart: Color = Color(0xFF1E3264),
    val gradientEnd: Color = Color(0xFF121212)
)

@Composable
fun HorizontalMediaShelf(
    title: String,
    items: List<ShelfCardItem>,
    onItemClick: (ShelfCardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun ShelfCard(
    item: ShelfCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(136.dp)
            .clickable { onClick() }
    ) {
        // Artwork Box (22.dp rounded corners)
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RogSurfaceDark),
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
                    painter = painterResource(id = R.drawable.ic_soundwave),
                    contentDescription = null,
                    tint = RogGoldPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Medium,
            color = RogTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle
        Text(
            text = item.subtitle,
            fontSize = 11.5.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Normal,
            color = RogTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
