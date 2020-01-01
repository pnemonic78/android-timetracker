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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.tikalk.app.findFragmentByClass
import com.tikalk.view.showAnimated
import com.tikalk.worktracker.R
import com.tikalk.worktracker.model.User
import com.tikalk.worktracker.net.InternetActivity
import com.tikalk.worktracker.user.ProfileFragment
import kotlinx.android.synthetic.main.activity_time_list.*
import kotlinx.android.synthetic.main.progress.*
import timber.log.Timber

class TimeListActivity : InternetActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    ProfileFragment.OnProfileListener {

    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the form and list.
        setContentView(R.layout.activity_time_list)

        // Set up navigation - action bar and sidebar.
        /// Let the navigation view check/uncheck the menu items.
        val navController = findNavController()
        nav_view.setupWithNavController(navController)
        nav_view.setNavigationItemSelectedListener(this)

        /// Show the hamburger and back icons
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        drawerToggle = ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(drawerToggle)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        handleIntent(intent)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.profileFragment -> {
                showProfile()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.projectsFragment -> {
                showProjects()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.reportFormFragment -> {
                showReports()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.tasksFragment -> {
                showTasks()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.timeListFragment -> {
                showMainFragment()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.timeSettingsFragment -> {
                showSettings()
                drawerLayout.closeDrawers()
                return true
            }
            R.id.usersFragment -> {
                showUsers()
                drawerLayout.closeDrawers()
                return true
            }
        }
        return false
    }

    override fun showProgress(show: Boolean) {
        progress?.showAnimated(show)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        intent.action = null

        if (action == ACTION_STOP) {
            val mainFragment = findMainFragment()
            mainFragment?.stopTimer()
        }
    }

    override fun onBackPressed() {
        val mainFragment = findMainFragment()
        if (mainFragment != null) {
            if (mainFragment.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onProfileSuccess(fragment: ProfileFragment, user: User) {
        Timber.i("profile success")
        fragment.dismissAllowingStateLoss()
    }

    override fun onProfileFailure(fragment: ProfileFragment, user: User, reason: String) {
        Timber.e("profile failure: $reason")
    }

    private fun findMainFragment(): TimeListFragment? {
        val navFragment = supportFragmentManager.primaryNavigationFragment ?: return null
        return navFragment.childFragmentManager.findFragmentByClass(TimeListFragment::class.java)
    }

    private fun findNavController(): NavController {
        return findNavController(this, R.id.nav_host_fragment)
    }

    private fun showMainFragment() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showMainFragment currentDestination=${destination.label}")
        if (destination.id != R.id.timeListFragment) {
            navController.popBackStack(R.id.timeListFragment, false)
        }
    }

    private fun showSettings() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showSettings currentDestination=${destination.label}")
        if (destination.id != R.id.timeSettingsFragment) {
            navController.navigate(R.id.action_show_settings)
        }
    }

    private fun showProfile() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showProfile currentDestination=${destination.label}")
        if (destination.id != R.id.profileFragment) {
            val args = Bundle()
            navController.navigate(R.id.action_show_profile, args)
        }
    }

    private fun showProjects() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showProjects currentDestination=${destination.label}")
        if (destination.id != R.id.projectsFragment) {
            navController.navigate(R.id.action_show_projects)
        }
    }

    private fun showReports() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showReports currentDestination=${destination.label}")
        if ((destination.id != R.id.reportFormFragment) and (destination.id != R.id.reportFragment)) {
            navController.navigate(R.id.action_show_reportForm)
        }
    }

    private fun showTasks() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showTasks currentDestination=${destination.label}")
        if (destination.id != R.id.tasksFragment) {
            navController.navigate(R.id.action_show_tasks)
        }
    }

    private fun showUsers() {
        val navController = findNavController()
        val destination = navController.currentDestination ?: return
        Timber.i("showUsers currentDestination=${destination.label}")
        if (destination.id != R.id.usersFragment) {
            navController.navigate(R.id.action_show_users)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (findNavController().navigateUp()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    companion object {
        const val ACTION_STOP = TimeListFragment.ACTION_STOP
    }
}