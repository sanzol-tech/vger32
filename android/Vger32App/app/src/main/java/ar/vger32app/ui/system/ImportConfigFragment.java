package ar.vger32app.ui.system;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.Map;

import ar.vger32app.R;
import ar.vger32app.config.QrConfigParser;
import ar.vger32app.databinding.FragmentImportConfigBinding;
import ar.vger32app.ui.BaseFragment;

public class ImportConfigFragment extends BaseFragment {

    private FragmentImportConfigBinding binding;

    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) handleScan(result.getContents());
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentImportConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnScan.setOnClickListener(v -> scanLauncher.launch(new ScanOptions()
                .setCaptureActivity(ScanActivity.class)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.import_config_scan_prompt))
                .setBeepEnabled(false)
                .setBarcodeImageEnabled(false)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void handleScan(String raw) {
        Map<String, String> changes = QrConfigParser.parse(raw);
        if (changes == null) { toast(R.string.import_config_invalid); return; }

        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.TextView tv = new android.widget.TextView(requireContext());
        tv.setText(QrConfigParser.summary(changes));
        tv.setPadding(dp16 * 2, dp16, dp16 * 2, dp16);
        tv.setSingleLine(false);
        tv.setHorizontallyScrolling(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(requireContext());
        sv.addView(tv);
        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(requireContext());
        hsv.addView(sv);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_config_confirm_title)
                .setView(hsv)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                    try {
                        QrConfigParser.apply(requireContext(), changes);
                        toast(R.string.import_config_success);
                    } catch (Exception e) {
                        toast(R.string.import_config_failed);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}