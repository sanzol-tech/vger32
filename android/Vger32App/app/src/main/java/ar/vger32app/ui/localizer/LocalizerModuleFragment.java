package ar.vger32app.ui.localizer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import ar.vger32app.R;
import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.databinding.FragmentLocalizerModuleBinding;
import ar.vger32app.localizer.Waypoint;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.LocalizerVM;

/*
 * Module WiFi scan tab. Fetches live WiFi scan from the selected ESP32
 * via GET /api/wifi-scan.
 * Import of stored fingerprints was moved to the Storage tab bottom sheet.
 */

public class LocalizerModuleFragment extends BaseFragment {

    private FragmentLocalizerModuleBinding binding;
    private LocalizerWifiAdapter adapter;
    private LocalizerVM viewModel;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLocalizerModuleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment())
                .get(LocalizerVM.class);
        setupList();
        setupHeader();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- OBSERVE --------------------------------------------

    private void observeViewModel() {
        viewModel.getModuleScanResults().observe(getViewLifecycleOwner(), items -> {
            adapter.setItems(items, viewModel.getModuleScanIncludedMacs());
            boolean hasResults = items != null && !items.isEmpty();
            binding.recyclerModule.setVisibility(hasResults ? View.VISIBLE : View.GONE);
            binding.txtEmptyModule.setVisibility(hasResults ? View.GONE : View.VISIBLE);
            binding.btnSaveModule.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        });

        viewModel.getModuleScanStatus().observe(getViewLifecycleOwner(), text -> {
            binding.txtModuleStatus.setText(text);
            binding.txtEmptyModule.setText(text);
        });

        viewModel.getUploadStatus().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                toast(msg);
        });

        viewModel.getImportStatus().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                toast(msg);
        });

        viewModel.isModuleScanLoading().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading)) binding.btnSaveModule.setVisibility(View.GONE);
        });
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupList() {
        adapter = new LocalizerWifiAdapter();
        binding.recyclerModule.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerModule.setAdapter(adapter);
    }

    private void setupHeader() {
        binding.btnFetch.setOnClickListener(v ->
                viewModel.scanFromModule(ModulesStore.getInstance().getSelectedMid()));
        binding.btnSaveModule.setOnClickListener(v ->
                promptSaveWaypoint());
    }

    // --------------------------------------------------------
    // --- SAVE WAYPOINT DIALOG -------------------------------

    private void promptSaveWaypoint() {
        if (!viewModel.hasModuleScanResults()) return;
        int max = SettingsManager.getFingerprintMaxNetworks();
        String message = viewModel.getModuleScanCount() > max
                ? getString(R.string.dialog_save_waypoint_top_networks, max)
                : null;
        inputDialog(R.string.dialog_save_waypoint, message, R.string.hint_waypoint_name,
                Waypoint.NAME_MAX_LEN, name -> {
                    String upper = name.toUpperCase();
                    if (!Waypoint.isValidName(upper)) return;
                    viewModel.saveWaypointFromModule(upper);
                    ((LocalizerFragment) requireParentFragment()).showTab(
                            LocalizerFragment.TAB_STORAGE);
                });
    }
}