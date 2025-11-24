package com.together.newverse.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.together.newverse.android.R
import com.together.newverse.android.utils.ACTION_START_SERVICE
import com.together.newverse.android.utils.ACTION_STOP_SERVICE
import com.together.newverse.android.utils.NOTIFICATION_CHANNEL_ID
import com.together.newverse.android.utils.NOTIFICATION_CHANNEL_NAME
import com.together.newverse.android.utils.NOTIFICATION_ID
import com.together.newverse.android.utils.provideBaseNotificationBuilder
import com.together.newverse.data.firebase.Database
import com.together.newverse.data.firebase.model.OrderDto
import com.together.newverse.domain.model.Order
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class ListenerService : Service() {

    private var openOrders: MutableList<Order> = mutableListOf()
    private var orderMap: MutableMap<String, List<Order>> = mutableMapOf()
    private var valueEventListener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        Database.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_SERVICE -> {
                    Log.d(TAG, "onStartCommand start")
                    startForegroundService()
                    // Only set up connection if user is authenticated
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        setUpOrderConnection()
                    } else {
                        Log.w(TAG, "User not authenticated, stopping service")
                        stopSelf()
                    }
                }

                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "onStartCommand stop")
                    cleanup()
                    stopForeground(true)
                    stopSelf()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel =
            NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(notificationManager)
            }

            val notification = provideBaseNotificationBuilder(this)
                .setContentText("Warte auf Bestellungen...")
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun getFlags(): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) FLAG_UPDATE_CURRENT
        else FLAG_MUTABLE or FLAG_UPDATE_CURRENT
    }

    private fun setUpOrderConnection() {
        val today = Date().time.toOrderId()  //  "20231120"

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                try {
                    val list = parseOrders(dataSnapshot)
                    val open = openOrders.map { it.id }
                    val nextBlock = list.map { it.id }

                    // Remove orders that are no longer in the database
                    open.minus(nextBlock.toSet()).forEach { orderId ->
                        openOrders.removeAll { it.id == orderId }
                    }

                    orderMap[dataSnapshot.key ?: today] = list
                    manageOrders(list)
                    Log.d(TAG, "Orders updated: ${dataSnapshot.key}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing orders", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error", error.toException())
            }
        }

        Database.orderSeller()
            .orderByKey()
            .startAt(today)
            .addValueEventListener(valueEventListener!!)
    }

    private fun parseOrders(dataSnapshot: DataSnapshot): List<Order> {
        val orders = mutableListOf<Order>()
        dataSnapshot.children.forEach { dateSnapshot ->
            dateSnapshot.children.forEach { orderSnapshot ->
                try {
                    val orderDto = orderSnapshot.getValue(OrderDto::class.java)
                    if (orderDto != null) {
                        val order = orderDto.toDomain(orderSnapshot.key ?: "")
                        orders.add(order)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing order ${orderSnapshot.key}", e)
                }
            }
        }
        return orders
    }

    private fun manageOrders(list: List<Order>) {
        var addedItemCount = 0
        var alteredItemCount = 0
        var firstTime = openOrders.isEmpty()

        list.forEach { newItem ->
            val existingOrder = openOrders.find { it.id == newItem.id }
            if (existingOrder != null) {
                if (hasOrderChanged(existingOrder, newItem)) {
                    alteredItemCount++
                    // Update the existing order
                    val index = openOrders.indexOf(existingOrder)
                    openOrders[index] = newItem
                }
            } else {
                addedItemCount++
                openOrders.add(newItem)
            }
        }

        // Keep service running even with no orders - just update notification
        if (openOrders.isEmpty() || openOrders.all { it.articles.isEmpty() }) {
            Log.d(TAG, "No orders currently, keeping service alive")
            // Don't stop - keep waiting for orders
            return
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        val notification = provideBaseNotificationBuilder(this)
        val text = if (firstTime) {
            "Es liegen ${openOrders.size} Bestellungen vor."
        } else {
            "Es bestehen ${openOrders.size} Bestellungen. " +
                "$addedItemCount neue Bestellungen und $alteredItemCount geänderte."
        }

        val stopIntent = Intent(this, ListenerService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }

        notification.addAction(
            R.drawable.ic_launcher_foreground, "Ausschalten",
            PendingIntent.getService(this, 2, stopIntent, getFlags())
        )
        notification.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        notification.setStyle(NotificationCompat.BigTextStyle().bigText(text))

        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Notification sent: Altered $alteredItemCount, Added $addedItemCount")
    }

    private fun hasOrderChanged(oldOrder: Order, newOrder: Order): Boolean {
        // Compare relevant fields to detect changes
        return oldOrder.articles.size != newOrder.articles.size ||
                oldOrder.pickUpDate != newOrder.pickUpDate ||
                oldOrder.message != newOrder.message ||
                oldOrder.articles != newOrder.articles
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        valueEventListener?.let {
            try {
                Database.orderSeller().removeEventListener(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener", e)
            }
        }
        valueEventListener = null
    }

    companion object {
        private const val TAG = "ListenerService"
    }
}

/**
 * Extension function to convert timestamp to order ID format (yyyyMMdd)
 */
private fun Long.toOrderId(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return format.format(date)
}
