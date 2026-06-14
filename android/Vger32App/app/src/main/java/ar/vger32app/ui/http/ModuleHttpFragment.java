package ar.vger32app.ui.http;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import ar.vger32app.R;
import ar.vger32app.databinding.BottomSheetResultBinding;
import ar.vger32app.databinding.FragmentModuleHttpBinding;
import ar.vger32app.databinding.ItemCardActionBinding;
import ar.vger32app.logger.LogManager;
import ar.vger32app.network.http.ApiKeyHelper;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.ModuleHttpVM;

/*
 * HTTP panel: grouped Web, Monitor and Actions card list.
 * Results are shown in a generic BottomSheet (copy to clipboard).
 * Exception: the Module Log card navigates to ModuleLogFragment (full screen),
 * consistent with how System Log is accessed from the System menu.
 */

public class ModuleHttpFragment extends BaseFragment {

    private static final String LOG_TAG = "ModuleHttpFragment";

    private FragmentModuleHttpBinding binding;
    private ModuleHttpVM viewModel;
    private BottomSheetDialog activeResultSheet;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentModuleHttpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ModuleHttpVM.class);
        setupCards();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeResultSheet != null) {
            activeResultSheet.dismiss();
            activeResultSheet = null;
        }
        binding = null;
    }

    // --------------------------------------------------------
    // --- OBSERVE --------------------------------------------

    private void observeViewModel() {
        viewModel.getResult().observe(getViewLifecycleOwner(),
                r -> showResult(r.title, r.body));

        viewModel.isLoading().observe(getViewLifecycleOwner(),
                loading -> setCardsEnabled(!Boolean.TRUE.equals(loading)));

        viewModel.getNeedsApiKey().observe(getViewLifecycleOwner(), mid ->
                ApiKeyHelper.promptAndSave(this, mid, new ApiKeyHelper.OnKeyReady() {
                    @Override
                    public void onKey(String key) {
                        viewModel.updateApiKey(key);
                    }

                    @Override
                    public void onCancelled() {
                    }
                }));
    }

    // --------------------------------------------------------
    // --- CARDS SETUP ----------------------------------------

    private void setCardsEnabled(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;
        for (ItemCardActionBinding card : new ItemCardActionBinding[]{
                binding.itemDashboard, binding.itemIdentity, binding.itemMetrics,
                binding.itemSensors, binding.itemBootHistory, binding.itemLogs,
                binding.itemRestart, binding.itemForceAp}) {
            card.getRoot().setEnabled(enabled);
            card.getRoot().setAlpha(alpha);
        }
    }

    private void setupCards() {
        setupCard(binding.itemDashboard, R.drawable.ic_language, R.string.http_item_dashboard, R.string.http_item_dashboard_desc, v -> openDashboard());
        setupCard(binding.itemIdentity, R.drawable.ic_fingerprint, R.string.http_item_identity, R.string.http_item_identity_desc, v -> viewModel.fetchSystemIdentity());
        setupCard(binding.itemMetrics, R.drawable.ic_bar_chart, R.string.http_item_metrics, R.string.http_item_metrics_desc, v -> viewModel.fetchSystemMetrics());
        setupCard(binding.itemSensors, R.drawable.ic_sensors, R.string.http_item_sensors, R.string.http_item_sensors_desc, v -> viewModel.fetchSensors());
        setupCard(binding.itemBootHistory, R.drawable.ic_history, R.string.http_item_boot_history, R.string.http_item_boot_history_desc, v -> viewModel.fetchBootHistory());
        setupCard(binding.itemLogs, R.drawable.ic_data_object, R.string.http_item_logs, R.string.http_item_logs_desc, v -> openModuleLog(v));
        setupCard(binding.itemRestart, R.drawable.ic_restart_alt, R.string.http_item_reboot, R.string.http_item_reboot_desc, v -> confirmReboot());
        setupCard(binding.itemForceAp, R.drawable.ic_adjust, R.string.http_item_force_ap, R.string.http_item_force_ap_desc, v -> confirmForceAp());

        int neon = ContextCompat.getColor(requireContext(), R.color.brand200);
        binding.itemDashboard.txtCardLabel.setTextColor(neon);
        binding.itemDashboard.imgCardIcon.setColorFilter(neon);

        int red = ContextCompat.getColor(requireContext(), R.color.RED);
        binding.itemRestart.txtCardLabel.setTextColor(red);
        binding.itemRestart.imgCardIcon.setColorFilter(red);
        binding.itemForceAp.txtCardLabel.setTextColor(red);
        binding.itemForceAp.imgCardIcon.setColorFilter(red);
    }

    private void setupCard(ItemCardActionBinding card,
                           @DrawableRes int iconRes, @StringRes int labelRes,
                           @StringRes int descRes, View.OnClickListener click) {
        card.imgCardIcon.setImageResource(iconRes);
        card.txtCardLabel.setText(getString(labelRes));
        card.txtCardDesc.setText(getString(descRes));
        card.getRoot().setOnClickListener(click);
    }

    // --------------------------------------------------------
    // --- ACTIONS --------------------------------------------

    private void openModuleLog(View v) {
        Navigation.findNavController(v).navigate(R.id.action_http_to_module_log);
    }

    private void openDashboard() {
        String ip = viewModel.getSelectedIp();
        String mid = viewModel.getSelectedMid();
        if (ip == null || ip.isEmpty()) {
            showResult(getString(R.string.dialog_error_title),
                    getString(R.string.status_no_module_selected));
            return;
        }
        ApiKeyHelper.resolveOrPrompt(this, mid, new ApiKeyHelper.OnKeyReady() {
            @Override
            public void onKey(String key) {
                String url = "http://" + ip + "/" + (key != null ? "?key=" + key : "");
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    LogManager.APP_LOGGER.warn(LOG_TAG, "No browser available to open dashboard");
                    showResult(getString(R.string.dialog_error_title),
                            getString(R.string.err_no_browser));
                }
            }

            @Override
            public void onCancelled() {
            }
        });
    }

    // --------------------------------------------------------
    // --- REBOOT ---------------------------------------------

    private void confirmReboot() {
        String mid = viewModel.getSelectedMid();
        if (mid == null) {
            showResult(getString(R.string.dialog_error_title),
                    getString(R.string.status_no_module_selected));
            return;
        }
        confirm(R.string.http_item_reboot,
                getString(R.string.dialog_reboot_message, mid),
                R.string.http_item_reboot, () -> viewModel.reboot());
    }

    // --------------------------------------------------------
    // --- FORCE AP -------------------------------------------

    private void confirmForceAp() {
        String mid = viewModel.getSelectedMid();
        if (mid == null) {
            showResult(getString(R.string.dialog_error_title),
                    getString(R.string.status_no_module_selected));
            return;
        }
        confirm(R.string.http_item_force_ap,
                getString(R.string.dialog_force_ap_message, mid),
                R.string.http_item_force_ap, () -> viewModel.forceAp());
    }

    // --------------------------------------------------------
    // --- RESULT SHEET — identity, metrics, sensors, boot history, reboot ---

    private void showResult(String title, String body) {
        if (!isAdded() || getContext() == null) return;

        if (activeResultSheet != null) activeResultSheet.dismiss();

        BottomSheetResultBinding sv = BottomSheetResultBinding.inflate(
                LayoutInflater.from(requireContext()));
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        sheet.setContentView(sv.getRoot());
        activeResultSheet = sheet;

        String bodyText = (body != null && !body.isEmpty())
                ? body : getString(R.string.status_no_data);

        sv.txtResultTitle.setText(title);
        sv.txtResultBody.setText(bodyText);

        sv.btnCopyResult.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(title, bodyText));
            sheet.dismiss();
            toast(R.string.copied_to_clipboard);
        });

        sheet.show();
    }
}