package grapheneos.srtpermtests.packageinstaller

import android.Manifest
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.android.cts.install.lib.Install
import com.android.cts.install.lib.InstallUtils
import com.android.cts.install.lib.TestApp
import com.google.common.truth.Truth
import grapheneos.test.common.SensorsSettingsUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoGrantSensorsSettingsTest : BaseInstallerTest() {

    override fun getTestAppPackageNames(): Array<String> = arrayOf(TestApks.archiveApk.packageName)

    @Test
    fun auto_grant_sensors_on_install() {
        SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, true) {
            runSensorsAutoGrantInstallTest(true)
        }
    }

    @Test
    fun auto_grant_sensors_off_install() {
        SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, false) {
            runSensorsAutoGrantInstallTest(false)
        }
    }

    private fun runSensorsAutoGrantInstallTest(settingValue: Boolean) {
        try {
            mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
            try {
                installApkByInstallerSession(
                    TestApks.archiveApk.packageName,
                    TestApks.archiveApk.apkPath,
                    PackageInstaller.STATUS_SUCCESS,
                    null
                )
            } finally {
                mInstrumentation.uiAutomation.dropShellPermissionIdentity()
            }

            assertEquals(
                "auto grant sensors is $settingValue but granted state of OTHER_SENSORS " +
                        "permission doesn't match after install",
                getExpectedPermissionResult(settingValue),
                mPackageManager.checkPermission(
                    Manifest.permission.OTHER_SENSORS,
                    TestApks.archiveApk.packageName,
                )
            )
        } finally {
            uninstallPackage(TestApks.archiveApk.packageName)
        }
    }

    @Test
    fun auto_grant_sensors_on_for_app_update() {
        try {
            SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, true) {
                mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
                try {
                    installApkByInstallerSession(
                        TestApks.helloWorldV1.packageName, TestApks.helloWorldV1.apkPath,
                        PackageInstaller.STATUS_SUCCESS, null
                    )
                } finally {
                    mInstrumentation.uiAutomation.dropShellPermissionIdentity()
                }

                assertEquals(
                    "auto grant sensors is on but OTHER_SENSORS not granted",
                    getExpectedPermissionResult(true),
                    mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS,
                        TestApks.helloWorldV1.packageName
                    )
                )

                mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
                try {
                    installApkByInstallerSession(
                        TestApks.helloWorldV1.packageName, TestApks.helloWorldV2.apkPath,
                        PackageInstaller.STATUS_SUCCESS, null
                    )
                } finally {
                    mInstrumentation.uiAutomation.dropShellPermissionIdentity()
                }

                assertEquals(
                    "auto grant sensors is on but OTHER_SENSORS not granted after update",
                    getExpectedPermissionResult(true),
                    mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS,
                        TestApks.helloWorldV2.packageName
                    )
                )
            }
        } finally {
            uninstallPackage(TestApks.helloWorldV2.packageName)
        }
    }

    @Test
    fun auto_grant_sensors_off_for_app_update() {
        try {
            SensorsSettingsUtil.withAutoGrantSensorSetting(mInstrumentation, false) {
                mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
                try {
                    installApkByInstallerSession(
                        TestApks.helloWorldV1.packageName, TestApks.helloWorldV1.apkPath,
                        PackageInstaller.STATUS_SUCCESS, null
                    )
                } finally {
                    mInstrumentation.uiAutomation.dropShellPermissionIdentity()
                }

                assertEquals(
                    "auto grant sensors is off but OTHER_SENSORS granted",
                    getExpectedPermissionResult(false),
                    mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS,
                        TestApks.helloWorldV1.packageName
                    )
                )

                mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
                try {
                    installApkByInstallerSession(
                        TestApks.helloWorldV1.packageName, TestApks.helloWorldV2.apkPath,
                        PackageInstaller.STATUS_SUCCESS, null
                    )
                } finally {
                    mInstrumentation.uiAutomation.dropShellPermissionIdentity()
                }

                assertEquals(
                    "auto grant sensors is off but OTHER_SENSORS granted after update",
                    getExpectedPermissionResult(false),
                    mPackageManager.checkPermission(
                        Manifest.permission.OTHER_SENSORS,
                        TestApks.helloWorldV2.packageName
                    )
                )
            }
        } finally {
            uninstallPackage(TestApks.helloWorldV2.packageName)
        }
    }

    @Test
    fun test_stuff() {
        mInstrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            // Assert that the test app was not previously installed
            assertEquals(-1, InstallUtils.getInstalledVersion(TestApp.A))


            // Install version 1 of TestApp.A
            // Install#commit() asserts that the installation succeeds, so if it fails,
            // an AssertionError would be thrown.
            Install.single(TestApp.A1).commit()

            // Even though the install session of TestApp.A1 is guaranteed to be committed by this stage
            // it's still good practice to assert that the installed version of the app is the desired
            // one. This is due to the fact that not all committed sessions are finalized sessions, i.e.
            // staged install session.
            assertEquals(1, InstallUtils.getInstalledVersion(TestApp.A))


            val replaceExistingFlag = 0x00000002
            Install.single(TestApp.A2)
                .addInstallFlags(replaceExistingFlag)
                .commit()

            assertEquals(2, InstallUtils.getInstalledVersion(TestApp.A))
        } finally {
            mInstrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
