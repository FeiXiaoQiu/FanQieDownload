package com.feixiaoqiu.fanqiedl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feixiaoqiu.fanqiedl.ui.BookDetailDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadOptionsDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadProgressDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadResultDialog
import com.feixiaoqiu.fanqiedl.ui.SearchScreen
import com.feixiaoqiu.fanqiedl.ui.SettingsScreen
import com.feixiaoqiu.fanqiedl.ui.theme.FanqieTheme
import com.feixiaoqiu.fanqiedl.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FanqieApp
        setContent {
            FanqieTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.container))
                val state by vm.ui.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var showSettings by remember { mutableStateOf(false) }

                LaunchedEffect(state.snackbar) {
                    val msg = state.snackbar
                    if (msg != null) {
                        snackbarHostState.showSnackbar(msg)
                        vm.consumeSnackbar()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (showSettings) {
                            SettingsScreen(
                                state = state,
                                onBack = { showSettings = false },
                                onToggle = vm::toggleNode,
                                onRemove = vm::removeNode,
                                onAdd = vm::addNode,
                                onUpdate = vm::updateNodeUrl,
                                onRestore = vm::restoreNodes,
                                onProbe = vm::probeNode,
                                onHitokotoUrlChange = vm::setHitokotoUrl,
                                onSaveHitokoto = vm::saveHitokotoUrl,
                                onTestHitokoto = vm::testHitokoto,
                            )
                        } else {
                            SearchScreen(
                                state = state,
                                onQueryChange = vm::onQueryChange,
                                onSearch = { vm.search(true) },
                                onOpenSettings = { showSettings = true },
                                onOpenBook = vm::openDetail,
                                onRefreshHitokoto = vm::refreshHitokoto,
                            )
                        }

                        if (state.selected != null && !state.showDownloadOptions && !state.downloading) {
                            BookDetailDialog(
                                state = state,
                                onDismiss = vm::closeDetail,
                                onDownload = vm::openDownloadOptions,
                            )
                        }
                        if (state.showDownloadOptions) {
                            DownloadOptionsDialog(
                                state = state,
                                onDismiss = vm::closeDownloadOptions,
                                onStart = vm::startDownload,
                                onStartChange = vm::setStartChapter,
                                onEndChange = vm::setEndChapter,
                                onResumeChange = vm::setResume,
                            )
                        }
                        if (state.downloading && state.downloadProgress != null) {
                            DownloadProgressDialog(
                                state = state,
                                onCancel = vm::cancelDownload,
                            )
                        }
                        if (state.downloadResult != null) {
                            DownloadResultDialog(
                                state = state,
                                onDismiss = vm::dismissDownloadResult,
                            )
                        }
                    }
                }
            }
        }
    }
}
