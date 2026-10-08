package com.smartatm.pretest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartatm.pretest.ui.MainViewModel
import com.smartatm.pretest.ui.screen.InputScreen
import com.smartatm.pretest.ui.screen.IntroScreen
import com.smartatm.pretest.ui.screen.MockCallScreen
import com.smartatm.pretest.ui.screen.ResultScreen
import com.smartatm.pretest.ui.screen.SettingsScreen
import com.smartatm.pretest.ui.screen.SmsDetailScreen
import com.smartatm.pretest.ui.screen.SmsListScreen
import com.smartatm.pretest.ui.theme.SmartATMPretestTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 음성인식(SpeechRecognizer)을 위한 Activity Context 연결
        viewModel.attachActivityContext(this)

        // Handle shared text intent if app launched via Share (PRD 7-2)
        // 화면 회전 등으로 재생성될 때는 같은 공유 Intent를 다시 처리하지 않음
        if (savedInstanceState == null) {
            handleSendIntent(intent)
        }

        setContent {
            SmartATMPretestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        viewModel.detachActivityContext()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.onSharedText(sharedText)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "intro"
    ) {
        composable("intro") {
            IntroScreen(
                onNavigateToSmsCheck = { navController.navigate("sms_list") },
                onNavigateToCallCheck = { navController.navigate("mock_call") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("sms_list") {
            SmsListScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenMessage = { id -> navController.navigate("sms_detail/$id") },
                onNavigateToManualInput = { navController.navigate("input") }
            )
        }

        composable(
            route = "sms_detail/{messageId}",
            arguments = listOf(navArgument("messageId") { type = NavType.IntType })
        ) { backStackEntry ->
            SmsDetailScreen(
                viewModel = viewModel,
                messageId = backStackEntry.arguments?.getInt("messageId") ?: -1,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { navController.navigate("result") }
            )
        }

        composable("input") {
            InputScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToResult = { navController.navigate("result") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("mock_call") {
            MockCallScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { navController.navigate("result") }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("result") {
            ResultScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    // 다른 앱에서 [공유]로 문자를 받으면 직접 입력 검사 화면으로 이동 (내용은 이미 입력됨)
    val hasSharedText by viewModel.hasPendingSharedText.collectAsState()
    LaunchedEffect(hasSharedText) {
        if (hasSharedText) {
            navController.navigate("input") { launchSingleTop = true }
            viewModel.consumeSharedText()
        }
    }
}
