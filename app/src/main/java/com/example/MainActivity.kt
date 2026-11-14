package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.util.configureAppCheck
import com.example.viewmodel.AppScreen
import com.example.viewmodel.CivilQuantitiesViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: CivilQuantitiesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()
                val isForward by viewModel.isNavigatingForward.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val uiScope = rememberCoroutineScope()
                val haptic = LocalHapticFeedback.current

                LaunchedEffect(Unit) {
                    viewModel.snackMessage.collectLatest { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                // Handle system back navigation using smooth history stack
                if (currentScreen != AppScreen.HOME) {
                    BackHandler {
                        viewModel.navigateBack()
                    }
                }

                val isBottomBarVisible = currentScreen in listOf(
                    AppScreen.HOME,
                    AppScreen.BLUEPRINT_IMPORT,
                    AppScreen.CONCRETE,
                    AppScreen.STEEL,
                    AppScreen.BOQ
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        AnimatedVisibility(
                            visible = isBottomBarVisible,
                            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
                            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140))
                        ) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp,
                                modifier = Modifier.testTag("main_bottom_nav")
                            ) {
                                val navItems = listOf(
                                    Triple(AppScreen.HOME, Icons.Default.Home to "الرئيسية", "nav_item_home"),
                                    Triple(AppScreen.BLUEPRINT_IMPORT, Icons.Default.DocumentScanner to "المخططات", "nav_item_blueprints"),
                                    Triple(AppScreen.CONCRETE, Icons.Default.ViewInAr to "خرسانة", "nav_item_concrete"),
                                    Triple(AppScreen.STEEL, Icons.Default.GridOn to "تسليح", "nav_item_steel"),
                                    Triple(AppScreen.BOQ, Icons.Default.RequestQuote to "المقايسة", "nav_item_boq")
                                )
                                navItems.forEach { (screen, iconLabel, tag) ->
                                    val selected = currentScreen == screen
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.navigateTo(screen)
                                        },
                                        icon = { Icon(iconLabel.first, contentDescription = iconLabel.second) },
                                        label = {
                                            Text(
                                                iconLabel.second,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.testTag(tag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val dir = if (isForward) 1 else -1
                                (slideInHorizontally(tween(220)) { width -> (width / 6) * dir } +
                                    fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(tween(180)) { width -> -(width / 8) * dir } +
                                        fadeOut(tween(150)))
                            },
                            label = "screenTransition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.HOME -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                                AppScreen.BLUEPRINT_IMPORT -> BlueprintAnalyzerScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() },
                                    onNavigateToBoq = { viewModel.navigateTo(AppScreen.BOQ) }
                                )
                                AppScreen.SPEC_ANALYZER -> SpecDocumentScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() },
                                    onNavigateToBoq = { viewModel.navigateTo(AppScreen.BOQ) },
                                    onNavigateToWorkPlan = { viewModel.navigateTo(AppScreen.WORK_PLAN) }
                                )
                                AppScreen.WORK_PLAN -> WorkPlanScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.COMMUNITY -> CommunityScreen(
                                    onBack = { viewModel.navigateBack() },
                                    onShowMessage = { msg ->
                                        uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                                AppScreen.CONCRETE -> ConcreteScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.STEEL -> SteelScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.MASONRY -> MasonryScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.FINISHING -> FinishingScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.BOQ -> BoqScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.CODES_GUIDE -> StandardsGuideScreen(
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.SAVED_PROJECTS -> SavedProjectsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateBack() }
                                )
                                AppScreen.ABOUT_PRIVACY -> AboutPrivacyScreen(
                                    onBack = { viewModel.navigateBack() },
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
