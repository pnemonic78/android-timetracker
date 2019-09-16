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

package com.tikalk.worktracker.time

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.MainThread
import com.tikalk.worktracker.R
import com.tikalk.worktracker.model.Project
import com.tikalk.worktracker.model.ProjectTask
import com.tikalk.worktracker.model.TikalEntity
import com.tikalk.worktracker.model.time.TimeRecord
import com.tikalk.worktracker.model.time.isNullOrEmpty
import com.tikalk.worktracker.time.work.TimerWorker
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.fragment_timer.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.max

class TimerFragment : TimeFormFragment() {

    private var timer: Disposable? = null
    private var intentLater: Intent? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_timer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        project_input.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(adapterView: AdapterView<*>) {
                projectItemSelected(projectEmpty)
            }

            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                val project = adapterView.adapter.getItem(position) as Project
                projectItemSelected(project)
            }
        }
        task_input.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(adapterView: AdapterView<*>) {
                taskItemSelected(taskEmpty)
            }

            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                val task = adapterView.adapter.getItem(position) as ProjectTask
                taskItemSelected(task)
            }
        }

        action_start.setOnClickListener { startTimer() }
        action_stop.setOnClickListener { stopTimer() }
    }

    @MainThread
    override fun bindForm(record: TimeRecord) {
        Timber.v("bindForm record=$record")
        val context: Context = this.context ?: return
        project_input.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, projects.toTypedArray())
        if (projects.isNotEmpty()) {
            project_input.setSelection(max(0, projects.indexOf(record.project)))
        }
        task_input.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, tasks.toTypedArray())
        if (tasks.isNotEmpty()) {
            task_input.setSelection(max(0, tasks.indexOf(record.task)))
        }
        project_input.requestFocus()

        val startTime = record.startTime
        if (startTime <= 0L) {
            project_input.isEnabled = true
            task_input.isEnabled = true
            action_switcher.displayedChild = 0
        } else {
            project_input.isEnabled = false
            task_input.isEnabled = false
            action_switcher.displayedChild = 1

            maybeStartTimer()
            maybeStopTimer()
        }
    }

    private fun startTimer() {
        Timber.v("startTimer")
        val context: Context = this.context ?: return
        val now = System.currentTimeMillis()
        record.startTime = now

        TimerWorker.startTimer(context, record)

        bindForm(record)
    }

    private fun stopTimer() {
        Timber.v("stopTimer")
        val context: Context = this.context ?: return
        val now = System.currentTimeMillis()
        if (record.finish == null) {
            record.finishTime = now
        }

        TimerWorker.stopTimer(context)

        editRecord(record, TimeListActivity.REQUEST_STOPPED)
    }

    fun stopTimerCommit() {
        Timber.v("stopTimerCommit")
        timer?.dispose()

        record.start = null
        record.finish = null
        prefs.stopRecord()
        bindForm(record)
    }

    private fun filterTasks(project: Project) {
        val context: Context = this.context ?: return
        val filtered = project.tasks
        val options = ArrayList<ProjectTask>(filtered.size + 1)
        options.add(taskEmpty)
        options.addAll(filtered)
        task_input.adapter = ArrayAdapter<ProjectTask>(context, android.R.layout.simple_list_item_1, options)
        task_input.setSelection(options.indexOf(record.task))
    }

    private fun maybeStartTimer() {
        val timer = this.timer
        if ((timer == null) || timer.isDisposed) {
            this.timer = Observable.interval(1L, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { updateTimer() }
                .addTo(disposables)
        }
        updateTimer()
    }

    private fun maybeStopTimer() {
        if (intentLater?.action == TimeListActivity.ACTION_STOP) {
            intentLater = null
            stopTimer()
        }
    }

    private fun updateTimer() {
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - record.startTime) / DateUtils.SECOND_IN_MILLIS
        timer_text.text = DateUtils.formatElapsedTime(elapsedSeconds)
    }

    private fun projectItemSelected(project: Project) {
        record.project = project
        filterTasks(project)
        action_start.isEnabled = (record.project.id > TikalEntity.ID_NONE) && (record.task.id > TikalEntity.ID_NONE)
    }

    private fun taskItemSelected(task: ProjectTask) {
        record.task = task
        action_start.isEnabled = (record.project.id > TikalEntity.ID_NONE) && (record.task.id > TikalEntity.ID_NONE)
    }

    private fun getStartedRecord(): TimeRecord? {
        val started = prefs.getStartedRecord()
        if (started != null) {
            return started
        }

        val args = arguments
        if (args != null) {
            val projectId = args.getLong(TimeListActivity.EXTRA_PROJECT_ID)
            val taskId = args.getLong(TimeListActivity.EXTRA_TASK_ID)
            val startTime = args.getLong(TimeListActivity.EXTRA_START_TIME)
            val finishTime = args.getLong(TimeListActivity.EXTRA_FINISH_TIME, System.currentTimeMillis())

            val project = projects.firstOrNull { it.id == projectId } ?: projectEmpty
            val task = tasks.firstOrNull { it.id == taskId } ?: taskEmpty

            val record = TimeRecord(TikalEntity.ID_NONE, user, project, task)
            if (startTime > 0L) {
                record.startTime = startTime
            }
            if (finishTime > 0L) {
                record.finishTime = finishTime
            }
            return record
        }

        return null
    }

    override fun populateForm(html: String, date: Calendar) {
        val doc: Document = Jsoup.parse(html)

        val form = doc.selectFirst("form[name='timeRecordForm']") ?: return

        val inputProjects = form.selectFirst("select[name='project']") ?: return
        populateProjects(inputProjects, projects)

        val inputTasks = form.selectFirst("select[name='task']") ?: return
        populateTasks(inputTasks, tasks)

        record.project = findSelectedProject(inputProjects, projects)
        record.task = findSelectedTask(inputTasks, tasks)

        populateTaskIds(doc, projects)

        val recordStarted = getStartedRecord()
        populateForm(recordStarted)
        runOnUiThread { bindForm(record) }
    }

    fun populateForm(recordStarted: TimeRecord?) {
        Timber.v("populateForm $recordStarted")
        if (recordStarted.isNullOrEmpty()) {
            val projectFavorite = prefs.getFavoriteProject()
            if (projectFavorite != TikalEntity.ID_NONE) {
                record.project = projects.firstOrNull { it.id == projectFavorite } ?: record.project
            }
            val taskFavorite = prefs.getFavoriteTask()
            if (taskFavorite != TikalEntity.ID_NONE) {
                record.task = tasks.firstOrNull { it.id == taskFavorite } ?: record.task
            }
        } else {
            record.project = projects.firstOrNull { it.id == recordStarted!!.project.id }
                ?: projectEmpty
            record.task = tasks.firstOrNull { it.id == recordStarted!!.task.id } ?: taskEmpty
            record.start = recordStarted!!.start
        }
    }

    fun later(intent: Intent?) {
        this.intentLater = intent
    }

    private fun editRecord(record: TimeRecord, requestId: Int = TimeListActivity.REQUEST_EDIT) {
        val intent = Intent(context, TimeEditActivity::class.java)
        intent.putExtra(TimeEditActivity.EXTRA_DATE, date.timeInMillis)
        if (record.id == TikalEntity.ID_NONE) {
            intent.putExtra(TimeEditActivity.EXTRA_PROJECT_ID, record.project.id)
            intent.putExtra(TimeEditActivity.EXTRA_TASK_ID, record.task.id)
            intent.putExtra(TimeEditActivity.EXTRA_START_TIME, record.startTime)
            intent.putExtra(TimeEditActivity.EXTRA_FINISH_TIME, record.finishTime)
        } else {
            intent.putExtra(TimeEditActivity.EXTRA_RECORD, record.id)
        }
        startActivityForResult(intent, requestId)
    }
}