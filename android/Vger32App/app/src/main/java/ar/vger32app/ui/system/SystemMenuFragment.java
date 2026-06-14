package ar.vger32app.ui.system;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;

import ar.vger32app.R;
import ar.vger32app.databinding.FragmentSystemMenuBinding;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.auth.UnlockFragment;

/*
 * Entry point for system-level actions: preferences, log, PIN change, config import, and about.
 */

public class SystemMenuFragment extends BaseFragment {

    private FragmentSystemMenuBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSystemMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnPreferences.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_system_menu_to_preferences));

        binding.btnLog.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_system_menu_to_log));

        binding.btnChangePin.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt(UnlockFragment.ARG_ACTION_MODE, UnlockFragment.PARAM_ACTION_CHGCODE);
            args.putInt(UnlockFragment.ARG_DESTINATION_ID, R.id.systemMenuFragment);
            Navigation.findNavController(v).navigate(R.id.action_system_menu_to_unlock, args);
        });

        binding.btnImportConfig.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_system_menu_to_import_config));

        binding.btnAbout.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_system_menu_to_about));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}