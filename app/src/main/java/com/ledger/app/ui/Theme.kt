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
 * 品牌配色：翡翠绿 + 暖金点缀。不使用 Android 12+ 动态取色，亮/暗两套全部自定义，
 * 所有 colorScheme 槽位都显式赋值，避免任何 Material 默认紫色漏出来。
 */

object Brand {
    val Emerald = Color(0xFF0E7C5A)
    val EmeraldDeep = Color(0xFF0A5C43)
    val EmeraldBright = Color(0xFF3DBE8C)
    val Gold = Color(0xFFD9A441)
    val GoldBright = Color(0xFFE6B75A)
}

private val LightColors = lightColorScheme(
    primary = Brand.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCF1E7),
    onPrimaryContainer = Color(0xFF053D2B),
    inversePrimary = Color(0xFF7FD6AE),
    secondary = Color(0xFF4F6B5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EFE9),
    onSecondaryContainer = Color(0xFF173328),
    tertiary = Brand.Gold,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFFF8EBCC),
    onTertiaryContainer = Color(0xFF4A3500),
    error = Color(0xFFD14B4B),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF5C1212),
    background = Color(0xFFF6F7F4),
    onBackground = Color(0xFF16201C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16201C),
    surfaceVariant = Color(0xFFEEF1EC),
    onSurfaceVariant = Color(0xFF6B7771),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF26302C),
    inverseOnSurface = Color(0xFFEEF2EE),
    outline = Color(0xFFC6CFCA),
    outlineVariant = Color(0xFFE4E9E5),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6EAE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBF9),
    surfaceContainer = Color(0xFFF2F5F1),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE9EEEA),
)

private val DarkColors = darkColorScheme(
    primary = Brand.EmeraldBright,
    onPrimary = Color(0xFF00281B),
    primaryContainer = Color(0xFF1D3B30),
    onPrimaryContainer = Color(0xFFB7ECD3),
    inversePrimary = Brand.Emerald,
    secondary = Color(0xFF9DB8AB),
    onSecondary = Color(0xFF0F241C),
    secondaryContainer = Color(0xFF243230),
    onSecondaryContainer = Color(0xFFD3E6DC),
    tertiary = Brand.GoldBright,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF3B3120),
    onTertiaryContainer = Color(0xFFF6DFA8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0E0E),
    errorContainer = Color(0xFF5A1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE5ECE8),
    surface = Color(0xFF1A201E),
    onSurface = Color(0xFFE5ECE8),
    surfaceVariant = Color(0xFF232A28),
    onSurfaceVariant = Color(0xFF93A19A),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE5ECE8),
    inverseOnSurface = Color(0xFF1A201E),
    outline = Color(0xFF3B4743),
    outlineVariant = Color(0xFF29322F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A322F),
    surfaceDim = Color(0xFF101413),
    surfaceContainerLowest = Color(0xFF0C100F),
    surfaceContainerLow = Color(0xFF151A19),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF212826),
    surfaceContainerHighest = Color(0xFF29312E),
)

/** Material colorScheme 之外的品牌色 */
@Immutable
data class LedgerPalette(
    val dark: Boolean,
    val income: Color,
    val expense: Color,
    val gold: Color,
    /** 卡片底色（与页面背景有明度差） */
    val card: Color,
    /** 暗色下卡片描边（亮色为透明，改用极淡阴影） */
    val cardBorder: Color,
    /** 图标容器 / 次要按钮的 tonal 底 */
    val tonal: Color,
    val onTonal: Color,
    /** 底部导航栏底色 */
    val navBar: Color,
    val hairline: Color,
)

private val LightPalette = LedgerPalette(
    dark = false,
    income = Color(0xFF2E9E6B),
    expense = Color(0xFFE25B5B),
    gold = Brand.Gold,
    card = Color(0xFFFFFFFF),
    cardBorder = Color.Transparent,
    tonal = Color(0xFFE3F2EA),
    onTonal = Brand.Emerald,
    navBar = Color(0xFFFFFFFF),
    hairline = Color(0xFFE7ECE8),
)

private val DarkPalette = LedgerPalette(
    dark = true,
    income = Color(0xFF4CC48A),
    expense = Color(0xFFFF7A7A),
    gold = Brand.GoldBright,
    card = Color(0xFF1A201E),
    cardBorder = Color(0x14FFFFFF),
    tonal = Color(0xFF1F3A2F),
    onTonal = Color(0xFF7FDDB2),
    navBar = Color(0xFF161B1A),
    hairline = Color(0x12FFFFFF),
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
