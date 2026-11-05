package com.ledger.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 自绘线性图标（24 网格、2dp 圆头描边），统一风格，不依赖 material-icons-extended。
 * Icon() 着色时会整体替换颜色，这里描边色写黑色即可。
 */
object LedgerIcons {
    private fun line(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Chart = line("chart", "M4,19.5h16", "M5,15L9.5,10.5L13,13.5L19.5,7", "M15.5,7h4v4")
    val List = line(
        "list",
        "M9.5,6.5h10", "M9.5,12h10", "M9.5,17.5h10",
        "M4.8,6.5h0.01", "M4.8,12h0.01", "M4.8,17.5h0.01",
        width = 2.2f,
    )
    val Person = line(
        "person",
        "M12,3.8a4,4 0,1 1,0 8a4,4 0,1 1,0 -8z",
        "M4.5,20.2c0.6,-3.6 3.6,-5.8 7.5,-5.8s6.9,2.2 7.5,5.8",
    )
    val Plus = line("plus", "M12,5v14", "M5,12h14", width = 2.4f)
    val Pen = line("pen", "M4,20v-3.6L15.2,5.2a2.4,2.4 0,0 1,3.4 3.4L7.6,20H4z", "M13.5,7l3.4,3.4")
    val Back = line("back", "M14.5,5L7.5,12L14.5,19", width = 2.3f)
    val ChevronRight = line("chevron", "M9.5,6l6,6l-6,6")
    val Trash = line(
        "trash",
        "M4,7h16", "M10,11v6", "M14,11v6",
        "M6,7l0.9,11.2A2,2 0,0 0,8.9 20h6.2a2,2 0,0 0,2 -1.8L18,7",
        "M9,7V5a1,1 0,0 1,1 -1h4a1,1 0,0 1,1 1v2",
    )
    val Edit = line("edit", "M12.5,20h7.5", "M16.2,3.8a2.1,2.1 0,0 1,3 3L7.5,18.5L3.5,19.5l1,-4z")
    val Import = line("import", "M12,4v11", "M7,10.5l5,5l5,-5", "M5,20h14")
    val Close = line("close", "M6,6L18,18", "M18,6L6,18")
    val Search = line("search", "M11,4a7,7 0,1 1,0 14a7,7 0,1 1,0 -14z", "M20,20l-3.8,-3.8")
    val Check = line("check", "M5,12.5l4.5,4.5L19,7.5", width = 2.4f)
    val Calendar = line(
        "calendar",
        "M6,5.5h12a2,2 0,0 1,2 2v11a2,2 0,0 1,-2 2H6a2,2 0,0 1,-2 -2v-11a2,2 0,0 1,2 -2z",
        "M4,10h16", "M8.5,3.5v4", "M15.5,3.5v4",
    )
    val Clock = line("clock", "M12,4a8,8 0,1 1,0 16a8,8 0,1 1,0 -16z", "M12,8v4.2l2.8,1.8")
    val Apps = line(
        "apps",
        "M5.5,4h3a1.5,1.5 0,0 1,1.5 1.5v3A1.5,1.5 0,0 1,8.5 10h-3A1.5,1.5 0,0 1,4 8.5v-3A1.5,1.5 0,0 1,5.5 4z",
        "M15.5,4h3a1.5,1.5 0,0 1,1.5 1.5v3a1.5,1.5 0,0 1,-1.5 1.5h-3A1.5,1.5 0,0 1,14 8.5v-3A1.5,1.5 0,0 1,15.5 4z",
        "M5.5,14h3a1.5,1.5 0,0 1,1.5 1.5v3A1.5,1.5 0,0 1,8.5 20h-3A1.5,1.5 0,0 1,4 18.5v-3A1.5,1.5 0,0 1,5.5 14z",
        "M15.5,14h3a1.5,1.5 0,0 1,1.5 1.5v3a1.5,1.5 0,0 1,-1.5 1.5h-3a1.5,1.5 0,0 1,-1.5 -1.5v-3a1.5,1.5 0,0 1,1.5 -1.5z",
    )
    val Image = line(
        "image",
        "M6,4.5h12a2,2 0,0 1,2 2v11a2,2 0,0 1,-2 2H6a2,2 0,0 1,-2 -2v-11a2,2 0,0 1,2 -2z",
        "M4.5,16.5l4.5,-4.5l4,4l2.5,-2.5l4,4",
        "M15,9h0.01",
    )
    val ArrowUp = line("up", "M12,19V5.5", "M6.5,11l5.5,-5.5l5.5,5.5", width = 2.4f)
    val ArrowDown = line("down", "M12,5v13.5", "M6.5,13l5.5,5.5l5.5,-5.5", width = 2.4f)
    val Book = line(
        "book",
        "M6,3.8h11a2,2 0,0 1,2 2v12.4a2,2 0,0 1,-2 2H6.5A1.5,1.5 0,0 1,5 18.7V4.8a1,1 0,0 1,1 -1z",
        "M9,3.8v16.4", "M12,9h4", "M12,12.5h3",
    )
}
