package ar.vger32app.ui.mqtt;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ar.vger32app.R;

/*
 * RecyclerView adapter for the MQTT monitor feed.
 *
 * Layout per row:
 * [HH:mm]  [badge]  [MID] <- mid line shown only when event has a module source
 * payload text  <- always shown
 *
 * For PUBLISH/SYS (no mid), the content sits on the same visual line as the badge.
 */

public class MqttFeedAdapter extends RecyclerView.Adapter<MqttFeedAdapter.ViewHolder> {

    private static final int MAX_EVENTS = 200;

    private final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final List<MqttFeedEvent> events = new ArrayList<>();

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    public void addEvent(MqttFeedEvent event) {
        if (events.size() >= MAX_EVENTS) {
            events.remove(0);
            notifyItemRemoved(0);
        }
        events.add(event);
        notifyItemInserted(events.size() - 1);
    }

    public void clear() {
        int size = events.size();
        events.clear();
        notifyItemRangeRemoved(0, size);
    }

    // --------------------------------------------------------
    // --- ADAPTER --------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mqtt_feed_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MqttFeedEvent event = events.get(position);
        Context ctx = holder.itemView.getContext();
        MqttEventType type = event.getType();

        holder.txtTime.setText(TIME_FMT.format(new Date(event.getTimestamp())));
        holder.txtBadge.setText(type.badge);
        holder.txtBadge.setTextColor(ContextCompat.getColor(ctx, type.colorRes));
        holder.txtContent.setText(event.getText());

        String mid = event.getModuleId();
        if (mid != null) {
            holder.txtMid.setVisibility(View.VISIBLE);
            holder.txtMid.setText(mid);
            holder.txtMid.setTextColor(ContextCompat.getColor(ctx, R.color.brand200));
        } else {
            holder.txtMid.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    // --------------------------------------------------------
    // --- VIEW HOLDER ----------------------------------------

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView txtTime;
        final TextView txtBadge;
        final TextView txtMid;
        final TextView txtContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTime = itemView.findViewById(R.id.txt_time);
            txtBadge = itemView.findViewById(R.id.txt_badge);
            txtMid = itemView.findViewById(R.id.txt_mid);
            txtContent = itemView.findViewById(R.id.txt_content);
        }
    }
}