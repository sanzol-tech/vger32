package ar.vger32app.ui.localizer;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import ar.vger32app.R;
import ar.vger32app.config.AppConfig;
import ar.vger32app.databinding.BottomSheetLocalizerStorageBinding;
import ar.vger32app.databinding.BottomSheetLocalizerStorageSelectBinding;
import ar.vger32app.databinding.FragmentLocalizerStorageBinding;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.LocalizerVM;

/*
 * Waypoints storage tab. Lists saved WiFi fingerprints.
 * FAB sheet: Import from module, Select all, Delete, Export, Upload, Cancel.
 */

public class LocalizerStorageFragment extends BaseFragment {

    private FragmentLocalizerStorageBinding binding;
    private LocalizerWaypointAdapter adapter;
    private LocalizerVM viewModel;
    private BottomSheetDialog activeSheet;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLocalizerStorageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment())
                .get(LocalizerVM.class);
        setupList();
        setupFab();
        observeViewModel();
        viewModel.loadWaypoints();
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

    // --------------------------------------------------------
    // --- OBSERVE --------------------------------------------

    private void observeViewModel() {
        viewModel.getWaypoints().observe(getViewLifecycleOwner(), all -> {
            adapter.setWaypoints(all);
            binding.txtStorageHeader.setText(
                    getString(R.string.localizer_header_waypoints_count, all.size()));
            binding.recyclerStorage.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
            binding.txtEmptyStorage.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getUploadStatus().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                toastLong(msg);
        });

        viewModel.getImportStatus().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                toastLong(msg);
        });
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupList() {
        adapter = new LocalizerWaypointAdapter();
        binding.recyclerStorage.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerStorage.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabStorage.setOnClickListener(v -> showStorageSheet());
        binding.btnCancelSelect.setOnClickListener(v -> exitSelectionMode());
    }

    // --------------------------------------------------------
    // --- BOTTOM SHEET ---------------------------------------

    private void showStorageSheet() {
        if (!isAdded() || getContext() == null) return;
        if (activeSheet != null && activeSheet.isShowing()) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());

        if (adapter.isSelectionMode()) {
            BottomSheetLocalizerStorageSelectBinding sv =
                    BottomSheetLocalizerStorageSelectBinding.inflate(
                            LayoutInflater.from(requireContext()));

            // Select all is always available; the others require at least one item selected.
            boolean hasSelection = !adapter.getSelectedIds().isEmpty();
            setMenuItemEnabled(sv.actionLocalDownload, hasSelection);
            setMenuItemEnabled(sv.actionUpload, hasSelection);
            setMenuItemEnabled(sv.actionDelete, hasSelection);

            sv.actionSelectAll.setOnClickListener(v -> {
                sheet.dismiss();
                adapter.selectAll();
            });
            sv.actionLocalDownload.setOnClickListener(v -> {
                sheet.dismiss();
                localDownload();
            });
            sv.actionUpload.setOnClickListener(v -> {
                sheet.dismiss();
                confirmUpload();
            });
            sv.actionDelete.setOnClickListener(v -> {
                sheet.dismiss();
                confirmDelete();
            });
            sheet.setContentView(sv.getRoot());
        } else {
            BottomSheetLocalizerStorageBinding sv =
                    BottomSheetLocalizerStorageBinding.inflate(
                            LayoutInflater.from(requireContext()));
            sv.actionDownload.setOnClickListener(v -> {
                sheet.dismiss();
                confirmDownload();
            });
            sv.actionSelectWaypoints.setOnClickListener(v -> {
                sheet.dismiss();
                enterSelectionMode();
            });
            sheet.setContentView(sv.getRoot());
        }

        activeSheet = sheet;
        sheet.show();
    }

    private static void setMenuItemEnabled(View item, boolean enabled) {
        item.setEnabled(enabled);
        item.setAlpha(enabled ? 1.0f : 0.38f);
    }

    // --------------------------------------------------------
    // --- SELECTION MODE -------------------------------------

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        binding.btnCancelSelect.setVisibility(View.VISIBLE);
        binding.txtStorageHeader.setText(getString(R.string.localizer_header_select_waypoints));
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        binding.btnCancelSelect.setVisibility(View.GONE);
        viewModel.loadWaypoints();
    }

    // --------------------------------------------------------
    // --- ACTIONS --------------------------------------------

    private void confirmDelete() {
        Set<String> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) return;
        confirm(R.string.dialog_delete_waypoints,
                getString(R.string.dialog_delete_waypoints_message, ids.size()),
                R.string.btn_delete, () -> {
                    viewModel.deleteWaypoints(ids);
                    exitSelectionMode();
                });
    }

    private void confirmDownload() {
        confirm(R.string.dialog_download_from_module_title,
                R.string.dialog_download_from_module_message,
                R.string.btn_import, () -> viewModel.importFromModule());
    }

    private void localDownload() {
        Set<String> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) return;
        String data = viewModel.exportWaypoints(ids);
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, AppConfig.WIFI_FINGERPRINTS_FILENAME);
            values.put(MediaStore.Downloads.MIME_TYPE, AppConfig.WIFI_FINGERPRINTS_MIME);
            Uri uri = requireContext().getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException(getString(R.string.export_error_no_file));
            try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException(getString(R.string.export_error_no_stream));
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }
            toast(R.string.status_download_ok);
        } catch (IOException e) {
            toastLong(e.getMessage());
        }
    }

    private void confirmUpload() {
        Set<String> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) return;
        confirm(R.string.dialog_upload_to_module_title,
                getString(R.string.dialog_upload_to_module_message, ids.size()),
                R.string.btn_upload, this::uploadSelected);
    }

    private void uploadSelected() {
        Set<String> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) {
            toast(R.string.status_no_data);
            return;
        }
        viewModel.uploadWaypoints(ids);
    }
}