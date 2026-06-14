package ar.vger32app.ui.system;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import ar.vger32app.R;

/*
 * Custom scan activity: portrait orientation + square viewfinder sized
 * for QR codes. Used by ImportConfigFragment via ScanOptions.setCaptureActivity().
 */
public class ScanActivity extends AppCompatActivity {

    private DecoratedBarcodeView barcodeView;
    private CaptureManager captureManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        barcodeView = findViewById(R.id.decoratedBarcodeView);
        barcodeView.setStatusText("");

        int size = (int) (Math.min(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels) * 0.65f);
        barcodeView.getBarcodeView().setFramingRectSize(new com.journeyapps.barcodescanner.Size(size, size));

        captureManager = new CaptureManager(this, barcodeView);
        captureManager.initializeFromIntent(getIntent(), savedInstanceState);
        captureManager.decode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        captureManager.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        captureManager.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        captureManager.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        captureManager.onSaveInstanceState(outState);
    }
}