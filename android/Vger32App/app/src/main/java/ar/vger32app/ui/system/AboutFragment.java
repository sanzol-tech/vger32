package ar.vger32app.ui.system;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import ar.vger32app.BuildConfig;
import ar.vger32app.R;
import ar.vger32app.databinding.FragmentAboutBinding;
import ar.vger32app.ui.BaseFragment;

/*
 * Displays app identity: logo, name, tagline, and build version.
 */

public class AboutFragment extends BaseFragment {

    private FragmentAboutBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.tvVersion.setText(getString(R.string.about_version_fmt, BuildConfig.VERSION_NAME));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}