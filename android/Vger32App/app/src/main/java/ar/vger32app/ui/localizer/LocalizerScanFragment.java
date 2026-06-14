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
import ar.vger32app.databinding.FragmentLocalizerScanBinding;
import ar.vger32app.localizer.Waypoint;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.LocalizerVM;

/*
 * Phone WiFi scan tab. Displays live scan results and allows saving
 * the current scan as a named waypoint.
 */

public class LocalizerScanFragment extends BaseFragment {

    private FragmentLocalizerScanBinding binding;
    private LocalizerWifiAdapter adapter;
    private LocalizerVM viewModel;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLocalizerScanBinding.inflate(inflater, container, false);
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
        viewModel.scanPhone();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- OBSERVE --------------------------------------------

    private void observeViewModel() {
        viewModel.getPhoneScanResults().observe(getViewLifecycleOwner(), results -> {
            adapter.setResults(results, viewModel.getPhoneScanIncludedBssids());
            boolean hasResults = results != null && !results.isEmpty();
            binding.recyclerScan.setVisibility(hasResults ? View.VISIBLE : View.GONE);
            binding.txtEmptyScan.setVisibility(hasResults ? View.GONE : View.VISIBLE);
            binding.btnSave.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        });

        viewModel.getPhoneScanStatus().observe(getViewLifecycleOwner(),
                text -> binding.txtScanStatus.setText(text));

        viewModel.isPhoneScanLoading().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading)) binding.btnSave.setVisibility(View.GONE);
        });
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupList() {
        adapter = new LocalizerWifiAdapter();
        binding.recyclerScan.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerScan.setAdapter(adapter);
    }

    private void setupHeader() {
        binding.btnRefresh.setOnClickListener(v -> viewModel.scanPhone());
        binding.btnSave.setOnClickListener(v -> promptSaveWaypoint());
    }

    // --------------------------------------------------------
    // --- SAVE WAYPOINT DIALOG -------------------------------

    private void promptSaveWaypoint() {
        if (!viewModel.hasPhoneScanResults()) return;
        int max = SettingsManager.getFingerprintMaxNetworks();
        String message = viewModel.getPhoneScanCount() > max
                ? getString(R.string.dialog_save_waypoint_top_networks, max)
                : null;
        inputDialog(R.string.dialog_save_waypoint, message, R.string.hint_waypoint_name,
                Waypoint.NAME_MAX_LEN, name -> {
                    String upper = name.toUpperCase();
                    if (!Waypoint.isValidName(upper)) return;
                    viewModel.saveWaypointFromScan(upper);
                    ((LocalizerFragment) requireParentFragment()).showTab(
                            LocalizerFragment.TAB_STORAGE);
                });
    }
}