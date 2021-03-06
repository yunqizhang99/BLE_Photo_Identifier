package android.e.blephotoidentifier;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelUuid;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Capture extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_TAKE_PHOTO = 1;
    private static final int SEPARATOR_CHAR = 42;
    private static final ParcelUuid SERVICE_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");

    private BluetoothManager manager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private ArrayList<String> names = new ArrayList<String>();
    private HashMap<String,String> currentNames = new HashMap<String,String>();
    String currentPhotoPath;

    private TextView people_list;
    private Button collect;
    private Button take_photo;
    private Button facial_recognition;
    private Button back;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        initializeBt();
        createUI();
    }

    private void initializeBt(){
        manager = (BluetoothManager) getApplicationContext().getSystemService(
                Context.BLUETOOTH_SERVICE);
        btAdapter = manager.getAdapter();
        if (btAdapter == null) {
            showFinishingAlertDialog("Bluetooth Error", "Bluetooth not detected on device");
        } else if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else if (!btAdapter.isMultipleAdvertisementSupported()) {
            showFinishingAlertDialog("Not supported", "BLE advertising not supported on this device");
        }
        bluetoothLeScanner = btAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<ScanFilter>();
        byte[] test = new byte[24];
        byte[] mask = new byte [24];
        for (int i = 0; i < 24; i++){
            test[i] = (byte)1;
            mask[i] = (byte)0;
        }
        filters.add(new ScanFilter.Builder().setServiceData(SERVICE_UUID,test,mask).build());
    }

    private void createUI(){
        people_list = findViewById(R.id.people_list);
        collect = findViewById(R.id.collect);
        back = findViewById(R.id.back);
        take_photo = findViewById((R.id.take_photo));
        facial_recognition = findViewById(R.id.facial_recognition);
        collect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                if (collect.getText().toString().equalsIgnoreCase("Collect Broadcast")){
                    collect.setText("STOP");
                    setEnabledViews(false,back,take_photo,facial_recognition);
                    scanLeDevice(true);
                } else {
                    scanLeDevice(false);
                    Assembler.clear();
                    setEnabledViews(true,back,take_photo,facial_recognition);
                    collect.setText("COLLECT BROADCAST");
                }
            }
        });
        take_photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                dispatchTakePictureIntent();
            }
        });
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            bluetoothLeScanner.startScan(filters,settings,leScanCallback);
        } else {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            String address = result.getDevice().getName();
            byte[] pData = Assembler.gather(address, result.getScanRecord().getServiceData(SERVICE_UUID));
            if (pData != null) {
                updateNames(pData);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e("BLE Photo Identifier","File couldn't be created.");
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "android.e.blephotoidentifier.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setEnabledViews(boolean enabled, View... views) {
        for (View v : views) {
            v.setEnabled(enabled);
        }
    }

    private void updateNames(byte[] data){
        int index = 0;
        while (data[index] != (byte)SEPARATOR_CHAR){
            index++;
        }
        byte[] name = Arrays.copyOfRange(data,0,index);
        byte[] description;
        if (index+1 >= data.length){
            description = new byte[0];
        } else {
           description = Arrays.copyOfRange(data,index+1,data.length);
        }
        String nameStr = new String(name);
        String descStr = new String(description);
        if (!currentNames.containsKey(nameStr)){
            currentNames.put(nameStr,descStr);
            names.add(nameStr);
        }
        updateView();
    }

    private void updateView(){
        String nameList = "";
        for (String name : names){
            nameList = nameList + "\n" + name;
        }
        people_list.setText(nameList);
    }

    // Pops an AlertDialog that quits the app on OK.
    private void showFinishingAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }

                }).show();
    }
}