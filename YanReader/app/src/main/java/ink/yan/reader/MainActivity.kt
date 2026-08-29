package ink.yan.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ink.yan.reader.ui.NodeScreen
import ink.yan.reader.ui.SearchScreen
import ink.yan.reader.ui.SettingsScreen
import ink.yan.reader.ui.theme.YanTheme
import ink.yan.reader.vm.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    YanNav()
                }
            }
        }
    }
}

private object Route {
    const val SEARCH = "search"
    const val NODES = "nodes"
    const val SETTINGS = "settings"
}

@Composable
private fun YanNav() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()

    NavHost(navController = nav, startDestination = Route.SEARCH) {
        composable(
            route = Route.SEARCH,
            enterTransition = { enter() },
            exitTransition = { exit() },
        ) {
            SearchScreen(vm, onNodes = { nav.navigate(Route.NODES) },
                onSettings = { nav.navigate(Route.SETTINGS) })
        }
        composable(
            route = Route.NODES,
            enterTransition = { enter() },
            exitTransition = { exit() },
        ) {
            NodeScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(
            route = Route.SETTINGS,
            enterTransition = { enter() },
            exitTransition = { exit() },
        ) {
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}

/** 统一的转场：弹性位移 + 淡入淡出，与玻璃的「液态」气质一致。 */
private val springSpec: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter() =
    slideInHorizontally(animationSpec = springSpec) { it / 6 } + fadeIn()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit() =
    slideOutHorizontally(animationSpec = springSpec) { -it / 6 } + fadeOut()
