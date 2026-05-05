package grapheneos.securepaste;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import grapheneos.securepaste.helper.SecurePasteCommandProvider;

import org.junit.Test;

public class SecurePasteJetpackComposeTest extends SecurePasteTestBase {
    private static final String FIELD_MODE_STATE = "state";
    // Keep false for AOSP's current foundation-android 1.10.0-alpha01 prebuilt at
    // 84c18801ee8982f06562f6c17cf5b4ee81e28c80, where value-based BasicTextField still
    // reads clipboard.getClipEntry() to decide Paste availability. Flip this to true once AOSP
    // prebuilts move to Compose 1.10.0-alpha02 or newer. The 1.10.0-alpha02 bump at
    // 8069c26524a6f229992b40390ed4d5140ad91257 contains the metadata-only availability
    // change from 384366df375c31c09e4692012f8dd049b7d74045.
    // https://cs.android.com/androidx/platform/frameworks/support/+/8069c26524a6f229992b40390ed4d5140ad91257
    // https://cs.android.com/androidx/platform/frameworks/support/+/384366df375c31c09e4692012f8dd049b7d74045
    // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.kt;l=789
    // https://cs.android.com/androidx/platform/frameworks/support/+/8069c26524a6f229992b40390ed4d5140ad91257:libraryversions.toml;l=26
    // https://cs.android.com/androidx/platform/frameworks/support/+/384366df375c31c09e4692012f8dd049b7d74045:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.kt;l=820
    // https://cs.android.com/androidx/platform/frameworks/support/+/384366df375c31c09e4692012f8dd049b7d74045:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.android.kt;l=138
    private static final boolean VALUE_BASIC_TEXT_FIELD_USES_METADATA_PASTE_AVAILABILITY = false;

    @Test
    public void blockedJetpackCompose_directClipboardReadDenied() {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(JETPACK_COMPOSE);
        launchHelperActivity(JETPACK_COMPOSE);

        assertDirectReadDenied(JETPACK_COMPOSE);
    }

    @Test
    public void blockedJetpackCompose_currentAospPrebuiltOmitsPasteForBlockedForeignClip()
            throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(JETPACK_COMPOSE);

        // This app intentionally uses the current AOSP Jetpack Compose prebuilts. AOSP currently
        // prebuilts foundation-android 1.10.0-alpha01 from SHA
        // 84c18801ee8982f06562f6c17cf5b4ee81e28c80, whose value-based BasicTextField path gates
        // Paste by reading clipboard.getClipEntry(), not metadata. For a blocked foreign clip,
        // Secure Paste denies that full read, so current AOSP Compose opens the toolbar but omits
        // Paste. SecurePasteComposeEmulationTest separately covers the newer metadata-based path.
        // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.kt;l=788
        // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.android.kt;l=122
        showSystemToolbarForEditor(JETPACK_COMPOSE);

        final Bundle result = readClipboard(JETPACK_COMPOSE);
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_OK)).isFalse();
        assertThat(result.getString(SecurePasteCommandProvider.RESULT_TEXT)).isNull();
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_HAS_CLIP)).isTrue();
        assertThat(result.getBoolean(SecurePasteCommandProvider.RESULT_HAS_TEXT)).isTrue();
        assertThat(result.getStringArray(SecurePasteCommandProvider.RESULT_MIME_TYPES))
                .asList()
                .contains("text/plain");

        assertFloatingToolbarShown();
        if (VALUE_BASIC_TEXT_FIELD_USES_METADATA_PASTE_AVAILABILITY) {
            assertFloatingToolbarItemPresent(getPasteLabel());
            assertDirectReadDenied(JETPACK_COMPOSE);
            clickFloatingToolbarItemOrFail(getPasteLabel());
            waitForEditorText(JETPACK_COMPOSE, TEXT_A);
            return;
        }
        assertFloatingToolbarItemAbsent(getPasteLabel());
        assertThat(clickFloatingToolbarItem(getPasteLabel())).isFalse();
        assertThat(getEditorText(JETPACK_COMPOSE)).doesNotContain(TEXT_A);
    }

    @Test
    public void blockedJetpackCompose_currentAospPrebuiltOwnedClipDoesNotSeedPasteGrant()
            throws Exception {
        blockPackageClipboardRead(JETPACK_COMPOSE);

        // This records that the previous owned-clip seeding workaround is not a valid model for
        // current AOSP Compose prebuilts: the same getClipEntry() menu gate still decides whether
        // Paste exists before the system toolbar can create a paste grant for a later foreign clip.
        packageSetsText(JETPACK_COMPOSE, "compose-owned-menu-trigger");
        showSystemToolbarForEditor(JETPACK_COMPOSE);
        writerSetsText(TEXT_A);

        assertFloatingToolbarShown();
        if (VALUE_BASIC_TEXT_FIELD_USES_METADATA_PASTE_AVAILABILITY) {
            assertFloatingToolbarItemPresent(getPasteLabel());
            assertDirectReadDenied(JETPACK_COMPOSE);
            assertThat(getEditorText(JETPACK_COMPOSE)).doesNotContain(TEXT_A);
            clickFloatingToolbarItemOrFail(getPasteLabel());
            waitForEditorText(JETPACK_COMPOSE, TEXT_A);
            return;
        }
        assertFloatingToolbarItemAbsent(getPasteLabel());
        assertThat(clickFloatingToolbarItem(getPasteLabel())).isFalse();
        assertThat(getEditorText(JETPACK_COMPOSE)).doesNotContain(TEXT_A);
        assertDirectReadDenied(JETPACK_COMPOSE);
    }

    @Test
    public void blockedJetpackCompose_stateBasedAospPrebuiltMetadataShowsPasteAndGrants()
            throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(JETPACK_COMPOSE);
        launchHelperActivity(JETPACK_COMPOSE);
        setJetpackComposeFieldMode(FIELD_MODE_STATE);

        // The state-based BasicTextField path in the current AOSP Compose prebuilt uses metadata
        // for Paste availability, so a blocked foreign text clip can still show the system Paste
        // item without first granting direct clipboard reads. Upstream state-based toolbar tests
        // drive the menu through touch gestures on the field/cursor handle, so this real prebuilt
        // coverage uses a physical long-click instead of the accessibility long-click helper used
        // for the value-based BasicTextField tests.
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=133
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=390
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=356
        // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.android.kt;l=108
        // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.kt;l=1428
        focusEditor();
        longClickEditor();
        assertFloatingToolbarShown();
        assertFloatingToolbarItemPresent(getPasteLabel());
        assertDirectReadDenied(JETPACK_COMPOSE);

        clickFloatingToolbarItemOrFail(getPasteLabel());
        waitForEditorText(JETPACK_COMPOSE, TEXT_A);
    }

    private void setJetpackComposeFieldMode(String mode) {
        final Bundle extras = new Bundle();
        extras.putString(SecurePasteCommandProvider.EXTRA_MODE, mode);
        assertThat(call(JETPACK_COMPOSE,
                SecurePasteCommandProvider.METHOD_SET_JETPACK_COMPOSE_FIELD_MODE, extras)
                .getBoolean(SecurePasteCommandProvider.RESULT_OK)).isTrue();
        mDevice.waitForIdle();
    }
}
