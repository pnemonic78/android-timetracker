/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2019, Tikal Knowledge, Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * • Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * • Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * • Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tikalk.worktracker.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.tikalk.app.TikalApplication
import com.tikalk.util.CrashlyticsTree
import com.tikalk.util.LogTree
import com.tikalk.worktracker.BuildConfig
import com.tikalk.worktracker.db.TrackerDatabase
import com.tikalk.worktracker.time.TimerWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber
import kotlin.math.max

/**
 * Time tracker application.
 */
class TrackerApplication : TikalApplication(), Application.ActivityLifecycleCallbacks {

    private var active = 0

    override fun onCreate() {
        super.onCreate()

        // Logging
        val enableDebugLogging = BuildConfig.DEBUG
        if (enableDebugLogging) {
            Timber.plant(LogTree(enableDebugLogging))
        } else {
            Timber.plant(CrashlyticsTree(enableDebugLogging))
        }

        registerActivityLifecycleCallbacks(this)

        startKoin {
            androidLogger(if (enableDebugLogging) Level.DEBUG else Level.INFO)
            androidContext(this@TrackerApplication)
            modules(
                listOf(
                    preferencesModule,
                    databaseModule,
                    retrofitModule,
                    apiModule,
                    dataModule
                )
            )
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterActivityLifecycleCallbacks(this)
        TrackerDatabase.getDatabase(this).close()
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityStarted(activity: Activity) {
        active++
        Timber.i("onActivityStarted $activity $active")
        TimerWorker.hideNotification(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity) {
        active = max(0, active - 1)
        Timber.i("onActivityStopped $activity $active isFinishing=${activity.isFinishing}")
        if (active == 0) {
            TimerWorker.maybeShowNotification(this)
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
    }
}