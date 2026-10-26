package team.shiro.railwidget

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import team.shiro.railwidget.diagnostics.CrashDiagnostics

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CrashDiagnosticsTest {
    @Test fun savesCauseAndDelegatesWithoutDeletingTripData() {
        val context = RuntimeEnvironment.getApplication()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
            CrashDiagnostics.install(context)
            val failure = IllegalStateException("startup failed", IllegalArgumentException("invalid date"))
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), failure)
            val report = CrashDiagnostics.read(context)!!
            assertTrue(report.contains("startup failed"))
            assertTrue(report.contains("Caused by:"))
            assertTrue(report.contains("invalid date"))
            assertTrue(delegated)
            CrashDiagnostics.clear(context)
            assertNull(CrashDiagnostics.read(context))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            CrashDiagnostics.clear(context)
        }
    }
}
