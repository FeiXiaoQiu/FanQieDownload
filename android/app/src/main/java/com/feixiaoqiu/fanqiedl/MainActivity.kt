package com.feixiaoqiu.fanqiedl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.feixiaoqiu.fanqiedl.data.BackgroundScale
import com.feixiaoqiu.fanqiedl.data.UpdateChecker
import com.feixiaoqiu.fanqiedl.ui.BookDetailDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadOptionsDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadProgressDialog
import com.feixiaoqiu.fanqiedl.ui.DownloadResultDialog
import com.feixiaoqiu.fanqiedl.ui.ReaderScreen
import com.feixiaoqiu.fanqiedl.ui.SearchScreen
import com.feixiaoqiu.fanqiedl.ui.SettingsScreen
import com.feixiaoqiu.fanqiedl.ui.SplashScreen
import com.feixiaoqiu.fanqiedl.ui.theme.BgBlack
import com.feixiaoqiu.fanqiedl.ui.theme.FanqieTheme
import com.feixiaoqiu.fanqiedl.ui.theme.Primary
import com.feixiaoqiu.fanqiedl.ui.theme.Scrim
import com.feixiaoqiu.fanqiedl.viewmodel.MainViewModel
import java.io.File

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
                var showSplash by remember { mutableStateOf(true) }

                val context = LocalContext.current
                val bgModel: ImageRequest? = remember(state.backgroundDisplayUrl) {
                    val p = state.backgroundDisplayUrl
                    val data = when {
                        p.isBlank() -> null
                        p.startsWith("http://") || p.startsWith("https://") ||
                            p.startsWith("file://") || p.startsWith("content://") -> p
                        else -> {
                            val f = File(p)
                            if (f.isFile) Uri.fromFile(f) else null
                        }
                    }
                    data?.let {
                        ImageRequest.Builder(context)
                            .data(it)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .crossfade(false)
                            .build()
                    }
                }
                LaunchedEffect(state.snackbar) {
                    val msg = state.snackbar
                    if (msg != null) {
                        snackbarHostState.showSnackbar(msg)
                        vm.consumeSnackbar()
                    }
                }

                // 系统返回：设置/阅读/弹层逐级退出，避免直接杀进程
                BackHandler(enabled = !showSplash) {
                    when {
                        state.showCatalog -> vm.toggleCatalog(false)
                        state.downloadResult != null -> vm.dismissDownloadResult()
                        state.downloading -> vm.cancelDownload()
                        state.showDownloadOptions -> vm.closeDownloadOptions()
                        state.selected != null && !state.reading -> vm.closeDetail()
                        state.reading -> vm.closeReader()
                        showSettings -> showSettings = false
                        state.books.isNotEmpty() || state.searchError != null -> vm.clearSearch()
                        else -> finish()
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
                    if (bgModel != null) {
                        if (state.backgroundScale == BackgroundScale.FIT) {
                            SubcomposeAsyncImage(
                                model = bgModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                success = { successState ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            painter = successState.painter,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .blur(10.dp),
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.Center,
                                        )
                                        Image(
                                            painter = successState.painter,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.Center,
                                        )
                                    }
                                },
                            )
                        } else {
                            SubcomposeAsyncImage(
                                model = bgModel,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(state.backgroundCropBlur.dp),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center,
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Scrim))

                    // 背景铺满全屏（含刘海/挖孔区域），避免顶部黑条
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                state.reading -> {
                                    ReaderScreen(
                                        state = state,
                                        onBack = vm::closeReader,
                                        onToggleCatalog = vm::toggleCatalog,
                                        onPrev = vm::prevChapter,
                                        onNext = vm::nextChapter,
                                        onJump = vm::goChapter,
                                        onRetry = vm::retryChapter,
                                    )
                                }
                                showSettings -> {
                                    SettingsScreen(
                                        state = state,
                                        onBack = { showSettings = false },
                                        onRemove = vm::removeNode,
                                        onAdd = vm::addNode,
                                        onUpdate = vm::updateNodeUrl,
                                        onRestore = vm::restoreNodes,
                                        onProbeAll = vm::probeAllNodes,
                                        onHitokotoUrlChange = vm::setHitokotoUrl,
                                        onSaveHitokoto = vm::saveHitokotoUrl,
                                        onTestHitokoto = vm::testHitokoto,
                                        onBgModeChange = vm::setBackgroundMode,
                                        onBgApiChange = vm::setBackgroundApiUrl,
                                        onSaveBackground = vm::saveBackground,
                                        onRefreshBackground = vm::refreshBackground,
                                        onAddCustomBg = { name, url -> vm.addCustomBackground(name, url) },
                                        onRemoveCustomBg = vm::removeCustomBackground,
                                        onUpdateCustomBg = { id, name, url -> vm.updateCustomBackground(id, name, url) },
                                        onSelectCustomBg = vm::selectCustomBackground,
                                        onCheckUpdate = { vm.checkForUpdate(silent = false) },
                                        r18Accepted = state.r18Accepted,
                                        onAcceptR18 = vm::acceptR18,
                                        onOpenRepo = { openUrl(UpdateChecker.REPO_URL) },
                                        onAddDownloadSource = { name, tmpl -> vm.addCustomDownloadSource(name, tmpl) },
                                        onRemoveDownloadSource = vm::removeCustomDownloadSource,
                                        onDownloadUpdate = vm::downloadApk,
                                        onBackgroundScaleChange = vm::setBackgroundScale,
                                        onBackgroundBlurChange = vm::setBackgroundCropBlur,
                                        onResetMirrors = vm::resetMirrors,
                                        onToggleAutoUpdateCheck = vm::toggleAutoUpdateCheck,
                                    )
                                }
                                else -> {
                                    SearchScreen(
                                        state = state,
                                        onQueryChange = vm::onQueryChange,
                                        onSearch = { vm.search(true) },
                                        onLoadMore = vm::loadMoreSearch,
                                        onOpenSettings = { showSettings = true },
                                        onOpenWeb = { openUrl(MainViewModel.WEB_HOME_URL) },
                                        onOpenBook = vm::openDetail,
                                        onRefreshHitokoto = vm::refreshHitokoto,
                                        onDownloadUpdate = vm::downloadApk,
                                        onCancelUpdate = vm::cancelApkDownload,
                                        onDismissUpdate = vm::dismissHomeUpdate,
                                        onInstallCached = vm::installCachedApk,
                                        onForceRedownload = vm::forceRedownloadApk,
                                        onDismissApkPrompt = vm::dismissApkExistsPrompt,
                                    )
                                }
                            }

                            if (
                                state.selected != null &&
                                !state.showDownloadOptions &&
                                !state.downloading &&
                                !state.reading
                            ) {
                                BookDetailDialog(
                                    state = state,
                                    onDismiss = vm::closeDetail,
                                    onDownload = vm::openDownloadOptions,
                                    onRead = vm::openReader,
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

                            if (state.showApkExistsPrompt) {
                                AlertDialog(
                                    onDismissRequest = vm::dismissApkExistsPrompt,
                                    title = { Text("已下载新版", color = Color.White, fontSize = 18.sp) },
                                    text = { Text("本地已有下载好的更新包，是否直接安装？", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) },
                                    confirmButton = {
                                        TextButton(onClick = vm::installCachedApk) {
                                            Text("直接安装", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    dismissButton = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = vm::forceRedownloadApk) {
                                                Text("重新下载", color = Primary.copy(alpha = 0.7f), fontSize = 14.sp)
                                            }
                                            TextButton(onClick = vm::dismissApkExistsPrompt) {
                                                Text("取消", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                            }
                                        }
                                    },
                                    containerColor = Color(0xE61A1A20),
                                    shape = RoundedCornerShape(16.dp),
                                )
                            }
                        }
                    }

                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // ignore
        }
    }
}
