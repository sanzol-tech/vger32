package ar.vger32app.ui;

import android.app.Activity;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.function.Consumer;

import ar.vger32app.R;

/*
 * Base class for all Fragments in the project. Centralises UI helpers:
 * message dialogs, toasts, confirm dialogs, input dialogs,
 * and safe UI-thread dispatch.
 */

public abstract class BaseFragment extends Fragment {

    public enum MessageType {INFO, ERROR, FATAL}

    // --------------------------------------------------------
    // --- MESSAGE DIALOGS ------------------------------------

    protected void showMessageDialog(String message, Throwable t) {
        showMessageDialog(message + ": " + t.getMessage(), MessageType.FATAL);
    }

    protected void showMessageDialog(String message) {
        showMessageDialog(message, MessageType.INFO);
    }

    protected void showMessageDialog(String message, MessageType type) {
        int[] attrs = getMessageAttributes(type);

        new AlertDialog.Builder(requireActivity())
                .setIcon(attrs[0])
                .setTitle(attrs[1])
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> d.dismiss())
                .show();
    }

    private int[] getMessageAttributes(MessageType type) {
        switch (type) {
            case ERROR:
                // ic_warning_yellow replaces deleted ic_emergency_home_red
                return new int[]{R.drawable.ic_warning_yellow, R.string.dialog_error_title};
            case FATAL:
                return new int[]{R.drawable.ic_cancel_red, R.string.dialog_error_title};
            default: // INFO
                // ic_info replaces deleted ic_local_police
                return new int[]{R.drawable.ic_info, R.string.app_name};
        }
    }

    // --------------------------------------------------------
    // --- TOASTS ---------------------------------------------

    protected void toast(@StringRes int resId) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
    }

    protected void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    protected void toastLong(@StringRes int resId) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_LONG).show();
    }

    protected void toastLong(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    // --------------------------------------------------------
    // --- CONFIRM DIALOGS ------------------------------------

    // Title + @StringRes message + custom positive button.
    protected void confirm(@StringRes int title, @StringRes int message,
                           @StringRes int positiveBtn, @NonNull Runnable onConfirm) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveBtn, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // Title + pre-formatted String message (use when message needs format args).
    protected void confirm(@StringRes int title, String message,
                           @StringRes int positiveBtn, @NonNull Runnable onConfirm) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveBtn, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // No title — use only when the message is self-explanatory (e.g. short action confirmation).
    protected void confirm(String message, @StringRes int positiveBtn,
                           @NonNull Runnable onConfirm) {
        new AlertDialog.Builder(requireActivity())
                .setMessage(message)
                .setPositiveButton(positiveBtn, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // --------------------------------------------------------
    // --- INPUT DIALOG ---------------------------------------

    // With optional informational message below the title (e.g. "Will save top N networks").
    // Pass null for message when no extra context is needed.
    protected void inputDialog(@StringRes int title, @Nullable String message,
                               @StringRes int hint, int maxLen,
                               @NonNull Consumer<String> onConfirm) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_input, null);
        EditText input = dialogView.findViewById(R.id.edit_input);
        input.setHint(hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_ok,
                        (d, w) -> onConfirm.accept(String.valueOf(input.getText()).trim()))
                .setNegativeButton(R.string.cancel, null);
        if (message != null) builder.setMessage(message);
        builder.show();
    }

    // No message variant — delegates to the overload above.
    protected void inputDialog(@StringRes int title, @StringRes int hint,
                               int maxLen, @NonNull Consumer<String> onConfirm) {
        inputDialog(title, null, hint, maxLen, onConfirm);
    }

    // --------------------------------------------------------
    // --- UI THREAD DISPATCH ---------------------------------

    protected void runOnUi(@NonNull Runnable r) {
        if (!isAdded()) return;
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(r);
    }
}