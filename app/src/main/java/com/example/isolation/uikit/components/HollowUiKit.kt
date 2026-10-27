package com.example.isolation.uikit.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JakartaFontFamily

/**
 * Colorful Material 3 Banner shown on Home Dashboard when new modules are ready to download.
 */
@Composable
fun ModuleDownloadNotificationBanner(
    pendingDownloadCount: Int,
    pendingModuleNames: String,
    onDownloadAllClick: () -> Unit,
    onOpenModuleHubClick: () -> Unit
) {
    if (pendingDownloadCount <= 0) return
    val scheme = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenModuleHubClick() }
            .testTag("banner_module_download_notification")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$pendingDownloadCount New Module${if (pendingDownloadCount == 1) "" else "s"} Available",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = pendingModuleNames,
                        fontSize = 12.sp,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onDownloadAllClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("btn_banner_download_modules")
            ) {
                Text(
                    text = "Install All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            }
        }
    }
}

/**
 * Colorful Material 3 Account & Modules Card on the Home Dashboard.
 */
@Composable
fun RootVaultStatusCard(
    devFullBuild: Boolean,
    hardwareFingerprint: String,
    activeModulesCount: Int,
    totalModulesCount: Int,
    remainingDays: Int,
    onOpenLicenseHub: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, scheme.secondary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLicenseHub() }
            .testTag("card_root_vault_status")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.secondary)
                ) {
                    Icon(
                        imageVector = if (devFullBuild) Icons.Default.Widgets else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = scheme.onSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (devFullBuild) "All Feature Modules Active" else "Cloud Account & Modules",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = scheme.secondary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "$activeModulesCount/$totalModulesCount Ready",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.secondary,
                                fontFamily = JakartaFontFamily,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (devFullBuild) {
                            "Tap to manage cloud modules, account sync, or switch build mode"
                        } else if (activeModulesCount > 0) {
                            "$activeModulesCount modules installed • Tap to check for updates"
                        } else {
                            "Tap to sign in, sync your license, or download feature modules"
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = scheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
