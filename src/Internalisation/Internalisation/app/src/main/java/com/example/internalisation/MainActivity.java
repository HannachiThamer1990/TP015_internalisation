package com.example.internalisation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private TelephonyManager telephonyManager;
    private int dbm = -1;
    private boolean isTracking = false;
    private Location lastLocation;
    private String currentLanguage = "en"; // Langue par défaut

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStartStop = findViewById(R.id.btnStartStop);
        TextView tvLastLog = findViewById(R.id.tvLastLog);
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // Écoute des changements de signal
        telephonyManager.listen(new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                int newDbm = signalStrength.getLevel();

                if (newDbm != dbm) {
                    dbm = newDbm;
                    logSignalStrengthChange();
                }
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        btnStartStop.setOnClickListener(v -> {
            if (isTracking) {
                stopTracking();
                btnStartStop.setText(getString(R.string.start_tracking));
                Toast.makeText(this, getString(R.string.tracking_stopped), Toast.LENGTH_SHORT).show();
            } else {
                if (checkLocationPermissions()) {
                    startTracking(tvLastLog);
                    btnStartStop.setText(getString(R.string.stop_tracking));
                    Toast.makeText(this, getString(R.string.tracking_started), Toast.LENGTH_SHORT).show();
                }
            }
            isTracking = !isTracking;
        });

        // Écoute du bouton de changement de langue
        btnChangeLanguage.setOnClickListener(v -> {
            changeLanguage();
            updateText(); // Met à jour le texte après le changement de langue
        });
    }

    private void startTracking(TextView tvLastLog) {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(10000)
                .setFastestInterval(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        // Vérifier si la localisation a changé
                        if (lastLocation == null || hasLocationChanged(lastLocation, location)) {
                            lastLocation = location; // Mettre à jour la dernière localisation
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            double altitude = location.getAltitude();
                            int batteryLevel = getBatteryLevel();

                            String logEntry = logData(latitude, longitude, altitude, dbm, batteryLevel);
                            tvLastLog.setText(getString(R.string.last_log) + ": " + logEntry);
                        }
                    }
                }
            }
        };

        if (checkLocationPermissions()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private boolean hasLocationChanged(Location lastLocation, Location newLocation) {
        return lastLocation.getLatitude() != newLocation.getLatitude() || lastLocation.getLongitude() != newLocation.getLongitude();
    }

    private void logSignalStrengthChange() {
        double latitude = lastLocation != null ? lastLocation.getLatitude() : 0;
        double longitude = lastLocation != null ? lastLocation.getLongitude() : 0;
        double altitude = lastLocation != null ? lastLocation.getAltitude() : 0;
        int batteryLevel = getBatteryLevel();

        String logEntry = logData(latitude, longitude, altitude, dbm, batteryLevel);
    }

    private void stopTracking() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private String logData(double latitude, double longitude, double altitude, int dbm, int batteryLevel) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = timestamp + "; " + latitude + "; " + longitude + "; " + altitude + "; " + dbm + "; " + batteryLevel;

        try {
            File file = new File(getExternalFilesDir(null), "LogTracking.csv");
            FileWriter writer = new FileWriter(file, true);
            writer.append(logEntry).append("\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return logEntry;
    }

    private boolean checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Méthode pour changer la langue
    private void changeLanguage() {
        if (currentLanguage.equals("en")) {
            currentLanguage = "fr"; // Changer à anglais
        } else {if (currentLanguage.equals("fr")) {
            currentLanguage = "fr"; // Changer à français
        } else
            currentLanguage = "ar"; // Changer à arabe
        }

        // Appliquer la langue
        Locale locale = new Locale(currentLanguage);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    // Mettre à jour le texte selon la langue actuelle
    private void updateText() {
        Button btnStartStop = findViewById(R.id.btnStartStop);
        TextView tvLastLog = findViewById(R.id.tvLastLog);
        Button btnChangeLanguage = findViewById(R.id.btnChangeLanguage);

        btnStartStop.setText(R.string.start_tracking);
        tvLastLog.setText(R.string.last_log);
        btnChangeLanguage.setText(R.string.change_language);
    }
}
