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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.IsolationMode
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.CoralError
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.HoneyTertiary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmSand
import com.example.ui.theme.WarmStone

@Composable
fun MountTerminalScreen(
    viewModel: IsoVaultViewModel
) {
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
            .padding(horizontal = 20.dp)
            .testTag("mount_terminal_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "System Details",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    fontFamily = FrauncesFontFamily,
                    color = InkPrimary
                )
                Text(
                    text = "Diagnostic logs and storage folders",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    fontFamily = JakartaFontFamily
                )
            }
            StatusBadge(
                text = if (isLsposedActive) "Connected" else "Standby",
                color = if (isLsposedActive) ForestPrimary else HoneyTertiary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        TabRow(
            selectedTabIndex = activeTab,
            containerColor = WarmSand,
            contentColor = ForestPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = ForestPrimary
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Event Log", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Boot Service", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Storage (${systemMounts.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeTab) {
            0 -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(WarmIvory)
                        .border(1.dp, WarmStone, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    LazyColumn(state = listState) {
                        items(terminalLogs) { log ->
                            Text(
                                text = log,
                                color = if (log.contains("BLOCKED") || log.contains("Error")) CoralError else InkPrimary,
                                fontSize = 12.sp,
                                fontFamily = JakartaFontFamily,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.runRootCheck()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_exec_mount")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Root", fontSize = 13.sp, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val text = terminalLogs.joinToString("\n")
                            clipboard.setText(AnnotatedString(text))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Log", fontSize = 13.sp, fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            1 -> {
                val script = viewModel.generateBootScript()

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(WarmIvory)
                            .border(1.dp, WarmStone, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = script,
                                    color = InkPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(script))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_copy_magisk_script")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Script", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = JakartaFontFamily)
                    }
                }
            }

            2 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(systemMounts) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WarmIvory),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = entry.mountPoint,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = InkPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = JakartaFontFamily,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusBadge(text = entry.fsType, color = ForestPrimary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.device,
                                    fontSize = 12.sp,
                                    color = InkSecondary,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
