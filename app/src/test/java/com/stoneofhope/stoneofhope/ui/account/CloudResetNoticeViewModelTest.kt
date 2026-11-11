package com.stoneofhope.stoneofhope.ui.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.sync.CloudAccountFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** iOS `AppRootView` cloud-reset alert: flag set → shown → OK clears it (and it stays gone). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CloudResetNoticeViewModelTest {
    private val flags = CloudAccountFlags(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("reset-notice-test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() },
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun hiddenUntilTheFlagIsSet_thenShownAndClearedOnDismiss() {
        val vm = CloudResetNoticeViewModel(flags)
        assertFalse(vm.visible.value)

        flags.showCloudResetNotice = true // a sync run detected the cloud reset while the app is open
        assertTrue(vm.visible.value)

        vm.dismiss()
        assertFalse(vm.visible.value)
        assertFalse(flags.showCloudResetNotice)
    }

    @Test
    fun flagSetBeforeLaunchShowsRightAway_andSurvivesUntilDismissed() {
        flags.showCloudResetNotice = true
        val vm = CloudResetNoticeViewModel(flags)
        assertTrue(vm.visible.value)
        assertTrue(CloudResetNoticeViewModel(flags).visible.value) // not consumed by merely showing
        vm.dismiss()
        assertFalse(CloudResetNoticeViewModel(flags).visible.value)
    }
}
