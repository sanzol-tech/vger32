package ar.vger32app.ui.localizer;

import android.content.res.ColorStateList;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ar.vger32app.R;
import ar.vger32app.localizer.WifiNetwork;

/*
 * Displays a list of WiFi access points from a scan.
 * Accepts data from two sources:
 *   - setResults(List<ScanResult>, Set<String>)  — phone WiFi scan
 *   - setItems(List<WifiItem>, Set<String>)       — device API scan (/api/wifi-scan)
 *
 * Networks not in the includedMacs set are shown greyed out — they exist in the
 * scan but will not be saved to the waypoint fingerprint (band, signal, or count filter).
 *
 * Signal strength shown as colored dot + RSSI value for included networks:
 *   >= SIGNAL_STRONG_DBM  → green  (strong)
 *   >= SIGNAL_MEDIUM_DBM  → yellow (medium)
 *    < SIGNAL_MEDIUM_DBM  → red    (weak)
 */

public class LocalizerWifiAdapter extends RecyclerView.Adapter<LocalizerWifiAdapter.ViewHolder> {

    private static final int SIGNAL_STRONG_DBM = -55;
    private static final int SIGNAL_MEDIUM_DBM = -70;

    // --------------------------------------------------------
    // --- UNIFIED ITEM TYPE ----------------------------------

    public static class WifiItem {
        public final String ssid;
        public final String mac;     // colon-separated, e.g. "A4:C1:38:F2:B1:D3"
        public final int channel;
        public final int rssi;
        public final boolean included; // will be saved in waypoint fingerprint

        public WifiItem(String ssid, String mac, int channel, int rssi, boolean included) {
            this.ssid = ssid;
            this.mac = mac;
            this.channel = channel;
            this.rssi = rssi;
            this.included = included;
        }

        public WifiItem(String ssid, String mac, int channel, int rssi) {
            this(ssid, mac, channel, rssi, true);
        }

        public static WifiItem from(ScanResult r, boolean included) {
            String ssid = (r.SSID != null && !r.SSID.isEmpty()) ? r.SSID : "";
            int channel = WifiNetwork.frequencyToChannel(r.frequency);
            return new WifiItem(ssid, r.BSSID, channel, r.level, included);
        }
    }

    // --------------------------------------------------------
    // --- DATA -----------------------------------------------

    private List<WifiItem> items = new ArrayList<>();

    public void setResults(List<ScanResult> results, Set<String> includedKeys) {
        items = new ArrayList<>(results.size());
        for (ScanResult r : results) {
            items.add(WifiItem.from(r, includedKeys.contains(r.BSSID + ":" + r.frequency)));
        }
        notifyDataSetChanged();
    }

    public void setItems(List<WifiItem> newItems, Set<String> includedKeys) {
        items = new ArrayList<>(newItems.size());
        for (WifiItem item : newItems) {
            items.add(new WifiItem(item.ssid, item.mac, item.channel, item.rssi,
                    includedKeys.contains(item.mac + ":" + item.channel)));
        }
        notifyDataSetChanged();
    }

    public void clear() {
        items = new ArrayList<>();
        notifyDataSetChanged();
    }

    // --------------------------------------------------------
    // --- RECYCLER -------------------------------------------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wifi, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WifiItem item = items.get(position);
        android.content.Context ctx = holder.itemView.getContext();

        holder.txtSsid.setText(item.ssid.isEmpty() ? ctx.getString(R.string.wifi_ssid_hidden) : item.ssid);
        holder.txtBssid.setText(ctx.getString(R.string.wifi_bssid_fmt, item.mac, item.channel));
        holder.txtRssi.setText(ctx.getString(R.string.wifi_rssi_fmt, item.rssi));

        if (item.included) {
            int colorRes = item.rssi >= SIGNAL_STRONG_DBM ? R.color.brand200
                    : item.rssi >= SIGNAL_MEDIUM_DBM ? R.color.YELLOW
                    : R.color.RED;
            int signalColor = ContextCompat.getColor(ctx, colorRes);
            int textColor = ContextCompat.getColor(ctx, R.color.color900);
            holder.viewSignal.setBackgroundTintList(ColorStateList.valueOf(signalColor));
            holder.txtRssi.setTextColor(signalColor);
            holder.txtSsid.setTextColor(textColor);
        } else {
            int grey = ContextCompat.getColor(ctx, R.color.color400);
            holder.viewSignal.setBackgroundTintList(ColorStateList.valueOf(grey));
            holder.txtRssi.setTextColor(grey);
            holder.txtSsid.setTextColor(grey);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --------------------------------------------------------
    // --- VIEW HOLDER ----------------------------------------

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewSignal;
        final TextView txtSsid;
        final TextView txtBssid;
        final TextView txtRssi;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewSignal = itemView.findViewById(R.id.view_signal);
            txtSsid = itemView.findViewById(R.id.txt_ssid);
            txtBssid = itemView.findViewById(R.id.txt_bssid);
            txtRssi = itemView.findViewById(R.id.txt_rssi);
        }
    }
}