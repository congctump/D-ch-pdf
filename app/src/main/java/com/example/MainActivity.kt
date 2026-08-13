package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.MedicalHeader
import com.example.ui.screens.GlossaryScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TranslateScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

data class NavTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MedicalAppMainContent()
            }
        }
    }
}

@Composable
fun MedicalAppMainContent(
    mainViewModel: MainViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        NavTab("Dịch PDF", Icons.Default.Translate, "tab_translate"),
        NavTab("Lịch Sử", Icons.Default.History, "tab_history"),
        NavTab("Từ Điển", Icons.Default.MenuBook, "tab_glossary"),
        NavTab("Cấu Hình", Icons.Default.Settings, "tab_settings")
    )

    Scaffold(
        topBar = {
            MedicalHeader()
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> TranslateScreen(
                    viewModel = mainViewModel,
                    onNavigateToSettings = { selectedTabIndex = 3 }
                )
                1 -> HistoryScreen(
                    viewModel = mainViewModel,
                    onSelectDocument = { selectedTabIndex = 0 }
                )
                2 -> GlossaryScreen(
                    viewModel = mainViewModel
                )
                3 -> SettingsScreen(
                    viewModel = mainViewModel
                )
            }
        }
    }
}
