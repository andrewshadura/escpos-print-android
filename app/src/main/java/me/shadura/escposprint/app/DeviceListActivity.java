package me.shadura.escposprint.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

import me.shadura.escposprint.R;
import me.shadura.escposprint.printservice.BluetoothService;

public class DeviceListActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mService = null;
    private BluetoothDevicesAdapter mDiscoveredDevicesArrayAdapter;
    private Snackbar mSnackbar;
    private SwipeRefreshLayout mRefreshLayout;
    private HashSet<BluetoothDevice> discoveredDevices = new HashSet<BluetoothDevice>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        mDiscoveredDevicesArrayAdapter = new BluetoothDevicesAdapter(this, R.layout.device_list_item);
        ListView discoveredListView = (ListView) findViewById(R.id.discovered_devices);
        discoveredListView.setAdapter(mDiscoveredDevicesArrayAdapter);
        discoveredListView.setOnItemClickListener(mDiscoveredDevicesClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        addPairedDevices();

        FloatingActionButton refresh_devices = findViewById(R.id.refresh_devices);

        final OnClickListener mCancelListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothAdapter.cancelDiscovery();
            }
        };

        mSnackbar = Snackbar.make(discoveredListView, "Searching for new Bluetooth devices", Snackbar.LENGTH_LONG)
                .setAction("Cancel", mCancelListener);

        mRefreshLayout = findViewById(R.id.discovered_refresh_layout);
        mRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                discoverDevices();
            }
        });

        refresh_devices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRefreshLayout.setRefreshing(true);
                discoverDevices();
            }
        });
    }

    private OnItemClickListener mDiscoveredDevicesClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            mBluetoothAdapter.cancelDiscovery();
            /* TODO: actual work */
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    if (discoveredDevices.contains(device)) {
                        return;
                    }
                    discoveredDevices.add(device);
                    mDiscoveredDevicesArrayAdapter.add(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mRefreshLayout.setRefreshing(false);
            }
        }
    };

    private void discoverDevices() {
        if (!mBluetoothAdapter.isDiscovering()) {
            mSnackbar.show();
            addPairedDevices();
            discoveredDevices.clear();
            mBluetoothAdapter.startDiscovery();
        }
    }

    private void addPairedDevices() {
        mDiscoveredDevicesArrayAdapter.clear();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mDiscoveredDevicesArrayAdapter.add(device);
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh: {
                mRefreshLayout.setRefreshing(true);
                discoverDevices();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    private static class BluetoothDeviceViews {
        TextView name, address;

        BluetoothDeviceViews(TextView name, TextView address) {
            this.name = name;
            this.address = address;
        }
    }

    private static class BluetoothDevicesAdapter extends ArrayAdapter<BluetoothDevice> {
        public BluetoothDevicesAdapter(@NonNull Context context, @LayoutRes int resource) {
            super(context, resource);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            BluetoothDeviceViews views;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
                views = new BluetoothDeviceViews(
                        (TextView) convertView.findViewById(R.id.bluetooth_device_name),
                        (TextView) convertView.findViewById(R.id.bluetooth_device_address)
                );
                convertView.setTag(views);
            } else {
                views = (BluetoothDeviceViews) convertView.getTag();
            }

            BluetoothDevice device = getItem(position);
            if (device != null) {
                String name = device.getName() != null ? device.getName() : "(unnamed)";
                views.name.setText(name);
                views.address.setText(device.getAddress());
            } else {
                throw new IllegalStateException("Bluetooth device list can't have invalid items");
            }

            return convertView;
        }

        public void removeItem(int position) {
            remove(getItem(position));
        }
    }
}
