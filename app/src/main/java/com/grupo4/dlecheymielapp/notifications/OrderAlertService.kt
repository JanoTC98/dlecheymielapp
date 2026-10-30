package com.grupo4.dlecheymielapp.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.grupo4.dlecheymielapp.DLecheApp
import com.grupo4.dlecheymielapp.MainActivity
import com.grupo4.dlecheymielapp.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Shows the notifications. Without the notification permission (Android 13+) it does nothing. */
object OrderAlertNotifier {
    private const val CHANNEL = "orders"
    private const val GROUP = "dleche_orders"
    private const val SUMMARY_ID = 1
    private const val MAX_INDIVIDUAL = 5

    fun show(context: Context, items: List<AlertItem>) {
        if (items.isEmpty() || !canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pedidos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos cuando le llega un pedido o hay una novedad en su cola."
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Many arrivals at once collapse into one line instead of burying the phone in notifications.
        val individual = items.take(MAX_INDIVIDUAL)
        individual.forEach { manager.notify(it.key.hashCode(), build(context, open, it.title, it.text, group = true)) }
        if (items.size > MAX_INDIVIDUAL) {
            manager.notify(SUMMARY_ID, build(context, open, "${items.size} novedades en sus pedidos", "Abra la app para verlas.", group = false))
        }
    }

    private fun build(context: Context, open: PendingIntent, title: String, text: String, group: Boolean): Notification =
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_order)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .apply { if (group) setGroup(GROUP) }
            .build()

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Schedules the periodic check with the platform's JobScheduler; Android does not run periodic jobs more than every 15 minutes. */
object OrderAlertScheduler {
    private const val JOB_ID = 4101
    private const val PERIOD_MILLIS = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        // Already scheduled: leave it alone so the period does not restart every time the app opens.
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, OrderAlertJobService::class.java))
            .setPeriodic(PERIOD_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}

/**
 * Every period it reads the signed-in user's queue and notifies about work that was not there at the previous check. It
 * never signs the user out and never writes to the server: if the session is gone it simply stops until the next login.
 */
class OrderAlertJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as DLecheApp
        running = scope.launch {
            try {
                check(app)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline, a server error or an expired session: the next period tries again.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun check(app: DLecheApp) {
        val container = app.container
        // Asks the server without touching the session state the screens observe: a failed check here must not sign anyone out.
        val user = container.authRepository.currentStaffUser() ?: return
        val fresh = container.newWorkDetector.newItems(user.id, container.orderAlertSource.items(user))
        OrderAlertNotifier.show(applicationContext, fresh)
    }
}
