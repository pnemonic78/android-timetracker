package com.tikalk.worktracker.db

import androidx.room.Dao
import androidx.room.Query
import com.tikalk.worktracker.model.Project
import io.reactivex.Flowable
import io.reactivex.Maybe

/**
 * Project entity DAO.
 */
@Dao
interface ProjectDao : BaseDao<Project> {

    /**
     * Select all articles from the articles table.
     *
     * @return all articles.
     */
    @Query("SELECT * FROM project")
    fun queryAll(): Flowable<List<Project>>

    /**
     * Select a project by its id.
     */
    @Query("SELECT * FROM project WHERE id = :projectId")
    fun queryById(projectId: Long): Maybe<Project>

    /**
     * Delete all entities.
     */
    @Query("DELETE FROM project")
    fun deleteAll()
}