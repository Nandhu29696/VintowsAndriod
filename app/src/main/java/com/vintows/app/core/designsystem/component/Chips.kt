package com.vintows.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vintows.app.core.designsystem.theme.StatusAmber
import com.vintows.app.core.designsystem.theme.StatusBlue
import com.vintows.app.core.designsystem.theme.StatusGreen
import com.vintows.app.core.designsystem.theme.StatusGrey
import com.vintows.app.core.designsystem.theme.StatusPurple
import com.vintows.app.core.designsystem.theme.StatusRed
import com.vintows.app.core.designsystem.theme.VintowsNavy
import com.vintows.app.core.util.initialsOf

/** Small rounded label, tinted background + coloured text. */
@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Support ticket status (values from the API: Open, InProgress, Resolved, Closed, …). */
@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        "Open" -> "Open" to StatusBlue
        "InProgress" -> "In Progress" to StatusAmber
        "Resolved" -> "Resolved" to StatusGreen
        "Closed" -> "Closed" to StatusGrey
        "PendingInstitutionReview" -> "Pending Review" to StatusPurple
        "RejectedByInstitution" -> "Rejected" to StatusRed
        else -> status to StatusGrey
    }
    TagChip(label, color, modifier)
}

/** Ticket priority: Low, Medium, High, Critical. */
@Composable
fun PriorityChip(priority: String, modifier: Modifier = Modifier) {
    val color = when (priority) {
        "Low" -> StatusGrey
        "High" -> StatusAmber
        "Critical" -> StatusRed
        else -> StatusBlue
    }
    TagChip(priority, color, modifier)
}

/** Circle with initials, like the requester avatars on the web ("DT", "AD"). */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = VintowsNavy,
) {
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
