package ar.vger32app.ui.system;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import ar.vger32app.R;
import ar.vger32app.databinding.FragmentSystemLogBinding;
import ar.vger32app.logger.AppLogger;
import ar.vger32app.logger.LogManager;
import ar.vger32app.ui.BaseFragment;

/*
 * Displays the app's internal log (AppLogger).
 * Renamed from LogFragment to distinguish it from ModuleLogFragment.
 * Visually symmetric to ModuleLogFragment: same toolbar, scroll, and font.
 */

public class SystemLogFragment extends BaseFragment {

    private FragmentSystemLogBinding binding;
    private final AppLogger appLogger = LogManager.APP_LOGGER;

    public SystemLogFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSystemLogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnCopy.setOnClickListener(v -> copyToClipboard());
        binding.btnDelete.setOnClickListener(v -> confirmClean());
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- DATA -----------------------------------------------

    private void load() {
        appLogger.flush();
        binding.txtTitle.setText(getString(R.string.log_lines_count, appLogger.size()));
        binding.txtContent.setText(appLogger.getLog());
    }

    // --------------------------------------------------------
    // --- ACTIONS --------------------------------------------

    private void confirmClean() {
        confirm(getString(R.string.lf_msg_confirm_delete_log), R.string.btn_delete, () -> {
            appLogger.clean();
            load();
        });
    }

    private void copyToClipboard() {
        String text = binding.txtContent.getText().toString();
        Context ctx = getContext();
        if (ctx == null) return;
        ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.clipboard_label_log), text));
            toast(R.string.copied_to_clipboard);
        }
    }
}