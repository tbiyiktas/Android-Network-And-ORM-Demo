package com.example.networkanddbcontextdemo;


import android.os.Build;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lib.location.LocationData;
import lib.location.LocationErrorCode;
import lib.location.LocationResult;

public class LocationHistoryAdapter extends RecyclerView.Adapter<LocationHistoryAdapter.VH> {

    private final List<LocationResult<LocationData>> items = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final DecimalFormat df = new DecimalFormat("#0.0000");

    public void submit(List<LocationResult<LocationData>> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_location_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LocationResult<LocationData> r = items.get(position);
        if (r.isSuccess() && r.data != null) {
            LocationData d = r.data;
            // Yaş hesapla: tercihen monotonik, yoksa wall clock
            long ageMs;
            if (d.elapsedRealtimeNanos != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                long nowNs = SystemClock.elapsedRealtimeNanos();
                long deltaNs = nowNs - d.elapsedRealtimeNanos;
                if (deltaNs < 0) deltaNs = 0;
                ageMs = deltaNs / 1_000_000L;
            } else {
                long delta = System.currentTimeMillis() - d.timeMillis;
                if (delta < 0) delta = 0;
                ageMs = delta;
            }

            String timeStr = sdf.format(d.timeMillis);
            String accStr = (d.accuracy == null) ? "—" : (Math.round(d.accuracy) + " m");

            h.txtLine.setText(
                    String.format(Locale.getDefault(),
                            "[%02d] %s  age=%dms  lat=%s  lon=%s  acc=%s",
                            position + 1,
                            timeStr,
                            ageMs,
                            df.format(d.latitude),
                            df.format(d.longitude),
                            accStr
                    )
            );
        } else {
            // Hata kaydı
            LocationErrorCode code = r.errorCode != null ? r.errorCode : LocationErrorCode.UNKNOWN;
            String msg = r.errorMessage != null ? r.errorMessage : "error";
            h.txtLine.setText(String.format(Locale.getDefault(),
                    "[%02d] ERROR: %s (%s)", position + 1, code, msg));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView txtLine;
        VH(@NonNull View itemView) {
            super(itemView);
            txtLine = itemView.findViewById(R.id.txtLine);
        }
    }
}
