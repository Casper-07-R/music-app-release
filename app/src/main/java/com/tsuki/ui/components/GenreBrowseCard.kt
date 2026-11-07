package com.tsuki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsuki.ui.R
import com.tsuki.ui.model.GenreCategory
import com.tsuki.ui.theme.*

/**
 * Material 3 Expressive Genre Category Card.
 */
@Composable
fun GenreBrowseCard(
    category: GenreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(RogSurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = category.title,
            fontSize = 14.5.sp,
            fontFamily = CircularFontFamily,
            fontWeight = FontWeight.Bold,
            color = RogTextPrimary,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        Icon(
            painter = painterResource(id = R.drawable.ic_soundwave),
            contentDescription = null,
            tint = RogTextMuted,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterEnd)
        )
    }
}
