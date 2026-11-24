package cc.csxcl.xiaobuhabridge.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.csxcl.xiaobuhabridge.config.BridgeConfig
import cc.csxcl.xiaobuhabridge.config.FeedbackMode
import cc.csxcl.xiaobuhabridge.config.FluidCloudDisplayMode
import cc.csxcl.xiaobuhabridge.fluid.FluidCloudNotifier
import cc.csxcl.xiaobuhabridge.fluid.FluidCloudPayload
import cc.csxcl.xiaobuhabridge.ha.HaClient
import cc.csxcl.xiaobuhabridge.model.HaEntity
import cc.csxcl.xiaobuhabridge.model.HaServiceCall
import cc.csxcl.xiaobuhabridge.model.NluResult
import cc.csxcl.xiaobuhabridge.nlu.LocalNluEngine
import cc.csxcl.xiaobuhabridge.ui.BridgeGroupTitle
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.ButtonPreference
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val feedbackModeLabels = listOf(
    "仅流体云",
    "流体云 + 本地播报",
    "流体云 + 云端播报",
    "流体云 + 仅气泡"
)

private val fluidDisplayModeLabels = listOf(
    "展开卡片",
    "对称胶囊",
    "持续展开"
)

private val durationOptionsMs = listOf(3000L, 4500L, 6000L, 8000L)
private val durationLabels = listOf("3 秒", "4.5 秒", "6 秒", "8 秒")

private val commonExcludePresets = listOf(
    "indicator",
    "sensor",
    "指示灯",
    "童锁",
    "蜂鸣器",
    "背光",
    "led"
)

private val defaultFilterDomains = listOf(
    "light", "switch", "climate", "cover", "fan", "scene", "script",
    "media_player", "vacuum", "humidifier", "input_boolean", "remote", "water_heater", "lock"
)

internal fun domainLabel(domain: String): String = when (domain.lowercase()) {
    "light" -> "灯光"
    "switch" -> "开关"
    "climate" -> "空调"
    "cover" -> "窗帘"
    "fan" -> "风扇"
    "scene" -> "场景"
    "script" -> "脚本"
    "media_player" -> "媒体"
    "vacuum" -> "扫地机"
    "humidifier" -> "加湿/净化"
    "input_boolean" -> "虚拟开关"
    "remote" -> "遥控"
    "water_heater" -> "热水器"
    "lock" -> "门锁"
    else -> domain
}

@Composable
internal fun ControlCenterTab(
    config: BridgeConfig,
    envStatus: EnvStatus,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onUpdateConfig: ((BridgeConfig) -> BridgeConfig) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenEntities: () -> Unit,
    onOpenLab: () -> Unit
) {
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding
    ) {
        item(key = "secondary_pages") {
            BridgeGroupTitle("功能页面")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                ArrowPreference(
                    title = "家居实体",
                    cardListPosition = CardListPosition.Head,
                    onClick = onOpenEntities
                )
                ArrowPreference(
                    title = "指令调试",
                    cardListPosition = CardListPosition.Tail,
                    onClick = onOpenLab
                )
            }
        }

        item(key = "core_switches") {
            BridgeGroupTitle("桥接总控")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                SwitchPreference(
                    title = "启用小布指令桥接",
                    checked = config.enabled,
                    cardListPosition = CardListPosition.Head,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(enabled = v) } }
                )
                SwitchPreference(
                    title = "顶部弹出流体云",
                    checked = config.enableFluidCloud,
                    cardListPosition = CardListPosition.Middle,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableFluidCloud = v) } }
                )
                ArrowPreference(
                    title = "实况通知权限",
                    cardListPosition = CardListPosition.Middle,
                    endActions = {
                        val ready = envStatus.notificationGranted && envStatus.promotedGranted
                        StatusBadgeText(text = if (ready) "已就绪" else "需授权", ok = ready)
                    },
                    onClick = onRequestPermission
                )
                SwitchPreference(
                    title = "模拟设备模式",
                    summary = "未配置 HA 时使用内置设备",
                    checked = config.demoMode,
                    cardListPosition = CardListPosition.Tail,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(demoMode = v) } }
                )
            }
        }

        item(key = "feedback_strategy") {
            BridgeGroupTitle("小布与流体云反馈")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                OverlayDropdownPreference(
                    title = "小布反馈方式",
                    items = feedbackModeLabels,
                    selectedIndex = config.feedbackMode.id,
                    cardListPosition = CardListPosition.Head,
                    onSelectedIndexChange = { idx ->
                        onUpdateConfig { it.copy(feedbackMode = FeedbackMode.fromId(idx)) }
                    }
                )
                OverlayDropdownPreference(
                    title = "流体云形态",
                    items = fluidDisplayModeLabels,
                    selectedIndex = config.fluidCloudDisplayMode.id,
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { idx ->
                        onUpdateConfig { it.copy(fluidCloudDisplayMode = FluidCloudDisplayMode.fromId(idx)) }
                    }
                )
                val durIdx = durationOptionsMs.indexOfFirst { it == config.fluidCloudDurationMs }.coerceAtLeast(1)
                OverlayDropdownPreference(
                    title = "停留时长",
                    items = durationLabels,
                    selectedIndex = durIdx,
                    cardListPosition = CardListPosition.Middle,
                    onSelectedIndexChange = { idx ->
                        onUpdateConfig { it.copy(fluidCloudDurationMs = durationOptionsMs[idx]) }
                    }
                )
                SwitchPreference(
                    title = "HA 本地 Assist 兜底",
                    summary = "前缀：${config.assistTriggerPrefixes}",
                    checked = config.enableLocalAssistFallback,
                    cardListPosition = CardListPosition.Tail,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(enableLocalAssistFallback = v) } }
                )
            }
        }
    }
}

@Composable
internal fun HomeAssistantTab(
    context: Context,
    config: BridgeConfig,
    syncedEntities: List<HaEntity>,
    rawEffectiveEntities: List<HaEntity>,
    filteredEntities: List<HaEntity>,
    displayedEntities: List<HaEntity>,
    searchKeyword: String,
    showExcludedInList: Boolean,
    onShowExcludedChange: (Boolean) -> Unit,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<String>,
    onToggleMultiSelectId: (String) -> Unit,
    syncing: Boolean,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onUpdateConfig: ((BridgeConfig) -> BridgeConfig) -> Unit,
    onOpenEdit: (EditDialogRequest) -> Unit,
    onSync: () -> Unit,
    onSelectEntity: (HaEntity) -> Unit
) {
    val allowedDomains = remember(config.allowedDomainsRaw) { config.parsedAllowedDomains() }
    val excludedKeywords = remember(config.excludedKeywordsRaw) { config.parsedExcludedKeywords() }
    val manualExcludedIds = remember(config.excludedEntityIdsRaw) { config.parsedExcludedEntityIds() }

    val availableDomains = remember(rawEffectiveEntities) {
        val present = rawEffectiveEntities.map { it.domain.lowercase() }.distinct()
        val ordered = defaultFilterDomains.filter { it in present } + present.filter { it !in defaultFilterDomains }
        if (ordered.isNotEmpty()) ordered else listOf("light", "switch", "climate", "cover", "fan", "scene")
    }

    val grouped = remember(displayedEntities) {
        displayedEntities.groupBy { it.areaName.ifBlank { "未分区 / 场景" } }
    }
    val lastSync = cc.csxcl.xiaobuhabridge.config.ConfigStore.loadLastSyncTimeFromApp(context)
    val lastSyncFormatted = remember(lastSync) {
        if (lastSync > 0L) SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync)) else ""
    }
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding
    ) {
        item(key = "ha_connection") {
            BridgeGroupTitle("Home Assistant 连接")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp)
            ) {
                ArrowPreference(
                    title = "局域网地址",
                    summary = config.haLanUrl.ifBlank { "未填写" },
                    cardListPosition = CardListPosition.Head,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "局域网地址",
                                initialValue = config.haLanUrl,
                                placeholder = "http://192.168.31.100:8123",
                                onConfirm = { v -> onUpdateConfig { it.copy(haLanUrl = v) } }
                            )
                        )
                    }
                )
                ArrowPreference(
                    title = "外网备用地址",
                    summary = config.haExternalUrl.ifBlank { "未填写" },
                    cardListPosition = CardListPosition.Middle,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "外网备用地址",
                                initialValue = config.haExternalUrl,
                                placeholder = "https://ha.example.com",
                                onConfirm = { v -> onUpdateConfig { it.copy(haExternalUrl = v) } }
                            )
                        )
                    }
                )
                ArrowPreference(
                    title = "长期访问令牌",
                    summary = if (config.haToken.isNotBlank()) "已配置 (••••${config.haToken.takeLast(4)})" else "未填写",
                    cardListPosition = CardListPosition.Middle,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "长期访问令牌",
                                summary = "在 HA 个人资料底部生成，仅保存在本机",
                                initialValue = config.haToken,
                                placeholder = "粘贴 eyJ... 开头的令牌",
                                onConfirm = { v -> onUpdateConfig { it.copy(haToken = v) } }
                            )
                        )
                    }
                )
                ArrowPreference(
                    title = "默认房间",
                    summary = config.defaultRoom.ifBlank { "未设置" },
                    cardListPosition = CardListPosition.Middle,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "默认房间",
                                summary = "未说房间名时优先匹配",
                                initialValue = config.defaultRoom,
                                placeholder = "例如：卧室",
                                onConfirm = { v -> onUpdateConfig { it.copy(defaultRoom = v) } }
                            )
                        )
                    }
                )
                ArrowPreference(
                    title = "自定义别名",
                    summary = config.customAliasesRaw.ifBlank { "未设置" },
                    cardListPosition = CardListPosition.Middle,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "自定义别名",
                                summary = "每行一条：口语词=实体名",
                                initialValue = config.customAliasesRaw,
                                placeholder = "大圆盘=卧室吸顶灯",
                                singleLine = false,
                                onConfirm = { v -> onUpdateConfig { it.copy(customAliasesRaw = v) } }
                            )
                        )
                    }
                )
                ButtonPreference(
                    title = "实体同步",
                    summary = when {
                        syncing -> "同步中..."
                        syncedEntities.isNotEmpty() && lastSyncFormatted.isNotEmpty() ->
                            "$lastSyncFormatted · 共 ${syncedEntities.size} 个（生效 ${filteredEntities.size} 个）"
                        else -> null
                    },
                    buttonText = if (syncing) "同步中..." else "立即同步",
                    onButtonClick = onSync,
                    onClick = onSync,
                    enabled = !syncing,
                    cardListPosition = CardListPosition.Tail
                )
            }
        }

        item(key = "entity_filter_panel") {
            BridgeGroupTitle("设备筛选")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "类型",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowLayout(
                        itemSpacing = 8.dp,
                        lineSpacing = 8.dp,
                        expandable = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Chip(
                            selected = allowedDomains.isEmpty(),
                            onClick = { onUpdateConfig { it.copy(allowedDomainsRaw = "") } },
                            label = "全部"
                        )
                        availableDomains.forEach { domain ->
                            Chip(
                                selected = domain in allowedDomains,
                                onClick = { onUpdateConfig { it.toggleAllowedDomain(domain) } },
                                label = domainLabel(domain)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "快捷排除",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowLayout(
                        itemSpacing = 8.dp,
                        lineSpacing = 8.dp,
                        expandable = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonExcludePresets.forEach { kw ->
                            val isExcluded = excludedKeywords.any { it.equals(kw, ignoreCase = true) }
                            Chip(
                                selected = isExcluded,
                                onClick = { onUpdateConfig { it.toggleExcludedKeyword(kw) } },
                                label = "排除 $kw"
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "自定义排除关键词",
                    summary = if (excludedKeywords.isEmpty()) "未设置" else excludedKeywords.joinToString("、"),
                    cardListPosition = CardListPosition.Middle,
                    onClick = {
                        onOpenEdit(
                            EditDialogRequest(
                                title = "自定义排除关键词",
                                summary = "逗号或换行分隔",
                                initialValue = config.excludedKeywordsRaw,
                                placeholder = "indicator,sensor,指示灯,童锁",
                                singleLine = false,
                                onConfirm = { v -> onUpdateConfig { it.copy(excludedKeywordsRaw = v) } }
                            )
                        )
                    }
                )
                SwitchPreference(
                    title = "显示已排除设备",
                    summary = if (manualExcludedIds.isNotEmpty()) "已手动屏蔽 ${manualExcludedIds.size} 个" else null,
                    checked = showExcludedInList,
                    cardListPosition = if (manualExcludedIds.isEmpty()) CardListPosition.Tail else CardListPosition.Middle,
                    onCheckedChange = onShowExcludedChange
                )
                if (manualExcludedIds.isNotEmpty()) {
                    ArrowPreference(
                        title = "清空手动屏蔽",
                        summary = "恢复 ${manualExcludedIds.size} 个设备",
                        cardListPosition = CardListPosition.Tail,
                        onClick = { onUpdateConfig { it.copy(excludedEntityIdsRaw = "") } }
                    )
                }
            }
        }

        item(key = "entity_header") {
            val baseLabel = if (syncedEntities.isNotEmpty() && lastSyncFormatted.isNotEmpty()) {
                "已同步实体 (${filteredEntities.size}/${syncedEntities.size} · $lastSyncFormatted)"
            } else {
                "内置模拟实体 (${filteredEntities.size}/${rawEffectiveEntities.size})"
            }
            val headerLabel = if (searchKeyword.isNotBlank()) {
                "搜索结果 (${displayedEntities.size}) · $baseLabel"
            } else {
                baseLabel
            }
            BridgeGroupTitle(headerLabel)
        }

        if (grouped.isEmpty()) {
            item(key = "entity_empty") {
                Card(
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    weight = CardDefaults.GroupWeight,
                    insideMargin = PaddingValues(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (searchKeyword.isNotBlank()) "无匹配设备" else "当前筛选下无设备",
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

        grouped.forEach { (area, list) ->
            item(key = "area_title_$area") {
                BridgeGroupTitle("$area (${list.size})")
            }
            item(key = "area_$area") {
                Card(
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    weight = CardDefaults.GroupWeight,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                ) {
                    list.forEachIndexed { idx, entity ->
                        val pos = when {
                            list.size == 1 -> CardListPosition.Full
                            idx == 0 -> CardListPosition.Head
                            idx == list.lastIndex -> CardListPosition.Tail
                            else -> CardListPosition.Middle
                        }
                        val manualBlocked = config.isEntityManuallyExcluded(entity)
                        val kwBlocked = config.isEntityExcludedByKeyword(entity)
                        val badgeText = when {
                            manualBlocked -> "已手动屏蔽"
                            kwBlocked -> "关键词排除"
                            entity.state.isNotBlank() -> "${entity.domain} · ${entity.state}"
                            else -> entity.domain
                        }
                        val summaryText = entity.entityId + if (entity.aliases.isNotEmpty()) " · 别名: ${entity.aliases.joinToString("/")}" else ""
                        if (multiSelectMode) {
                            val checked = entity.entityId in multiSelectedIds
                            CheckboxPreference(
                                title = entity.name,
                                summary = summaryText,
                                checked = checked,
                                cardListPosition = pos,
                                startAction = {
                                    HaEntityIcon(
                                        entity = entity,
                                        dimmed = manualBlocked || kwBlocked
                                    )
                                },
                                endActions = {
                                    Text(
                                        text = badgeText,
                                        style = COUITheme.textStyles.footnote1,
                                        color = if (manualBlocked || kwBlocked) {
                                            COUITheme.colorScheme.onSurfaceVariantSummary
                                        } else {
                                            COUITheme.colorScheme.onSurfaceVariantActions
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                },
                                onCheckedChange = { onToggleMultiSelectId(entity.entityId) }
                            )
                        } else {
                            ArrowPreference(
                                title = entity.name,
                                summary = summaryText,
                                cardListPosition = pos,
                                startAction = {
                                    HaEntityIcon(
                                        entity = entity,
                                        dimmed = manualBlocked || kwBlocked
                                    )
                                },
                                centerStartAction = true,
                                endActions = {
                                    Text(
                                        text = badgeText,
                                        style = COUITheme.textStyles.footnote1,
                                        color = if (manualBlocked || kwBlocked) {
                                            COUITheme.colorScheme.onSurfaceVariantSummary
                                        } else {
                                            COUITheme.colorScheme.onSurfaceVariantActions
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                },
                                onClick = { onSelectEntity(entity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface LabReceiptState {
    data object Idle : LabReceiptState
    data class Missed(val costUs: Long) : LabReceiptState
    data class Hit(
        val costUs: Long,
        val result: NluResult,
        val execSuccess: Boolean? = null,
        val execLatencyMs: Long? = null,
        val execMessage: String? = null
    ) : LabReceiptState
}

@Composable
internal fun LocalNluLabTab(
    context: Context,
    config: BridgeConfig,
    entities: List<HaEntity>,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onShowTip: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("帮我把卧室西顶灯关了") }
    var receipt by remember { mutableStateOf<LabReceiptState>(LabReceiptState.Idle) }

    val quickSamples = listOf(
        "帮我把卧室西顶灯关了",
        "全屋关灯",
        "把客厅空调调到26度",
        "拉开客厅窗帘",
        "我要睡觉了",
        "打开手电筒"
    )

    fun runQuery(execute: Boolean) {
        val text = query.trim()
        if (text.isEmpty()) return
        val engine = LocalNluEngine(entities, config.defaultRoom, config.parsedCustomAliases())
        val t0 = System.nanoTime()
        val parsed = engine.parse(text)
        val costUs = (System.nanoTime() - t0) / 1000

        if (parsed == null) {
            receipt = LabReceiptState.Missed(costUs)
            return
        }

        val hitState = LabReceiptState.Hit(costUs = costUs, result = parsed)
        receipt = hitState

        if (execute) {
            scope.launch {
                val exec = withContext(Dispatchers.IO) {
                    HaClient.executeCalls(config, parsed.serviceCalls, parsed.replyText)
                }
                receipt = hitState.copy(
                    execSuccess = exec.success,
                    execLatencyMs = exec.latencyMs,
                    execMessage = exec.message
                )
                if (config.enableFluidCloud) {
                    val undo = parsed.undoCall
                    val firstEntity = parsed.matchedEntities.firstOrNull()
                    FluidCloudNotifier.show(
                        context,
                        FluidCloudPayload(
                            shortCapsuleText = if (exec.success) parsed.capsuleShortText else "HA 控制失败",
                            title = if (exec.success) parsed.cardTitle else "控制失败",
                            subtitle = if (exec.success) "${parsed.cardSubtitle} · ${exec.latencyMs}ms" else exec.message,
                            domain = firstEntity?.domain ?: "light",
                            isTurnOnStyle = parsed.isTurnOnStyle,
                            isError = !exec.success,
                            durationMs = config.fluidCloudDurationMs,
                            displayMode = config.fluidCloudDisplayMode,
                            undoDomain = if (exec.success) undo?.domain else null,
                            undoService = if (exec.success) undo?.service else null,
                            undoPayloadJson = if (exec.success) undo?.toPayloadJson()?.toString() else null,
                            undoTargetLabel = firstEntity?.displayName,
                            icon = firstEntity?.icon.orEmpty(),
                            iconColor = firstEntity?.iconColor.orEmpty()
                        )
                    )
                }
                onShowTip(exec.message)
            }
        }
    }

    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding
    ) {
        item(key = "lab_input") {
            BridgeGroupTitle("指令输入")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                insideMargin = PaddingValues(16.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "输入口令",
                    backgroundMode = TextFieldMode.Line,
                    showClearButton = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowLayout(
                    itemSpacing = 8.dp,
                    lineSpacing = 8.dp,
                    expandable = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickSamples.forEach { sample ->
                        Chip(
                            selected = query == sample,
                            onClick = {
                                query = sample
                                runQuery(execute = false)
                            },
                            label = sample
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "仅解析",
                        onClick = { runQuery(execute = false) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "执行",
                        onClick = { runQuery(execute = true) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "lab_result") {
            BridgeGroupTitle("解析与执行回执")
            Card(
                cornerRadius = CardDefaults.GroupCornerRadius,
                weight = CardDefaults.GroupWeight,
                insideMargin = PaddingValues(16.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                when (val current = receipt) {
                    LabReceiptState.Idle -> {
                        Text(
                            text = "输入指令后点击解析或执行",
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    is LabReceiptState.Missed -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "未命中家居规则",
                                    style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                                    color = COUITheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${current.costUs} μs",
                                    style = COUITheme.textStyles.footnote1,
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Text(
                                text = "已放行给小布原生处理",
                                style = COUITheme.textStyles.body2,
                                color = COUITheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    is LabReceiptState.Hit -> {
                        LabHitReceiptContent(current)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabHitReceiptContent(state: LabReceiptState.Hit) {
    val parsed = state.result
    val formattedCode = remember(parsed.serviceCalls) {
        formatServiceCallsCode(parsed.serviceCalls)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "命中本地规则",
                style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = COUITheme.colorScheme.primary
            )
            Text(
                text = "${state.costUs} μs",
                style = COUITheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                color = COUITheme.colorScheme.onSurfaceVariantSummary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReceiptFieldRow("动作意图", parsed.action.name, monospaceValue = true)
            ReceiptFieldRow("流体云胶囊", parsed.capsuleShortText)
            ReceiptFieldRow("小布回复", parsed.replyText)
        }

        HorizontalDivider()

        Text(
            text = "目标实体 (${parsed.matchedEntities.size})",
            style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
            color = COUITheme.colorScheme.onSurfaceVariantSummary
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            parsed.matchedEntities.forEachIndexed { idx, entity ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = buildString {
                            append("${idx + 1}. ${entity.name}")
                            if (entity.areaName.isNotBlank()) {
                                append(" · ${entity.areaName}")
                            }
                        },
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurface
                    )
                    Text(
                        text = entity.entityId,
                        style = COUITheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = "执行载荷 (${parsed.serviceCalls.size})",
            style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
            color = COUITheme.colorScheme.onSurfaceVariantSummary
        )
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(COUITheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = formattedCode,
                    style = COUITheme.textStyles.footnote1.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = COUITheme.colorScheme.onSurface
                )
            }
        }

        if (state.execSuccess != null) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.execSuccess) "执行成功" else "执行失败",
                    style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = if (state.execSuccess) COUITheme.colorScheme.primary else COUITheme.colorScheme.error
                )
                state.execLatencyMs?.let { ms ->
                    Text(
                        text = "$ms ms",
                        style = COUITheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                        color = COUITheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            state.execMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    text = msg,
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun ReceiptFieldRow(
    label: String,
    value: String,
    monospaceValue: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = if (monospaceValue) {
                COUITheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace)
            } else {
                COUITheme.textStyles.footnote1
            },
            color = COUITheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatServiceCallsCode(calls: List<HaServiceCall>): String =
    calls.joinToString("\n\n") { call ->
        val payload = call.toPayloadJson().toString(2)
        "// POST /api/services/${call.domain}/${call.service}\n$payload"
    }

@Composable
private fun StatusBadgeText(text: String, ok: Boolean) {
    Text(
        text = text,
        style = COUITheme.textStyles.footnote1,
        color = if (ok) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier.padding(end = 4.dp)
    )
}
