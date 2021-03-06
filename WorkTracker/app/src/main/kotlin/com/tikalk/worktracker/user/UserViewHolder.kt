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

package com.tikalk.worktracker.user

import androidx.annotation.MainThread
import androidx.recyclerview.widget.RecyclerView
import com.tikalk.worktracker.R
import com.tikalk.worktracker.databinding.UserItemBinding
import com.tikalk.worktracker.model.User

class UserViewHolder(val binding: UserItemBinding) : RecyclerView.ViewHolder(binding.root) {

    var user: User? = null
        set(value) {
            field = value
            if (value != null) {
                bind(value)
            } else {
                clear()
            }
        }

    @MainThread
    private fun bind(user: User) {
        binding.name.text = user.displayName
        binding.login.text = user.username
        binding.role.text = user.roles?.joinToString(", ") ?: ""
        binding.uncompletedEntry.setImageLevel(if (user.isUncompletedEntry) LEVEL_ACTIVE else LEVEL_NORMAL)
        binding.uncompletedEntry.contentDescription =
            itemView.context.getString(R.string.uncompleted_entry)
    }

    @MainThread
    private fun clear() {
        binding.name.text = ""
        binding.login.text = ""
        binding.role.text = ""
        binding.uncompletedEntry.setImageLevel(LEVEL_NORMAL)
        binding.uncompletedEntry.contentDescription = ""
    }

    companion object {
        private const val LEVEL_NORMAL = 0
        private const val LEVEL_ACTIVE = 1
    }
}
