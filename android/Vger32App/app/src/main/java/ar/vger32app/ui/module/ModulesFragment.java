package ar.vger32app.ui.module;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import ar.vger32app.R;
import ar.vger32app.databinding.BottomSheetModuleOptionsBinding;
import ar.vger32app.databinding.BottomSheetModulesMenuBinding;
import ar.vger32app.databinding.FragmentModulesBinding;
import ar.vger32app.module.Module;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.ModulesVM;

/*
 * Discovery modes: LAN scan, mDNS, UDP, manual IP, MQTT ping, AP connect.
 * FAB — opens discover sheet when idle; becomes stop when any discoverer is running.
 * Long press on module → per-module options sheet (API key + scrambler key + delete).
 */

public class ModulesFragment extends BaseFragment {

    private FragmentModulesBinding binding;
    private ModulesAdapter adapter;
    private ModulesVM viewModel;
    private BottomSheetDialog activeSheet;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentModulesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ModulesVM.class);
        setupResultsList();
        setupFab();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
        binding = null;
    }

    private void observeViewModel() {
        viewModel.getModules().observe(getViewLifecycleOwner(),
                modules -> adapter.setModules(modules));
        viewModel.getSelectedMid().observe(getViewLifecycleOwner(),
                mid -> adapter.setSelectedMid(mid));
        viewModel.getStatus().observe(getViewLifecycleOwner(),
                text -> binding.txtScanStatus.setText(text));
        viewModel.isDiscovering().observe(getViewLifecycleOwner(), discovering -> {
            boolean active = Boolean.TRUE.equals(discovering);
            binding.fabModules.setImageResource(active ? R.drawable.ic_close : R.drawable.ic_menu);
            binding.fabModules.setBackgroundTintList(active
                    ? ContextCompat.getColorStateList(requireContext(), R.color.RED)
                    : ContextCompat.getColorStateList(requireContext(), R.color.brand300));
        });
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupResultsList() {
        adapter = new ModulesAdapter();
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerResults.setAdapter(adapter);
        adapter.setOnModuleTapListener(module -> viewModel.selectModule(module));
        adapter.setOnModuleLongPressListener(this::showKeysSheet);
    }

    private void setupFab() {
        binding.fabModules.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.isDiscovering().getValue())) {
                viewModel.stopAllDiscovery();
            } else {
                showDiscoverSheet();
            }
        });
    }

    // --------------------------------------------------------
    // --- DISCOVER SHEET -------------------------------------

    private void showDiscoverSheet() {
        if (activeSheet != null && activeSheet.isShowing()) return;

        viewModel.refreshApNetwork();

        BottomSheetModulesMenuBinding discoverBinding = BottomSheetModulesMenuBinding.inflate(
                LayoutInflater.from(requireContext()));
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        sheet.setContentView(discoverBinding.getRoot());
        activeSheet = sheet;

        boolean apDetected = Boolean.TRUE.equals(viewModel.isApNetworkDetected().getValue());
        float apAlpha = apDetected ? 1.0f : 0.35f;
        float otherAlpha = apDetected ? 0.35f : 1.0f;

        discoverBinding.actionConnectAp.setEnabled(apDetected);
        discoverBinding.actionConnectAp.setAlpha(apAlpha);
        discoverBinding.actionMqttPing.setEnabled(!apDetected);
        discoverBinding.actionMqttPing.setAlpha(otherAlpha);
        discoverBinding.actionMdns.setEnabled(!apDetected);
        discoverBinding.actionMdns.setAlpha(otherAlpha);
        discoverBinding.actionUdp.setEnabled(!apDetected);
        discoverBinding.actionUdp.setAlpha(otherAlpha);
        discoverBinding.actionIp.setEnabled(!apDetected);
        discoverBinding.actionIp.setAlpha(otherAlpha);
        discoverBinding.actionScan.setEnabled(!apDetected);
        discoverBinding.actionScan.setAlpha(otherAlpha);

        discoverBinding.actionMqttPing.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.sendMqttPing();
        });
        discoverBinding.actionMdns.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.startMdnsDiscovery();
        });
        discoverBinding.actionUdp.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.startUdpDiscovery();
        });
        discoverBinding.actionIp.setOnClickListener(v -> {
            sheet.dismiss();
            showIpDialog();
        });
        discoverBinding.actionConnectAp.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.connectToIp("192.168.4.1");
        });
        discoverBinding.actionScan.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.startLanScan();
        });
        discoverBinding.actionForgetAll.setOnClickListener(v -> {
            sheet.dismiss();
            confirmDeleteAll();
        });

        sheet.show();
    }

    // --------------------------------------------------------
    // --- PER-MODULE OPTIONS SHEET ---------------------------

    private void showKeysSheet(Module module) {
        if (module == null) return;
        if (activeSheet != null && activeSheet.isShowing()) return;

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        BottomSheetModuleOptionsBinding keysBinding = BottomSheetModuleOptionsBinding.inflate(
                LayoutInflater.from(requireContext()));
        sheet.setContentView(keysBinding.getRoot());
        activeSheet = sheet;

        String mid = module.getModuleId();
        keysBinding.txtModuleId.setText(mid);

        keysBinding.editApiKey.setHint(viewModel.hasApiKey(mid)
                ? getString(R.string.module_keys_hint_set)
                : getString(R.string.module_keys_hint_not_set));

        keysBinding.editScramblerKey.setHint(viewModel.hasScramblerKey(mid)
                ? getString(R.string.module_keys_hint_set)
                : getString(R.string.module_keys_hint_not_set));

        keysBinding.btnKeysSave.setOnClickListener(v -> {
            viewModel.saveKeys(mid,
                    keysBinding.editApiKey.getText().toString().trim(),
                    keysBinding.editScramblerKey.getText().toString().trim());
            sheet.dismiss();
            toast(R.string.module_keys_saved);
        });

        keysBinding.btnKeysClear.setOnClickListener(v -> {
            viewModel.clearKeys(mid);
            sheet.dismiss();
            toast(R.string.module_keys_cleared);
        });

        keysBinding.btnDeleteModule.setOnClickListener(v -> {
            sheet.dismiss();
            confirmDeleteModule(module);
        });

        sheet.show();
    }

    // --------------------------------------------------------
    // --- DIALOGS --------------------------------------------

    private void showIpDialog() {
        inputDialog(R.string.dialog_connect_by_ip, R.string.hint_ip_address, 39, ip -> {
            if (!ip.isEmpty()) viewModel.connectToIp(ip);
        });
    }

    private void confirmDeleteModule(Module module) {
        confirm(getString(R.string.dialog_forget_module_message, module.getModuleId()),
                R.string.btn_delete, () -> viewModel.deleteModule(module.getModuleId()));
    }

    private void confirmDeleteAll() {
        confirm(getString(R.string.dialog_forget_all_modules_message),
                R.string.btn_delete, () -> viewModel.deleteAll());
    }
}