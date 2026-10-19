package com.together.newverse.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.together.newverse.domain.model.ContactChannel
import org.jetbrains.compose.resources.stringResource
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.contact_channel_email
import newverse.shared.generated.resources.contact_channel_instagram
import newverse.shared.generated.resources.contact_channel_phone
import newverse.shared.generated.resources.contact_channel_signal
import newverse.shared.generated.resources.contact_channel_web
import newverse.shared.generated.resources.contact_channel_whatsapp

/**
 * How a channel is named and drawn.
 *
 * Kept beside the model rather than in either flavor: the seller's editor, the
 * buyer's top-bar menu and the About screen all draw the same list, and a channel
 * that looked different in two of them would be a bug nobody noticed.
 */
@Composable
fun ContactChannel.displayName(): String = stringResource(
    when (this) {
        ContactChannel.PHONE -> Res.string.contact_channel_phone
        ContactChannel.EMAIL -> Res.string.contact_channel_email
        ContactChannel.WHATSAPP -> Res.string.contact_channel_whatsapp
        ContactChannel.SIGNAL -> Res.string.contact_channel_signal
        ContactChannel.WEB -> Res.string.contact_channel_web
        ContactChannel.INSTAGRAM -> Res.string.contact_channel_instagram
    }
)

fun ContactChannel.icon(): ImageVector = when (this) {
    ContactChannel.PHONE -> Icons.Default.Phone
    ContactChannel.EMAIL -> Icons.Default.Email
    ContactChannel.WHATSAPP, ContactChannel.SIGNAL -> Icons.Default.Chat
    ContactChannel.WEB -> Icons.Default.Language
    ContactChannel.INSTAGRAM -> Icons.Default.PhotoCamera
}
