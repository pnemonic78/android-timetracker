/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2017, Tikal Knowledge, Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.tikalk.worktracker.BuildConfig
import com.tikalk.worktracker.R
import com.tikalk.worktracker.auth.LoginActivity
import com.tikalk.worktracker.model.Project
import com.tikalk.worktracker.model.ProjectTask
import com.tikalk.worktracker.model.User
import com.tikalk.worktracker.model.time.TimeRecord
import com.tikalk.worktracker.model.time.split
import com.tikalk.worktracker.net.InternetActivity
import com.tikalk.worktracker.net.TimeTrackerService
import com.tikalk.worktracker.net.TimeTrackerServiceFactory
import com.tikalk.worktracker.preference.TimeTrackerPrefs
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_time_edit.*
import kotlinx.android.synthetic.main.progress.*
import kotlinx.android.synthetic.main.time_form.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Response
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class TimeEditActivity : InternetActivity() {

    companion object {
        private const val REQUEST_AUTHENTICATE = 1

        private const val STATE_DATE = "date"

        const val EXTRA_DATE = BuildConfig.APPLICATION_ID + ".DATE"
        const val EXTRA_RECORD = BuildConfig.APPLICATION_ID + ".RECORD_ID"

        const val EXTRA_PROJECT_ID = BuildConfig.APPLICATION_ID + ".PROJECT_ID"
        const val EXTRA_TASK_ID = BuildConfig.APPLICATION_ID + ".TASK_ID"
        const val EXTRA_START_TIME = BuildConfig.APPLICATION_ID + ".START_TIME"
        const val EXTRA_FINISH_TIME = BuildConfig.APPLICATION_ID + ".FINISH_TIME"
    }

    private val context: Context = this

    private lateinit var prefs: TimeTrackerPrefs

    // UI references
    private var submitMenuItem: MenuItem? = null
    private var startPickerDialog: TimePickerDialog? = null
    private var finishPickerDialog: TimePickerDialog? = null

    private val disposables = CompositeDisposable()
    private var date = Calendar.getInstance()
    private var user = User("")
    private var record = TimeRecord(user, Project(""), ProjectTask(""))
    private val projects = ArrayList<Project>()
    private val tasks = ArrayList<ProjectTask>()
    private var errorMessage: String = ""
    private var projectEmpty: Project = Project.EMPTY
    private var taskEmpty: ProjectTask = ProjectTask.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = TimeTrackerPrefs(this)

        // Set up the form.
        setContentView(R.layout.activity_time_edit)

        user.username = prefs.userCredentials.login
        user.email = user.username

        project_input.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(adapterView: AdapterView<*>) {
                record.project = projectEmpty
            }

            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                val project = adapterView.adapter.getItem(position) as Project
                projectItemSelected(project)
            }
        }
        task_input.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(adapterView: AdapterView<*>) {
                record.task = taskEmpty
            }

            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, id: Long) {
                val task = adapterView.adapter.getItem(position) as ProjectTask
                taskItemSelected(task)
            }
        }
        start_input.setOnClickListener { pickStartTime() }
        finish_input.setOnClickListener { pickFinishTime() }

        handleIntent(intent, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent, savedInstanceState: Bundle? = null) {
        val now = date.timeInMillis
        val extras = intent.extras
        if (extras != null) {
            val dateExtra = extras.getLong(EXTRA_DATE, now)
            date.timeInMillis = savedInstanceState?.getLong(STATE_DATE, dateExtra) ?: dateExtra
            val recordId = extras.getLong(EXTRA_RECORD, record.id)
            fetchPage(date, recordId)
        } else {
            date.timeInMillis = savedInstanceState?.getLong(STATE_DATE, now) ?: now
            fetchPage(date, 0L)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        menuInflater.inflate(R.menu.time_edit, menu)
        submitMenuItem = menu.findItem(R.id.menu_submit)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                onBackPressed()
                return true
            }
            R.id.menu_delete -> {
                deleteRecord()
                return true
            }
            R.id.menu_submit -> {
                submit()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchPage(date: Calendar, id: Long) {
        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true)

        val authToken = prefs.basicCredentials.authToken()
        val service = TimeTrackerServiceFactory.createPlain(authToken)

        val fetcher: Single<Response<String>> = if (id == 0L) {
            val dateFormatted = formatSystemDate(date)
            service.fetchTimes(dateFormatted)
        } else {
            service.fetchTimes(id)
        }
        fetcher
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                showProgress(false)

                this.date = date
                if (validResponse(response)) {
                    populateForm(response.body()!!, date, id)
                } else {
                    authenticate(true)
                }
            }, { err ->
                Timber.e(err, "Error fetching page: ${err.message}")
            })
            .addTo(disposables)
    }

    private fun validResponse(response: Response<String>): Boolean {
        val body = response.body()
        if (response.isSuccessful && (body != null)) {
            val networkResponse = response.raw().networkResponse()
            val priorResponse = response.raw().priorResponse()
            if ((networkResponse != null) && (priorResponse != null) && priorResponse.isRedirect) {
                val networkUrl = networkResponse.request().url()
                val priorUrl = priorResponse.request().url()
                if (networkUrl == priorUrl) {
                    return true
                }
                if (networkUrl.pathSegments()[networkUrl.pathSize() - 1] == TimeTrackerService.PHP_TIME) {
                    return true
                }
                return false
            }
            return true
        }
        return false
    }

    /** Populate the record and then bind the form. */
    private fun populateForm(html: String, date: Calendar, id: Long) {
        val doc: Document = Jsoup.parse(html)

        record.id = id

        val errorNode = doc.selectFirst("td[class='error']")
        errorMessage = errorNode?.text()?.trim() ?: ""

        val form = doc.selectFirst("form[name='timeRecordForm']")

        val inputProjects = form.selectFirst("select[name='project']")
        populateProjects(doc, inputProjects, projects)
        record.project = findSelectedProject(inputProjects, projects)

        val inputTasks = form.selectFirst("select[name='task']")
        populateTasks(doc, inputTasks, tasks)
        record.task = findSelectedTask(inputTasks, tasks)

        val inputStart = form.selectFirst("input[name='start']")
        val startValue = inputStart.attr("value")
        record.start = parseSystemTime(date, startValue)

        val inputFinish = form.selectFirst("input[name='finish']")
        val finishValue = inputFinish.attr("value")
        record.finish = parseSystemTime(date, finishValue)

        val inputNote = form.selectFirst("textarea[name='note']")
        record.note = inputNote.text()

        if (id == 0L) {
            val extras = intent.extras
            if (extras != null) {
                val projectId = extras.getLong(EXTRA_PROJECT_ID)
                val taskId = extras.getLong(EXTRA_TASK_ID)
                val startTime = extras.getLong(EXTRA_START_TIME)
                val finishTime = extras.getLong(EXTRA_FINISH_TIME)

                val project = projects.firstOrNull { it.id == projectId } ?: projectEmpty
                val task = tasks.firstOrNull { it.id == taskId } ?: taskEmpty

                record = TimeRecord(user, project, task)
                if (startTime > 0L) {
                    record.startTime = startTime
                } else {
                    record.start = null
                }
                if (finishTime > 0L) {
                    record.finishTime = finishTime
                } else {
                    record.finish = null
                }
            }
        }

        bindForm(record)
    }

    private fun findScript(doc: Document, tokenStart: String, tokenEnd: String): String {
        val scripts = doc.select("script")
        var scriptText: String
        var indexStart: Int
        var indexEnd: Int

        for (script in scripts) {
            scriptText = script.html()
            indexStart = scriptText.indexOf(tokenStart)
            if (indexStart >= 0) {
                indexStart += tokenStart.length
                indexEnd = scriptText.indexOf(tokenEnd, indexStart)
                if (indexEnd < 0) {
                    indexEnd = scriptText.length
                }
                return scriptText.substring(indexStart, indexEnd)
            }
        }

        return ""
    }

    private fun populateProjects(doc: Document, select: Element, projects: MutableList<Project>) {
        projects.clear()

        val options = select.select("option")
        var value: String
        var name: String
        for (option in options) {
            name = option.ownText()
            value = option.attr("value")
            val item = Project(name)
            if (value.isEmpty()) {
                projectEmpty = item
            } else {
                item.id = value.toLong()
            }
            projects.add(item)
        }

        populateTaskIds(doc, projects)
    }

    private fun populateTasks(doc: Document, select: Element, tasks: MutableList<ProjectTask>) {
        tasks.clear()

        val options = select.select("option")
        var value: String
        var name: String
        for (option in options) {
            name = option.ownText()
            value = option.attr("value")
            val item = ProjectTask(name)
            if (value.isEmpty()) {
                taskEmpty = item
            } else {
                item.id = value.toLong()
            }
            tasks.add(item)
        }
    }

    private fun populateTaskIds(doc: Document, projects: List<Project>) {
        val tokenStart = "var task_ids = new Array();"
        val tokenEnd = "// Prepare an array of task names."
        val scriptText = findScript(doc, tokenStart, tokenEnd)

        for (project in projects) {
            project.taskIds.clear()
        }

        if (scriptText.isNotEmpty()) {
            val pattern = Pattern.compile("task_ids\\[(\\d+)\\] = \"(.+)\"")
            val lines = scriptText.split(";")
            for (line in lines) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    val projectId = matcher.group(1).toLong()
                    val taskIds: List<Long> = matcher.group(2)
                        .split(",")
                        .map { it.toLong() }
                    projects.find { it.id == projectId }!!
                        .taskIds.addAll(taskIds)
                }
            }
        }
    }

    private fun bindForm(record: TimeRecord) {
        error_label.text = errorMessage
        project_input.adapter = ArrayAdapter<Project>(context, android.R.layout.simple_list_item_1, projects.toTypedArray())
        project_input.setSelection(projects.indexOf(record.project))
        task_input.adapter = ArrayAdapter<ProjectTask>(context, android.R.layout.simple_list_item_1, tasks.toTypedArray())
        task_input.setSelection(tasks.indexOf(record.task))
        start_input.text = if (record.start != null)
            DateUtils.formatDateTime(context, record.startTime, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
        else
            ""
        start_input.error = null
        startPickerDialog = null
        finish_input.text = if (record.finish != null)
            DateUtils.formatDateTime(context, record.finishTime, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
        else
            ""
        finish_input.error = null
        finishPickerDialog = null
        note_input.setText(record.note)
        project_input.requestFocus()
    }

    private fun bindRecord(record: TimeRecord) {
        record.note = note_input.text.toString()
    }

    private fun authenticate(immediate: Boolean = false) {
        showProgress(true)
        val intent = Intent(context, LoginActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_SUBMIT, immediate)
        startActivityForResult(intent, REQUEST_AUTHENTICATE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_AUTHENTICATE) {
            if (resultCode == RESULT_OK) {
                fetchPage(date, record.id)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DATE, date.timeInMillis)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        date.timeInMillis = savedInstanceState.getLong(STATE_DATE)
    }

    private fun submit() {
        val record = this.record

        if (!validateForm(record)) {
            return
        }
        bindRecord(record)

        if (record.id == 0L) {
            val splits = record.split()
            val size = splits.size
            val lastIndex = size - 1
            for (i in 0 until size) {
                submit(splits[i], i == 0, i == lastIndex)
            }
        } else {
            submit(record, true, true)
        }
    }

    private fun submit(record: TimeRecord, first: Boolean = true, last: Boolean = true) {
        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        if (first) {
            showProgress(true)
        }

        val authToken = prefs.basicCredentials.authToken()
        val service = TimeTrackerServiceFactory.createPlain(authToken)

        val submitter: Single<Response<String>> = if (record.id == 0L) {
            service.addTime(record.project.id,
                record.task.id,
                formatSystemDate(record.start),
                formatSystemTime(record.start),
                formatSystemTime(record.finish),
                record.note)
        } else {
            service.editTime(record.id,
                record.project.id,
                record.task.id,
                formatSystemDate(record.start),
                formatSystemTime(record.start),
                formatSystemTime(record.finish),
                record.note)
        }
        submitter
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                if (last) {
                    showProgress(false)
                }

                if (validResponse(response)) {
                    if (last) {
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    authenticate(true)
                }
            }, { err ->
                Timber.e(err, "Error saving record: ${err.message}")
                showProgress(false)
            })
            .addTo(disposables)
    }

    private fun pickStartTime() {
        if (startPickerDialog == null) {
            val cal = getCalendar(record.start)
            val listener = TimePickerDialog.OnTimeSetListener { picker, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                record.start = cal
                start_input.text = DateUtils.formatDateTime(context, cal.timeInMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
                start_input.error = null
            }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            startPickerDialog = TimePickerDialog(context, listener, hour, minute, DateFormat.is24HourFormat(context))
        }
        startPickerDialog!!.show()
    }

    private fun pickFinishTime() {
        if (finishPickerDialog == null) {
            val cal = getCalendar(record.finish)
            val listener = TimePickerDialog.OnTimeSetListener { picker, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                record.finish = cal
                finish_input.text = DateUtils.formatDateTime(context, cal.timeInMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
                finish_input.error = null
            }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            finishPickerDialog = TimePickerDialog(context, listener, hour, minute, DateFormat.is24HourFormat(context))
        }
        finishPickerDialog!!.show()
    }

    private fun getCalendar(cal: Calendar?): Calendar {
        if (cal == null) {
            val calDate = Calendar.getInstance()
            calDate.timeInMillis = date.timeInMillis
            return calDate
        }
        return cal
    }

    private fun validateForm(record: TimeRecord): Boolean {
        var valid = true

        if (record.project.id <= 0) {
            valid = false
            (project_input.selectedView as TextView).error = getString(R.string.error_field_required)
        } else {
            (project_input.selectedView as TextView).error = null
        }
        if (record.task.id <= 0) {
            valid = false
            (task_input.selectedView as TextView).error = getString(R.string.error_field_required)
        } else {
            (task_input.selectedView as TextView).error = null
        }
        if (record.start == null) {
            valid = false
            start_input.error = getString(R.string.error_field_required)
        } else {
            start_input.error = null
        }
        if (record.finish == null) {
            valid = false
            finish_input.error = getString(R.string.error_field_required)
        } else if (record.startTime + DateUtils.MINUTE_IN_MILLIS > record.finishTime) {
            valid = false
            finish_input.error = getString(R.string.error_finish_time_before_start_time)
        } else {
            finish_input.error = null
        }

        return valid
    }

    private fun filterTasks(project: Project) {
        val filtered = tasks.filter { it.id in project.taskIds }
        val options = ArrayList<ProjectTask>(filtered.size + 1)
        options.add(taskEmpty)
        options.addAll(filtered)
        task_input.adapter = ArrayAdapter<ProjectTask>(context, android.R.layout.simple_list_item_1, options)
        task_input.setSelection(options.indexOf(record.task))
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        time_form.visibility = if (show) View.GONE else View.VISIBLE
        time_form.animate().setDuration(shortAnimTime).alpha(
            (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                time_form.visibility = if (show) View.GONE else View.VISIBLE
            }
        })

        progress.visibility = if (show) View.VISIBLE else View.GONE
        progress.animate().setDuration(shortAnimTime).alpha(
            (if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                progress.visibility = if (show) View.VISIBLE else View.GONE
            }
        })

        submitMenuItem?.isEnabled = !show
    }

    private fun findSelectedProject(project: Element, projects: List<Project>): Project {
        for (option in project.children()) {
            if (option.hasAttr("selected")) {
                val value = option.attr("value")
                if (value.isNotEmpty()) {
                    val id = value.toLong()
                    return projects.find { id == it.id }!!
                }
                break
            }
        }
        return projects[0]
    }

    private fun findSelectedTask(task: Element, tasks: List<ProjectTask>): ProjectTask {
        for (option in task.children()) {
            if (option.hasAttr("selected")) {
                val value = option.attr("value")
                if (value.isNotEmpty()) {
                    val id = value.toLong()
                    return tasks.find { id == it.id }!!
                }
                break
            }
        }
        return tasks[0]
    }

    private fun deleteRecord() {
        if (record.id == 0L) {
            setResult(RESULT_OK)
            finish()
        } else {
            deleteRecord(record)
        }
    }

    private fun deleteRecord(record: TimeRecord) {
        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true)

        val authToken = prefs.basicCredentials.authToken()
        val service = TimeTrackerServiceFactory.createPlain(authToken)

        service.deleteTime(record.id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                showProgress(false)

                if (validResponse(response)) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    authenticate(true)
                }
            }, { err ->
                Timber.e(err, "Error deleting record: ${err.message}")
            })
            .addTo(disposables)
    }

    private fun projectItemSelected(project: Project) {
        record.project = project
        filterTasks(project)
    }

    private fun taskItemSelected(task: ProjectTask) {
        record.task = task
    }
}
