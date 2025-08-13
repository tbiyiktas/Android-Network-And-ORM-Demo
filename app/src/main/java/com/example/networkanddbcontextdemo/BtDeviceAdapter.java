
package com.example.networkanddbcontextdemo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.networkanddbcontextdemo.R;
import com.example.networkanddbcontextdemo.adapter.BtDeviceAdapter.BtDeviceViewHolder;
import lib.bt.interfaces.IBluetoothDevice;

import java.util.List;

public class BtDeviceAdapter extends RecyclerView.Adapter<BtDeviceViewHolder> {

    private List<IBluetoothDevice> devices;
    private OnDeviceClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public BtDeviceAdapter(List<IBluetoothDevice> devices) {
        this.devices = devices;
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    // Arayüz tanımı
    public interface OnDeviceClickListener {
        void onDeviceClick(IBluetoothDevice device);
    }

    @NonNull
    @Override
    public BtDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.btdevice_item, parent, false);
        return new BtDeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BtDeviceViewHolder holder, int position) {
        IBluetoothDevice device = devices.get(position);
        holder.deviceName.setText(device.getName());
        holder.deviceAddress.setText(device.getAddress());

        // Seçili öğenin arka planını değiştirme
        if (selectedPosition == position) {
            holder.itemView.setBackgroundResource(R.color.colorSelectedItem); // Seçili renk
        } else {
            //holder.itemView.setBackgroundResource(R.color.t); // Varsayılan renk
        }

        // Tıklama dinleyicisi
        holder.itemView.setOnClickListener(v -> {
            int previousSelectedPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousSelectedPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void updateDevices(List<IBluetoothDevice> newDevices) {
        this.devices.clear();
        this.devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    public static class BtDeviceViewHolder extends RecyclerView.ViewHolder {
        public TextView deviceName;
        public TextView deviceAddress;

        public BtDeviceViewHolder(View view) {
            super(view);
            deviceName = view.findViewById(R.id.deviceNameTextView);
            deviceAddress = view.findViewById(R.id.deviceAddressTextView);
        }
    }

    public void addDevice(IBluetoothDevice device) {
        if (!devices.contains(device)) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }
    public void clearDevices() {
        this.devices.clear();
        notifyDataSetChanged();
    }
}
