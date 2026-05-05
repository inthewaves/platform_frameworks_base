package grapheneos.securepaste.helper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SecurePasteActivity extends Activity {
    public static final String EDITOR_DESCRIPTION = "Secure Paste Editor";
    public static final String DRAG_SOURCE_DESCRIPTION = "Secure Paste Drag Source";
    public static final String DRAG_TARGET_DESCRIPTION = "Secure Paste Drag Target";
    public static final String DRAG_LABEL = "secure-paste-drag-label";
    public static final String DRAG_TEXT = "secure-paste-drag-text";
    public static final String DRAG_EXTRA_KEY = "secure-paste-drag-extra-key";
    public static final String DRAG_EXTRA_VALUE = "secure-paste-drag-extra-value";
    public static final String CUSTOM_PASTE_DESCRIPTION = "Custom Paste";
    public static final String CACHED_PASTE_DESCRIPTION = "Cached Paste";
    public static final String COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP =
            "older_getPrimaryClip_gate";
    public static final String COMPOSE_EMULATION_NEWER_METADATA =
            "newer_metadata_gate";
    public static final String COMPOSE_EMULATION_NEWER_METADATA_RECEIVE_CONTENT =
            "newer_metadata_receive_content_gate";

    private static final int MENU_FRAMEWORK_TITLE_PASTE = 0x53500001;
    private static final int MENU_CUSTOM_PASTE = 0x53500002;

    private static WeakReference<SecurePasteActivity> sActivity =
            new WeakReference<>(null);
    private static volatile String sComposeEmulationMode = COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP;
    private static volatile boolean sComposeEmulatedToolbarRequested;
    private static volatile int sLastComposePasteMenuItemId;
    private static volatile DragResult sDragResult = new DragResult();

    private EditText mEditor;
    private boolean mReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        if (isCustomToolbarApp() || isCacheClientApp()) {
            root.addView(newPasteButton());
        }
        if (isCacheClientApp()) {
            root.addView(newCachedPasteButton());
        }
        mEditor = new EditText(this);
        mEditor.setContentDescription(EDITOR_DESCRIPTION);
        mEditor.setMinLines(4);
        mEditor.setSingleLine(false);
        mEditor.setTextIsSelectable(true);
        mEditor.setText("");
        mEditor.setSelectAllOnFocus(false);
        mEditor.setCustomSelectionActionModeCallback(newPasteActionModeCallback());
        mEditor.setCustomInsertionActionModeCallback(newPasteActionModeCallback());
        root.addView(mEditor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (isWriterApp()) {
            root.addView(newDragRow());
        }

        setContentView(root);
        sActivity = new WeakReference<>(this);
        mReady = true;
        mEditor.requestFocus();
    }

    @Override
    protected void onDestroy() {
        if (sActivity.get() == this) {
            sActivity = new WeakReference<>(null);
        }
        super.onDestroy();
    }

    public static boolean isReady() {
        final SecurePasteActivity activity = sActivity.get();
        return activity != null && activity.mReady;
    }

    public static String getEditorText() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null) {
            return null;
        }
        return activity.mEditor.getText().toString();
    }

    public static boolean setEditorText(String text) {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null) {
            return false;
        }
        activity.runOnUiThread(() -> activity.mEditor.setText(text));
        return true;
    }

    public static void resetDragResult() {
        sDragResult = new DragResult();
    }

    public static DragResult getDragResult() {
        return sDragResult;
    }

    public static boolean requestEditorFocus() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null) {
            return false;
        }
        activity.runOnUiThread(() -> {
            activity.mEditor.requestFocus();
            activity.mEditor.setSelection(activity.mEditor.getText().length());
        });
        return true;
    }

    public static boolean clearEditorFocus() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null) {
            return false;
        }
        activity.runOnUiThread(() -> activity.mEditor.clearFocus());
        return true;
    }

    public static boolean setComposeEmulationMode(String mode) {
        if (!COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP.equals(mode)
                && !COMPOSE_EMULATION_NEWER_METADATA.equals(mode)
                && !COMPOSE_EMULATION_NEWER_METADATA_RECEIVE_CONTENT.equals(mode)) {
            return false;
        }
        sComposeEmulationMode = mode;
        sComposeEmulatedToolbarRequested = false;
        sLastComposePasteMenuItemId = 0;
        return true;
    }

    public static boolean composeEmulatedPasteUsesFrameworkId() {
        return sLastComposePasteMenuItemId == android.R.id.paste;
    }

    public static boolean showComposeEmulatedToolbar() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null || !activity.isComposeEmulationApp()) {
            return false;
        }
        final boolean[] result = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            // Newer Compose does not rely on an EditText long-click ActionMode. It calls
            // toolbarRequester.show(), whose onShow callback refreshes clipboard metadata and then
            // asks the platform text context-menu toolbar provider to show an ActionMode:
            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.kt;l=238
            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/contextmenu/modifier/TextContextMenuToolbarHandlerModifier.kt;l=198
            sComposeEmulatedToolbarRequested = true;
            final ActionMode actionMode = activity.mEditor.startActionMode(
                    activity.newPasteActionModeCallback(), ActionMode.TYPE_FLOATING);
            result[0] = actionMode != null;
            if (actionMode == null) {
                sComposeEmulatedToolbarRequested = false;
            }
            latch.countDown();
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    public static boolean directPasteFromClipboard() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null) {
            return false;
        }
        final String text = SecurePasteCommandProvider.readClipboardText(activity);
        return pasteText(text);
    }

    public static boolean pasteText(String text) {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null || text == null) {
            return false;
        }
        activity.runOnUiThread(() -> {
            final int start = Math.max(0, activity.mEditor.getSelectionStart());
            final int end = Math.max(0, activity.mEditor.getSelectionEnd());
            activity.mEditor.getText().replace(Math.min(start, end), Math.max(start, end), text);
        });
        return true;
    }

    public static boolean startDragForTest() {
        final SecurePasteActivity activity = sActivity.get();
        if (activity == null || activity.mEditor == null) {
            return false;
        }
        final boolean[] result = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(() -> {
            result[0] = activity.mEditor.startDragAndDrop(
                    newDragClipData(),
                    new View.DragShadowBuilder(activity.mEditor), null, View.DRAG_FLAG_GLOBAL);
            latch.countDown();
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private static ClipData newDragClipData() {
        final ClipDescription description = new ClipDescription(DRAG_LABEL,
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN});
        final PersistableBundle extras = new PersistableBundle();
        extras.putString(DRAG_EXTRA_KEY, DRAG_EXTRA_VALUE);
        description.setExtras(extras);
        return new ClipData(description, new ClipData.Item(DRAG_TEXT));
    }

    private Button newPasteButton() {
        final Button button = new Button(this);
        button.setText(CUSTOM_PASTE_DESCRIPTION);
        button.setContentDescription(CUSTOM_PASTE_DESCRIPTION);
        button.setOnClickListener(v -> directPasteFromClipboard());
        return button;
    }

    private Button newCachedPasteButton() {
        final Button button = new Button(this);
        button.setText(CACHED_PASTE_DESCRIPTION);
        button.setContentDescription(CACHED_PASTE_DESCRIPTION);
        button.setOnClickListener(v -> SecurePasteActivity.pasteText(
                SecurePasteCommandProvider.getCachedText()));
        return button;
    }

    private LinearLayout newDragRow() {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Keep the UiAutomator drag low and horizontal so it does not pull down the SystemUI
        // notification shade from the status bar region.
        row.addView(newDragSourceView(), new LinearLayout.LayoutParams(
                0, 220, 1));
        row.addView(newDragTargetView(), new LinearLayout.LayoutParams(
                0, 220, 1));
        return row;
    }

    private TextView newDragSourceView() {
        final TextView view = new TextView(this);
        view.setText(DRAG_SOURCE_DESCRIPTION);
        view.setContentDescription(DRAG_SOURCE_DESCRIPTION);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(160);
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) {
                return false;
            }
            final DragResult result = new DragResult();
            sDragResult = result;
            result.startDragResult = v.startDragAndDrop(
                    newDragClipData(), new View.DragShadowBuilder(v), null,
                    View.DRAG_FLAG_GLOBAL);
            return true;
        });
        return view;
    }

    private TextView newDragTargetView() {
        final TextView view = new TextView(this);
        view.setText(DRAG_TARGET_DESCRIPTION);
        view.setContentDescription(DRAG_TARGET_DESCRIPTION);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(160);
        view.setOnDragListener((v, event) -> {
            final DragResult result = sDragResult;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    recordDragStarted(result, event);
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION:
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    recordDrop(result, event);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    result.ended = true;
                    result.dropResult = event.getResult();
                    return true;
                default:
                    return false;
            }
        });
        return view;
    }

    private static void recordDragStarted(DragResult result, DragEvent event) {
        result.started = true;
        result.startedHasClipData = event.getClipData() != null;
        result.startedText = textFromClipData(event.getClipData());
        final ClipDescription description = event.getClipDescription();
        if (description != null) {
            result.startedLabel = String.valueOf(description.getLabel());
            result.startedMimeTypes = mimeTypes(description);
            final PersistableBundle extras = description.getExtras();
            result.startedExtraValue = extras == null ? null : extras.getString(DRAG_EXTRA_KEY);
        }
    }

    private static void recordDrop(DragResult result, DragEvent event) {
        result.dropped = true;
        result.dropHasClipData = event.getClipData() != null;
        result.dropText = textFromClipData(event.getClipData());
    }

    private static String textFromClipData(ClipData clipData) {
        if (clipData == null || clipData.getItemCount() == 0) {
            return null;
        }
        final CharSequence text = clipData.getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    private static String[] mimeTypes(ClipDescription description) {
        final String[] mimeTypes = new String[description.getMimeTypeCount()];
        for (int i = 0; i < description.getMimeTypeCount(); i++) {
            mimeTypes[i] = description.getMimeType(i);
        }
        return mimeTypes;
    }

    private ActionMode.Callback newPasteActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                addCustomPasteItems(menu);
                return !isComposeEmulationApp() || menu.findItem(android.R.id.paste) != null;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                addCustomPasteItems(menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == MENU_FRAMEWORK_TITLE_PASTE
                        || item.getItemId() == MENU_CUSTOM_PASTE
                        || (item.getItemId() == android.R.id.paste && isComposeEmulationApp())) {
                    directPasteFromClipboard();
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                sComposeEmulatedToolbarRequested = false;
            }
        };
    }

    private void addCustomPasteItems(Menu menu) {
        if (!isCustomToolbarApp() && !isComposeEmulationApp()) {
            return;
        }
        menu.removeItem(android.R.id.paste);
        menu.removeItem(MENU_FRAMEWORK_TITLE_PASTE);
        menu.removeItem(MENU_CUSTOM_PASTE);
        if (isComposeEmulationApp()) {
            menu.clear();
            sLastComposePasteMenuItemId = 0;
            addComposeEmulatedPasteItem(menu);
            return;
        }
        menu.add(0, MENU_FRAMEWORK_TITLE_PASTE, 0, getString(android.R.string.paste))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, MENU_CUSTOM_PASTE, 1, "Non-framework paste")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private void addComposeEmulatedPasteItem(Menu menu) {
        if (!composeEmulatedCanShowPaste()) {
            return;
        }
        final MenuItem paste = menu.add(0, android.R.id.paste, 0,
                getString(android.R.string.paste));
        sLastComposePasteMenuItemId = paste.getItemId();
        paste.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private boolean composeEmulatedCanShowPaste() {
        final ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP.equals(sComposeEmulationMode)) {
            // AOSP's current Compose prebuilt, foundation-android 1.10.0-alpha01, reads the clip
            // before adding Paste:
            // https://cs.android.com/androidx/platform/frameworks/support/+/84c18801ee8982f06562f6c17cf5b4ee81e28c80:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/TextFieldSelectionManager.kt;l=788
            // Android Clipboard.getClipEntry() reaches ClipboardManager.getPrimaryClip():
            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidClipboard.android.kt;l=26
            try {
                final ClipData clip = clipboard.getPrimaryClip();
                return clip != null && clip.getDescription().hasMimeType("text/*");
            } catch (RuntimeException e) {
                return false;
            }
        }

        if (!sComposeEmulatedToolbarRequested) {
            return false;
        }

        // Newer Compose availability behavior checks metadata before adding Paste. It shows Paste
        // for text clips, and for non-text clips only when receive content is configured:
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.kt;l=1555
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldSelectionState.android.kt;l=130
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=369
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidDeviceTest/kotlin/androidx/compose/foundation/text/input/internal/selection/TextFieldTextContextMenuToolbarTest.kt;l=398
        // Compose omits disabled items instead of adding disabled Paste:
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/ContextMenu.android.kt;l=54
        try {
            final boolean hasClip = clipboard.hasPrimaryClip();
            if (!hasClip) {
                return false;
            }
            final ClipDescription description = clipboard.getPrimaryClipDescription();
            if (description != null && description.hasMimeType("text/*")) {
                return true;
            }
            return COMPOSE_EMULATION_NEWER_METADATA_RECEIVE_CONTENT.equals(sComposeEmulationMode)
                    && hasClip;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isCustomToolbarApp() {
        return getPackageName().contains("customtoolbar");
    }

    private boolean isWriterApp() {
        return getPackageName().endsWith(".writer");
    }

    private boolean isComposeEmulationApp() {
        return getPackageName().contains("composeemulation");
    }

    private boolean isCacheClientApp() {
        return getPackageName().contains("cacheclient");
    }

    public static final class DragResult {
        public volatile boolean startDragResult;
        public volatile boolean started;
        public volatile boolean startedHasClipData;
        public volatile String startedText;
        public volatile String startedLabel;
        public volatile String[] startedMimeTypes;
        public volatile String startedExtraValue;
        public volatile boolean dropped;
        public volatile boolean dropHasClipData;
        public volatile String dropText;
        public volatile boolean ended;
        public volatile boolean dropResult;
    }
}
