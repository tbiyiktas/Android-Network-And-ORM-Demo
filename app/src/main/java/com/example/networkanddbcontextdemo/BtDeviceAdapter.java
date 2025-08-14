package com.example.networkanddbcontextdemo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothDevice;

public final class BtDeviceAdapter extends RecyclerView.Adapter<BtDeviceAdapter.VH> {

    private final List<IBluetoothDevice> items;
    private final Consumer<IBluetoothDevice> onClick;

    public BtDeviceAdapter(@NonNull List<IBluetoothDevice> items,
                           @NonNull Consumer<IBluetoothDevice> onClick) {
        this.items = Objects.requireNonNull(items);
        this.onClick = Objects.requireNonNull(onClick);
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        IBluetoothDevice d = items.get(position);
        String addr = d != null ? d.getAddress() : null;
        return addr != null ? addr.hashCode() : RecyclerView.NO_ID;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.btdevice_item, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        IBluetoothDevice d = items.get(position);
        h.bind(d);
        h.itemView.setOnClickListener(v -> onClick.accept(d));
    }

    @Override public int getItemCount() { return items.size(); }

    public void addOrUpdate(IBluetoothDevice device) {
        if (device == null) return;
        String addr = device.getAddress();
        if (addr == null) return;
        int idx = indexOf(addr);
        if (idx >= 0) { items.set(idx, device); notifyItemChanged(idx); }
        else { items.add(device); notifyItemInserted(items.size() - 1); }
    }
    public void clear() { items.clear(); notifyDataSetChanged(); }
    private int indexOf(String addr) {
        for (int i=0;i<items.size();i++) {
            IBluetoothDevice d = items.get(i);
            if (d != null && addr.equals(d.getAddress())) return i;
        }
        return -1;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView tvName, tvAddress, tvType, tvBond;
        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvAddress = v.findViewById(R.id.tvAddress);
            tvType = v.findViewById(R.id.tvType);
            tvBond = v.findViewById(R.id.tvBond);
        }
        void bind(IBluetoothDevice d) {
            if (d == null) return;
            tvName.setText(d.getName());
            tvAddress.setText(d.getAddress());
            tvType.setText(d.getType() != null ? d.getType().name() : "UNKNOWN");
            IBluetoothDevice.BondState b = d.getBondState();
            tvBond.setText(b != null ? b.name() : "NONE");
        }
    }
}
