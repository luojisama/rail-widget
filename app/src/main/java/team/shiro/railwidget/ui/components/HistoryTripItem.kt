package team.shiro.railwidget.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.shiro.railwidget.data.model.Trip

@Composable
fun HistoryTripItem(
    trip: Trip,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Train,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = {
            Text(
                text = "${trip.trainCode} · ${trip.departureStation} ➔ ${trip.arrivalStation}",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val seatDisplay = if (trip.carriage.isNotBlank() || trip.seat.isNotBlank()) "${trip.carriage} ${trip.seat}" else "详见凭证"
            Text(
                text = "${trip.departureDate} ${trip.departureTime}  ${seatDisplay}  检票口: ${trip.getCleanTicketGate()}",
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    )
}
