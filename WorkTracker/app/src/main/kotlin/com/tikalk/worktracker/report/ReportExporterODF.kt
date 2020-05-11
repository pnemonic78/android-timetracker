/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2020, Tikal Knowledge, Ltd.
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
import android.net.Uri
import android.text.format.DateUtils
import com.tikalk.worktracker.R
import com.tikalk.worktracker.model.time.ReportFilter
import com.tikalk.worktracker.model.time.ReportTotals
import com.tikalk.worktracker.model.time.TimeRecord
import io.reactivex.SingleObserver
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument
import org.odftoolkit.odfdom.doc.table.OdfTableCell
import java.io.File
import java.util.*

/**
 * Write the list of records as an OpenDocument Format (ODF) spreadsheet file.
 */
class ReportExporterODF(context: Context, records: List<TimeRecord>, filter: ReportFilter, totals: ReportTotals) : ReportExporter(context, records, filter, totals) {

    override fun createRunner(context: Context, records: List<TimeRecord>, filter: ReportFilter, totals: ReportTotals, observer: SingleObserver<in Uri>): ReportExporterRunner {
        return ReportExporterODFRunner(context, records, filter, totals, observer)
    }

    private class ReportExporterODFRunner(context: Context, records: List<TimeRecord>, filter: ReportFilter, totals: ReportTotals, observer: SingleObserver<in Uri>) : ReportExporterRunner(context, records, filter, totals, observer) {
        override fun writeContents(context: Context, records: List<TimeRecord>, filter: ReportFilter, totals: ReportTotals, folder: File, filenamePrefix: String): File {
            val showProjectField = filter.showProjectField
            val showTaskField = filter.showTaskField
            val showStartField = filter.showStartField
            val showFinishField = filter.showFinishField
            val showDurationField = filter.showDurationField
            val showNoteField = filter.showNoteField
            val showCostField = filter.showCostField

            val locale = Locale.getDefault()
            val currency = Currency.getInstance(locale)
            val currencyCode = currency.currencyCode

            val file = File(folder, filenamePrefix + EXTENSION)
            out = null

            val doc = OdfSpreadsheetDocument.newSpreadsheetDocument()
            val sheet = doc.contentRoot
            println(sheet)
            val tables = doc.tableList
            println(tables)
            if (tables.isEmpty()) {
                val tableElem = sheet.newTableTableElement()
                sheet.appendChild(tableElem)
            }
            val table = tables[0]

            var rowIndex = 0
            var columnIndex = 0
            var cell: OdfTableCell

            cell = table.getCellByPosition(columnIndex++, rowIndex)
            cell.stringValue = context.getString(R.string.date_header)
            if (showProjectField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.project_header)
            }
            if (showTaskField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.task_header)
            }
            if (showStartField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.start_header)
            }
            if (showFinishField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.finish_header)
            }
            if (showDurationField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.duration_header)
            }
            if (showNoteField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.note_header)
            }
            if (showCostField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.stringValue = context.getString(R.string.cost_header)
            }

            for (record in records) {
                rowIndex++
                columnIndex = 0

                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.dateValue = record.start
                if (showProjectField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.stringValue = record.project.name
                }
                if (showTaskField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.stringValue = record.task.name
                }
                if (showStartField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.timeValue = record.start
                }
                if (showFinishField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.timeValue = record.finish
                }
                if (showDurationField) {
                    val durationMs = record.finishTime - record.startTime
                    val durationHs = durationMs.toDouble() / DateUtils.HOUR_IN_MILLIS
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.doubleValue = durationHs
                }
                if (showNoteField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.stringValue = record.note
                }
                if (showCostField) {
                    cell = table.getCellByPosition(columnIndex++, rowIndex)
                    cell.setCurrencyValue(record.cost, currencyCode)
                }
            }

            rowIndex += 2
            columnIndex = 0
            cell = table.getCellByPosition(columnIndex++, rowIndex)
            cell.stringValue = context.getString(R.string.total)
            if (showProjectField) {
                columnIndex++
            }
            if (showTaskField) {
                columnIndex++
            }
            if (showStartField) {
                columnIndex++
            }
            if (showFinishField) {
                columnIndex++
            }
            if (showDurationField) {
                val durationMs = totals.duration
                val durationHs = durationMs.toDouble() / DateUtils.HOUR_IN_MILLIS
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.doubleValue = durationHs
            }
            if (showNoteField) {
                columnIndex++
            }
            if (showCostField) {
                cell = table.getCellByPosition(columnIndex++, rowIndex)
                cell.setCurrencyValue(totals.cost, currencyCode)
            }

            doc.save(file)
            doc.close()

            return file
        }
    }

    companion object {
        private val SPREADSHEET = OdfSpreadsheetDocument.OdfMediaType.SPREADSHEET
        val MIME_TYPE = SPREADSHEET.mediaTypeString
        val EXTENSION = "." + SPREADSHEET.suffix
    }
}