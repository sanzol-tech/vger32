package ar.vger32app.ui.module;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ar.vger32app.R;
import ar.vger32app.module.Module;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.utils.DateTimeUtils;

/*
 * Custom view that replaces the Spinner for module selection.
 * Shows the selected module using item_module.xml layout.
 * Tapping opens a dialog with the full list of known modules.
 *
 * Self-registers to ModuleStore on window attach / detach.
 * No Fragment lifecycle required.
 */

public class ModulesSelectorView extends FrameLayout
        implements ModulesStore.OnModuleUpdateListener {

    private View selectedView;
    private TextView txtPlaceholder;

    public ModulesSelectorView(Context context) {
        super(context);
        init();
    }

    public ModulesSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ModulesSelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // --------------------------------------------------------
    // --- INIT -----------------------------------------------

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        txtPlaceholder = new TextView(getContext());
        txtPlaceholder.setText(getContext().getString(R.string.status_no_module_selected));
        int padHorizontal = getResources().getDimensionPixelSize(R.dimen.spacing_m);
        int padVertical = getResources().getDimensionPixelSize(R.dimen.spacing_m);
        txtPlaceholder.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);
        txtPlaceholder.setTextColor(ContextCompat.getColor(getContext(), R.color.color600));
        addView(txtPlaceholder);

        selectedView = inflater.inflate(R.layout.item_module, this, false);
        selectedView.setClickable(false);
        selectedView.setFocusable(false);
        addView(selectedView);

        try (TypedArray ta = getContext().obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackground})) {
            setBackground(ta.getDrawable(0));
        }

        setClickable(true);
        setFocusable(true);
        setOnClickListener(view -> showDialog());

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                ModulesStore.getInstance().addListener(ModulesSelectorView.this);
                refresh();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                ModulesStore.getInstance().removeListener(ModulesSelectorView.this);
            }
        });
    }

    // --------------------------------------------------------
    // --- OnModuleUpdateListener -----------------------------

    @Override
    public void onModuleUpdated() {
        post(this::refresh);
    }

    // --------------------------------------------------------
    // --- REFRESH --------------------------------------------

    private void refresh() {
        Module module = ModulesStore.getInstance().getSelectedModule();
        if (module == null) {
            txtPlaceholder.setVisibility(VISIBLE);
            selectedView.setVisibility(GONE);
        } else {
            txtPlaceholder.setVisibility(GONE);
            selectedView.setVisibility(VISIBLE);
            bindModule(selectedView, module);
        }
    }

    // --------------------------------------------------------
    // --- DIALOG ---------------------------------------------

    private void showDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_module_selector, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        View btnClose = dialogView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        RecyclerView recycler = dialogView.findViewById(R.id.recycler_modules);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Module> all = new ArrayList<>();
        all.add(null);
        all.addAll(ModulesStore.getInstance().getAll());

        ModulesAdapter adapter = new ModulesAdapter();
        adapter.setModules(all);
        adapter.setOnModuleTapListener(module -> {
            ModulesStore.getInstance().setSelectedMid(module != null ? module.getModuleId() : null);
            dialog.dismiss();
        });
        recycler.setAdapter(adapter);

        dialog.show();
    }

    // --------------------------------------------------------
    // --- BIND — used for the header display (item_module.xml) ---

    static void bindModule(View v, Module module) {
        Context ctx = v.getContext();

        TextView txtMid = v.findViewById(R.id.txt_mid);
        TextView txtChip = v.findViewById(R.id.txt_chip);
        TextView txtChipSep = v.findViewById(R.id.txt_chip_sep);
        TextView txtPid = v.findViewById(R.id.txt_pid);
        TextView txtPidSep = v.findViewById(R.id.txt_pid_sep);
        TextView txtIp = v.findViewById(R.id.txt_ip);
        TextView txtLastSeen = v.findViewById(R.id.txt_lastseen);
        View viewStatus = v.findViewById(R.id.view_status);

        txtMid.setText(module.getModuleId());
        txtIp.setText(module.getIp() != null ? module.getIp() : "");
        txtLastSeen.setText(DateTimeUtils.formatElapsed(module.getLastSeenAt()));

        String chip = module.getChip();
        String pid = module.getProfileId();
        boolean hasChip = chip != null && !chip.isEmpty();
        boolean hasPid = pid != null && !pid.isEmpty();

        if (hasChip) {
            txtChip.setText(chip);
            txtChip.setVisibility(VISIBLE);
            txtChipSep.setVisibility(VISIBLE);
        } else {
            txtChip.setVisibility(GONE);
            txtChipSep.setVisibility(GONE);
        }

        if (hasPid) {
            txtPid.setText(pid);
            txtPid.setVisibility(VISIBLE);
            if (txtPidSep != null) txtPidSep.setVisibility(hasChip ? VISIBLE : GONE);
        } else {
            txtPid.setVisibility(GONE);
            if (txtPidSep != null) txtPidSep.setVisibility(GONE);
        }

        int statusColor = module.isOnline()
                ? ContextCompat.getColor(ctx, R.color.BLUE)
                : ContextCompat.getColor(ctx, R.color.color400);
        viewStatus.setBackgroundTintList(ColorStateList.valueOf(statusColor));
        v.setAlpha(module.isOnline() ? 1.0f : 0.5f);
    }
}