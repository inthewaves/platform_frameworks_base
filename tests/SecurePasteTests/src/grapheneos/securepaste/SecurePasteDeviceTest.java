package grapheneos.securepaste;

import static com.google.common.truth.Truth.assertThat;

import grapheneos.securepaste.helper.SecurePasteCommandProvider;

import org.junit.Test;

public class SecurePasteDeviceTest extends SecurePasteTestBase {
    @Test
    public void blockedPackage_cannotReadForeignTextClip() {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(READER);
        launchHelperActivity(READER);

        assertDirectReadDenied(READER);
    }

    @Test
    public void allowedPackage_canReadForeignTextClip() {
        writerSetsText(TEXT_A);
        allowPackageClipboardRead(READER);
        launchHelperActivity(READER);

        assertDirectReadAllowed(READER, TEXT_A);
    }

    @Test
    public void defaultBlockedByGlobalPolicy_cannotReadForeignClip() {
        writerSetsText(TEXT_A);
        setGlobalClipboardDefault(false);
        resetPackageClipboardPolicy(READER);
        launchHelperActivity(READER);

        assertDirectReadDenied(READER);
    }

    @Test
    public void clipOwner_canReadOwnClip() {
        // Apps can read back clips they own. Password managers commonly copy a secret, then
        // read the current clipboard later so they only clear it if the user has not replaced it
        // although note that these features don't work if the app isn't focused, e.g. this gets logged:
        // E ClipboardService: Denying clipboard access to keepass2android.keepass2android, application is not in focus nor is it a system service for user 0
        // 
        // https://github.com/Kunzisoft/KeePassDX/blob/581df551ea3ad70562c6d72a3fb49780d03da6a8/app/src/main/java/com/kunzisoft/keepass/timeout/ClipboardHelper.kt#L72-L127
        // https://github.com/PhilippC/keepass2android/blob/1db0e0ac6867a4b52243417d944c7fd112afcf0f/src/keepass2android-app/services/CopyToClipboardService.cs#L650-L688
        // https://github.com/bpellin/keepassdroid/blob/810f5e8516eb7ec9d648017e47de029e0c91112f/app/src/main/java/com/keepassdroid/EntryActivity.java#L444-L483
        blockPackageClipboardRead(WRITER);
        launchHelperActivity(WRITER);

        final android.os.Bundle result = call(WRITER,
                SecurePasteCommandProvider.METHOD_READ_OWN_CLIP);
        assertThat(result.getString(SecurePasteCommandProvider.RESULT_TEXT))
                .contains("secure-paste-own-text");
    }

    @Test
    public void blockedPackage_listenerDoesNotRevealForeignClipChanges() {
        blockPackageClipboardRead(READER);
        launchHelperActivity(READER);
        call(READER, SecurePasteCommandProvider.METHOD_REGISTER_LISTENER);
        call(READER, SecurePasteCommandProvider.METHOD_RESET_LISTENER);

        writerSetsText(TEXT_A);
        writerSetsText(TEXT_B);
        sleep(500);

        assertThat(call(READER, SecurePasteCommandProvider.METHOD_GET_LISTENER_COUNT)
                .getInt(SecurePasteCommandProvider.RESULT_COUNT)).isEqualTo(0);
    }

    @Test
    public void blockedPackage_systemToolbarPasteIntoEditTextSucceeds() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(READER);

        systemToolbarPasteInto(READER);

        waitForEditorText(READER, TEXT_A);
    }

    @Test
    public void blockedPackage_pasteAsPlainTextSucceeds() throws Exception {
        writerSetsHtml(TEXT_A, "<b>" + TEXT_A + "</b>");
        blockPackageClipboardRead(READER);

        if (!tryPasteAsPlainTextInto(READER)) {
            systemToolbarPasteInto(READER);
        }

        waitForEditorText(READER, TEXT_A);
    }

    @Test
    public void blockedPackage_toolbarGrantExpires() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(READER);

        systemToolbarPasteInto(READER);
        waitForEditorText(READER, TEXT_A);
        sleep(1_500);

        assertDirectReadDenied(READER);
    }

    @Test
    public void blockedPackage_noToolbarAction_noTemporaryGrant() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(READER);
        launchHelperActivity(READER);
        focusEditor();
        longClickEditor();

        assertDirectReadDenied(READER);
    }

    @Test
    public void blockedPackage_keyboardPastePolicy() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(READER);
        launchHelperActivity(READER);
        focusEditor();

        sendCtrlV();

        waitForEditorText(READER, TEXT_A);
    }

    @Test
    public void blockedPackage_toolbarPasteUriClipGrantsOnlyPasteTarget() throws Exception {
        writerSetsUriClip(URI_TEXT);
        blockPackageClipboardRead(READER);

        systemToolbarPasteInto(READER);

        waitForEditorText(READER, URI_TEXT);
    }

    @Test
    public void blockedPackage_uriGrantDoesNotBypassClipboardAfterTimeout() throws Exception {
        writerSetsUriClip(URI_TEXT);
        blockPackageClipboardRead(READER);

        systemToolbarPasteInto(READER);
        waitForEditorText(READER, URI_TEXT);
        sleep(1_500);

        assertDirectReadDenied(READER);
    }
}
