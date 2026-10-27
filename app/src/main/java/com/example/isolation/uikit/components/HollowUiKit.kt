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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JakartaFontFamily

/**
 * Shared UI-Kit Banner shown on Dashboard when Admin grants new modules that are ready
 * to be dynamically downloaded and bound into `/data/adb/.hollow_vault/modules/`.
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
            containerColor = scheme.primaryContainer.copy(alpha = 0.28f)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, scheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenModuleHubClick() }
            .testTag("banner_module_download_notification")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.primary.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$pendingDownloadCount Authorized Module${if (pendingDownloadCount == 1) "" else "s"} Ready",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        fontFamily = JakartaFontFamily
                    )
                    Text(
                        text = "$pendingModuleNames • Bind to /data/adb/.hollow_vault",
                        fontSize = 11.sp,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = JakartaFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onDownloadAllClick,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("btn_banner_download_modules")
            ) {
                Text(
                    text = "Download",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                )
            }
        }
    }
}

/**
 * Compact Root Secret Vault & Build Mode Status Bar on the Dashboard.
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
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLicenseHub() }
            .testTag("card_root_vault_status")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = if (devFullBuild) Icons.Default.Security else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (devFullBuild) "Developer Build (All Modules)" else "Root Secret Vault Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.primary.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$activeModulesCount/$totalModulesCount Modules",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }
                    Text(
                        text = if (devFullBuild) {
                            "HW $hardwareFingerprint • Tap to test Release Shell & Admin Approval"
                        } else {
                            "/data/adb/.hollow_vault • $remainingDays days valid • Survives Clear Data"
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
