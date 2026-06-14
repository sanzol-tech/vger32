package ar.vger32app.ui.localizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ar.vger32app.R;
import ar.vger32app.localizer.Waypoint;

/*
 * RecyclerView adapter for the fingerprints list in FingerprintsFragment.
 * Supports two modes:
 *   Normal    — tapping a row does nothing (list is informational)
 *   Selection — checkboxes visible, tapping toggles selection
 *
 * Selection state is tracked by a Set of selected waypoint IDs.
 * The fragment reads selected IDs via getSelectedIds() to build export/delete actions.
 */

public class LocalizerWaypointAdapter extends RecyclerView.Adapter<LocalizerWaypointAdapter.ViewHolder> {

    private final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault());

    private List<Waypoint> waypoints = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private boolean selectionMode = false;

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    public void setWaypoints(List<Waypoint> waypoints) {
        this.waypoints = new ArrayList<>(waypoints);
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean enabled) {
        selectionMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public boolean hasSelection() {
        return !selectedIds.isEmpty();
    }

    public void selectAll() {
        for (Waypoint wp : waypoints) selectedIds.add(wp.getId());
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    // --------------------------------------------------------
    // --- ADAPTER --------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waypoint, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Waypoint wp = waypoints.get(position);
        boolean selected = selectedIds.contains(wp.getId());

        holder.txtName.setText(wp.getName());
        String src = wp.getSource();
        holder.txtSource.setText(src.isEmpty() ? "" : " · " + src);
        holder.txtMeta.setText(
                wp.networkCount() + " networks · "
                        + DATE_FMT.format(new Date(wp.getTimestamp())));

        if (selectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            holder.checkbox.setChecked(selected);
            holder.itemView.setAlpha(selected ? 1.0f : 0.6f);
        } else {
            holder.checkbox.setVisibility(View.GONE);
            holder.itemView.setAlpha(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (!selectionMode) return;
            if (selectedIds.contains(wp.getId())) {
                selectedIds.remove(wp.getId());
            } else {
                selectedIds.add(wp.getId());
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return waypoints.size();
    }

    // --------------------------------------------------------
    // --- VIEW HOLDER ----------------------------------------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView txtName;
        final TextView txtMeta;
        final TextView txtSource;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkbox = itemView.findViewById(R.id.checkbox);
            txtName = itemView.findViewById(R.id.txt_name);
            txtMeta = itemView.findViewById(R.id.txt_meta);
            txtSource = itemView.findViewById(R.id.txt_source);
        }
    }
}



