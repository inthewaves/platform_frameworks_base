package grapheneos.securepaste.helper;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SecurePasteCommandProvider extends ContentProvider {
    private static final String JETPACK_COMPOSE_PACKAGE =
            "grapheneos.securepaste.jetpackcompose";
    private static final String JETPACK_COMPOSE_ACTIVITY =
            "grapheneos.securepaste.jetpackcompose.SecurePasteJetpackComposeActivity";

    public static final String METHOD_SET_CLIP_TEXT = "set_clip_text";
    public static final String METHOD_SET_CLIP_HTML = "set_clip_html";
    public static final String METHOD_SET_CLIP_URI = "set_clip_uri";
    public static final String METHOD_SET_CLIP_NONTEXT_URI = "set_clip_nontext_uri";
    public static final String METHOD_CLEAR_CLIP = "clear_clip";
    public static final String METHOD_READ_CLIP = "read_clip";
    public static final String METHOD_READ_OWN_CLIP = "read_own_clip";
    public static final String METHOD_REGISTER_LISTENER = "register_listener";
    public static final String METHOD_RESET_LISTENER = "reset_listener";
    public static final String METHOD_GET_LISTENER_COUNT = "get_listener_count";
    public static final String METHOD_CACHE_CLIP = "cache_clip";
    public static final String METHOD_ACTIVITY_READY = "activity_ready";
    public static final String METHOD_GET_EDITOR_TEXT = "get_editor_text";
    public static final String METHOD_SET_EDITOR_TEXT = "set_editor_text";
    public static final String METHOD_REQUEST_FOCUS = "request_focus";
    public static final String METHOD_CLEAR_FOCUS = "clear_focus";
    public static final String METHOD_DIRECT_PASTE = "direct_paste";
    public static final String METHOD_CACHED_PASTE = "cached_paste";
    public static final String METHOD_START_DRAG = "start_drag";
    public static final String METHOD_RESET_DRAG_RESULT = "reset_drag_result";
    public static final String METHOD_GET_DRAG_RESULT = "get_drag_result";
    public static final String METHOD_ACCESSIBILITY_CONNECTED = "accessibility_connected";
    public static final String METHOD_ACCESSIBILITY_PASTE = "accessibility_paste";
    public static final String METHOD_IME_READY = "ime_ready";
    public static final String METHOD_IME_PASTE = "ime_paste";
    public static final String METHOD_SET_COMPOSE_EMULATION_MODE = "set_compose_emulation_mode";
    public static final String METHOD_SHOW_COMPOSE_EMULATED_TOOLBAR =
            "show_compose_emulated_toolbar";
    public static final String METHOD_COMPOSE_EMULATED_PASTE_USES_FRAMEWORK_ID =
            "compose_emulated_paste_uses_framework_id";
    public static final String METHOD_SET_JETPACK_COMPOSE_FIELD_MODE =
            "set_jetpack_compose_field_mode";

    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_HTML = "html";
    public static final String EXTRA_MODE = "mode";
    public static final String RESULT_OK = "ok";
    public static final String RESULT_TEXT = "text";
    public static final String RESULT_EXCEPTION = "exception";
    public static final String RESULT_HAS_CLIP = "hasClip";
    public static final String RESULT_HAS_TEXT = "hasText";
    public static final String RESULT_DESCRIPTION = "description";
    public static final String RESULT_MIME_TYPES = "mimeTypes";
    public static final String RESULT_COUNT = "count";
    public static final String RESULT_DRAG_START_RESULT = "dragStartResult";
    public static final String RESULT_DRAG_STARTED_HAS_CLIP_DATA = "dragStartedHasClipData";
    public static final String RESULT_DRAG_STARTED_TEXT = "dragStartedText";
    public static final String RESULT_DRAG_STARTED_EXTRA_VALUE = "dragStartedExtraValue";
    public static final String RESULT_DRAG_DROPPED = "dragDropped";
    public static final String RESULT_DRAG_DROP_HAS_CLIP_DATA = "dragDropHasClipData";
    public static final String RESULT_DRAG_DROP_TEXT = "dragDropText";
    public static final String RESULT_DRAG_ENDED = "dragEnded";
    public static final String RESULT_DRAG_DROP_RESULT = "dragDropResult";

    private static final AtomicInteger sListenerCount = new AtomicInteger();
    private static ClipboardManager.OnPrimaryClipChangedListener sListener;
    private static String sCachedText;
    private static volatile boolean sAccessibilityConnected;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        final Bundle result = new Bundle();
        final Context context = requireContext();
        try {
            switch (method) {
                case METHOD_SET_CLIP_TEXT:
                    setTextClip(context, getString(extras, EXTRA_TEXT, "secure-paste-text"));
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_SET_CLIP_HTML:
                    setHtmlClip(context, getString(extras, EXTRA_TEXT, "secure-paste-text"),
                            getString(extras, EXTRA_HTML, "<b>secure-paste-text</b>"));
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_SET_CLIP_URI:
                    setUriClip(context, getString(extras, EXTRA_TEXT, "secure-paste-uri-text"));
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_SET_CLIP_NONTEXT_URI:
                    setNonTextUriClip(context);
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_CLEAR_CLIP:
                    clipboard(context).clearPrimaryClip();
                    sCachedText = null;
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_READ_CLIP:
                    readClip(context, result);
                    break;
                case METHOD_READ_OWN_CLIP:
                    setTextClip(context, getString(extras, EXTRA_TEXT, "secure-paste-own-text"));
                    readClip(context, result);
                    break;
                case METHOD_REGISTER_LISTENER:
                    registerListener(context);
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_RESET_LISTENER:
                    sListenerCount.set(0);
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_GET_LISTENER_COUNT:
                    result.putInt(RESULT_COUNT, sListenerCount.get());
                    break;
                case METHOD_CACHE_CLIP:
                    sCachedText = readClipboardText(context);
                    result.putString(RESULT_TEXT, sCachedText);
                    result.putBoolean(RESULT_OK, sCachedText != null);
                    break;
                case METHOD_ACTIVITY_READY:
                    result.putBoolean(RESULT_OK, isActivityReady(context));
                    break;
                case METHOD_GET_EDITOR_TEXT:
                    result.putString(RESULT_TEXT, getEditorText(context));
                    result.putBoolean(RESULT_OK, isActivityReady(context));
                    break;
                case METHOD_SET_EDITOR_TEXT:
                    result.putBoolean(RESULT_OK,
                            setEditorText(context, getString(extras, EXTRA_TEXT, "")));
                    break;
                case METHOD_REQUEST_FOCUS:
                    result.putBoolean(RESULT_OK, requestEditorFocus(context));
                    break;
                case METHOD_CLEAR_FOCUS:
                    result.putBoolean(RESULT_OK, clearEditorFocus(context));
                    break;
                case METHOD_DIRECT_PASTE:
                    result.putBoolean(RESULT_OK, directPasteFromClipboard(context));
                    result.putString(RESULT_TEXT, getEditorText(context));
                    break;
                case METHOD_CACHED_PASTE:
                    result.putBoolean(RESULT_OK, SecurePasteActivity.pasteText(sCachedText));
                    result.putString(RESULT_TEXT, SecurePasteActivity.getEditorText());
                    break;
                case METHOD_START_DRAG:
                    result.putBoolean(RESULT_OK, SecurePasteActivity.startDragForTest());
                    break;
                case METHOD_RESET_DRAG_RESULT:
                    SecurePasteActivity.resetDragResult();
                    result.putBoolean(RESULT_OK, true);
                    break;
                case METHOD_GET_DRAG_RESULT:
                    readDragResult(result);
                    break;
                case METHOD_SET_COMPOSE_EMULATION_MODE:
                    result.putBoolean(RESULT_OK, SecurePasteActivity.setComposeEmulationMode(
                            getString(extras, EXTRA_MODE,
                                    SecurePasteActivity.COMPOSE_EMULATION_OLDER_GET_PRIMARY_CLIP)));
                    break;
                case METHOD_SHOW_COMPOSE_EMULATED_TOOLBAR:
                    result.putBoolean(RESULT_OK, SecurePasteActivity.showComposeEmulatedToolbar());
                    break;
                case METHOD_COMPOSE_EMULATED_PASTE_USES_FRAMEWORK_ID:
                    result.putBoolean(RESULT_OK,
                            SecurePasteActivity.composeEmulatedPasteUsesFrameworkId());
                    break;
                case METHOD_SET_JETPACK_COMPOSE_FIELD_MODE:
                    result.putBoolean(RESULT_OK, setJetpackComposeFieldMode(context,
                            getString(extras, EXTRA_MODE, "value")));
                    break;
                case METHOD_ACCESSIBILITY_CONNECTED:
                    result.putBoolean(RESULT_OK, sAccessibilityConnected);
                    break;
                case METHOD_ACCESSIBILITY_PASTE:
                    result.putBoolean(RESULT_OK,
                            SecurePasteAccessibilityServiceBase.performPasteOnFocusedNode());
                    break;
                case METHOD_IME_READY:
                    result.putBoolean(RESULT_OK, invokeImeBoolean("isReady"));
                    break;
                case METHOD_IME_PASTE:
                    result.putBoolean(RESULT_OK, invokeImeBoolean("requestPaste"));
                    break;
                default:
                    result.putBoolean(RESULT_OK, false);
                    result.putString(RESULT_EXCEPTION, "Unknown method " + method);
            }
        } catch (Throwable t) {
            result.putBoolean(RESULT_OK, false);
            result.putString(RESULT_EXCEPTION, t.toString());
        }
        return result;
    }

    public static void setAccessibilityConnected(boolean connected) {
        sAccessibilityConnected = connected;
    }

    public static String getCachedText() {
        return sCachedText;
    }

    public static String readClipboardText(Context context) {
        final ClipData clip = clipboard(context).getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        final CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? null : text.toString();
    }

    private static boolean isActivityReady(Context context) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (Boolean) invokeJetpackCompose("isReady");
        }
        return SecurePasteActivity.isReady();
    }

    private static String getEditorText(Context context) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (String) invokeJetpackCompose("getEditorText");
        }
        return SecurePasteActivity.getEditorText();
    }

    private static boolean setEditorText(Context context, String text) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (Boolean) invokeJetpackCompose("setEditorText",
                    new Class<?>[] {String.class}, text);
        }
        return SecurePasteActivity.setEditorText(text);
    }

    private static boolean requestEditorFocus(Context context) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (Boolean) invokeJetpackCompose("requestEditorFocus");
        }
        return SecurePasteActivity.requestEditorFocus();
    }

    private static boolean clearEditorFocus(Context context) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (Boolean) invokeJetpackCompose("clearEditorFocus");
        }
        return SecurePasteActivity.clearEditorFocus();
    }

    private static boolean directPasteFromClipboard(Context context) throws Exception {
        if (isJetpackComposeApp(context)) {
            return (Boolean) invokeJetpackCompose("directPasteFromClipboard");
        }
        return SecurePasteActivity.directPasteFromClipboard();
    }

    private static boolean setJetpackComposeFieldMode(Context context, String mode)
            throws Exception {
        if (!isJetpackComposeApp(context)) {
            return false;
        }
        return (Boolean) invokeJetpackCompose("setFieldMode",
                new Class<?>[] {String.class}, mode);
    }

    private static boolean isJetpackComposeApp(Context context) {
        return JETPACK_COMPOSE_PACKAGE.equals(context.getPackageName());
    }

    private static Object invokeJetpackCompose(String methodName) throws Exception {
        return invokeJetpackCompose(methodName, new Class<?>[0]);
    }

    private static Object invokeJetpackCompose(String methodName, Class<?>[] parameterTypes,
            Object... args) throws Exception {
        final Class<?> cls = Class.forName(JETPACK_COMPOSE_ACTIVITY);
        final Method method = cls.getDeclaredMethod(methodName, parameterTypes);
        return method.invoke(null, args);
    }

    private static void setTextClip(Context context, String text) {
        clipboard(context).setPrimaryClip(ClipData.newPlainText("secure-paste-label", text));
    }

    private static void setHtmlClip(Context context, String text, String html) {
        clipboard(context).setPrimaryClip(ClipData.newHtmlText(
                "secure-paste-html-label", text, html));
    }

    private static void setUriClip(Context context, String text) {
        final Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".provider")
                .path("clip")
                .build();
        final ClipData clip = new ClipData("secure-paste-uri-label",
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN, "text/uri-list"},
                new ClipData.Item(text, null, uri));
        clipboard(context).setPrimaryClip(clip);
    }

    private static void setNonTextUriClip(Context context) {
        final Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".provider")
                .path("clip")
                .build();
        final ClipData clip = new ClipData("secure-paste-nontext-uri-label",
                new String[] {"application/octet-stream"}, new ClipData.Item(uri));
        clipboard(context).setPrimaryClip(clip);
    }

    private static void readClip(Context context, Bundle result) {
        try {
            final ClipboardManager clipboard = clipboard(context);
            final ClipData clip = clipboard.getPrimaryClip();
            result.putBoolean(RESULT_OK, clip != null);
            result.putBoolean(RESULT_HAS_CLIP, clipboard.hasPrimaryClip());
            result.putBoolean(RESULT_HAS_TEXT, clipboard.hasText());
            final ClipDescription description = clipboard.getPrimaryClipDescription();
            if (description != null) {
                result.putString(RESULT_DESCRIPTION, String.valueOf(description.getLabel()));
                final String[] mimeTypes = new String[description.getMimeTypeCount()];
                for (int i = 0; i < description.getMimeTypeCount(); i++) {
                    mimeTypes[i] = description.getMimeType(i);
                }
                result.putStringArray(RESULT_MIME_TYPES, mimeTypes);
            }
            if (clip != null && clip.getItemCount() > 0) {
                final CharSequence text = clip.getItemAt(0).coerceToText(context);
                result.putString(RESULT_TEXT, text == null ? null : text.toString());
                result.putInt(RESULT_COUNT, clip.getItemCount());
            }
        } catch (Throwable t) {
            result.putBoolean(RESULT_OK, false);
            result.putString(RESULT_EXCEPTION, t.toString());
        }
    }

    private static void readDragResult(Bundle result) {
        final SecurePasteActivity.DragResult dragResult = SecurePasteActivity.getDragResult();
        result.putBoolean(RESULT_OK, dragResult.started);
        result.putBoolean(RESULT_DRAG_START_RESULT, dragResult.startDragResult);
        result.putBoolean(RESULT_DRAG_STARTED_HAS_CLIP_DATA,
                dragResult.startedHasClipData);
        result.putString(RESULT_DRAG_STARTED_TEXT, dragResult.startedText);
        result.putString(RESULT_DESCRIPTION, dragResult.startedLabel);
        result.putStringArray(RESULT_MIME_TYPES, dragResult.startedMimeTypes);
        result.putString(RESULT_DRAG_STARTED_EXTRA_VALUE, dragResult.startedExtraValue);
        result.putBoolean(RESULT_DRAG_DROPPED, dragResult.dropped);
        result.putBoolean(RESULT_DRAG_DROP_HAS_CLIP_DATA, dragResult.dropHasClipData);
        result.putString(RESULT_DRAG_DROP_TEXT, dragResult.dropText);
        result.putBoolean(RESULT_DRAG_ENDED, dragResult.ended);
        result.putBoolean(RESULT_DRAG_DROP_RESULT, dragResult.dropResult);
    }

    private static void registerListener(Context context) {
        if (sListener != null) {
            return;
        }
        sListener = () -> sListenerCount.incrementAndGet();
        clipboard(context).addPrimaryClipChangedListener(sListener);
    }

    private static ClipboardManager clipboard(Context context) {
        return context.getSystemService(ClipboardManager.class);
    }

    private static String getString(Bundle extras, String key, String fallback) {
        if (extras == null) {
            return fallback;
        }
        final String value = extras.getString(key);
        return value == null ? fallback : value;
    }

    private static boolean invokeImeBoolean(String methodName) throws Exception {
        final Class<?> cls = Class.forName("grapheneos.securepaste.ime.SecurePasteImeService");
        final Method method = cls.getDeclaredMethod(methodName);
        return (Boolean) method.invoke(null);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (FileOutputStream out = new FileOutputStream(pipe[1].getFileDescriptor())) {
                    out.write("secure-paste-uri-provider-data".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                }
            }, "SecurePasteUriPipe").start();
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException(e.toString());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
