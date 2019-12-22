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

package com.tikalk.worktracker.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.ShareCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.tikalk.app.isNavDestination
import com.tikalk.html.findParentElement
import com.tikalk.worktracker.R
import com.tikalk.worktracker.app.TrackerFragment
import com.tikalk.worktracker.auth.LoginFragment
import com.tikalk.worktracker.db.TrackerDatabase
import com.tikalk.worktracker.db.toReportRecord
import com.tikalk.worktracker.db.toTimeRecord
import com.tikalk.worktracker.model.Project
import com.tikalk.worktracker.model.ProjectTask
import com.tikalk.worktracker.model.time.ReportFilter
import com.tikalk.worktracker.model.time.ReportTotals
import com.tikalk.worktracker.model.time.TaskRecordStatus
import com.tikalk.worktracker.model.time.TimeRecord
import com.tikalk.worktracker.net.InternetFragment
import com.tikalk.worktracker.time.formatCurrency
import com.tikalk.worktracker.time.formatElapsedTime
import com.tikalk.worktracker.time.parseSystemDate
import com.tikalk.worktracker.time.parseSystemTime
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_report_list.*
import kotlinx.android.synthetic.main.report_totals.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class ReportFragment : InternetFragment(),
    LoginFragment.OnLoginListener {

    private val recordsData = MutableLiveData<List<TimeRecord>>()
    private val totalsData = MutableLiveData<ReportTotals>()
    private val filterData = MutableLiveData<ReportFilter>()
    private var listAdapter = ReportAdapter(ReportFilter())
    private val projects: MutableList<Project> = CopyOnWriteArrayList()
    private val tasks: MutableList<ProjectTask> = CopyOnWriteArrayList()
    private var firstRun = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        firstRun = (savedInstanceState == null)
        recordsData.observe(this, Observer<List<TimeRecord>> { records ->
            bindList(records)
        })
        totalsData.observe(this, Observer<ReportTotals> { totals ->
            bindTotals(totals)
        })
        filterData.observe(this, Observer<ReportFilter> { filter ->
            this.listAdapter = ReportAdapter(filter)
            list.adapter = listAdapter
            if (firstRun) {
                fetchPage(filter)
            } else {
                showProgress(true)
                loadPage()
                    .subscribe({
                        populateTotals(recordsData.value)
                    }, { err ->
                        Timber.e(err, "Error loading page: ${err.message}")
                        showProgressMain(false)
                    })
                    .addTo(disposables)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_report_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        list.adapter = listAdapter
    }

    private fun fetchPage(filter: ReportFilter, progress: Boolean = true) {
        Timber.i("fetchPage filter=$filter")
        // Show a progress spinner, and kick off a background task to fetch the page.
        if (progress) showProgress(true)

        // Fetch from local database first.
        loadPage()
            .subscribe({
                populateTotals(recordsData.value)

                // Fetch from remote server.
                service.generateReport(filter.toFields())
                    .subscribeOn(Schedulers.io())
                    .subscribe({ response ->
                        if (isValidResponse(response)) {
                            val html = response.body()!!
                            processPage(html, progress)
                        } else {
                            authenticateMain()
                        }
                    }, { err ->
                        Timber.e(err, "Error fetching page: ${err.message}")
                        handleErrorMain(err)
                        if (progress) showProgressMain(false)
                    })
                    .addTo(disposables)
            }, { err ->
                Timber.e(err, "Error loading page: ${err.message}")
                if (progress) showProgressMain(false)
            })
            .addTo(disposables)
    }

    private fun processPage(html: String, progress: Boolean = true) {
        val records = ArrayList<TimeRecord>()
        val doc: Document = Jsoup.parse(html)
        populateList(doc, records)
        populateTotals(records)
        savePage(records)
        if (progress) showProgressMain(false)
    }

    /** Populate the list. */
    private fun populateList(doc: Document, records: MutableList<TimeRecord>) {
        records.clear()

        var columnIndexDate = -1
        var columnIndexProject = -1
        var columnIndexTask = -1
        var columnIndexStart = -1
        var columnIndexFinish = -1
        var columnIndexNote = -1
        var columnIndexCost = -1

        // The first row of the table is the header
        val table = findRecordsTable(doc)
        if (table != null) {
            // loop through all the rows and parse each record
            val rows = table.getElementsByTag("tr")
            val size = rows.size
            val totalsRowIndex = size - 1
            if (size > 1) {
                val headerRow = rows.first()
                if (headerRow != null) {
                    val children = headerRow.children()
                    val childrenSize = children.size
                    for (col in 0 until childrenSize) {
                        val th = children[col]
                        when (th.ownText()) {
                            "Date" -> columnIndexDate = col
                            "Project" -> columnIndexProject = col
                            "Task" -> columnIndexTask = col
                            "Start" -> columnIndexStart = col
                            "Finish" -> columnIndexFinish = col
                            "Note" -> columnIndexNote = col
                            "Cost" -> columnIndexCost = col
                        }
                    }

                    val totalsBlankRowIndex = totalsRowIndex - 1
                    for (i in 1 until totalsBlankRowIndex) {
                        val tr = rows[i]
                        val record = parseRecord(tr,
                            i,
                            columnIndexDate,
                            columnIndexProject,
                            columnIndexTask,
                            columnIndexStart,
                            columnIndexFinish,
                            columnIndexNote,
                            columnIndexCost)
                        if (record != null) {
                            records.add(record)
                        }
                    }
                }
            }
        }

        recordsData.postValue(records)
    }

    private fun populateTotals(records: List<TimeRecord>?) {
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

        totalsData.postValue(totals)
    }

    @MainThread
    private fun bindList(records: List<TimeRecord>) {
        if (!isVisible) return
        listAdapter.submitList(records)
        if (records === recordsData.value) {
            listAdapter.notifyDataSetChanged()
        }
        if (records.isNotEmpty()) {
            listSwitcher.displayedChild = CHILD_LIST
        } else {
            listSwitcher.displayedChild = CHILD_EMPTY
        }
    }

    @MainThread
    private fun bindTotals(totals: ReportTotals) {
        val context: Context = requireContext()
        val timeBuffer = StringBuilder(20)
        val timeFormatter = Formatter(timeBuffer, Locale.getDefault())
        val filter = filterData.value

        if (filter?.showDurationField == true) {
            timeBuffer.setLength(0)
            durationTotalLabel.visibility = View.VISIBLE
            durationTotal.text = formatElapsedTime(context, timeFormatter, totals.duration).toString()
        } else {
            durationTotalLabel.visibility = View.INVISIBLE
            durationTotal.text = null
        }
        if (filter?.showCostField == true) {
            timeBuffer.setLength(0)
            costTotalLabel.visibility = View.VISIBLE
            costTotal.text = formatCurrency(context, timeFormatter, totals.cost).toString()
        } else {
            costTotalLabel.visibility = View.INVISIBLE
            costTotal.text = null
        }
    }

    override fun authenticate(submit: Boolean) {
        Timber.i("authenticate submit=$submit")
        if (!isNavDestination(R.id.loginFragment)) {
            val args = Bundle()
            requireFragmentManager().putFragment(args, LoginFragment.EXTRA_CALLER, this)
            args.putBoolean(LoginFragment.EXTRA_SUBMIT, submit)
            findNavController().navigate(R.id.action_reportList_to_login, args)
        }
    }

    /**
     * Find the first table whose first row has both class="tableHeader" and its label is 'Date'
     */
    private fun findRecordsTable(doc: Document): Element? {
        val body = doc.body()
        val form = body.selectFirst("form[name='reportViewForm']") ?: return null
        val td = form.selectFirst("td[class='tableHeader']") ?: return null

        val label = td.ownText()
        if (label == "Date") {
            return findParentElement(td, "table")
        }

        return null
    }

    private fun parseRecord(row: Element,
                            index: Int,
                            columnIndexDate: Int,
                            columnIndexProject: Int,
                            columnIndexTask: Int,
                            columnIndexStart: Int,
                            columnIndexFinish: Int,
                            columnIndexNote: Int,
                            columnIndexCost: Int): TimeRecord? {
        val cols = row.getElementsByTag("td")
        val record = TimeRecord.EMPTY.copy()
        record.id = index + 1L
        record.status = TaskRecordStatus.CURRENT

        val tdDate = cols[columnIndexDate]
        val date = parseSystemDate(tdDate.ownText()) ?: return null

        var project: Project = record.project
        if (columnIndexProject > 0) {
            val tdProject = cols[columnIndexProject]
            if (tdProject.attr("class") == "tableHeader") {
                return null
            }
            val projectName = tdProject.ownText()
            project = parseRecordProject(projectName) ?: return null
            record.project = project
        }

        if (columnIndexTask > 0) {
            val tdTask = cols[columnIndexTask]
            val taskName = tdTask.ownText()
            val task = parseRecordTask(project, taskName) ?: return null
            record.task = task
        }

        if (columnIndexStart > 0) {
            val tdStart = cols[columnIndexStart]
            val startText = tdStart.ownText()
            val start = parseRecordTime(date, startText) ?: return null
            record.start = start
        }

        if (columnIndexFinish > 0) {
            val tdFinish = cols[columnIndexFinish]
            val finishText = tdFinish.ownText()
            val finish = parseRecordTime(date, finishText) ?: return null
            record.finish = finish
        }

        if (columnIndexNote > 0) {
            val tdNote = cols[columnIndexNote]
            val noteText = tdNote.ownText()
            val note = parseRecordNote(noteText)
            record.note = note
        }

        if (columnIndexCost > 0) {
            val tdCost = cols[columnIndexCost]
            val costText = tdCost.ownText()
            val cost = parseCost(costText)
            record.cost = cost
        }

        return record
    }

    private fun parseRecordProject(name: String): Project? {
        return projects.find { name == it.name } ?: Project(name)
    }

    private fun parseRecordTask(project: Project, name: String): ProjectTask? {
        return project.tasks.find { task -> (task.name == name) } ?: ProjectTask(name)
    }

    private fun parseRecordTime(date: Calendar, text: String): Calendar? {
        return parseSystemTime(date, text)
    }

    private fun parseRecordNote(text: String): String {
        return text.trim()
    }

    private fun parseCost(cost: String): Double {
        return if (cost.isBlank()) 0.00 else cost.toDouble()
    }

    private fun loadPage(): Single<Unit> {
        val filter = filterData.value
        Timber.i("loadPage $filter")
        return Single.fromCallable {
            loadProjectsWithTasks(db)
            if (filter != null) {
                loadRecords(db, filter.startTime, filter.finishTime)
            } else {
                loadRecords(db, 0L, 0L)
            }
        }
            .subscribeOn(Schedulers.io())
    }

    private fun loadRecords(db: TrackerDatabase, start: Long, finish: Long) {
        val reportRecordsDao = db.reportRecordDao()
        val reportRecordsDb = reportRecordsDao.queryByDate(start, finish)
        if (reportRecordsDb.isEmpty()) {
            val recordsDao = db.timeRecordDao()
            val recordsDb = recordsDao.queryByDate(start, finish)
            val records = recordsDb.map { it.toTimeRecord(projects, tasks) }
            recordsData.postValue(records)
        } else {
            val records = reportRecordsDb.map { it.toTimeRecord(projects, tasks) }
            recordsData.postValue(records)
        }
    }

    private fun loadProjectsWithTasks(db: TrackerDatabase) {
        val projectsDao = db.projectDao()
        val projectsWithTasks = projectsDao.queryAllWithTasks()
        val projectsDb = ArrayList<Project>()
        val tasksDb = HashSet<ProjectTask>()
        for (projectWithTasks in projectsWithTasks) {
            val project = projectWithTasks.project
            project.tasks = projectWithTasks.tasks
            projectsDb.add(project)
            tasksDb.addAll(projectWithTasks.tasks)
        }
        projects.clear()
        projects.addAll(projectsDb)

        tasks.clear()
        tasks.addAll(tasksDb)
    }

    @MainThread
    fun run() {
        Timber.i("run")

        val args = arguments
        if (args != null) {
            if (args.containsKey(EXTRA_FILTER)) {
                val filter = args.getParcelable<ReportFilter>(EXTRA_FILTER)
                if (filter != null) {
                    filterData.value = filter
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        run()
    }

    override fun onLoginSuccess(fragment: LoginFragment, login: String) {
        Timber.i("login success")
        fragment.dismissAllowingStateLoss()
        run()
    }

    override fun onLoginFailure(fragment: LoginFragment, login: String, reason: String) {
        Timber.e("login failure: $reason")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.report, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export_csv -> {
                exportCSV(item)
                return true
            }
            R.id.menu_export_html -> {
                exportHTML(item)
                return true
            }
            R.id.menu_export_xml -> {
                exportXML(item)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportCSV(item: MenuItem? = null) {
        item?.isEnabled = false
        showProgress(true)

        val context = requireContext()
        val records = recordsData.value ?: return
        val filter = filterData.value ?: return

        ReportExporterCSV(context, records, filter)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ uri ->
                Timber.i("Exported CSV to $uri")
                shareFile(context, uri, ReportExporterCSV.MIME_TYPE)
                showProgress(false)
                item?.isEnabled = true
            }, { err ->
                Timber.e(err, "Error updating profile: ${err.message}")
                showProgress(false)
                item?.isEnabled = true
            })
            .addTo(disposables)
    }

    private fun exportHTML(item: MenuItem? = null) {
        item?.isEnabled = false
        showProgress(true)

        val context = requireContext()
        val records = recordsData.value ?: return
        val filter = filterData.value ?: return
        val totals = totalsData.value ?: return

        ReportExporterHTML(context, records, filter, totals)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file ->
                Timber.i("Exported HTML to $file")
                shareFile(context, file, ReportExporterHTML.MIME_TYPE)
                showProgress(false)
                item?.isEnabled = true
            }, { err ->
                Timber.e(err, "Error updating profile: ${err.message}")
                showProgress(false)
                item?.isEnabled = true
            })
            .addTo(disposables)
    }

    private fun exportXML(item: MenuItem? = null) {
        item?.isEnabled = false
        showProgress(true)

        val context = requireContext()
        val records = recordsData.value ?: return
        val filter = filterData.value ?: return

        ReportExporterXML(context, records, filter)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ file ->
                Timber.i("Exported XML to $file")
                shareFile(context, file, ReportExporterXML.MIME_TYPE)
                showProgress(false)
                item?.isEnabled = true
            }, { err ->
                Timber.e(err, "Error updating profile: ${err.message}")
                showProgress(false)
                item?.isEnabled = true
            })
            .addTo(disposables)
    }

    private fun shareFile(context: Context, fileUri: Uri, mimeType: String? = null) {
        val activity = this.activity ?: return
        val intent = ShareCompat.IntentBuilder.from(activity)
            .addStream(fileUri)
            .setType(mimeType ?: context.contentResolver.getType(fileUri))
            .intent
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Validate that the device can open your File!
        val pm = context.packageManager
        if (intent.resolveActivity(pm) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(context, fileUri.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun savePage(records: List<TimeRecord>) {
        Timber.i("savePage")
        saveRecords(db, records)
    }

    private fun saveRecords(db: TrackerDatabase, records: List<TimeRecord>) {
        val recordsDao = db.reportRecordDao()
        recordsDao.deleteAll()
        recordsDao.insert(records.map { it.toReportRecord() })
    }

    companion object {
        const val EXTRA_CALLER = TrackerFragment.EXTRA_CALLER
        const val EXTRA_FILTER = "filter"

        private const val CHILD_LIST = 0
        private const val CHILD_EMPTY = 1
    }
}