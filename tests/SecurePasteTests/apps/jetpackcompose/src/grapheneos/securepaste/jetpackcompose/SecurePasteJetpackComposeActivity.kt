package grapheneos.securepaste.jetpackcompose

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import grapheneos.securepaste.helper.SecurePasteActivity
import grapheneos.securepaste.helper.SecurePasteCommandProvider
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SecurePasteJetpackComposeActivity : ComponentActivity() {
    private val editorState = mutableStateOf(TextFieldValue(""))
    private val fieldMode = mutableStateOf(FIELD_MODE_VALUE)
    @Volatile private var editorText = ""
    @Volatile private var ready = false
    private var focusRequester: FocusRequester? = null
    private var focusManager: FocusManager? = null
    private var stateEditorState: TextFieldState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        activity = WeakReference(this)

        setContent {
            val requester = remember { FocusRequester() }
            val manager = LocalFocusManager.current
            val state = rememberTextFieldState()
            val stateText = state.text.toString()
            val valueText = editorState.value.text
            val modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .heightIn(min = 128.dp)
                .focusRequester(requester)
                .semantics {
                    contentDescription = SecurePasteActivity.EDITOR_DESCRIPTION
                }

            SideEffect {
                focusRequester = requester
                focusManager = manager
                stateEditorState = state
                editorText = if (fieldMode.value == FIELD_MODE_STATE) stateText else valueText
                ready = true
            }

            LaunchedEffect(Unit) {
                requester.requestFocus()
            }

            if (fieldMode.value == FIELD_MODE_STATE) {
                BasicTextField(
                    state = state,
                    modifier = modifier,
                    textStyle = TextStyle(fontSize = 18.sp),
                )
            } else {
                BasicTextField(
                    value = editorState.value,
                    onValueChange = { value ->
                        editorState.value = value
                        editorText = value.text
                    },
                    modifier = modifier,
                    textStyle = TextStyle(fontSize = 18.sp),
                )
            }
        }
    }

    override fun onDestroy() {
        if (activity.get() === this) {
            activity = WeakReference<SecurePasteJetpackComposeActivity>(null)
        }
        super.onDestroy()
    }

    private fun setTextOnUiThread(text: String) {
        editorText = text
        if (fieldMode.value == FIELD_MODE_STATE) {
            stateEditorState?.setTextAndPlaceCursorAtEnd(text)
        } else {
            editorState.value = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    private fun setFieldModeOnUiThread(mode: String) {
        val text = editorText
        fieldMode.value = mode
        if (mode == FIELD_MODE_STATE) {
            stateEditorState?.setTextAndPlaceCursorAtEnd(text)
        } else {
            editorState.value = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    private fun requestFocusOnUiThread() {
        focusRequester?.requestFocus()
    }

    private fun clearFocusOnUiThread() {
        focusManager?.clearFocus(force = true)
    }

    companion object {
        private const val FIELD_MODE_VALUE = "value"
        private const val FIELD_MODE_STATE = "state"

        @Volatile
        private var activity = WeakReference<SecurePasteJetpackComposeActivity>(null)

        @JvmStatic
        fun isReady(): Boolean = activity.get()?.ready == true

        @JvmStatic
        fun getEditorText(): String? = activity.get()?.editorText

        @JvmStatic
        fun setEditorText(text: String): Boolean {
            val current = activity.get() ?: return false
            current.runOnUiThread { current.setTextOnUiThread(text) }
            return true
        }

        @JvmStatic
        fun setFieldMode(mode: String): Boolean {
            if (mode != FIELD_MODE_VALUE && mode != FIELD_MODE_STATE) {
                return false
            }
            val current = activity.get() ?: return false
            val latch = CountDownLatch(1)
            current.runOnUiThread {
                current.setFieldModeOnUiThread(mode)
                latch.countDown()
            }
            return try {
                latch.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        @JvmStatic
        fun requestEditorFocus(): Boolean {
            val current = activity.get() ?: return false
            current.runOnUiThread { current.requestFocusOnUiThread() }
            return true
        }

        @JvmStatic
        fun clearEditorFocus(): Boolean {
            val current = activity.get() ?: return false
            current.runOnUiThread { current.clearFocusOnUiThread() }
            return true
        }

        @JvmStatic
        fun directPasteFromClipboard(): Boolean {
            val current = activity.get() ?: return false
            val text = SecurePasteCommandProvider.readClipboardText(current) ?: return false
            current.runOnUiThread {
                if (current.fieldMode.value == FIELD_MODE_STATE) {
                    val state = current.stateEditorState ?: return@runOnUiThread
                    val start = state.selection.min
                    val end = state.selection.max
                    state.edit {
                        replace(start, end, text)
                        placeCursorBeforeCharAt(start + text.length)
                    }
                    current.editorText = state.text.toString()
                } else {
                    val oldValue = current.editorState.value
                    val start = oldValue.selection.min
                    val end = oldValue.selection.max
                    val newText = oldValue.text.replaceRange(start, end, text)
                    current.editorText = newText
                    current.editorState.value = TextFieldValue(
                        newText,
                        selection = TextRange(start + text.length),
                    )
                }
            }
            return true
        }
    }
}
