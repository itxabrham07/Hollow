package com.example.isolation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.MonoDataText
import com.example.isolation.ui.components.PrimaryButton
import com.example.isolation.ui.components.ProSegmentedTabRow
import com.example.isolation.ui.components.SecondaryButton
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily

@Composable
fun MountTerminalScreen(
    viewModel: IsoVaultViewModel
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val systemMounts by viewModel.systemMounts.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("mount_terminal_screen")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System & Mount Terminal",
                    style = typography.headlineMedium,
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Namespace diagnostics, Magisk/KSU boot script & active mounts",
                    style = typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            StatusIndicator(
                label = if (isLsposedActive) "Hooked" else "Standby",
                color = if (isLsposedActive) scheme.secondary else scheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ProSegmentedTabRow(
            options = listOf(
                "Event Log" to Icons.Default.Terminal,
                "Boot Script" to null,
                "Mounts (${systemMounts.size})" to Icons.Default.Storage
            ),
            selectedIndex = activeTab,
            onSelect = { activeTab = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (activeTab) {
            0 -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(shapes.medium)
                        .background(scheme.surface)
                        .border(1.dp, scheme.outlineVariant, shapes.medium)
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(terminalLogs) { log ->
                            MonoDataText(
                                text = log,
                                color = if (log.contains("BLOCKED") || log.contains("Error")) scheme.error else scheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrimaryButton(
                        text = "Check Root",
                        icon = Icons.Default.PlayArrow,
                        onClick = { viewModel.runRootCheck() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_exec_mount")
                    )

                    SecondaryButton(
                        text = "Copy Log",
                        icon = Icons.Default.ContentCopy,
                        onClick = {
                            val text = terminalLogs.joinToString("\n")
                            clipboard.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            1 -> {
                val script = viewModel.generateBootScript()

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(shapes.medium)
                            .background(scheme.surface)
                            .border(1.dp, scheme.outlineVariant, shapes.medium)
                            .padding(16.dp)
                    ) {
                        LazyColumn {
                            item {
                                MonoDataText(
                                    text = script,
                                    color = scheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Copy Boot Service Script",
                        icon = Icons.Default.ContentCopy,
                        onClick = { clipboard.setText(AnnotatedString(script)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_copy_magisk_script")
                    )
                }
            }

            2 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(systemMounts) { entry ->
                        ContentCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MonoDataText(
                                    text = entry.mountPoint,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusIndicator(
                                    label = entry.fsType,
                                    color = scheme.primary,
                                    showDot = false
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            MonoDataText(
                                text = entry.device,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
