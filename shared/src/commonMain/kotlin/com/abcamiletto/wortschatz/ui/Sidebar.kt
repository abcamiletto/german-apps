package com.abcamiletto.wortschatz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.abcamiletto.wortschatz.data.SyncStatus
import com.abcamiletto.wortschatz.resources.Res
import com.abcamiletto.wortschatz.resources.icon
import com.abcamiletto.wortschatz.ui.theme.Hairline
import com.abcamiletto.wortschatz.ui.theme.Ink
import com.abcamiletto.wortschatz.ui.theme.Slate
import com.abcamiletto.wortschatz.ui.theme.Track
import org.jetbrains.compose.resources.painterResource

private val SIDEBAR_WIDTH = 232.dp

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.small + Gap.tiny)) {
        Image(painterResource(Res.drawable.icon), contentDescription = null, modifier = Modifier.size(32.dp))
        Text("Wortschatz", style = MaterialTheme.typography.titleMedium)
    }
}

/** On a wide screen the two places in the app stay in view, in place of the phone's header buttons. */
@Composable
fun Sidebar(onHome: Boolean, onWords: Boolean, status: SyncStatus?, goHome: () -> Unit, goWords: () -> Unit) {
    Column(Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().padding(Gap.large)) {
        Brand(Modifier.padding(start = Gap.small, top = Gap.small, bottom = Gap.wide))
        NavigationItem("Home", Icons.Outlined.Home, onHome, "navHome", goHome)
        Spacer(Modifier.height(Gap.tiny))
        NavigationItem("All words", Icons.AutoMirrored.Outlined.MenuBook, onWords, "navWords", goWords)
        Spacer(Modifier.weight(1f))
        SyncState(status, Modifier.padding(start = Gap.medium, bottom = Gap.tiny))
    }
    VerticalDivider(color = Hairline)
}

@Composable
private fun NavigationItem(label: String, icon: ImageVector, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(Corner.small)
            .then(if (selected) Modifier.background(Track) else Modifier)
            .hand()
            .clickable(onClick = onClick)
            .padding(horizontal = Gap.medium)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap.medium),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Ink else Slate, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink else Slate)
    }
}

@Composable
private fun SyncState(status: SyncStatus?, modifier: Modifier = Modifier) {
    val (icon, label) = when (status) {
        SyncStatus.Synced -> Icons.Outlined.CloudDone to "Synced"
        SyncStatus.Offline -> Icons.Outlined.CloudOff to "Offline"
        else -> return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.small)) {
        Icon(icon, contentDescription = null, tint = Slate, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Slate)
    }
}
