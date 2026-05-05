package grapheneos.securepaste;

import org.junit.Test;

public class SecurePasteEditTextTest extends SecurePasteTestBase {
    @Test
    public void blockedEditText_directClipboardReadDenied() {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(EDIT_TEXT);
        launchHelperActivity(EDIT_TEXT);

        assertDirectReadDenied(EDIT_TEXT);
    }

    @Test
    public void blockedEditText_systemToolbarPasteSucceeds() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(EDIT_TEXT);

        systemToolbarPasteInto(EDIT_TEXT);

        waitForEditorText(EDIT_TEXT, TEXT_A);
    }

    @Test
    public void blockedEditText_ctrlVPastePolicy() throws Exception {
        writerSetsText(TEXT_A);
        blockPackageClipboardRead(EDIT_TEXT);
        launchHelperActivity(EDIT_TEXT);
        focusEditor();

        sendCtrlV();

        waitForEditorText(EDIT_TEXT, TEXT_A);
    }
}
