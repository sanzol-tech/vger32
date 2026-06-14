package ar.vger32app.ui.mqtt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;

import ar.vger32app.R;
import ar.vger32app.databinding.BottomSheetMqttBinding;
import ar.vger32app.databinding.FragmentModuleMqttBinding;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.MqttFeedVM;

/*
 * MQTT monitor panel. FAB opens a command bottom sheet.
 * Stacking guard prevents double-open on rapid taps.
 * Copy and clear buttons sit in the command sheet header alongside the selected module name.
 */

public class ModuleMqttFragment extends BaseFragment {

    private FragmentModuleMqttBinding binding;
    private MqttFeedAdapter adapter;
    private MqttFeedVM viewModel;
    private BottomSheetDialog activeSheet;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentModuleMqttBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MqttFeedVM.class);
        setupFeed();
        setupFab();
        observeViewModel();
        if (viewModel.size() == 0) viewModel.postSessionStarted();
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
        viewModel.getLatestEvent().observe(getViewLifecycleOwner(), event -> {
            adapter.addEvent(event);
            binding.recyclerFeed.scrollToPosition(adapter.getItemCount() - 1);
        });
    }

    private void setupFeed() {
        adapter = new MqttFeedAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        binding.recyclerFeed.setLayoutManager(lm);
        binding.recyclerFeed.setAdapter(adapter);
        for (MqttFeedEvent event : viewModel.getEvents()) adapter.addEvent(event);
        if (adapter.getItemCount() > 0)
            binding.recyclerFeed.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void setupFab() {
        binding.fabCommands.setOnClickListener(v -> showCommandSheet());
    }

    // --------------------------------------------------------
    // --- FEED ACTIONS ---------------------------------------

    private void copyFeedToClipboard() {
        String text = viewModel.getFeedAsText();
        if (text.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label_mqtt_feed), text));
        toast(R.string.copied_to_clipboard);
    }

    private void clearFeed() {
        adapter.clear();
        viewModel.clearFeed();
    }

    // --------------------------------------------------------
    // --- COMMAND SHEET --------------------------------------

    private void showCommandSheet() {
        if (activeSheet != null && activeSheet.isShowing()) return;

        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        BottomSheetMqttBinding sv = BottomSheetMqttBinding.inflate(
                LayoutInflater.from(requireContext()));
        sheet.setContentView(sv.getRoot());
        activeSheet = sheet;

        String mid = viewModel.getSelectedMid();
        sv.txtSelectedModule.setText(mid != null ? mid : getString(R.string.mqtt_all_modules));

        boolean hasMid = Boolean.TRUE.equals(viewModel.hasSelectedModule().getValue());
        sv.btnCmdForceAp.setEnabled(hasMid);
        sv.btnCmdPublishNow.setEnabled(hasMid);
        sv.btnCmdSleep.setEnabled(hasMid);
        sv.btnCmdReboot.setEnabled(hasMid);

        sv.btnCopyFeed.setOnClickListener(v -> {
            sheet.dismiss();
            copyFeedToClipboard();
        });
        sv.btnClearFeed.setOnClickListener(v -> {
            sheet.dismiss();
            confirm(getString(R.string.dialog_clear_feed_message),
                    R.string.action_clear_feed, this::clearFeed);
        });

        sv.btnCmdPing.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.ping();
        });
        sv.btnCmdForceAp.setOnClickListener(v -> {
            sheet.dismiss();
            confirmForceAp();
        });
        sv.btnCmdPublishNow.setOnClickListener(v -> {
            sheet.dismiss();
            viewModel.sendCommand("publish_now", "");
        });
        sv.btnCmdSleep.setOnClickListener(v -> {
            sheet.dismiss();
            promptSleep();
        });
        sv.btnCmdReboot.setOnClickListener(v -> {
            sheet.dismiss();
            confirmReboot();
        });

        sv.btnCmdSend.setOnClickListener(v -> {
            String message = sv.editMsg.getText() != null
                    ? sv.editMsg.getText().toString().trim() : "";
            if (!message.isEmpty()) {
                viewModel.sendMessage(message);
                sheet.dismiss();
            }
        });

        sv.editMsg.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sv.btnCmdSend.performClick();
                return true;
            }
            return false;
        });

        sheet.show();
    }

    // --------------------------------------------------------
    // --- DESTRUCTIVE COMMANDS -------------------------------

    private void confirmReboot() {
        String mid = viewModel.getSelectedMid();
        if (mid == null) return;
        confirm(R.string.cmd_reboot,
                getString(R.string.dialog_mqtt_reboot_message, mid),
                R.string.cmd_reboot,
                () -> viewModel.sendCommand("reboot", ""));
    }

    private void confirmForceAp() {
        String mid = viewModel.getSelectedMid();
        if (mid == null) return;
        confirm(R.string.cmd_force_ap,
                getString(R.string.dialog_force_ap_message, mid),
                R.string.cmd_force_ap,
                () -> viewModel.sendCommand("force_ap", ""));
    }

    private void promptSleep() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.layout_dialog_sleep, null);
        EditText editValue = view.findViewById(R.id.edit_sleep_value);
        MaterialButtonToggleGroup toggleUnit = view.findViewById(R.id.toggle_sleep_unit);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_mqtt_sleep_title)
                .setView(view)
                .setPositiveButton(R.string.btn_send, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (validateSleepInput(editValue, toggleUnit)) {
                String raw = editValue.getText().toString().trim();
                int value = Integer.parseInt(raw);
                int checkedId = toggleUnit.getCheckedButtonId();

                int seconds;
                if (checkedId == R.id.btn_unit_h) seconds = value * 3600;
                else if (checkedId == R.id.btn_unit_m) seconds = value * 60;
                else seconds = value;

                viewModel.sendCommand("sleep", String.valueOf(seconds));

                dialog.dismiss();
            }
        });
    }

    private boolean validateSleepInput(EditText editValue, MaterialButtonToggleGroup toggleUnit) {
        String raw = editValue.getText() != null ? editValue.getText().toString().trim() : "";

        if (raw.isEmpty() || Integer.parseInt(raw) == 0) {
            editValue.setError(getString(R.string.status_invalid_value));
            editValue.requestFocus();
            return false;
        }

        if (toggleUnit.getCheckedButtonId() == View.NO_ID) {
            toast(R.string.status_no_module_selected);
            return false;
        }

        return true;
    }
}