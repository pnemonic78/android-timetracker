package com.tikalk.worktracker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.crashlytics.android.Crashlytics
import com.tikalk.worktracker.time.TimerService
import io.fabric.sdk.android.Fabric
import timber.log.Timber

/**
 * Time tracker application.
 */
class TrackerApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var active = 0

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Fabric.with(this, Crashlytics())

        registerActivityLifecycleCallbacks(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityStarted(activity: Activity) {
        active++
        Timber.v("onActivityStarted $activity $active")
        TimerService.hideNotification(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity) {
        active = Math.max(0, active - 1)
        Timber.v("onActivityStopped $activity $active")
        if (active == 0) {
            TimerService.maybeShowNotification(this)
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
    }
}