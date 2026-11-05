package com.ledger.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 品牌配色：黑白灰单色系，唯一保留的彩色是支出/负盈亏的红色。不使用 Android 12+ 动态取色，
 * 亮/暗两套全部自定义，所有 colorScheme 槽位都显式赋值，避免任何 Material 默认紫色漏出来。
 */

object Brand {
    /** 浅色：主文字 / 主色 */
    val Ink = Color(0xFF111111)
    /** 深色：主文字 / 主色 */
    val Paper = Color(0xFFF5F5F5)
    val ExpenseLight = Color(0xFFE5484D)
    val ExpenseDark = Color(0xFFF07178)
}

private val LightColors = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAEA),
    onPrimaryContainer = Brand.Ink,
    inversePrimary = Brand.Paper,
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAEAEA),
    onSecondaryContainer = Brand.Ink,
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAEAEA),
    onTertiaryContainer = Brand.Ink,
    error = Brand.ExpenseLight,
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E5),
    onErrorContainer = Color(0xFF5C1214),
    background = Color(0xFFF4F4F4),
    onBackground = Brand.Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF8A8A8A),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF222222),
    inverseOnSurface = Brand.Paper,
    outline = Color(0xFFC8C8C8),
    outlineVariant = Color(0xFFE5E5E5),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E6E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEAEAEA),
)

private val DarkColors = darkColorScheme(
    primary = Brand.Paper,
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF232323),
    onPrimaryContainer = Brand.Paper,
    inversePrimary = Brand.Ink,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF232323),
    onSecondaryContainer = Brand.Paper,
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Brand.Ink,
    tertiaryContainer = Color(0xFF232323),
    onTertiaryContainer = Brand.Paper,
    error = Brand.ExpenseDark,
    onError = Color(0xFF3D0B0E),
    errorContainer = Color(0xFF4A1C1F),
    onErrorContainer = Color(0xFFFFDADB),
    background = Color(0xFF0B0B0B),
    onBackground = Brand.Paper,
    surface = Color(0xFF171717),
    onSurface = Brand.Paper,
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceTint = Color.Transparent,
    inverseSurface = Brand.Paper,
    inverseOnSurface = Color(0xFF171717),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF262626),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF0B0B0B),
    surfaceContainerLowest = Color(0xFF070707),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF171717),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
)

/** Material colorScheme 之外的品牌色 */
@Immutable
data class LedgerPalette(
    val dark: Boolean,
    /** 收入/正盈亏：不用绿，直接用主文字色，靠「+」号区分 */
    val income: Color,
    /** income 作为底色时其上的文字色 */
    val onIncome: Color,
    val expense: Color,
    /** 中性徽标底 / 字（原暖金徽标改为灰阶） */
    val badge: Color,
    val onBadge: Color,
    /** 卡片底色（与页面背景有明度差） */
    val card: Color,
    /** 暗色下卡片描边（亮色为透明，改用极淡阴影） */
    val cardBorder: Color,
    /** 图标容器 / 次要按钮 / chip 的 tonal 底 */
    val tonal: Color,
    val onTonal: Color,
    /** 项目图标容器的中性灰底 */
    val iconBg: Color,
    /** 底部导航栏底色 */
    val navBar: Color,
    val hairline: Color,
)

private val LightPalette = LedgerPalette(
    dark = false,
    income = Brand.Ink,
    onIncome = Color.White,
    expense = Brand.ExpenseLight,
    badge = Color(0xFFEAEAEA),
    onBadge = Brand.Ink,
    card = Color(0xFFFFFFFF),
    cardBorder = Color.Transparent,
    tonal = Color(0xFFEAEAEA),
    onTonal = Brand.Ink,
    iconBg = Color(0xFFEDEDED),
    navBar = Color(0xFFFFFFFF),
    hairline = Color(0xFFE5E5E5),
)

private val DarkPalette = LedgerPalette(
    dark = true,
    income = Brand.Paper,
    onIncome = Brand.Ink,
    expense = Brand.ExpenseDark,
    badge = Color(0xFF232323),
    onBadge = Brand.Paper,
    card = Color(0xFF171717),
    cardBorder = Color(0xFF262626),
    tonal = Color(0xFF232323),
    onTonal = Brand.Paper,
    iconBg = Color(0xFF232323),
    navBar = Color(0xFF121212),
    hairline = Color(0xFF262626),
)

val LocalLedger = staticCompositionLocalOf { LightPalette }

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun ledgerTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

/** 金额数字：等宽数字 + SemiBold */
val AmountStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

object MoneyColors {
    @Composable
    @ReadOnlyComposable
    fun income(): Color = LocalLedger.current.income

    @Composable
    @ReadOnlyComposable
    fun expense(): Color = LocalLedger.current.expense

    @Composable
    @ReadOnlyComposable
    fun of(cents: Long): Color = when {
        cents > 0 -> income()
        cents < 0 -> expense()
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalLedger provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = ledgerTypography(),
            shapes = LedgerShapes,
            content = content,
        )
    }
}
