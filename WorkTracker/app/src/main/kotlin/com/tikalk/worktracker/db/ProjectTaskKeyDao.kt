package com.tikalk.worktracker.db

import androidx.room.Dao
import androidx.room.Query
import com.tikalk.worktracker.model.ProjectTaskKey

/**
 * DAO for joining Project and Task entities.
 */
@Dao
interface ProjectTaskKeyDao : BaseDao<ProjectTaskKey> {

    /**
     * Select all keys from the table.
     *
     * @return all keys.
     */
    @Query("SELECT * FROM project_task_key")
    fun getAll(): List<ProjectTaskKey>

    /**
     * Select a project's keys.
     */
    @Query("SELECT * FROM project_task_key WHERE project_id = :projectId")
    fun getAllByProject(projectId: Long): List<ProjectTaskKey>
}