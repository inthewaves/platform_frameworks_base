package grapheneos.securepaste;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import grapheneos.securepaste.helper.SecurePasteActivity;
import grapheneos.securepaste.helper.SecurePasteCommandProvider;

import org.junit.Test;

public class SecurePasteComposeEmulationTest extends SecurePasteTestBase {
    @Test
    public void blockedForeignClip_aospCompose110Alpha01GetClipEntryGateOmitsPaste()
            throws Exception {
        assertComposeToolbarRequestOmitsPaste(
                SecurePasteActivity.COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP);
    }

    @Test
    public void blockedForeignClip_newerComposeMetadataGateShowsPasteAndGrants()
            throws Exception {
        assertComposeToolbarRequestShowsPasteAndGrants(
                SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);
    }

    @Test
    public void blockedForeignClip_newerComposeMetadataAvailabilityDoesNotGrantBeforeClick()
            throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);

        focusEditor();
        assertThat(showComposeEmulatedToolbar()).isTrue();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isTrue();

        // Compose's upstream regression coverage verifies that evaluating Paste availability does
        // not read clip data, while the Android implementation refreshes only metadata. Secure
        // Paste should therefore keep full clipboard reads denied until Paste itself is invoked.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=356
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.android.kt;l=130
        assertDirectReadDenied(COMPOSE_EMULATION);
        assertThat(getEditorText(COMPOSE_EMULATION)).doesNotContain(TEXT_A);
    }

    @Test
    public void blockedForeignClip_newerComposePasteItemUsesFrameworkId() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);

        focusEditor();
        assertThat(showComposeEmulatedToolbar()).isTrue();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isTrue();

        // Current Compose maps TextContextMenuKeys.PasteKey to android.R.id.paste, and its own
        // toolbar tests invoke the selected MenuItem by itemId. The secure paste grant path relies
        // on the framework paste id, not a title-only custom menu item.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/contextmenu/internal/AndroidTextContextMenuToolbarProvider.android.kt;l=292
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=691
        assertThat(composeEmulatedPasteUsesFrameworkId()).isTrue();
    }

    @Test
    public void emptyClipboard_newerComposeMetadataGateOmitsPaste() throws Exception {
        assertThat(call(WRITER, SecurePasteCommandProvider.METHOD_CLEAR_CLIP)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);

        focusEditor();

        // Compose does not show Paste when the clipboard is empty, and its regression coverage
        // verifies that it does not ask for clip description metadata after learning there is no
        // clip.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=382
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=369
        assertThat(showComposeEmulatedToolbar()).isFalse();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isFalse();
    }

    @Test
    public void blockedNonTextClip_newerComposeMetadataGateOmitsPasteWithoutReceiveContent()
            throws Exception {
        writerSetsNonTextUriClip();
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);

        focusEditor();

        // Compose only shows Paste for non-text clips when receiveContent is configured.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=398
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.kt;l=1555
        assertThat(showComposeEmulatedToolbar()).isFalse();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isFalse();
        assertDirectReadDenied(COMPOSE_EMULATION);
    }

    @Test
    public void blockedNonTextClip_newerComposeReceiveContentGateShowsPasteWithoutReadGrant()
            throws Exception {
        writerSetsNonTextUriClip();
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(
                SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA_RECEIVE_CONTENT);

        focusEditor();

        // With receiveContent configured, Compose treats metadata hasClip as enough to offer Paste
        // for non-text content. Showing that item still must not grant broad clipboard reads before
        // a paste action is selected.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=414
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.kt;l=1555
        assertThat(showComposeEmulatedToolbar()).isTrue();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isTrue();
        assertDirectReadDenied(COMPOSE_EMULATION);
    }

    @Test
    public void blockedForeignClip_newerComposePlainLongClickDoesNotSubstituteForToolbarRequester()
            throws Exception {
        assertPlainLongClickOmitsPaste(SecurePasteActivity.COMPOSE_EMULATION_NEWER_METADATA);
    }

    private void assertComposeToolbarRequestOmitsPaste(String mode) throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(mode);

        focusEditor();
        assertThat(showComposeEmulatedToolbar()).isFalse();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isFalse();
        assertThat(getEditorText(COMPOSE_EMULATION)).doesNotContain(TEXT_A);
        assertDirectReadDenied(COMPOSE_EMULATION);
    }

    private void assertComposeToolbarRequestShowsPasteAndGrants(String mode) throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(mode);

        focusEditor();
        assertThat(showComposeEmulatedToolbar()).isTrue();
        assertThat(hasFloatingToolbarItem(getPasteLabel())).isTrue();
        assertDirectReadDenied(COMPOSE_EMULATION);
        assertThat(clickFloatingToolbarItem(getPasteLabel())).isTrue();
        waitForEditorText(COMPOSE_EMULATION, TEXT_A);
    }

    private void assertPlainLongClickOmitsPaste(String mode) throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(COMPOSE_EMULATION);
        launchHelperActivity(COMPOSE_EMULATION);
        setComposeEmulationMode(mode);

        focusEditor();
        longClickEditor();

        assertThat(hasFloatingToolbarItem(getPasteLabel())).isFalse();
        assertThat(getEditorText(COMPOSE_EMULATION)).doesNotContain(TEXT_A);
        assertDirectReadDenied(COMPOSE_EMULATION);
    }

    private void setComposeEmulationMode(String mode) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_MODE, mode);
        assertThat(call(COMPOSE_EMULATION,
                SecurePasteCommandProvider.METHOD_SET_COMPOSE_EMULATION_MODE, extras)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
    }

    private boolean showComposeEmulatedToolbar() {
        return call(COMPOSE_EMULATION,
                SecurePasteCommandProvider.METHOD_SHOW_COMPOSE_EMULATED_TOOLBAR)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK);
    }

    private boolean composeEmulatedPasteUsesFrameworkId() {
        return call(COMPOSE_EMULATION,
                SecurePasteCommandProvider.METHOD_COMPOSE_EMULATED_PASTE_USES_FRAMEWORK_ID)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK);
    }
}
