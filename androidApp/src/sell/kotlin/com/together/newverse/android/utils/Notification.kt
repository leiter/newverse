package com.together.newverse.android.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.together.newverse.android.SellMainActivity
import com.together.newverse.android.R

fun provideBaseNotificationBuilder(
    context: Context,
    pendingIntent: PendingIntent = provideActivityPendingIntent(context)
): NotificationCompat.Builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
    .setAutoCancel(true)
    .setSmallIcon(R.drawable.ic_launcher_foreground)
    .setContentTitle("Newverse Bestellungen")
    .setContentIntent(pendingIntent)

fun provideActivityPendingIntent(
    context: Context
): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, SellMainActivity::class.java).apply {
            action = ACTION_SHOW_ORDER_FRAGMENT
        },
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
