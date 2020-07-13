package org.tasks.data

import com.natpryce.makeiteasy.MakeItEasy.with
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.helper.UUIDHelper
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.tasks.injection.InjectingTestCase
import org.tasks.injection.ProductionModule
import org.tasks.makers.TagDataMaker.newTagData
import org.tasks.makers.TagMaker.TAGDATA
import org.tasks.makers.TagMaker.TASK
import org.tasks.makers.TagMaker.newTag
import org.tasks.makers.TaskMaker.CREATION_TIME
import org.tasks.makers.TaskMaker.ID
import org.tasks.makers.TaskMaker.newTask
import org.tasks.time.DateTime
import javax.inject.Inject

@UninstallModules(ProductionModule::class)
@HiltAndroidTest
class CaldavDaoTests : InjectingTestCase() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var tagDao: TagDao
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var caldavDao: CaldavDao

    @Test
    fun insertNewTaskAtTopOfEmptyList() = runBlocking {
        val task = newTask()
        taskDao.createNew(task)
        val caldavTask = CaldavTask(task.id, "calendar")
        caldavDao.insert(task, caldavTask, true)

        checkOrder(null, task.id)
    }

    @Test
    fun insertNewTaskAboveExistingTask() = runBlocking {
        val created = DateTime(2020, 5, 21, 15, 29, 16, 452)
        val first = newTask(with(CREATION_TIME, created))
        val second = newTask(with(CREATION_TIME, created.plusSeconds(1)))
        taskDao.createNew(first)
        taskDao.createNew(second)
        caldavDao.insert(first, CaldavTask(first.id, "calendar"), true)

        caldavDao.insert(second, CaldavTask(second.id, "calendar"), true)

        checkOrder(null, first.id)
        checkOrder(created.minusSeconds(1), second.id)
    }

    @Test
    fun insertNewTaskBelowExistingTask() = runBlocking {
        val created = DateTime(2020, 5, 21, 15, 29, 16, 452)
        val first = newTask(with(CREATION_TIME, created))
        val second = newTask(with(CREATION_TIME, created.plusSeconds(1)))
        taskDao.createNew(first)
        taskDao.createNew(second)
        caldavDao.insert(first, CaldavTask(first.id, "calendar"), false)

        caldavDao.insert(second, CaldavTask(second.id, "calendar"), false)

        checkOrder(null, first.id)
        checkOrder(null, second.id)
    }

    @Test
    fun insertNewTaskBelowExistingTaskWithSameCreationDate() = runBlocking {
        val created = DateTime(2020, 5, 21, 15, 29, 16, 452)
        val first = newTask(with(CREATION_TIME, created))
        val second = newTask(with(CREATION_TIME, created))
        taskDao.createNew(first)
        taskDao.createNew(second)
        caldavDao.insert(first, CaldavTask(first.id, "calendar"), false)

        caldavDao.insert(second, CaldavTask(second.id, "calendar"), false)

        checkOrder(null, first.id)
        checkOrder(created.plusSeconds(1), second.id)
    }

    @Test
    fun insertNewTaskAtBottomOfEmptyList() = runBlocking {
        val task = newTask()
        taskDao.createNew(task)
        val caldavTask = CaldavTask(task.id, "calendar")
        caldavDao.insert(task, caldavTask, false)

        checkOrder(null, task.id)
    }

    @Test
    fun getCaldavTasksWithTags() = runBlocking {
            val task = newTask(with(ID, 1L))
            taskDao.createNew(task)
            val one = newTagData()
            val two = newTagData()
            tagDataDao.createNew(one)
            tagDataDao.createNew(two)
            tagDao.insert(newTag(with(TASK, task), with(TAGDATA, one)))
            tagDao.insert(newTag(with(TASK, task), with(TAGDATA, two)))
            caldavDao.insert(CaldavTask(task.id, "calendar"))
            assertEquals(listOf(task.id), caldavDao.getTasksWithTags())
        }

    @Test
    fun ignoreNonCaldavTaskWithTags() = runBlocking {
        val task = newTask(with(ID, 1L))
        taskDao.createNew(task)
        val tag = newTagData()
        tagDataDao.createNew(tag)
        tagDao.insert(newTag(with(TASK, task), with(TAGDATA, tag)))
        assertTrue(caldavDao.getTasksWithTags().isEmpty())
    }

    @Test
    fun ignoreCaldavTaskWithoutTags() = runBlocking {
        val task = newTask(with(ID, 1L))
        taskDao.createNew(task)
        tagDataDao.createNew(newTagData())
        caldavDao.insert(CaldavTask(task.id, "calendar"))
        assertTrue(caldavDao.getTasksWithTags().isEmpty())
    }

    @Test
    fun noResultsForEmptyAccounts() = runBlocking {
        val caldavAccount = CaldavAccount()
        caldavAccount.uuid = UUIDHelper.newUUID()
        caldavDao.insert(caldavAccount)
        assertTrue(caldavDao.getCaldavFilters(caldavAccount.uuid!!).isEmpty())
    }

    private suspend fun checkOrder(dateTime: DateTime, task: Long) = checkOrder(dateTime.toAppleEpoch(), task)

    private suspend fun checkOrder(order: Long?, task: Long) {
        val sortOrder = caldavDao.getTask(task)!!.order
        if (order == null) {
            assertNull(sortOrder)
        } else {
            assertEquals(order, sortOrder)
        }
    }
}