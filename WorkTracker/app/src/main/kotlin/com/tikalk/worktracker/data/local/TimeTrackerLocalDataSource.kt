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

package com.tikalk.worktracker.data.local

import android.text.format.DateUtils
import com.tikalk.worktracker.data.TimeTrackerDataSource
import com.tikalk.worktracker.data.remote.TimeListPageSaver
import com.tikalk.worktracker.db.ProjectWithTasks
import com.tikalk.worktracker.db.TrackerDatabase
import com.tikalk.worktracker.db.WholeTimeRecordEntity
import com.tikalk.worktracker.db.toTimeRecord
import com.tikalk.worktracker.model.*
import com.tikalk.worktracker.model.time.*
import com.tikalk.worktracker.preference.TimeTrackerPrefs
import com.tikalk.worktracker.time.*
import io.reactivex.rxjava3.core.Observable
import java.util.*
import kotlin.collections.ArrayList

class TimeTrackerLocalDataSource(
    private val db: TrackerDatabase,
    private val preferences: TimeTrackerPrefs
) : TimeTrackerDataSource {

    override fun editPage(recordId: Long, refresh: Boolean): Observable<TimeEditPage> {
        val projects = ArrayList<Project>()
        val errorMessage: String? = null

        return loadProjectsWithTasks(db)
            .map { projectsWithTasks ->
                populateProjects(projectsWithTasks, projects)

                val record = loadRecord(db, recordId) ?: TimeRecord.EMPTY.copy()

                return@map TimeEditPage(
                    record,
                    projects,
                    errorMessage,
                    record.start ?: Calendar.getInstance()
                )
            }
    }

    private fun loadRecord(db: TrackerDatabase, recordId: Long): TimeRecord? {
        if (recordId != TikalEntity.ID_NONE) {
            val recordsDao = db.timeRecordDao()
            val recordEntity = recordsDao.queryById(recordId)
            if (recordEntity != null) {
                return recordEntity.toTimeRecord()
            }
        }
        return null
    }

    override fun projectsPage(refresh: Boolean): Observable<List<Project>> {
        return loadProjects(db)
    }

    private fun loadProjects(db: TrackerDatabase): Observable<List<Project>> {
        val projectsDao = db.projectDao()
        val projectsDb = projectsDao.queryAllSingle()
        return projectsDb
            .map { projects ->
                projects
                    .filter { it.id != TikalEntity.ID_NONE }
                    .sortedBy { it.name }
            }
            .toObservable()
    }

    override fun tasksPage(refresh: Boolean): Observable<List<ProjectTask>> {
        return loadTasks(db)
    }

    private fun loadTasks(db: TrackerDatabase): Observable<List<ProjectTask>> {
        val tasksDao = db.taskDao()
        val tasksDb = tasksDao.queryAllSingle()
        return tasksDb
            .map { tasks ->
                tasks
                    .filter { it.id != TikalEntity.ID_NONE }
                    .sortedBy { it.name }
            }
            .toObservable()
    }

    override fun usersPage(refresh: Boolean): Observable<UsersPage> {
        return Observable.merge(
            Observable.just(UsersPage(listOf(preferences.user))),
            loadUsers(db).map { UsersPage(it) }
        )
    }

    private fun loadUsers(db: TrackerDatabase): Observable<List<User>> {
        val userDao = db.userDao()
        val usersDb = userDao.queryAllSingle()
        return usersDb
            .map { users ->
                users
                    .filter { it.id != TikalEntity.ID_NONE }
                    .sortedBy { it.displayName }
            }
            .toObservable()
    }

    override fun reportFormPage(refresh: Boolean): Observable<ReportFormPage> {
        val projects = ArrayList<Project>()
        val filter = ReportFilter()
        val errorMessage: String? = null

        return loadProjectsWithTasks(db)
            .map { projectsWithTasks ->
                populateProjects(projectsWithTasks, projects)

                return@map ReportFormPage(
                    filter,
                    projects,
                    errorMessage
                )
            }
    }

    private fun loadProjectsWithTasks(db: TrackerDatabase): Observable<List<ProjectWithTasks>> {
        val projectsDao = db.projectDao()
        return projectsDao.queryAllWithTasksObservable()
            .map { projects ->
                projects
                    .filter { it.project.id != TikalEntity.ID_NONE }
            }
    }

    private fun populateProjects(
        projectsWithTasks: List<ProjectWithTasks>,
        projects: MutableCollection<Project>
    ) {
        projects.clear()

        for (projectWithTasks in projectsWithTasks) {
            val project = projectWithTasks.project
            project.tasks = projectWithTasks.tasks
            projects.add(project)
        }
    }

    override fun reportPage(filter: ReportFilter, refresh: Boolean): Observable<ReportPage> {
        val projects = ArrayList<Project>()

        return loadProjectsWithTasks(db)
            .map { projectsWithTasks ->
                populateProjects(projectsWithTasks, projects)

                val records = loadReportRecords(db, filter, projects)

                val totals = calculateTotals(records)

                return@map ReportPage(
                    filter,
                    records,
                    totals
                )
            }
    }

    private fun loadReportRecords(
        db: TrackerDatabase,
        filter: ReportFilter,
        projects: Collection<Project>?
    ): List<TimeRecord> {
        val start = filter.startTime
        val finish = filter.finishTime
        val reportRecordsDao = db.reportRecordDao()
        val reportRecordsDb = reportRecordsDao.queryByDate(start, finish)

        if (reportRecordsDb.isNullOrEmpty()) {
            val recordsDao = db.timeRecordDao()
            val recordsDb = recordsDao.queryByDate(start, finish)
            return recordsDb.map { it.toTimeRecord() }
        }
        return reportRecordsDb.map { it.toTimeRecord(projects) }
    }

    private fun calculateTotals(records: List<TimeRecord>?): ReportTotals {
        val totals = ReportTotals()

        var duration: Long
        if (records != null) {
            for (record in records) {
                duration = record.finishTime - record.startTime
                if (duration > 0L) {
                    totals.duration += duration
                }
                totals.cost += record.cost
            }
        }

        return totals
    }

    override fun timeListPage(date: Calendar, refresh: Boolean): Observable<TimeListPage> {
        val projects = ArrayList<Project>()
        val record = TimeRecord.EMPTY
        val errorMessage: String? = null

        return loadProjectsWithTasks(db)
            .map { projectsWithTasks ->
                populateProjects(projectsWithTasks, projects)

                val records = loadRecords(db, date)
                    .map { entity ->
                        entity.toTimeRecord()
                    }

                val totals = loadTotals(db, date)

                return@map TimeListPage(
                    record,
                    projects,
                    errorMessage,
                    date,
                    records,
                    totals
                )
            }
    }

    private fun loadRecords(
        db: TrackerDatabase,
        day: Calendar? = null
    ): List<WholeTimeRecordEntity> {
        val recordsDao = db.timeRecordDao()
        return if (day == null) {
            recordsDao.queryAll()
        } else {
            val start = day.copy()
            start.setToStartOfDay()
            val finish = day.copy()
            finish.setToEndOfDay()
            recordsDao.queryByDate(start.timeInMillis, finish.timeInMillis)
        }
    }

    private fun loadTotals(db: TrackerDatabase, date: Calendar): TimeTotals {
        val totals = TimeTotals()

        val cal = date.copy()
        val startDay = cal.setToStartOfDay().timeInMillis
        val finishDay = cal.setToEndOfDay().timeInMillis

        cal.dayOfWeek = Calendar.SUNDAY
        val startWeek = cal.setToStartOfDay().timeInMillis
        cal.dayOfWeek = Calendar.SATURDAY
        val finishWeek = cal.setToEndOfDay().timeInMillis

        cal.dayOfMonth = 1
        val startMonth = cal.setToStartOfDay().timeInMillis
        cal.dayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val finishMonth = cal.setToEndOfDay().timeInMillis

        val recordsDao = db.timeRecordDao()
        val totalsAll = recordsDao.queryTotals(
            startDay,
            finishDay,
            startWeek,
            finishWeek,
            startMonth,
            finishMonth
        )
        if (totalsAll.size >= 3) {
            totals.daily = totalsAll[0].daily
            totals.weekly = totalsAll[1].weekly
            totals.monthly = totalsAll[2].monthly
        }
        val quota = calculateQuota(date)
        totals.remaining = quota - totals.monthly

        return totals
    }

    private fun calculateQuota(date: Calendar): Long {
        var quota = 0L
        val day = date.copy()
        val lastDayOfMonth = day.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (dayOfMonth in 1..lastDayOfMonth) {
            day.dayOfMonth = dayOfMonth
            if (day.dayOfWeek in WORK_DAYS) {
                quota += WORK_HOURS
            }
        }
        return quota * DateUtils.HOUR_IN_MILLIS
    }

    override fun profilePage(refresh: Boolean): Observable<ProfilePage> {
        val page = ProfilePage(
            preferences.user,
            preferences.userCredentials,
            nameInputEditable = false,
            emailInputEditable = false,
            loginInputEditable = false,
            passwordConfirm = null,
            errorMessage = null
        )
        return Observable.just(page)
    }

    override fun timerPage(refresh: Boolean): Observable<TimerPage> {
        val projects = ArrayList<Project>()
        val record = preferences.getStartedRecord() ?: TimeRecord.EMPTY.copy()

        return loadProjectsWithTasks(db)
            .map { projectsWithTasks ->
                populateProjects(projectsWithTasks, projects)

                return@map TimerPage(
                    record,
                    projects
                )
            }
    }

    override fun savePage(page: TimeListPage) {
        TimeListPageSaver(db).save(page)
    }

    companion object {
        private val WORK_DAYS = intArrayOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY
        )
        private const val WORK_HOURS = 9L
    }
}