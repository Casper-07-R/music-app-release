package com.tsuki.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsuki.ui.R
import com.tsuki.ui.model.NavigationTab
import com.tsuki.ui.theme.*

@Composable
fun TsukiBottomNavBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(RogObsidian)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                NavigationTab.HOME to R.drawable.ic_home,
                NavigationTab.SEARCH to R.drawable.ic_search,
                NavigationTab.YOUR_LIBRARY to R.drawable.ic_library,
                NavigationTab.PROFILE to R.drawable.ic_profile
            )

            tabs.forEach { (tab, iconRes) ->
                val isSelected = tab == selectedTab
                val animatedTint by animateColorAsState(
                    targetValue = if (isSelected) RogTextGold else RogTextSecondary,
                    animationSpec = tween(200),
                    label = "tabTint"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = tab.label,
                        tint = animatedTint,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = animatedTint
                    )
                }
            }
        }
    }
}

@Composable
fun SpotifyBottomNavBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) = TsukiBottomNavBar(selectedTab, onTabSelected, modifier)
