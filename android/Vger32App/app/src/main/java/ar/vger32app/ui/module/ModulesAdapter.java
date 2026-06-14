package ar.vger32app.ui.module;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ar.vger32app.R;
import ar.vger32app.module.DiscoverySource;
import ar.vger32app.module.Module;
import ar.vger32app.utils.DateTimeUtils;

/*
 * RecyclerView adapter for the module list in ModulesFragment.
 * Supports null as the first item to represent "no module selected".
 *
 * Tap       → OnModuleTapListener       (select module)
 * Long press → OnModuleLongPressListener (open per-module keys bottom sheet)
 *
 * Modules reachable via the ESP32 default AP address (192.168.4.1) display
 * their name in PURPLE with an "(AP)" suffix to signal the connection context.
 */

public class ModulesAdapter extends RecyclerView.Adapter<ModulesAdapter.ViewHolder> {

    public interface OnModuleTapListener {
        void onModuleTap(Module module);
    }

    public interface OnModuleLongPressListener {
        void onModuleLongPress(Module module);
    }

    private static final String DEFAULT_AP_IP = "192.168.4.1";

    private List<Module> modules = new ArrayList<>();
    private String selectedMid = null;
    private OnModuleTapListener tapListener;
    private OnModuleLongPressListener longPressListener;

    public void setModules(List<Module> modules) {
        this.modules = new ArrayList<>(modules);
        notifyDataSetChanged();
    }

    public void setSelectedMid(String mid) {
        String old = this.selectedMid;
        this.selectedMid = mid;
        int oldPos = indexOfMid(old);
        int newPos = indexOfMid(mid);
        if (oldPos >= 0) notifyItemChanged(oldPos);
        if (newPos >= 0 && newPos != oldPos) notifyItemChanged(newPos);
    }

    private int indexOfMid(String mid) {
        if (mid == null) return -1;
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            if (m != null && mid.equals(m.getModuleId())) return i;
        }
        return -1;
    }

    public void setOnModuleTapListener(OnModuleTapListener l) {
        this.tapListener = l;
    }

    public void setOnModuleLongPressListener(OnModuleLongPressListener l) {
        this.longPressListener = l;
    }

    public Module getModule(int position) {
        return modules.get(position);
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_module_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Module module = modules.get(position);
        Context ctx = holder.itemView.getContext();

        if (module == null) {
            holder.txtMid.setText(ctx.getString(R.string.module_none));
            holder.txtMid.setTextColor(ContextCompat.getColor(ctx, R.color.color900));
            holder.txtPid.setVisibility(View.GONE);
            holder.txtIp.setVisibility(View.GONE);
            holder.txtLastSeen.setVisibility(View.GONE);
            holder.txtDiscoverySource.setVisibility(View.GONE);
            holder.txtChip.setVisibility(View.GONE);
            holder.txtChipSep.setVisibility(View.GONE);
            holder.viewStatus.setBackgroundTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.color400)));
            holder.itemView.setAlpha(0.5f);
            holder.itemView.setBackgroundResource(R.drawable.bg_module_item);
            holder.itemView.setOnClickListener(v -> {
                if (tapListener != null) tapListener.onModuleTap(null);
            });
            holder.itemView.setOnLongClickListener(null);
            return;
        }

        holder.txtPid.setVisibility(View.VISIBLE);
        holder.txtIp.setVisibility(View.VISIBLE);
        holder.txtLastSeen.setVisibility(View.VISIBLE);
        holder.txtDiscoverySource.setVisibility(View.VISIBLE);
        boolean online = module.isOnline();
        boolean isSelected = module.getModuleId().equals(selectedMid);
        boolean isDefaultAp = DEFAULT_AP_IP.equals(module.getIp());

        if (isDefaultAp) {
            holder.txtMid.setText(module.getModuleId() + " (AP)");
            holder.txtMid.setTextColor(ContextCompat.getColor(ctx, R.color.color900));
        } else {
            holder.txtMid.setText(module.getModuleId());
            holder.txtMid.setTextColor(ContextCompat.getColor(ctx, R.color.color900));
        }

        holder.txtPid.setText(module.getProfileId() != null ? module.getProfileId() : "—");
        holder.txtIp.setText(module.getIp() != null ? module.getIp() : "—");
        holder.txtLastSeen.setText(DateTimeUtils.formatElapsed(module.getLastSeenAt()));
        holder.txtDiscoverySource.setText(formatSource(ctx, module.getLastDiscoverySource()));

        String chip = module.getChip();
        if (chip != null && !chip.isEmpty()) {
            holder.txtChip.setText(chip);
            holder.txtChip.setVisibility(View.VISIBLE);
            holder.txtChipSep.setVisibility(View.VISIBLE);
        } else {
            holder.txtChip.setVisibility(View.GONE);
            holder.txtChipSep.setVisibility(View.GONE);
        }

        holder.viewStatus.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(ctx, online ? R.color.BLUE : R.color.color400)));
        holder.itemView.setAlpha(online ? 1.0f : 0.8f);
        holder.itemView.setBackgroundResource(
                isSelected ? R.drawable.bg_module_item_selected : R.drawable.bg_module_item);

        holder.itemView.setOnClickListener(v -> {
            if (tapListener != null) tapListener.onModuleTap(module);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longPressListener != null) {
                longPressListener.onModuleLongPress(module);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return modules.size();
    }

    // --------------------------------------------------------
    // --- FORMATTERS -----------------------------------------

    private static String formatSource(Context ctx, DiscoverySource source) {
        if (source == null) return "";
        switch (source) {
            case MQTT_PONG:
                return ctx.getString(R.string.source_mqtt_pong);
            case MDNS:
                return ctx.getString(R.string.source_mdns);
            case UDP_DISCOVERY:
                return ctx.getString(R.string.source_udp_discovery);
            case LAN_SCAN:
                return ctx.getString(R.string.source_lan_scan);
            case MANUAL_IP:
                return ctx.getString(R.string.source_manual_ip);
            default:
                return "";
        }
    }

    // --------------------------------------------------------
    // --- VIEW HOLDER ----------------------------------------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewStatus;
        final TextView txtMid;
        final TextView txtChip;
        final TextView txtChipSep;
        final TextView txtPid;
        final TextView txtIp;
        final TextView txtLastSeen;
        final TextView txtDiscoverySource;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatus = itemView.findViewById(R.id.view_status);
            txtMid = itemView.findViewById(R.id.txt_mid);
            txtChip = itemView.findViewById(R.id.txt_chip);
            txtChipSep = itemView.findViewById(R.id.txt_chip_sep);
            txtPid = itemView.findViewById(R.id.txt_pid);
            txtIp = itemView.findViewById(R.id.txt_ip);
            txtLastSeen = itemView.findViewById(R.id.txt_lastseen);
            txtDiscoverySource = itemView.findViewById(R.id.txt_discovery_source);
        }
    }
}