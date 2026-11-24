package cc.csxcl.xiaobuhabridge.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.config.ConfigStore
import cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier
import cc.csxcl.xiaobuhabridge.fluid.FluidCloudPayload
import cc.csxcl.xiaobuhabridge.ha.HaClient
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.model.HaServiceCall
import cc.csxcl.xiaobuhabridge.nlu.DemoEntities
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbar
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarGroup
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarItem
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.FloatingActionButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BridgeTab(val label: String, val title: String, val icon: ImageVector) {
    CONTROL("控制中心", "Xiaobu Ha bridge", COUIIcons.Home),
    ENTITIES("家居实体", "家居实体", COUIIcons.GridView),
    LAB("调试台", "指令调试", COUIIcons.Tune)
}

enum class BridgePage {
    HOME,
    ENTITIES,
    LAB
}

internal data class EditDialogRequest(
    val title: String,
    val summary: String? = null,
    val initialValue: String,
    val placeholder: String,
    val singleLine: Boolean = true,
    val onConfirm: (String) -> Unit
)

internal data class EnvStatus(
    val xposedWorldReadable: Boolean,
    val notificationGranted: Boolean,
    val promotedGranted: Boolean,
    val syncedCount: Int,
    val lastSyncTime: Long
)

@Composable
fun BridgeAppScreen(
    context: Context,
    resumeTick: Int,
    onRequestNotificationPermission: () -> Unit,
    onBack: (() -> Unit)? = null,
    page: BridgePage = BridgePage.HOME,
    onOpenPage: (BridgePage) -> Unit = {},
    onPrefsChanged: () -> Unit = {},
    restartDialog: (@Composable (show: Boolean, onDismiss: () -> Unit) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var config by remember { mutableStateOf(ConfigStore.loadConfigFromApp(context)) }
    var entities by remember { mutableStateOf(ConfigStore.loadEntitiesFromApp(context)) }
    var syncing by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf<EditDialogRequest?>(null) }
    var editBuffer by remember { mutableStateOf("") }
    var selectedEntity by remember { mutableStateOf<HaEntity?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var showRestartPanel by remember { mutableStateOf(false) }

    // 家居实体页多选与搜索状态（提升到顶层以便顶栏按钮与底部悬浮工具栏联动）
    var entitySearchExpanded by remember { mutableStateOf(false) }
    var entitySearchKeyword by remember { mutableStateOf("") }
    var showExcludedEntities by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var envStatus by remember { mutableStateOf(readEnvStatus(context, entities.size)) }

    LaunchedEffect(resumeTick) {
        val loadedEntities = ConfigStore.loadEntitiesFromApp(context)
        config = ConfigStore.loadConfigFromApp(context)
        entities = loadedEntities
        envStatus = readEnvStatus(context, loadedEntities.size)
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        multiSelectedIds = emptySet()
    }

    fun updateConfig(transform: (BridgeConfig) -> BridgeConfig) {
        val next = transform(config)
        config = next
        ConfigStore.saveConfig(context, next)
        onPrefsChanged()
    }

    fun showTip(msg: String) {
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun triggerSyncEntities() {
        if (syncing) return
        if (!config.hasRealHaConfigured()) {
            showTip("请先在「家居实体」中配置 HA 地址和令牌")
            onOpenPage(BridgePage.ENTITIES)
            return
        }
        syncing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { HaClient.syncEntities(config) }
            syncing = false
            result.onSuccess { list ->
                ConfigStore.saveEntities(context, list)
                onPrefsChanged()
                entities = list
                envStatus = readEnvStatus(context, list.size)
                val filteredCount = config.filterEntities(list).size
                showTip("同步成功，共 ${list.size} 个实体（当前筛选生效 $filteredCount 个）")
            }.onFailure { err ->
                showTip("同步失败：${err.message}")
            }
        }
    }

    fun triggerFluidTest() {
        FluidCloudNotifier.show(
            context,
            FluidCloudPayload(
                shortCapsuleText = "卧室灯 · 已关",
                title = "卧室吸顶灯 · 已关闭",
                subtitle = "卧室 · 26ms (流体云测试)",
                domain = "light",
                isTurnOnStyle = false,
                durationMs = config.fluidCloudDurationMs,
                displayMode = config.fluidCloudDisplayMode,
                capsuleLeftText = "卧室灯",
                capsuleRightText = "已关",
                undoDomain = "light",
                undoService = "turn_on",
                undoPayloadJson = "{\"entity_id\":\"light.bedroom_ceiling\"}",
                undoTargetLabel = "卧室吸顶灯",
                icon = "mdi:ceiling-light",
                iconColor = "#18BCF2"
            )
        )
    }

    fun executeEntityAction(
        entity: HaEntity,
        service: String,
        actionVerb: String,
        isTurnOnStyle: Boolean,
        undoService: String?,
        newStateHint: String?
    ) {
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val call = HaServiceCall(entity.domain, service, listOf(entity.entityId))
            val displayName = entity.displayName
            val res = withContext(Dispatchers.IO) {
                HaClient.executeCalls(config, listOf(call), "已${actionVerb}${displayName}")
            }
            actionBusy = false
            if (res.success && newStateHint != null && entities.isNotEmpty()) {
                val updated = entities.map {
                    if (it.entityId == entity.entityId) it.copy(state = newStateHint) else it
                }
                entities = updated
                ConfigStore.saveEntities(context, updated)
                onPrefsChanged()
                if (selectedEntity?.entityId == entity.entityId) {
                    selectedEntity = entity.copy(state = newStateHint)
                }
            }
            if (config.enableFluidCloud) {
                val undoCall = undoService?.let { HaServiceCall(entity.domain, it, listOf(entity.entityId)) }
                FluidCloudNotifier.show(
                    context,
                    FluidCloudPayload(
                        shortCapsuleText = "${displayName.take(4)} · 已$actionVerb",
                        title = "${displayName} · 已$actionVerb",
                        subtitle = "${entity.areaName.ifBlank { "Home Assistant" }} · ${res.latencyMs}ms",
                        domain = entity.domain,
                        isTurnOnStyle = isTurnOnStyle,
                        isError = !res.success,
                        durationMs = config.fluidCloudDurationMs,
                        displayMode = config.fluidCloudDisplayMode,
                        capsuleLeftText = displayName.take(4),
                        capsuleRightText = if (res.success) "已$actionVerb" else "失败",
                        undoDomain = if (res.success) undoCall?.domain else null,
                        undoService = if (res.success) undoCall?.service else null,
                        undoPayloadJson = if (res.success) undoCall?.toPayloadJson()?.toString() else null,
                        undoTargetLabel = displayName,
                        icon = entity.icon,
                        iconColor = entity.iconColor
                    )
                )
            }
            selectedEntity = null
            showTip(res.message)
        }
    }

    val scrollBehavior = COUIScrollBehavior()
    val pageColor = AppBarBlurOverlayDefaults.containerColor()
    val backdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    val activeTab = when (page) {
        BridgePage.HOME -> BridgeTab.CONTROL
        BridgePage.ENTITIES -> BridgeTab.ENTITIES
        BridgePage.LAB -> BridgeTab.LAB
    }

    val rawEffectiveEntities = remember(entities, config.demoMode) {
        when {
            entities.isNotEmpty() -> entities
            config.demoMode -> DemoEntities.all
            else -> emptyList()
        }
    }
    val filteredEntities = remember(
        rawEffectiveEntities,
        config.allowedDomainsRaw,
        config.excludedKeywordsRaw,
        config.excludedEntityIdsRaw
    ) {
        config.filterEntities(rawEffectiveEntities)
    }
    val displayedEntities = remember(
        rawEffectiveEntities,
        filteredEntities,
        showExcludedEntities,
        entitySearchKeyword,
        config.allowedDomainsRaw
    ) {
        val base = if (showExcludedEntities) {
            rawEffectiveEntities.filter { config.isEntityAllowedByDomain(it) }
        } else {
            filteredEntities
        }
        val kw = entitySearchKeyword.trim().lowercase()
        if (kw.isEmpty()) {
            base
        } else {
            base.filter { e ->
                e.name.lowercase().contains(kw) ||
                    e.entityId.lowercase().contains(kw) ||
                    e.areaName.lowercase().contains(kw) ||
                    e.domain.lowercase().contains(kw)
            }
        }
    }

    val isEntityMultiSelectActive = activeTab == BridgeTab.ENTITIES && multiSelectMode
    val displayedIds = remember(displayedEntities) {
        displayedEntities.map { it.entityId }.toSet()
    }
    val allDisplayedSelected = displayedIds.isNotEmpty() && displayedIds.all { it in multiSelectedIds }
    val manualExcludedSet = remember(config.excludedEntityIdsRaw) { config.parsedExcludedEntityIds() }
    val allSelectedAreManuallyExcluded = multiSelectedIds.isNotEmpty() && multiSelectedIds.all { it in manualExcludedSet }

    val multiSelectBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val multiSelectBackProgress = remember { Animatable(0f) }
    NavigationBackHandler(
        state = multiSelectBackState,
        isBackEnabled = isEntityMultiSelectActive && !entitySearchExpanded,
        onBackCancelled = {
            scope.launch {
                multiSelectBackProgress.animateTo(0f, tween(durationMillis = 160))
            }
        },
        onBackCompleted = {
            exitMultiSelect()
            scope.launch { multiSelectBackProgress.snapTo(0f) }
        }
    )
    LaunchedEffect(multiSelectBackState) {
        snapshotFlow { multiSelectBackState.transitionState }.collect { transitionState ->
            if (
                transitionState is NavigationEventTransitionState.InProgress &&
                transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
            ) {
                multiSelectBackProgress.snapTo(transitionState.latestEvent.progress.coerceIn(0f, 1f))
            }
        }
    }
    LaunchedEffect(isEntityMultiSelectActive) {
        if (!isEntityMultiSelectActive) {
            multiSelectBackProgress.snapTo(0f)
        }
    }

    var lastEditDialog by remember { mutableStateOf<EditDialogRequest?>(null) }
    if (editDialog != null) lastEditDialog = editDialog
    var lastSelectedEntity by remember { mutableStateOf<HaEntity?>(null) }
    if (selectedEntity != null) lastSelectedEntity = selectedEntity

    // 统一在根层提供 LocalBackdrop，让顶栏、悬浮工具栏、AlertDialog、BottomSheet 与下拉菜单全部启用原生毛玻璃模糊
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Scaffold(
            edgeToEdge = true,
            containerColor = pageColor,
            snackbarHost = { SnackbarHost(state = snackbarHostState) },
            topBar = {
                if (activeTab == BridgeTab.ENTITIES) {
                    EntitiesSearchTopAppBar(
                        title = if (isEntityMultiSelectActive) {
                            if (multiSelectedIds.isEmpty()) "多选设备" else "已选 ${multiSelectedIds.size} 项"
                        } else {
                            activeTab.title
                        },
                        query = entitySearchKeyword,
                        onQueryChange = { entitySearchKeyword = it },
                        expanded = entitySearchExpanded,
                        onExpandedChange = { entitySearchExpanded = it },
                        onSearch = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                        onCancel = {
                            entitySearchKeyword = ""
                            entitySearchExpanded = false
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                        onBack = {
                            if (isEntityMultiSelectActive) exitMultiSelect() else onBack?.invoke()
                        },
                        scrollBehavior = scrollBehavior,
                        backdrop = backdrop,
                        extraActions = {
                            if (multiSelectMode) {
                                BridgeBarIconButton(COUIIcons.Basic.Close, "退出多选", ::exitMultiSelect)
                            } else {
                                BridgeBarIconButton(COUIIcons.SelectAll, "多选") {
                                    multiSelectMode = true
                                    multiSelectedIds = emptySet()
                                }
                            }
                        }
                    )
                } else {
                    BridgeTopAppBar(
                        title = activeTab.title,
                        scrollBehavior = scrollBehavior,
                        onBack = onBack,
                        actions = {
                            if (activeTab == BridgeTab.CONTROL) {
                                BridgeBarIconButton(COUIIcons.Refresh, "重启作用域") {
                                    showRestartPanel = true
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isEntityMultiSelectActive) {
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding())
                            .graphicsLayer {
                                val p = multiSelectBackProgress.value
                                translationY = with(density) { 48.dp.toPx() } * p
                                alpha = (1f - p * 0.7f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BottomFloatingToolbar(visible = true) {
                            BottomFloatingToolbarGroup {
                                BottomFloatingToolbarItem(
                                    label = if (allDisplayedSelected) "取消全选" else "全选",
                                    enabled = displayedIds.isNotEmpty(),
                                    onClick = {
                                        multiSelectedIds = if (allDisplayedSelected) emptySet() else displayedIds
                                    }
                                )
                                BottomFloatingToolbarItem(
                                    label = when {
                                        allSelectedAreManuallyExcluded -> "恢复 (${multiSelectedIds.size})"
                                        multiSelectedIds.isNotEmpty() -> "屏蔽 (${multiSelectedIds.size})"
                                        else -> "屏蔽"
                                    },
                                    enabled = multiSelectedIds.isNotEmpty(),
                                    onClick = {
                                        val count = multiSelectedIds.size
                                        if (allSelectedAreManuallyExcluded) {
                                            updateConfig { it.removeManualExcludeEntities(multiSelectedIds) }
                                            showTip("已恢复 $count 个设备")
                                        } else {
                                            updateConfig { it.addManualExcludeEntities(multiSelectedIds) }
                                            showTip("已屏蔽 $count 个设备")
                                        }
                                        exitMultiSelect()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (page == BridgePage.HOME) {
                    FloatingActionButton(
                        onClick = ::triggerFluidTest,
                        backdrop = backdrop
                    ) {
                        Icon(
                            imageVector = COUIIcons.Play,
                            contentDescription = "测试弹出流体云",
                            tint = FloatingActionButtonDefaults.ContentColor
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentPadding = innerPadding.bridgeListPadding(
                top = 12.dp,
                bottom = when {
                    isEntityMultiSelectActive -> 96.dp
                    page == BridgePage.HOME -> 88.dp
                    else -> 24.dp
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
            ) {
                when (activeTab) {
                    BridgeTab.CONTROL -> ControlCenterTab(
                        config = config,
                        envStatus = envStatus,
                        contentPadding = contentPadding,
                        scrollBehavior = scrollBehavior,
                        onUpdateConfig = ::updateConfig,
                        onRequestPermission = onRequestNotificationPermission,
                        onOpenEntities = { onOpenPage(BridgePage.ENTITIES) },
                        onOpenLab = { onOpenPage(BridgePage.LAB) }
                    )

                    BridgeTab.ENTITIES -> HomeAssistantTab(
                        context = context,
                        config = config,
                        syncedEntities = entities,
                        rawEffectiveEntities = rawEffectiveEntities,
                        filteredEntities = filteredEntities,
                        displayedEntities = displayedEntities,
                        searchKeyword = entitySearchKeyword,
                        showExcludedInList = showExcludedEntities,
                        onShowExcludedChange = { showExcludedEntities = it },
                        multiSelectMode = multiSelectMode,
                        multiSelectedIds = multiSelectedIds,
                        onToggleMultiSelectId = { eid ->
                            multiSelectedIds = if (eid in multiSelectedIds) {
                                multiSelectedIds - eid
                            } else {
                                multiSelectedIds + eid
                            }
                        },
                        syncing = syncing,
                        contentPadding = contentPadding,
                        scrollBehavior = scrollBehavior,
                        onUpdateConfig = ::updateConfig,
                        onOpenEdit = { req ->
                            editBuffer = req.initialValue
                            editDialog = req
                        },
                        onSync = ::triggerSyncEntities,
                        onSelectEntity = { entity -> selectedEntity = entity }
                    )

                    BridgeTab.LAB -> LocalNluLabTab(
                        context = context,
                        config = config,
                        entities = filteredEntities,
                        contentPadding = contentPadding,
                        scrollBehavior = scrollBehavior,
                        onShowTip = ::showTip
                    )
                }

                if (restartDialog != null) {
                    restartDialog(showRestartPanel) { showRestartPanel = false }
                } else {
                    DefaultRestartSpeechDialog(
                        show = showRestartPanel,
                        onDismiss = { showRestartPanel = false }
                    )
                }
            }

            val activeDialog = editDialog ?: lastEditDialog
            OverlayAlertDialog(
                show = editDialog != null,
                title = activeDialog?.title.orEmpty(),
                summary = activeDialog?.summary,
                confirmText = "保存",
                cancelText = "取消",
                onConfirm = {
                    editDialog?.onConfirm?.invoke(editBuffer.trim())
                    editDialog = null
                    showTip("已保存")
                },
                onCancel = { editDialog = null },
                content = {
                    if (activeDialog != null) {
                        TextField(
                            value = editBuffer,
                            onValueChange = { editBuffer = it },
                            label = activeDialog.placeholder,
                            backgroundMode = TextFieldMode.Line,
                            showClearButton = true,
                            singleLine = activeDialog.singleLine,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DialogDefaults.insideMargin.width)
                                .padding(top = 8.dp)
                        )
                    }
                }
            )

            val currentEntity = selectedEntity ?: lastSelectedEntity
            OverlayBottomSheet(
                show = selectedEntity != null,
                title = currentEntity?.name,
                onDismissRequest = { selectedEntity = null }
            ) {
                if (currentEntity != null) {
                    EntityControlSheetContent(
                        entity = currentEntity,
                        isManuallyExcluded = config.isEntityManuallyExcluded(currentEntity),
                        busy = actionBusy,
                        onAction = { service, verb, isOn, undoSvc, nextState ->
                            executeEntityAction(currentEntity, service, verb, isOn, undoSvc, nextState)
                        },
                        onToggleManualExclude = {
                            val wasExcluded = config.isEntityManuallyExcluded(currentEntity)
                            updateConfig { it.toggleManualExcludeEntity(currentEntity.entityId) }
                            showTip(if (wasExcluded) "已恢复：${currentEntity.name}" else "已屏蔽：${currentEntity.name}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntitiesSearchTopAppBar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: Backdrop,
    extraActions: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val clearTint = COUITheme.colorScheme.onSurface.copy(alpha = 0.28f)
    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != 0f) {
            scrollBehavior.state.heightOffsetLimit = 0f
        }
    }
    AppBarBlurOverlay(
        scrollFraction = {
            AppBarBlurOverlayDefaults.scrollAlpha(-scrollBehavior.state.contentOffset, density)
        },
        containerColor = AppBarBlurOverlayDefaults.containerColor(),
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .height(TopAppBarDefaults.CollapsedHeight),
            contentAlignment = Alignment.Center
        ) {
            TopAppBarSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                placeholder = "搜索设备 / 实体 ID / 房间",
                cancelText = "取消",
                onSearch = onSearch,
                onCancel = onCancel,
                searchButtonContentDescription = "搜索设备",
                gradientColor = Color.Transparent,
                backdrop = backdrop,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Canvas(
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = "清除" }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onQueryChange("") }
                                )
                        ) {
                            val unit = size.width / 36f
                            drawCircle(clearTint, radius = 9f * unit)
                            val path = Path().apply {
                                moveTo(18f, 16.868f)
                                lineTo(15.031f, 13.898f)
                                lineTo(13.899f, 15.03f)
                                lineTo(16.869f, 18f)
                                lineTo(13.898f, 20.97f)
                                lineTo(15.03f, 22.101f)
                                lineTo(18f, 19.131f)
                                lineTo(20.97f, 22.101f)
                                lineTo(22.102f, 20.969f)
                                lineTo(19.132f, 18f)
                                lineTo(22.101f, 15.03f)
                                lineTo(20.969f, 13.899f)
                                close()
                            }
                            withTransform({ scale(unit, unit, Offset.Zero) }) {
                                drawPath(path, Color.White)
                            }
                        }
                    }
                },
                titleContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BridgeBarIconButton(COUIIcons.Back, "返回", onBack)
                        Spacer(modifier = Modifier.width(TopAppBarDefaults.NavigationIconGap))
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight(550),
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                extraActions = extraActions
            )
        }
    }
}

@Composable
private fun EntityControlSheetContent(
    entity: HaEntity,
    isManuallyExcluded: Boolean,
    busy: Boolean,
    onAction: (service: String, verb: String, isOn: Boolean, undoService: String?, nextState: String?) -> Unit,
    onToggleManualExclude: () -> Unit
) {
    val stateLabel = when (entity.state.lowercase()) {
        "on", "open", "playing", "unlocked", "cleaning" -> "开启"
        "off", "closed", "paused", "idle", "locked", "docked" -> "关闭"
        "" -> "未知"
        else -> entity.state
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Card(
            cornerRadius = CardDefaults.GroupCornerRadius,
            weight = CardDefaults.GroupWeight,
            insideMargin = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HaEntityIcon(
                    entity = entity,
                    dimmed = isManuallyExcluded,
                    size = 40.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.entityId,
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${entity.areaName.ifBlank { "未分区" }} · ${domainLabel(entity.domain)} · $stateLabel",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (entity.domain) {
            "scene", "script" -> {
                TextButton(
                    text = if (busy) "执行中..." else "确认执行",
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { onAction("turn_on", "执行", true, null, null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            "cover" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "打开",
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { onAction("open_cover", "打开", true, "close_cover", "open") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "关闭",
                        enabled = !busy,
                        onClick = { onAction("close_cover", "关闭", false, "open_cover", "closed") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "停止",
                        enabled = !busy,
                        onClick = { onAction("stop_cover", "停止", false, null, null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            "lock" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "解锁",
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { onAction("unlock", "解锁", true, "lock", "unlocked") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "上锁",
                        enabled = !busy,
                        onClick = { onAction("lock", "上锁", false, "unlock", "locked") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            "vacuum" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "开始清扫",
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { onAction("start", "启动", true, "return_to_base", "cleaning") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "回充",
                        enabled = !busy,
                        onClick = { onAction("return_to_base", "回充", false, "start", "returning") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            else -> {
                val isCurrentlyOn = entity.state.equals("on", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "开启",
                        enabled = !busy,
                        colors = if (!isCurrentlyOn) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { onAction("turn_on", "开启", true, "turn_off", "on") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "关闭",
                        enabled = !busy,
                        colors = if (isCurrentlyOn) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { onAction("turn_off", "关闭", false, "turn_on", "off") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "切换",
                        enabled = !busy,
                        onClick = {
                            val nextOn = !isCurrentlyOn
                            onAction("toggle", "切换", nextOn, "toggle", if (nextOn) "on" else "off")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            cornerRadius = CardDefaults.GroupCornerRadius,
            weight = CardDefaults.GroupWeight,
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = "屏蔽此设备",
                checked = isManuallyExcluded,
                cardListPosition = CardListPosition.Full,
                onCheckedChange = { onToggleManualExclude() }
            )
        }
    }
}

private fun readEnvStatus(context: Context, syncedCount: Int): EnvStatus {
    val notifGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return EnvStatus(
        xposedWorldReadable = ConfigStore.isWorldReadableSupported(),
        notificationGranted = notifGranted,
        promotedGranted = FluidCloudNotifier.canPostPromoted(context),
        syncedCount = syncedCount,
        lastSyncTime = ConfigStore.loadLastSyncTimeFromApp(context)
    )
}
