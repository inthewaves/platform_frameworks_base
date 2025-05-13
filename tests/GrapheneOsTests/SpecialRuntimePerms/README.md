# SpecialRuntimePermsTestCases

Test cases for GrapheneOS special runtime permissions. Should be treated as part of testing process
for regression testing, but not the entire process

## Running tests

Run an active device with internet access (might be best done with 
`emulator -wipe-data -read-only` for a clean slate) and then run

```bash
atest SpecialRuntimePermsTestCases
```

This command will handle building and pushing the APKs onto the device (see 
[AndroidTest.xml](AndroidTest.xml) for details).

## Overview of tests

- Installing apps with sensors toggles
- Updating apps and ensuring runtime permission states are preserved
- Archiving and unarchiving apps and ensuring runtime permission states are preserved in various 
- cases (based on the tests from https://github.com/GrapheneOS/platform_frameworks_base/pull/163)
- Ensuring the general functionality of special runtime permissions
  - Revoking internet permission should result in the app seeing as the network as unavailable
    instead of throwing errors
  - Revoking sensor permission should result in sensor events not being received, and a notification
    should be posted if an app tries to access sensors when the permission is revoked

Much of the test code and apps are based on existing AOSP CTS tests (`cts/` and
`packages/modules/Permission/tests/cts`). Some CTS test failures were used to build test cases here.

## Known issues

- InternetAndSensorsPermissionTest is based on a CTS test that was marked as flaky 
  (`packages/modules/Permission/tests/cts/permission/src/android/permission/cts/LocationAccessCheckTest.java`).
  Sometimes the tests might fail from a `DeadObjectException` or other misc failures.
