package com.stoneofhope.stoneofhope.ui.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stoneofhope.stoneofhope.data.reading.ReadingModel
import com.stoneofhope.stoneofhope.ui.theme.AppTheme

/** iOS `AppColor.ReadingChipStyle`, shared by the Today carousel and the Reading Log grid. */
data class ReadingChipStyle(val fill: Color, val text: Color, val border: BorderStroke?)

@Composable
fun readingChipStyle(state: ReadingModel.ChipState): ReadingChipStyle {
    val c = AppTheme.colors
    return when (state) {
        ReadingModel.ChipState.ReadToday -> ReadingChipStyle(c.blue100, Color.White, null)
        ReadingModel.ChipState.Read -> ReadingChipStyle(c.blue20, c.blue100, BorderStroke(1.dp, c.blue80.copy(alpha = 0.7f)))
        ReadingModel.ChipState.Unread -> ReadingChipStyle(c.readingChipMutedFill, c.blue100, null)
    }
}
