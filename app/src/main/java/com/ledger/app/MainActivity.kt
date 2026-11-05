package com.ledger.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ledger.app.data.AppDatabase
import com.ledger.app.ui.ImportPreviewScreen
import com.ledger.app.ui.LedgerTheme
import com.ledger.app.ui.MainScreen
import com.ledger.app.ui.Navigator
import com.ledger.app.ui.ProjectDetailScreen
import com.ledger.app.ui.ProjectEditScreen
import com.ledger.app.ui.RecordEditScreen
import com.ledger.app.ui.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge：状态栏/导航栏透明，由 Compose 背景（随主题）透出；图标明暗随系统深色模式
        val transparent = Color.Transparent.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent),
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent),
        )
        // 三键导航模式下不要系统再叠一层半透明遮罩，底部导航栏底色直接延伸到系统导航栏下
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val db = AppDatabase.get(this)
        setContent {
            LedgerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
                    val nav = remember { Navigator(stack) }
                    // 一级页面当前 Tab（从二级页面返回时保持）
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    BackHandler(enabled = stack.size > 1) { nav.pop() }
                    // 非「总览」Tab 时，返回键先回到总览
                    BackHandler(enabled = stack.size == 1 && tab != 0) { tab = 0 }
                    AnimatedContent(
                        targetState = stack.last(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> MainScreen(db, nav, tab) { tab = it }
                            is Screen.ProjectDetail -> ProjectDetailScreen(db, nav, screen.projectId)
                            is Screen.ProjectEdit -> ProjectEditScreen(db, nav, screen.projectId)
                            is Screen.RecordEdit -> RecordEditScreen(db, nav, screen.recordId, screen.projectId)
                            is Screen.ImportPreview -> ImportPreviewScreen(db, nav, screen)
                        }
                    }
                }
            }
        }
    }
}
