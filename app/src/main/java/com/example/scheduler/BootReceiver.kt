package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.SiaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = SiaDatabase.getDatabase(context)
                    val pendingTasks = db.siaDao().getPendingTasks()
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

                    val now = System.currentTimeMillis()
                    for (task in pendingTasks) {
                        if (task.scheduledTimeMs > now) {
                            val alarmIntent = Intent(context, ScheduledTaskReceiver::class.java).apply {
                                action = "com.example.sia.EXECUTE_SCHEDULED_TASK"
                                putExtra("task_id", task.id)
                            }
                            val pi = PendingIntent.getBroadcast(
                                context,
                                task.id.hashCode(),
                                alarmIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            alarmManager?.let { am ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.scheduledTimeMs, pi)
                                } else {
                                    am.set(AlarmManager.RTC_WAKEUP, task.scheduledTimeMs, pi)
                                }
                            }
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
