package com.example.isolation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.WarmSand

/** Two views of stored files: what the isolated apps saved, and what you locked yourself. */
@Composable
fun FilesScreen(viewModel: IsoVaultViewModel) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().testTag("files_screen")) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Files",
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                fontFamily = FrauncesFontFamily,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = WarmSand,
                contentColor = ForestPrimary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[tab]),
                        color = ForestPrimary
                    )
                },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("App files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("My Locker", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (tab) {
            0 -> GalleryScreen(viewModel = viewModel)
            else -> VaultScreen(viewModel = viewModel)
        }
    }
}
