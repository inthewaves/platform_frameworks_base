package grapheneos.securepaste;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import grapheneos.securepaste.helper.SecurePasteActivity;
import grapheneos.securepaste.helper.SecurePasteCommandProvider;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class SecurePasteTestBase {
    static final String TEST_PKG = "grapheneos.securepaste";
    static final String WRITER = "grapheneos.securepaste.writer";
    static final String READER = "grapheneos.securepaste.reader";
    static final String EDIT_TEXT = "grapheneos.securepaste.edittext";
    static final String JETPACK_COMPOSE = "grapheneos.securepaste.jetpackcompose";
    static final String COMPOSE_EMULATION = "grapheneos.securepaste.composeemulation";
    static final String IME = "grapheneos.securepaste.ime";
    static final String CUSTOM_TOOLBAR = "grapheneos.securepaste.customtoolbar";
    static final String CACHE_CLIENT = "grapheneos.securepaste.cacheclient";
    static final String SHARED_A = "grapheneos.securepaste.shareduid.a";
    static final String SHARED_B = "grapheneos.securepaste.shareduid.b";
    static final String SYSTEM = "grapheneos.securepaste.system";

    static final String TEXT_A = "secure-paste-text-A";
    static final String TEXT_B = "secure-paste-text-B";
    static final String URI_TEXT = "secure-paste-uri-text";

    private static final String ACTIVITY =
            "grapheneos.securepaste.helper.SecurePasteActivity";
    private static final String JETPACK_COMPOSE_ACTIVITY =
            "grapheneos.securepaste.jetpackcompose.SecurePasteJetpackComposeActivity";
    private static final String ACCESSIBILITY_SERVICE =
            TEST_PKG + "/" + TEST_PKG + ".SecurePasteAccessibilityService";
    private static final String IME_SERVICE = IME + "/.SecurePasteImeService";
    private static final long UI_OBJECT_TIMEOUT_MILLIS = 5_000;
    // Matches the UiAutomator pattern in:
    // frameworks/base/core/tests/coretests/src/android/widget/FloatingToolbarUtils.java
    private static final String TOOLBAR_CONTAINER_RES = "floating_popup_container";

    protected Instrumentation mInstrumentation;
    protected Context mContext;
    protected UiDevice mDevice;
    protected int mUserId;

    private final Set<String> mTouchedPackages = new HashSet<>();
    private String mOldGlobalClipboardDefault;
    private String mOldEnabledAccessibilityServices;
    private String mOldAccessibilityEnabled;
    private String mOldEnabledInputMethods;
    private String mOldDefaultInputMethod;

    @Before
    public void setUpSecurePasteBase() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mDevice = UiDevice.getInstance(mInstrumentation);
        mUserId = mContext.getUserId();
        Configurator.getInstance().setUiAutomationFlags(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        mDevice.wakeUp();
        shell("wm dismiss-keyguard");
        call(WRITER, SecurePasteCommandProvider.METHOD_CLEAR_CLIP);
        resetAllKnownPackageState();
    }

    @After
    public void tearDownSecurePasteBase() throws Exception {
        call(WRITER, SecurePasteCommandProvider.METHOD_CLEAR_CLIP);
        for (String pkg : mTouchedPackages) {
            resetPackageClipboardPolicy(pkg);
        }
        resetAllKnownPackageState();
        restoreGlobalClipboardDefault();
        restoreAccessibilityState();
        restoreImeState();
        shell("am force-stop --user " + mUserId + " " + WRITER);
        shell("am force-stop --user " + mUserId + " " + READER);
        shell("am force-stop --user " + mUserId + " " + EDIT_TEXT);
        shell("am force-stop --user " + mUserId + " " + JETPACK_COMPOSE);
        shell("am force-stop --user " + mUserId + " " + COMPOSE_EMULATION);
        shell("am force-stop --user " + mUserId + " " + CUSTOM_TOOLBAR);
        shell("am force-stop --user " + mUserId + " " + CACHE_CLIENT);
        shell("am force-stop --user " + mUserId + " " + SHARED_A);
        shell("am force-stop --user " + mUserId + " " + SHARED_B);
        shell("am force-stop --user " + mUserId + " " + SYSTEM);
        mDevice.pressHome();
    }

    protected void writerSetsText(String text) {
        packageSetsText(WRITER, text);
    }

    protected void packageSetsText(String pkg, String text) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_TEXT, text);
        assertThat(call(pkg, SecurePasteCommandProvider.METHOD_SET_CLIP_TEXT, extras)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    protected void writerSetsHtml(String text, String html) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_TEXT, text);
        extras.putString(SecurePasteCommandProvider.EXTRA_HTML, html);
        assertThat(call(WRITER, SecurePasteCommandProvider.METHOD_SET_CLIP_HTML, extras)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    protected void writerSetsUriClip(String text) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_TEXT, text);
        assertThat(call(WRITER, SecurePasteCommandProvider.METHOD_SET_CLIP_URI, extras)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    protected void writerSetsNonTextUriClip() {
        assertThat(call(WRITER, SecurePasteCommandProvider.METHOD_SET_CLIP_NONTEXT_URI)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    protected Bundle readClipboard(String pkg) {
        return call(pkg, SecurePasteCommandProvider.METHOD_READ_CLIP);
    }

    protected String readClipboardText(String pkg) {
        return readClipboard(pkg).getString(SecurePasteCommandProvider.RESULT_TEXT);
    }

    protected void assertDirectReadDenied(String pkg) {
        final Bundle result = readClipboard(pkg);
        assertThat(result.getString(SecurePasteCommandProvider.RESULT_TEXT)).isNull();
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_OK)).isFalse();
    }

    protected void assertDirectReadAllowed(String pkg, String expected) {
        final Bundle result = readClipboard(pkg);
        assertThat(result.getString(SecurePasteCommandProvider.RESULT_EXCEPTION)).isNull();
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
        assertThat(result.getString(SecurePasteCommandProvider.RESULT_TEXT)).contains(expected);
    }

    protected void blockPackageClipboardRead(String pkg) {
        mTouchedPackages.add(pkg);
        shell("cmd package edit-gos-package-state " + pkg + " " + mUserId
                + " add-flag ALLOW_CLIPBOARD_READ_NON_DEFAULT"
                + " clear-flag ALLOW_CLIPBOARD_READ");
    }

    protected void allowPackageClipboardRead(String pkg) {
        mTouchedPackages.add(pkg);
        shell("cmd package edit-gos-package-state " + pkg + " " + mUserId
                + " add-flag ALLOW_CLIPBOARD_READ_NON_DEFAULT"
                + " add-flag ALLOW_CLIPBOARD_READ");
    }

    protected void resetPackageClipboardPolicy(String pkg) {
        shell("cmd package edit-gos-package-state " + pkg + " " + mUserId
                + " clear-flag ALLOW_CLIPBOARD_READ_NON_DEFAULT"
                + " clear-flag ALLOW_CLIPBOARD_READ");
    }

    protected void setGlobalClipboardDefault(boolean allowed) {
        if (mOldGlobalClipboardDefault == null) {
            mOldGlobalClipboardDefault = shell("settings get global "
                    + Settings.Global.ALLOW_CLIPBOARD_READ_BY_DEFAULT).trim();
        }
        shell("settings put global " + Settings.Global.ALLOW_CLIPBOARD_READ_BY_DEFAULT + " "
                + (allowed ? "1" : "0"));
    }

    protected void assertSystemModuleInstalled(String pkg, String moduleName) {
        final String path = shell("pm path --user " + mUserId + " " + pkg).trim();
        assertThat(path).startsWith("package:/system/");
    }

    protected void launchHelperActivity(String pkg) {
        shell("am start -W --user " + mUserId + " -n " + pkg + "/" + activityName(pkg));
        waitForProviderResult(pkg, SecurePasteCommandProvider.METHOD_ACTIVITY_READY);
        call(pkg, SecurePasteCommandProvider.METHOD_REQUEST_FOCUS);
        mDevice.waitForIdle();
    }

    protected void focusEditor() throws UiObjectNotFoundException {
        final UiObject editor = mDevice.findObject(
                new UiSelector().description(SecurePasteActivity.EDITOR_DESCRIPTION));
        assertThat(editor.exists()).isTrue();
        editor.click();
        mDevice.waitForIdle();
    }

    protected void longClickEditor() throws UiObjectNotFoundException {
        final UiObject editor = mDevice.findObject(
                new UiSelector().description(SecurePasteActivity.EDITOR_DESCRIPTION));
        assertThat(editor.exists()).isTrue();
        editor.longClick();
        mDevice.waitForIdle();
    }

    protected void systemToolbarPasteInto(String pkg) throws Exception {
        showSystemToolbarForEditor(pkg);
        clickFloatingToolbarItemOrFail(getPasteLabel());
    }

    protected void showSystemToolbarForEditor(String pkg) throws Exception {
        launchHelperActivity(pkg);
        showSystemToolbarForFocusedEditor(pkg);
    }

    protected void showSystemToolbarForFocusedEditor(String pkg) throws Exception {
        focusEditor();
        if (JETPACK_COMPOSE.equals(pkg)) {
            accessibilityLongClickEditor();
        } else {
            longClickEditor();
        }
    }

    protected boolean tryPasteAsPlainTextInto(String pkg) throws Exception {
        launchHelperActivity(pkg);
        focusEditor();
        longClickEditor();
        return clickFloatingToolbarItem(getPasteAsPlainTextLabel());
    }

    protected void clickByDescription(String description) throws UiObjectNotFoundException {
        final UiObject object = waitForObjectByDescriptionOrText(description);
        assertThat(object.exists()).isTrue();
        object.click();
        mDevice.waitForIdle();
    }

    protected boolean clickByText(String text) throws UiObjectNotFoundException {
        final UiObject object = waitForObjectByTextOrDescription(text);
        if (!object.exists()) {
            return false;
        }
        object.click();
        mDevice.waitForIdle();
        return true;
    }

    protected void dragByDescription(String sourceDescription, String targetDescription)
            throws UiObjectNotFoundException {
        final UiObject source = waitForObjectByDescriptionOrText(sourceDescription);
        final UiObject target = waitForObjectByDescriptionOrText(targetDescription);
        assertThat(source.exists()).isTrue();
        assertThat(target.exists()).isTrue();
        // startDragAndDrop requires an active pointer down; CTS drives it with injected input too:
        // cts/tests/framework/base/windowmanager/src/android/server/wm/draganddrop/DragDropTest.java
        assertThat(source.dragTo(target, 80)).isTrue();
        mDevice.waitForIdle();
    }

    protected boolean clickFloatingToolbarItem(String text) {
        final UiObject2 item = findFloatingToolbarItem(text);
        if (item == null) {
            return false;
        }
        item.click();
        mDevice.waitForIdle();
        return true;
    }

    protected boolean hasFloatingToolbarItem(String text) {
        return findFloatingToolbarItem(text) != null;
    }

    protected void assertFloatingToolbarShown() {
        assertWithMessage("visible floating toolbar items")
                .that(floatingToolbarItemTexts()).isNotEmpty();
    }

    protected void assertFloatingToolbarItemPresent(String text) {
        if (hasFloatingToolbarItem(text)) {
            return;
        }
        fail("Expected floating toolbar item \"" + text + "\" but visible items were "
                + floatingToolbarItemTexts());
    }

    protected void assertFloatingToolbarItemAbsent(String text) {
        if (!hasFloatingToolbarItem(text)) {
            return;
        }
        fail("Unexpected floating toolbar item \"" + text + "\" in visible items "
                + floatingToolbarItemTexts());
    }

    protected void clickFloatingToolbarItemOrFail(String text) {
        if (clickFloatingToolbarItem(text)) {
            return;
        }
        fail("Expected to click floating toolbar item \"" + text + "\" but visible items were "
                + floatingToolbarItemTexts());
    }

    private void accessibilityLongClickEditor() {
        // Compose's own text-field tests trigger SemanticsActions.OnLongClick, and
        // AndroidComposeViewAccessibilityDelegateCompat maps framework ACTION_LONG_CLICK to that
        // semantics action:
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/textfield/TextFieldTest.kt;l=838
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt;l=1492
        final long deadline = System.currentTimeMillis() + UI_OBJECT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            final AccessibilityNodeInfo root =
                    mInstrumentation.getUiAutomation().getRootInActiveWindow();
            final AccessibilityNodeInfo editor = findAccessibilityNodeByDescription(
                    root, SecurePasteActivity.EDITOR_DESCRIPTION);
            if (editor != null && performAccessibilityActionOnNodeSubtreeOrParent(
                    editor, AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                mDevice.waitForIdle();
                return;
            }
            sleep(100);
        }
        fail("Unable to perform accessibility long-click on editor");
    }

    private boolean performAccessibilityActionOnNodeSubtreeOrParent(
            AccessibilityNodeInfo node, int action) {
        if (performAccessibilityActionInSubtree(node, action)) {
            return true;
        }
        AccessibilityNodeInfo current = node.getParent();
        while (current != null) {
            if (current.performAction(action)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean performAccessibilityActionInSubtree(AccessibilityNodeInfo node, int action) {
        if (node == null) {
            return false;
        }
        if (node.performAction(action)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (performAccessibilityActionInSubtree(node.getChild(i), action)) {
                return true;
            }
        }
        return false;
    }

    private AccessibilityNodeInfo findAccessibilityNodeByDescription(
            AccessibilityNodeInfo node, String description) {
        if (node == null) {
            return null;
        }
        final CharSequence nodeDescription = node.getContentDescription();
        if (nodeDescription != null && nodeDescription.toString().contains(description)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final AccessibilityNodeInfo match =
                    findAccessibilityNodeByDescription(node.getChild(i), description);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private UiObject2 findFloatingToolbarItem(String text) {
        final UiObject2 toolbar = mDevice.wait(Until.findObject(
                floatingToolbarSelector().hasDescendant(By.text(text))),
                UI_OBJECT_TIMEOUT_MILLIS);
        return toolbar == null ? null : toolbar.findObject(By.text(text));
    }

    private List<String> floatingToolbarItemTexts() {
        final UiObject2 toolbar = mDevice.wait(Until.findObject(floatingToolbarSelector()),
                UI_OBJECT_TIMEOUT_MILLIS);
        final List<String> texts = new ArrayList<>();
        collectText(toolbar, texts);
        return texts;
    }

    private BySelector floatingToolbarSelector() {
        return By.res("android", TOOLBAR_CONTAINER_RES);
    }

    private void collectText(UiObject2 object, List<String> texts) {
        if (object == null) {
            return;
        }
        addTextIfNotEmpty(texts, object.getText());
        addTextIfNotEmpty(texts, object.getContentDescription());
        for (UiObject2 child : object.getChildren()) {
            collectText(child, texts);
        }
    }

    private void addTextIfNotEmpty(List<String> texts, String text) {
        if (text != null && !text.isEmpty() && !texts.contains(text)) {
            texts.add(text);
        }
    }

    private UiObject waitForObjectByDescriptionOrText(String text) {
        final long deadline = System.currentTimeMillis() + UI_OBJECT_TIMEOUT_MILLIS;
        UiObject object = findObjectByDescriptionOrText(text);
        while (!object.exists() && System.currentTimeMillis() < deadline) {
            sleep(100);
            object = findObjectByDescriptionOrText(text);
        }
        return object;
    }

    private UiObject waitForObjectByTextOrDescription(String text) {
        final long deadline = System.currentTimeMillis() + UI_OBJECT_TIMEOUT_MILLIS;
        UiObject object = findObjectByTextOrDescription(text);
        while (!object.exists() && System.currentTimeMillis() < deadline) {
            sleep(100);
            object = findObjectByTextOrDescription(text);
        }
        return object;
    }

    private UiObject findObjectByDescriptionOrText(String text) {
        UiObject object = mDevice.findObject(new UiSelector().description(text));
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().descriptionContains(text));
        }
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().text(text));
        }
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().textContains(text));
        }
        return object;
    }

    private UiObject findObjectByTextOrDescription(String text) {
        UiObject object = mDevice.findObject(new UiSelector().text(text));
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().textContains(text));
        }
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().description(text));
        }
        if (!object.exists()) {
            object = mDevice.findObject(new UiSelector().descriptionContains(text));
        }
        return object;
    }

    protected void waitForEditorText(String pkg, String expected) {
        final long deadline = System.currentTimeMillis() + 10_000;
        String text = null;
        while (System.currentTimeMillis() < deadline) {
            text = call(pkg, SecurePasteCommandProvider.METHOD_GET_EDITOR_TEXT)
                    .getString(SecurePasteCommandProvider.RESULT_TEXT);
            if (text != null && text.contains(expected)) {
                return;
            }
            sleep(100);
        }
        assertThat(text).contains(expected);
    }

    protected String getEditorText(String pkg) {
        return call(pkg, SecurePasteCommandProvider.METHOD_GET_EDITOR_TEXT)
                .getString(SecurePasteCommandProvider.RESULT_TEXT);
    }

    protected void clearEditor(String pkg) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_TEXT, "");
        call(pkg, SecurePasteCommandProvider.METHOD_SET_EDITOR_TEXT, extras);
    }

    protected void sendCtrlV() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
        mDevice.waitForIdle();
    }

    protected void sendPasteKey() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_PASTE);
        mDevice.waitForIdle();
    }

    protected void sendShiftInsert() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_INSERT, KeyEvent.META_SHIFT_ON);
        mDevice.waitForIdle();
    }

    protected void enableAccessibilityService() {
        backupAccessibilityState();
        putSecureSetting("enabled_accessibility_services", ACCESSIBILITY_SERVICE);
        putSecureSetting("accessibility_enabled", "1");
        waitForProviderResult(TEST_PKG, SecurePasteCommandProvider.METHOD_ACCESSIBILITY_CONNECTED);
    }

    protected void enableSecurePasteIme() {
        backupImeState();
        shell("ime enable --user " + mUserId + " " + IME_SERVICE);
        shell("ime set --user " + mUserId + " " + IME_SERVICE);
        if (!waitForDefaultIme(IME_SERVICE, 5_000)) {
            putSecureSetting("default_input_method", IME_SERVICE);
            shell("ime set --user " + mUserId + " " + IME_SERVICE);
        }
        assertThat(waitForDefaultIme(IME_SERVICE, 10_000)).isTrue();
        launchHelperActivity(READER);
        waitForProviderResult(IME, SecurePasteCommandProvider.METHOD_IME_READY);
    }

    protected boolean systemSelectionToolbarFlagEnabled() {
        try {
            final Class<?> flags = Class.forName("android.permission.flags.Flags");
            return (Boolean) flags.getDeclaredMethod("systemSelectionToolbarEnabled").invoke(null);
        } catch (Throwable ignored) {
            return true;
        }
    }

    protected Bundle call(String pkg, String method) {
        return call(pkg, method, null);
    }

    protected Bundle call(String pkg, String method, Bundle extras) {
        final Bundle result = mContext.getContentResolver().call(
                Uri.parse("content://" + pkg + ".provider"), method, null, extras);
        if (result == null) {
            fail("No provider result for " + pkg + " method " + method);
        }
        return result;
    }

    protected String shell(String command) {
        try {
            return mDevice.executeShellCommand(command);
        } catch (Exception e) {
            throw new AssertionError("Shell command failed: " + command, e);
        }
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected String getPasteLabel() {
        return mContext.getString(android.R.string.paste);
    }

    protected String getPasteAsPlainTextLabel() {
        return mContext.getString(android.R.string.paste_as_plain_text);
    }

    private void waitForProviderResult(String pkg, String method) {
        final long deadline = System.currentTimeMillis() + 10_000;
        Bundle result = null;
        while (System.currentTimeMillis() < deadline) {
            result = call(pkg, method);
            if (result.getBoolean(SecurePasteCommandProvider.RESULT_OK)) {
                return;
            }
            sleep(100);
        }
        assertThat(result).isNotNull();
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    private boolean waitForDefaultIme(String ime, long timeoutMillis) {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            final String current = getSecureSetting("default_input_method").trim();
            if (current.equals(ime) || current.equals(IME + "/" + IME + ".SecurePasteImeService")) {
                return true;
            }
            sleep(100);
        }
        return false;
    }

    private void backupAccessibilityState() {
        if (mOldEnabledAccessibilityServices == null) {
            mOldEnabledAccessibilityServices = getSecureSetting("enabled_accessibility_services");
            mOldAccessibilityEnabled = getSecureSetting("accessibility_enabled");
        }
    }

    private void backupImeState() {
        if (mOldEnabledInputMethods == null) {
            mOldEnabledInputMethods = getSecureSetting("enabled_input_methods");
            mOldDefaultInputMethod = getSecureSetting("default_input_method");
        }
    }

    private void restoreAccessibilityState() {
        if (mOldEnabledAccessibilityServices != null) {
            restoreSecureSetting("enabled_accessibility_services", mOldEnabledAccessibilityServices);
            restoreSecureSetting("accessibility_enabled", mOldAccessibilityEnabled);
        }
    }

    private void restoreImeState() {
        if (mOldEnabledInputMethods != null) {
            restoreSecureSetting("enabled_input_methods", mOldEnabledInputMethods);
            restoreSecureSetting("default_input_method", mOldDefaultInputMethod);
        }
    }

    private void restoreGlobalClipboardDefault() {
        if (mOldGlobalClipboardDefault == null) {
            return;
        }
        if ("null".equals(mOldGlobalClipboardDefault)) {
            shell("settings delete global " + Settings.Global.ALLOW_CLIPBOARD_READ_BY_DEFAULT);
        } else {
            shell("settings put global " + Settings.Global.ALLOW_CLIPBOARD_READ_BY_DEFAULT + " "
                    + mOldGlobalClipboardDefault);
        }
    }

    private String getSecureSetting(String key) {
        return shell("settings get --user " + mUserId + " secure " + key).trim();
    }

    private void putSecureSetting(String key, String value) {
        shell("settings put --user " + mUserId + " secure " + key + " " + value);
    }

    private void restoreSecureSetting(String key, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            shell("settings delete --user " + mUserId + " secure " + key);
        } else {
            shell("settings put --user " + mUserId + " secure " + key + " " + value);
        }
    }

    private void resetAllKnownPackageState() {
        resetPackageClipboardPolicy(WRITER);
        resetPackageClipboardPolicy(READER);
        resetPackageClipboardPolicy(EDIT_TEXT);
        resetPackageClipboardPolicy(JETPACK_COMPOSE);
        resetPackageClipboardPolicy(COMPOSE_EMULATION);
        resetPackageClipboardPolicy(IME);
        resetPackageClipboardPolicy(CUSTOM_TOOLBAR);
        resetPackageClipboardPolicy(CACHE_CLIENT);
        resetPackageClipboardPolicy(SHARED_A);
        resetPackageClipboardPolicy(SHARED_B);
        resetPackageClipboardPolicy(SYSTEM);
    }

    private String activityName(String pkg) {
        return JETPACK_COMPOSE.equals(pkg) ? JETPACK_COMPOSE_ACTIVITY : ACTIVITY;
    }
}
