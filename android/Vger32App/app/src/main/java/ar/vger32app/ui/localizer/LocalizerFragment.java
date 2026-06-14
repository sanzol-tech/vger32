package ar.vger32app.ui.localizer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import ar.vger32app.R;
import ar.vger32app.databinding.FragmentLocalizerBinding;
import ar.vger32app.ui.BaseFragment;
import ar.vger32app.ui.viewmodel.LocalizerVM;

/*
 * LocalizerFragment — host
 * Owns the ViewModel and the tab bar. Swaps three subfragments
 * into localizer_container on tab selection:
 * TAB_SCAN    → LocalizerScanFragment
 * TAB_MODULE  → LocalizerModuleFragment
 * TAB_STORAGE → LocalizerStorageFragment
 * The header_module_selector is managed here because it spans all tabs
 * except SCAN.
 * Subfragments access the shared ViewModel via:
 * new ViewModelProvider(requireParentFragment()).get(LocalizerVM.class)
 */

public class LocalizerFragment extends BaseFragment {

    static final int TAB_SCAN = 0;
    static final int TAB_MODULE = 1;
    static final int TAB_STORAGE = 2;

    private FragmentLocalizerBinding binding;
    private int currentTab = -1;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLocalizerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Instantiate here so subfragments can share it via requireParentFragment().
        new ViewModelProvider(this).get(LocalizerVM.class);
        setupTabs();
        if (savedInstanceState == null) showTab(TAB_SCAN);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --------------------------------------------------------
    // --- TAB SWITCHING  (package-visible for subfragments) ---

    void showTab(int tab) {
        if (tab == currentTab) return;
        currentTab = tab;

        // Module selector visible on MODULE and STORAGE tabs
        binding.headerModuleSelector.getRoot().setVisibility(
                tab == TAB_SCAN ? View.GONE : View.VISIBLE);

        // Update tab text colors
        int active = requireContext().getColor(R.color.color900);
        int inactive = requireContext().getColor(R.color.color400);
        binding.tabScan.setTextColor(tab == TAB_SCAN ? active : inactive);
        binding.tabModule.setTextColor(tab == TAB_MODULE ? active : inactive);
        binding.tabStorage.setTextColor(tab == TAB_STORAGE ? active : inactive);

        // Swap subfragment
        Fragment target = getChildFragmentManager()
                .findFragmentByTag(tagFor(tab));
        if (target == null) target = createTab(tab);

        FragmentTransaction tx = getChildFragmentManager().beginTransaction();
        tx.replace(R.id.localizer_container, target, tagFor(tab));
        tx.commit();
    }

    // --------------------------------------------------------
    // --- INTERNAL -------------------------------------------

    private void setupTabs() {
        binding.tabScan.setOnClickListener(v -> showTab(TAB_SCAN));
        binding.tabModule.setOnClickListener(v -> showTab(TAB_MODULE));
        binding.tabStorage.setOnClickListener(v -> showTab(TAB_STORAGE));
    }

    private Fragment createTab(int tab) {
        switch (tab) {
            case TAB_MODULE:
                return new LocalizerModuleFragment();
            case TAB_STORAGE:
                return new LocalizerStorageFragment();
            default:
                return new LocalizerScanFragment();
        }
    }

    private String tagFor(int tab) {
        switch (tab) {
            case TAB_MODULE:
                return "tab_module";
            case TAB_STORAGE:
                return "tab_storage";
            default:
                return "tab_scan";
        }
    }
}