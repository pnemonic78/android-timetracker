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
import android.content.res.Configuration
import android.graphics.Color
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.recyclerview.widget.RecyclerView
import com.tikalk.worktracker.databinding.TimeItemBinding
import com.tikalk.worktracker.model.TikalEntity
import com.tikalk.worktracker.model.time.TimeRecord
import com.tikalk.worktracker.report.toLocationItem
import java.util.*

open class TimeListViewHolder(
    private val binding: TimeItemBinding,
    private val clickListener: TimeListAdapter.OnTimeListListener? = null
) : RecyclerView.ViewHolder(binding.root),
    View.OnClickListener {

    protected val timeBuffer = StringBuilder(20)
    protected val timeFormatter: Formatter = Formatter(timeBuffer, Locale.getDefault())
    protected val night =
        (binding.root.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    var record: TimeRecord? = null
        set(value) {
            field = value
            if (value != null) {
                bind(value)
                bindColors(value)
            } else {
                clear()
            }
        }

    init {
        binding.root.setOnClickListener(this)
        // CardView does not handle clicks.
        (binding.root as ViewGroup).getChildAt(0).setOnClickListener(this)
    }

    @MainThread
    protected open fun bind(record: TimeRecord) {
        val context: Context = binding.root.context
        binding.project.text = record.project.name
        binding.task.text = record.task.name
        val startTime = record.startTime
        val endTime = record.finishTime
        timeBuffer.setLength(0)
        val formatterRange =
            DateUtils.formatDateRange(context, timeFormatter, startTime, endTime, FORMAT_DURATION)
        binding.timeRange.text = formatterRange.out() as CharSequence
        timeBuffer.setLength(0)
        val formatterElapsed = formatElapsedTime(context, timeFormatter, endTime - startTime)
        binding.timeDuration.text = formatterElapsed.out() as CharSequence
        binding.note.text = record.note
        binding.cost.text = formatCost(record.cost)
        binding.location.text =
            if (record.location.id != TikalEntity.ID_NONE) record.location.toLocationItem(context).label else ""
    }

    @MainThread
    protected open fun clear() {
        binding.project.text = ""
        binding.task.text = ""
        binding.timeRange.text = ""
        binding.timeDuration.text = ""
        binding.note.text = ""
        binding.cost.text = ""
        binding.location.text = ""
    }

    @MainThread
    private fun bindColors(record: TimeRecord) {
        val projectHash: Int =
            if (record.project.id != TikalEntity.ID_NONE) record.project.id.toInt() else record.project.hashCode()
        val taskHash: Int =
            if (record.task.id != TikalEntity.ID_NONE) record.task.id.toInt() else record.task.hashCode()
        val spread = (projectHash * projectHash * taskHash)
        val spreadBits = spread.and(511)

        // 512 combinations => 3 bits per color
        val redBits = spreadBits.and(0x07)
        val greenBits = spreadBits.shr(3).and(0x07)
        val blueBits = spreadBits.shr(6).and(0x07)
        val r = redBits * 24 //*32 => some colors too bright
        val g = greenBits * 24 //*32 => some colors too bright
        val b = blueBits * 24 //*32 => some colors too bright
        val color = if (night) Color.rgb(255 - r, 255 - g, 255 - b) else Color.rgb(r, g, b)

        bindColors(record, color)
    }

    @MainThread
    protected open fun bindColors(record: TimeRecord, color: Int) {
        binding.project.setTextColor(color)
        binding.task.setTextColor(color)
        binding.note.setTextColor(color)
        binding.cost.setTextColor(color)
        binding.location.setTextColor(color)
    }

    private fun formatCost(cost: Double): CharSequence {
        return if (cost <= 0.0) "" else cost.toString()
    }

    override fun onClick(v: View) {
        val record = this.record
        if (record != null) {
            clickListener?.onRecordClick(record)
        }
    }

    companion object {
        private const val FORMAT_DURATION = DateUtils.FORMAT_SHOW_TIME
    }
}