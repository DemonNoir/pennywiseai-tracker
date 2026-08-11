package com.pennywiseai.tracker.slip.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * จัดการ Notification เมื่อระบบอัตโนมัติบันทึกสลิปเรียบร้อยแล้ว
 * พร้อมปุ่ม "เลือกหมวดหมู่ / แก้ไข" ที่กดแล้วจะเปิด PennyWise เข้าสู่หน้าเลือกหมวดหมู่ทันที
 */
@Singleton
class SlipNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CHANNEL_ID = "pennywise_slip_channel"
        private const val CHANNEL_NAME = "สแกนสลิปอัตโนมัติ (Slip Auto Scan)"
        private const val NOTIFICATION_ID = 8899
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "การแจ้งเตือนการบันทึกสลิปธนาคารอัตโนมัติ"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showSlipSavedNotification(transactionId: Long, receiverName: String, amountText: String) {
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("pennywise://transaction/edit/$transactionId")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("✅ บันทึกสลิปสำเร็จ ($amountText)")
            .setContentText("ผู้รับ: $receiverName | กดเพื่อเลือกหมวดหมู่รายจ่าย")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "เลือกหมวดหมู่ / แก้ไข",
                pendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
