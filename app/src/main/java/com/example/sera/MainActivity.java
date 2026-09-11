package com.example.sera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERM = 1001;

    // Emergency contacts
    private static final String CONTACT1 = "9030432293";
    private static final String CONTACT2 = "9963785380";
    private static final String CONTACT3 = "6304026607";

    // Emergency services
    private static final String POLICE_NUMBER = "100";
    private static final String AMBULANCE_NUMBER = "108";

    private static final String SOS_URL = "http://10.0.2.2:3000/sos";

    private FusedLocationProviderClient fusedLocationClient;
    private RequestQueue requestQueue;

    private LocationCallback liveLocationCallback;

    private final Handler sosHandler = new Handler(Looper.getMainLooper());
    private Runnable sosRunnable;
    private boolean sosCancelled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(this);

        // UI
        ImageButton btnSOS = findViewById(R.id.btnSendSOS);
        Button btnCancelSOS = findViewById(R.id.btnCancelSOS);

        TextView btnPolice = findViewById(R.id.btnPolice);
        TextView btnAmbulance = findViewById(R.id.btnAmbulance);
        TextView btnEmergencyCall = findViewById(R.id.btnEmergencyCall);
        View btnLiveLocation = findViewById(R.id.btnLiveLocation);

        // SOS
        btnSOS.setOnClickListener(v -> {
            vibratePhone();
            startDelayedSOS(btnCancelSOS);
        });

        btnCancelSOS.setOnClickListener(v -> {
            sosCancelled = true;
            sosHandler.removeCallbacks(sosRunnable);
            btnCancelSOS.setVisibility(View.GONE);
            Toast.makeText(this, "SOS Cancelled", Toast.LENGTH_SHORT).show();
        });

        // Emergency calls
        btnPolice.setOnClickListener(v -> callNumber(POLICE_NUMBER));
        btnAmbulance.setOnClickListener(v -> callNumber(AMBULANCE_NUMBER));
        btnEmergencyCall.setOnClickListener(v -> callNumber(CONTACT1));

        // Live Location
        btnLiveLocation.setOnClickListener(v -> {
            Toast.makeText(this, "Live Location Started", Toast.LENGTH_SHORT).show();
            startLiveLocationUpdates();
        });

        requestAllPermissions();
    }

    // ---------------- PERMISSIONS ----------------
    private void requestAllPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.VIBRATE
            }, REQ_PERM);
        }
    }

    // ---------------- LIVE LOCATION (10s) ----------------
    private void startLiveLocationUpdates() {

        if (!isLocationEnabled()) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        liveLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    String link = "https://maps.google.com/?q="
                            + location.getLatitude() + ","
                            + location.getLongitude();

                    SmsManager sms = SmsManager.getDefault();
                    sms.sendTextMessage(CONTACT1, null, link, null, null);
                    sms.sendTextMessage(CONTACT2, null, link, null, null);
                    sms.sendTextMessage(CONTACT3, null, link, null, null);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                liveLocationCallback,
                getMainLooper()
        );
    }

    // ✅ STOP LIVE LOCATION (OPTIONAL BUT RECOMMENDED)
    private void stopLiveLocationUpdates() {
        if (liveLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(liveLocationCallback);
            Toast.makeText(this, "Live Location Stopped", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- SOS FLOW ----------------
    private void startDelayedSOS(Button cancelBtn) {
        sosCancelled = false;
        cancelBtn.setVisibility(View.VISIBLE);

        Toast.makeText(this,
                "Sending SOS in 5 seconds… Tap CANCEL to stop",
                Toast.LENGTH_LONG).show();

        sosRunnable = () -> {
            cancelBtn.setVisibility(View.GONE);
            if (!sosCancelled) startSOS();
        };

        sosHandler.postDelayed(sosRunnable, 5000);
    }

    private void startSOS() {
        if (!isLocationEnabled()) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        handleLocation(location);
                    }
                });
    }

    private void handleLocation(Location location) {
        String loc = location.getLatitude() + "," + location.getLongitude();
        sendSMS(loc);
        sendToServer(loc);
    }

    private void sendSMS(String loc) {
        String msg = "🚨 SERA SOS\nhttps://maps.google.com/?q=" + loc;
        SmsManager sms = SmsManager.getDefault();

        sms.sendTextMessage(CONTACT1, null, msg, null, null);
        sms.sendTextMessage(CONTACT2, null, msg, null, null);
        sms.sendTextMessage(CONTACT3, null, msg, null, null);
    }

    // ---------------- CALLING ----------------
    private void callNumber(String number) {
        try {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)));
            } else {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
            }
        } catch (Exception ignored) {}
    }

    // ---------------- SERVER ----------------
    private void sendToServer(String loc) {
        try {
            JSONObject body = new JSONObject();
            body.put("type", "SOS");
            body.put("location", loc);

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    SOS_URL,
                    body,
                    response -> {},
                    error -> Log.e("SERVER", error.toString())
            );
            requestQueue.add(req);
        } catch (Exception ignored) {}
    }

    // ---------------- UTIL ----------------
    private boolean isLocationEnabled() {
        LocationManager lm =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm != null &&
                (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(
                    700, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(700);
        }
    }
}