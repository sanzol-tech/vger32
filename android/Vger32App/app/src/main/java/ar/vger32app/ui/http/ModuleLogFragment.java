package ar.vger32app.ui.http;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import ar.vger32app.R;
import ar.vger32app.databinding.FragmentModuleLogBinding;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.ModuleLogVM;

/*
 * Displays the selected module's firmware log (GET /api/logs).
 * Programmatically symmetric to SystemLogFragment: same toolbar pattern,
 * scroll behaviour, and font. Navigated to from ModuleHttpFragment.
 *
 * The ViewModel reverses the log so the newest entry appears at the top.
 * scrollTo(0, 0) on content update ensures the top is always visible after
 * a refresh, regardless of the user's previous scroll position.
 */

public class ModuleLogFragment extends BaseFragment {

    private FragmentModuleLogBinding binding;
    private ModuleLogVM viewModel;

    public ModuleLogFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentModuleLogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ModuleLogVM.class);
        setupButtons();
        observeViewModel();
        viewModel.load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupButtons() {
        binding.btnCopy.setOnClickListener(v -> copyToClipboard());
        binding.btnDelete.setOnClickListener(v -> confirmClear());
    }

    private void observeViewModel() {
        viewModel.getTitle().observe(getViewLifecycleOwner(),
                t -> binding.txtTitle.setText(t));

        viewModel.getContent().observe(getViewLifecycleOwner(), c -> {
            binding.txtContent.setText(c);
            binding.scrollView.scrollTo(0, 0);
        });

        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean active = Boolean.TRUE.equals(loading);
            binding.progress.getRoot().setVisibility(active ? View.VISIBLE : View.GONE);
            binding.scrollView.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
            binding.btnCopy.setEnabled(!active);
            binding.btnDelete.setEnabled(!active);
        });
    }

    // --------------------------------------------------------
    // --- ACTIONS --------------------------------------------

    private void confirmClear() {
        String mid = viewModel.getMid();
        if (mid == null) return;
        confirm(R.string.http_item_logs,
                getString(R.string.dialog_clear_module_log_message, mid),
                R.string.btn_delete,
                () -> viewModel.clear());
    }

    private void copyToClipboard() {
        String text = binding.txtContent.getText() != null
                ? binding.txtContent.getText().toString() : "";
        Context ctx = getContext();
        if (ctx == null) return;
        ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.http_item_logs), text));
            toast(R.string.copied_to_clipboard);
        }
    }
}