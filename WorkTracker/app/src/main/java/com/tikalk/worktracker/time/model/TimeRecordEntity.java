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
package com.tikalk.worktracker.time.model;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.support.annotation.NonNull;

import com.tikalk.worktracker.model.ProjectEntity;
import com.tikalk.worktracker.model.ProjectTask;
import com.tikalk.worktracker.model.ProjectTaskEntity;
import com.tikalk.worktracker.model.TikalEntity;
import com.tikalk.worktracker.model.UserEntity;

import java.sql.Date;

/**
 * Time record entity. Represents some work done for a project task.
 *
 * @author Moshe Waisberg.
 */
@Entity(tableName = "timeRecord",
        foreignKeys = {
                @ForeignKey(entity = ProjectTaskEntity.class,
                        parentColumns = "_id",
                        childColumns = "projectTaskId"),
                @ForeignKey(entity = UserEntity.class,
                        parentColumns = "_id",
                        childColumns = "userId")})
public class TimeRecordEntity extends TikalEntity {

    @Ignore
    private UserEntity user;
    private long userId;
    @Ignore
    private ProjectTask task;
    private long projectTaskId;
    public Date start;
    public Date finish;
    public String note;
    @NonNull
    public TaskRecordStatus status = TaskRecordStatus.INSERTED;

    @NonNull
    public UserEntity getUser() {
        if (user == null) {
            user = new UserEntity();
        }
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
        this.userId = (user != null) ? user._id : 0;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
        getUser()._id = userId;
    }

    @NonNull
    public ProjectTask getTask() {
        if (task == null) {
            task = new ProjectTask();
        }
        return task;
    }

    public void setTask(ProjectTask task) {
        this.task = task;
        this.projectTaskId = (task != null) ? task._id : 0;
    }

    public long getProjectTaskId() {
        return projectTaskId;
    }

    public void setProjectTaskId(long projectTaskId) {
        this.projectTaskId = projectTaskId;
        getTask()._id = projectTaskId;
    }

    public ProjectEntity getProject() {
        return getTask().getProject();
    }
}
