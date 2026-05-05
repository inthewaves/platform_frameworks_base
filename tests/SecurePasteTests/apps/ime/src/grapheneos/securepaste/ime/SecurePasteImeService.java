package grapheneos.securepaste.ime;

import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

// Test-only IME used by SecurePasteCompatibilityTest to exercise the default-IME paste path.
// The harness enables it as the current input method, then asks it to call
// InputConnection.performContextMenuAction(android.R.id.paste) so tests can verify that IME
// initiated paste is treated as an explicit paste action
public class SecurePasteImeService extends InputMethodService {
    private static volatile SecurePasteImeService sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        final LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        final Button button = new Button(this);
        button.setText("IME Paste");
        button.setOnClickListener(v -> requestPaste());
        root.addView(button);
        return root;
    }

    public static boolean isReady() {
        return sInstance != null;
    }

    public static boolean requestPaste() {
        final SecurePasteImeService service = sInstance;
        if (service == null) {
            return false;
        }
        final InputConnection inputConnection = service.getCurrentInputConnection();
        return inputConnection != null
                && inputConnection.performContextMenuAction(android.R.id.paste);
    }
}
