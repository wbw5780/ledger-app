package com.ledger.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.data.Record
import com.ledger.app.data.RecordSource
import com.ledger.app.util.IconStore
import com.ledger.app.util.SeriesPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------- 尺寸规范

/** 页面左右留白 */
val PagePadding = 16.dp
/** 卡片间距 */
val CardGap = 12.dp
val CardRadius = 20.dp
val CardShape = RoundedCornerShape(CardRadius)

// ---------------------------------------------------------------- 卡片

/** 品牌卡片：圆角 20dp；亮色极淡阴影，暗色 1dp 描边 + 明度差分层 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardShape,
    color: Color = LocalLedger.current.card,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalLedger.current
    var m = modifier
    if (!p.dark) m = m.shadow(10.dp, shape, ambientColor = Color(0x0D000000), spotColor = Color(0x17000000))
    m = m.clip(shape).background(color)
    if (p.dark) m = m.border(1.dp, p.cardBorder, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/** 分组列表中单行的形状：首行上圆角、末行下圆角 */
fun groupShape(first: Boolean, last: Boolean): Shape {
    val r = CardRadius
    return RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
}

/** 分组列表行容器（无阴影，靠卡片与背景明度差分层；暗色加左右描边感由底色差体现） */
@Composable
fun GroupedRow(first: Boolean, last: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val p = LocalLedger.current
    val shape = groupShape(first, last)
    Box(Modifier.fillMaxWidth().clip(shape).background(p.card).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (!last) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 68.dp, end = 16.dp).fillMaxWidth().height(1.dp)
                    .background(p.hairline)
            )
        }
    }
}

// ---------------------------------------------------------------- 金额

private fun grouped(absCents: Long): String =
    String.format(Locale.US, "%,d.%02d", absCents / 100, absCents % 100)

/**
 * 金额文本：等宽数字 + SemiBold；¥ 比数字小一号。
 * signed=true 时正数带 +，负数带 -。
 */
@Composable
fun AmountText(
    cents: Long,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    signed: Boolean = true,
    weight: FontWeight = FontWeight.SemiBold,
    /** 超过该字符数时按比例缩小字号（防止大额撑出卡片），null 不缩放 */
    fitChars: Int? = null,
) {
    val sign = when {
        cents < 0 -> "-"
        signed && cents > 0 -> "+"
        else -> ""
    }
    val digits = grouped(abs(cents))
    val len = sign.length + 1 + digits.length
    val size = if (fitChars != null && len > fitChars) fontSize * (fitChars.toFloat() / len) else fontSize
    val text = remember(cents, sign, size) {
        buildAnnotatedString {
            append(sign)
            withStyle(SpanStyle(fontSize = size * 0.68f)) { append("¥") }
            append(digits)
        }
    }
    Text(
        text,
        modifier = modifier,
        color = color,
        maxLines = 1,
        style = AmountStyle.copy(
            fontSize = size,
            fontWeight = weight,
            letterSpacing = if (size.value >= 28f) (-0.5).sp else 0.sp,
        ),
    )
}

// ---------------------------------------------------------------- 图标容器

/** 项目图标：统一装在圆角方形（14dp）容器内，容器底色取图标主色的淡色；无图标时显示首字 */
@Composable
fun ProjectIcon(iconFile: String?, name: String, size: Dp = 50.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, iconFile) {
        value = if (iconFile.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { IconStore.load(ctx, iconFile)?.asImageBitmap() }.getOrNull()
        }
    }
    BitmapOrLetter(bmp, name, size)
}

@Composable
fun BitmapOrLetter(bmp: ImageBitmap?, name: String, size: Dp) {
    val p = LocalLedger.current
    val radius = if (size >= 48.dp) 14.dp else (size.value * 0.3f).dp
    val shape = RoundedCornerShape(radius)
    val container = p.iconBg
    Box(Modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size * 0.66f).clip(RoundedCornerShape(size * 0.16f)),
            )
        } else {
            Text(
                name.trim().take(1).ifEmpty { "?" },
                color = p.onTonal,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/** 流水行左侧徽标：来源首字 */
@Composable
fun SourceBadge(r: Record, size: Dp = 40.dp) {
    val p = LocalLedger.current
    val letter = when (r.source) {
        RecordSource.WECHAT -> "微"
        RecordSource.ALIPAY -> "支"
        else -> "记"
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(p.tonal), contentAlignment = Alignment.Center) {
        Text(letter, color = p.onTonal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

fun sourceLabel(source: String): String = when (source) {
    RecordSource.WECHAT -> "微信"
    RecordSource.ALIPAY -> "支付宝"
    else -> "手动"
}

// ---------------------------------------------------------------- 顶栏

/** 一级页面大标题顶栏：透明融入背景 */
@Composable
fun LargeHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = PagePadding + 4.dp, end = PagePadding, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 二级页面顶栏：圆形返回按钮 + 标题 + 右侧动作，透明背景 */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(LedgerIcons.Back, "返回", onBack)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 圆形图标按钮（卡片底色），用于顶栏 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = LocalLedger.current.card,
    size: Dp = 42.dp,
    enabled: Boolean = true,
) {
    val p = LocalLedger.current
    var m = Modifier.size(size).clip(CircleShape).background(container)
    if (p.dark && container == p.card) m = m.border(1.dp, p.cardBorder, CircleShape)
    Box(m.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.38f), modifier = Modifier.size(size * 0.5f))
    }
}

/** 顶栏右上「记一笔」快捷入口：主色（黑/白）圆角方块 + 笔图标 */
@Composable
fun QuickRecordButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LedgerIcons.Pen, "记一笔", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- 区块头 / 徽标

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, badge: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            CountBadge(badge)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** 中性灰徽标（浅色 #EAEAEA 底黑字，深色 #232323 底白字） */
@Composable
fun CountBadge(text: String) {
    val p = LocalLedger.current
    Box(
        Modifier.clip(CircleShape).background(p.badge).padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(text, color = p.onBadge, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = AmountStyle)
    }
}

/** 涨跌箭头徽标（灰底主文字色↑ / 红底↓） */
@Composable
fun TrendBadge(cents: Long) {
    if (cents == 0L) return
    val c = MoneyColors.of(cents)
    Box(Modifier.size(26.dp).clip(CircleShape).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(if (cents > 0) LedgerIcons.ArrowUp else LedgerIcons.ArrowDown, null, tint = c, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------- 盈亏卡

/** 盈亏主卡：标签 + 净额大数字（正数主文字色 / 负数红 + ↑↓）+ 卡内累计曲线 */
@Composable
fun HeroProfitCard(
    label: String,
    net: Long,
    series: List<SeriesPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = ChartHeightOverview,
) {
    LedgerCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountText(net, 36.sp, MoneyColors.of(net), fitChars = 12)
            Spacer(Modifier.width(10.dp))
            TrendBadge(net)
        }
        Spacer(Modifier.height(14.dp))
        CumulativeChart(series, height = chartHeight)
    }
}

/** 收入 / 支出 小统计卡（仅详情页使用） */
@Composable
fun StatTile(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    LedgerCard(modifier, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        AmountText(cents, 20.sp, color, signed = false, fitChars = 11)
    }
}

// ---------------------------------------------------------------- 按钮 / 输入

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
    }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val p = LocalLedger.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.tonal, contentColor = p.onTonal),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
    }
}

/** 药丸形筛选 chip */
@Composable
fun PillChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalLedger.current
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else p.tonal, label = "chipBg")
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "chipFg",
    )
    val m = Modifier.height(36.dp).clip(CircleShape).background(bg)
    Box(m.clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun ledgerFieldColors(): TextFieldColors {
    val p = LocalLedger.current
    val cs = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = if (p.dark) cs.outline else cs.outlineVariant,
        focusedContainerColor = p.card,
        unfocusedContainerColor = p.card,
        disabledContainerColor = p.card,
        focusedLabelColor = cs.primary,
        cursorColor = cs.primary,
    )
}

val FieldShape = RoundedCornerShape(14.dp)

/** 空状态：一句主要提示 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    LedgerCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(vertical = 28.dp, horizontal = 20.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- 底部导航

data class NavTab(val label: String, val icon: ImageVector)

val MainTabs = listOf(
    NavTab("总览", LedgerIcons.Chart),
    NavTab("明细", LedgerIcons.List),
    NavTab("我的", LedgerIcons.Person),
)

/** 自定义底部导航：药丸形主色（浅色黑 / 深色白）选中指示器 + 反色图标 */
@Composable
fun LedgerBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalLedger.current
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth()
            .then(if (!p.dark) Modifier.shadow(16.dp, RoundedCornerShape(0.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000)) else Modifier)
            .background(p.navBar)
            .drawBehind { drawLine(p.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTabs.forEachIndexed { i, t ->
                val sel = i == selected
                val pill by animateColorAsState(if (sel) cs.primary else Color.Transparent, label = "pill")
                val iconTint by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, label = "icon")
                val labelColor by animateColorAsState(if (sel) cs.primary else cs.onSurfaceVariant, label = "label")
                Column(
                    Modifier.weight(1f).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(64.dp).height(36.dp).clip(CircleShape).background(pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(t.icon, t.label, tint = iconTint, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        t.label,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
