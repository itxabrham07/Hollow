package com.example.isolation.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@Composable
fun FileSelectionBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onClearSelection: () -> Unit
) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("selection_toolbar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                }
                Text(
                    text = "$selectedCount selected",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onMove, modifier = Modifier.size(36.dp).testTag("action_move")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move", tint = AccentSky, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp).testTag("action_copy")) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentSky, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp).testTag("action_rename")) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", tint = AccentSky, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onExport, modifier = Modifier.size(36.dp).testTag("action_share")) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share/Export", tint = AccentSky, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("action_delete")) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete to Bin", tint = AccentCoral, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
