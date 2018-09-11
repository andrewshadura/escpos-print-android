package me.shadura.escposprint.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.shadura.escposprint.R;
import me.shadura.escposprint.printservice.BluetoothService;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class ManageManualPrintersActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter = null;

    private BluetoothService mService = null;
    private ManualPrintersAdapter adapter = null;

    /* Intent request codes */
    private static final int REQUEST_FIND_DEVICE = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_manual_printers);

        ListView printersList = findViewById(R.id.manage_printers_list);

        // Build adapter
        final SharedPreferences prefs = getSharedPreferences(AddPrintersActivity.SHARED_PREFS_MANUAL_PRINTERS, Context.MODE_PRIVATE);
        int numPrinters = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0);
        List<ManualPrinterInfo> printers = getPrinters(prefs, numPrinters);
        adapter = new ManualPrintersAdapter(this, R.layout.manage_printers_list_item, printers);

        // Setup adapter with click to remove
        printersList.setAdapter(adapter);
        printersList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final SharedPreferences.Editor editor = prefs.edit();
                int numPrinters = prefs.getInt(AddPrintersActivity.PREF_NUM_PRINTERS, 0);
                editor.putInt(AddPrintersActivity.PREF_NUM_PRINTERS, numPrinters - 1);
                editor.remove(AddPrintersActivity.PREF_NAME + position);
                editor.remove(AddPrintersActivity.PREF_ADDRESS + position);
                editor.apply();
                adapter.removeItem(position);
                return true;
            }
        });
    }

    public void findPrinters(View button) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            ListView printersList = findViewById(R.id.manage_printers_list);

            Snackbar.make(printersList, "Bluetooth is not available", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            Intent serverIntent = new Intent(ManageManualPrintersActivity.this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_FIND_DEVICE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH: {
                if (resultCode == Activity.RESULT_OK) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            /* TODO: Add an explainer */
                        } else {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    }, PERMISSION_REQUEST_COARSE_LOCATION);
                        }
                    } else {
                        Intent serverIntent = new Intent(ManageManualPrintersActivity.this, DeviceListActivity.class);
                        startActivityForResult(serverIntent, REQUEST_FIND_DEVICE);
                    }
                } else {
                    ListView printersList = findViewById(R.id.manage_printers_list);

                    Snackbar.make(printersList, "This app needs Bluetooth to add new printers", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
                break;
            }
            case REQUEST_FIND_DEVICE: {
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    if (BluetoothAdapter.checkBluetoothAddress(address)) {
                        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                        final ManualPrinterInfo printerInfo = new ManualPrinterInfo(device.getName(), address, true, true);
                        adapter.add(printerInfo);
                        mService = new BluetoothService(this, new Handler() {
                            @Override
                            public void handleMessage(Message msg) {
                                switch (msg.what) {
                                    case BluetoothService.MESSAGE_STATE_CHANGE: {
                                        if (msg.arg1 == BluetoothService.State.STATE_CONNECTED.ordinal()) {
                                            final long position = adapter.getPosition(printerInfo);
                                            printerInfo.connecting = false;
                                            adapter.notifyDataSetChanged();
                                            mService.stop();
                                            mService = null;
                                        }
                                        break;
                                    }
                                    case BluetoothService.MESSAGE_DEVICE_NAME: {
                                        break;
                                    }
                                    case BluetoothService.MESSAGE_READ: {
                                        break;
                                    }
                                    case BluetoothService.MESSAGE_WRITE: {
                                        break;
                                    }
                                    case BluetoothService.MESSAGE_CONNECTION_LOST: {
                                        break;
                                    }
                                    case BluetoothService.MESSAGE_CONNECTION_FAILURE: {
                                        break;
                                    }
                                }
                            }
                        });
                        mService.connect(device);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
        String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                Intent serverIntent = new Intent(ManageManualPrintersActivity.this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_FIND_DEVICE);
            }
        }
    }

        @NonNull
    private List<ManualPrinterInfo> getPrinters(SharedPreferences prefs, int numPrinters) {
        List<ManualPrinterInfo> printers = new ArrayList<>(numPrinters);
        String address, name;
        boolean enabled;
        for (int i = 0; i < numPrinters; i++) {
            name = prefs.getString(AddPrintersActivity.PREF_NAME + i, null);
            address = prefs.getString(AddPrintersActivity.PREF_ADDRESS + i, null);
            enabled = prefs.getBoolean(AddPrintersActivity.PREF_ENABLED + i, false);
            printers.add(new ManualPrinterInfo(name, address, enabled, false));
        }
        return printers;
    }

    private static class ManualPrinterInfo {
        String address, name;
        boolean enabled, connecting;

        private ManualPrinterInfo(String name, String address, boolean enabled, boolean connecting) {
            this.name = name;
            this.address = address;
            this.enabled = enabled;
            this.connecting = connecting;
        }
    }

    private static class ManualPrinterInfoViews {
        TextView address, name;
        Switch enabled;
        ProgressBar connecting;

        ManualPrinterInfoViews(TextView name, TextView address, Switch enabled, ProgressBar connecting) {
            this.name = name;
            this.address = address;
            this.enabled = enabled;
            this.connecting = connecting;
        }
    }

    private static class ManualPrintersAdapter extends ArrayAdapter<ManualPrinterInfo> {
        private ManualPrintersAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<ManualPrinterInfo> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ManualPrinterInfoViews views;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_printers_list_item, parent, false);
                views = new ManualPrinterInfoViews(
                        (TextView) convertView.findViewById(R.id.manual_printer_name),
                        (TextView) convertView.findViewById(R.id.manual_printer_address),
                        (Switch) convertView.findViewById(R.id.manual_printer_enabled),
                        (ProgressBar) convertView.findViewById(R.id.manual_printer_progressbar)
                );
                convertView.setTag(views);
            } else {
                views = (ManualPrinterInfoViews) convertView.getTag();
            }

            ManualPrinterInfo info = getItem(position);
            if (info != null) {
                views.name.setText(info.name);
                views.address.setText(info.address);
                views.enabled.setChecked(info.enabled);
                views.enabled.setEnabled(!info.connecting);
                views.connecting.setVisibility(info.connecting ? VISIBLE : GONE);
            } else {
                throw new IllegalStateException("Manual printers list can't have invalid items");
            }

            return convertView;
        }

        public void removeItem(int position) {
            remove(getItem(position));
        }
    }
}
