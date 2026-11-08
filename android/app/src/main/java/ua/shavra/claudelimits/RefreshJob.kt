package ua.shavra.claudelimits

import android.app.job.JobParameters
import android.app.job.JobService

/** Періодичне фонове оновлення (JobScheduler, без сторонніх бібліотек). */
class RefreshJob : JobService() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onStartJob(params: JobParameters): Boolean {
        if (Repo.active(this).isEmpty()) return false
        Repo.refresh(this) {
            jobFinished(params, false)
            // короткий інтервал — плануємо наступний запуск самі
            if (Features.isChained(this)) Features.scheduleJob(this)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
