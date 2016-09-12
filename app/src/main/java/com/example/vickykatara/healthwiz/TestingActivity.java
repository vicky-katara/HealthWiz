package com.example.vickykatara.healthwiz;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.widget.CompoundButton;
import android.widget.RemoteViews;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

import static com.example.vickykatara.healthwiz.MainActivity.ALLERGIES_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.BLOOD_GROUP_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.CONDITIONS_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.DATE_OF_BIRTH_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.EMERGENCY_CONTACT_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.NAME_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.ORGAN_DONOR_KEY;
import static com.example.vickykatara.healthwiz.MainActivity.sharedPreferences;

public class TestingActivity extends AppCompatActivity
                    implements SensorEventListener,
                    GoogleApiClient.OnConnectionFailedListener,
                    GoogleApiClient.ConnectionCallbacks,
                    LocationListener {

    private boolean mockFall = false;
    private boolean mockLight = false;
    private boolean mockPressure = false;
    private boolean mockTimeToHospital = false;

    private double lastCapturedLightValue;
    private double lastCapturedPressureValue;
    private boolean lastCapturedFall;
    private int lastCapturedSecsToHospital;
    private String lastCapturedTimeToHospitalString;

    private static double MIN_LIGHT_FOR_SAFETY = 400;
    private static double MIN_PRESSURE_FOR_SAFETY = 29.5;
    private static double MAX_SECS_TO_HOSPITAL_FOR_SAFETY = 300; // 5 mins

    private static double REX_LATTITUDE = 35.817870;
    private static double REX_LONGITUDE = -78.702539;

    private static double DEFAULT_LATTITUDE = 45.769945;
    private static double DEFAULT_LONGITUDE = -78.676402;

    private SensorManager mSensorManager;
    private Sensor mPressure, mLight;

    private Thread fallGenerator;
    private Thread distanceFetcher;

    private boolean noPressureSensor = false;
    private boolean connectionActive = true;
    private Location mLastLocation;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest locationRequest;
    private double prevLat;
    private double prevLong;
    private boolean prevComputationCompleted;
    private Handler handler = new Handler();
//    private String prevUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mPressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if(mPressure == null)
            noPressureSensor = true;
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        fallGenerator = new Thread(new FallGenerator());
        fallGenerator.start();

//        distanceFetcher = new Thread(new DistanceFetcher(getApplicationContext()));
//        distanceFetcher.start();
//        makeAlertDialog(mSensorManager.getSensorList(Sensor.TYPE_PRESSURE).toString());

//        System.out.println(mSensorManager.getSensorList(Sensor.TYPE_PRESSURE).toString());

        updateLight();
        updatePressure();
        updateFall();
        calculateDistance();
        updateTimeToHospital();

        ((Switch) findViewById(R.id.lightSwitch)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mockLight = true;
                            ((TextView) findViewById(R.id.lightTextView)).setText("399 lx (Mocked)");
                            checkNotificationCriteria();
                        } else {
                            mockLight = false;
                        }
                    }
                }
        );

        ((Switch) findViewById(R.id.pressureSwitch)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mockPressure = true;
                            ((TextView) findViewById(R.id.pressureTextView)).setText("29.4 \"Hg (Mocked)");
                            checkNotificationCriteria();
                        } else {
                            mockPressure = false;
                        }
                    }
                }
        );

        ((Switch) findViewById(R.id.fallSwitch)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mockFall = true;
                            ((TextView) findViewById(R.id.fallTextView)).setText("User Fell (Mocked)");
                            checkNotificationCriteria();
                        } else {
                            mockFall = false;
                        }
                    }
                }
        );

        ((Switch) findViewById(R.id.timeToHospitalSwitch)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mockTimeToHospital = true;
                            ((TextView) findViewById(R.id.timeToHospitalTextView)).setText("6mins (Mocked)");
                            checkNotificationCriteria();
                        } else {
                            mockTimeToHospital = false;
                            updateTimeToHospital();
                        }
                    }
                }
        );

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        makeAlertDialog("Google API Client Initialized");

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(10*1000);
        locationRequest.setFastestInterval(5*1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected() ) {
            mGoogleApiClient.connect();
            makeAlertDialog("Google API Client Connected");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_LIGHT) {
            lastCapturedLightValue = event.values[0];
            updateLight();
        } else if (sensor.getType() == Sensor.TYPE_PRESSURE) {
            lastCapturedPressureValue = 0.0295301d * event.values[0];
            updatePressure();
        }
    }

    private void updatePressure() {
        if(noPressureSensor) {
            ((TextView) findViewById(R.id.pressureTextView)).setText("No Pressure Sensor");
            lastCapturedPressureValue = 398.0;
            return;
        }
        if (mockPressure == false) {
            ((TextView) findViewById(R.id.pressureTextView)).setText(String.format("%.2f", lastCapturedPressureValue) + " \"Hg");
            checkNotificationCriteria();
        }
    }

    private void updateLight() {
        if (mockLight == false) {
            ((TextView) findViewById(R.id.lightTextView)).setText(String.format("%.2f", lastCapturedLightValue) + " lx");
            checkNotificationCriteria();
        }
    }

    private void updateFall() {
        if (mockFall == false) {
            ((TextView) findViewById(R.id.fallTextView)).setText(lastCapturedFall ? "User Fell" : "No Fall detected");
            checkNotificationCriteria();
        }
    }

    private void updateTimeToHospital() {
        if (mockTimeToHospital == false) {
            ((TextView) findViewById(R.id.timeToHospitalTextView)).setText(lastCapturedTimeToHospitalString);
            checkNotificationCriteria();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mPressure, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
        fallGenerator = new Thread(new FallGenerator());
        fallGenerator.start();
        distanceFetcher = new Thread(new FallGenerator());
        distanceFetcher.start();

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        } else {
            if (mGoogleApiClient.isConnected() == false )
                makeAlertDialog("mGoogleApiClient is disconnected");
        }
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        mSensorManager.unregisterListener(this);
        fallGenerator.interrupt();
        distanceFetcher.interrupt();
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    public void makeAlertDialog(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        connectionActive = true;
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        final LocationListener listener = this;

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            makeAlertDialog("Permission Issues");
        }

        if (mGoogleApiClient.isConnected()) {
            LocationServices
                    .FusedLocationApi
                    .requestLocationUpdates
                            (mGoogleApiClient, locationRequest, listener);
            makeAlertDialog("Location Listener Registered");
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            makeAlertDialog("getLastLocation");
        } else {
            System.err.println(" Not Connected ");
            makeAlertDialog("Not Connected");
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
        if(i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST)
            makeAlertDialog("Network Lost");
        if(i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED)
            makeAlertDialog("Location Service Disconnected");
        connectionActive = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        System.out.println("***** onLocationChanged() to "+lastCapturedTimeToHospitalString+" **** ");
        mLastLocation = location;
//        prevComputationCompleted = false;
        calculateDistance();
    }

    private void calculateDistance() {
        StringBuilder stringBuilder = new StringBuilder();
        // init client

        double selfLatitude = DEFAULT_LATTITUDE, selfLongitude = DEFAULT_LONGITUDE;

        if(mockTimeToHospital || connectionActive == false) {
            makeAlertDialog("mockTimeToHospital || connectionActive == false. Not fetching time.");
            return;
        }

        if(mLastLocation != null) {
            selfLatitude = mLastLocation.getLatitude();
            selfLongitude = mLastLocation.getLongitude();
        } else {
            System.out.println(" ******** mLastLocation null ******** ");
        }

        String urlString =
                "https://maps.googleapis.com/maps/api/distancematrix/json?units=imperial&origins="
                        +selfLatitude+","+selfLongitude
                        +"&destinations="+
                        REX_LATTITUDE+","+REX_LONGITUDE
                        +"&key=AIzaSyCxps88hh-xEcbDLMkBtVblla6xmMVlXN4";

        if(!isSignificantlyDifferent(prevLat, selfLatitude) &&
                !isSignificantlyDifferent(prevLong, selfLongitude) &&
                        prevComputationCompleted ) {
            System.out.println("No significant change in location. Using old Time");
//            makeAlertDialog("No significant change in location. Using old Time");
            return;
        } else {
            makeAlertDialog("Location Updated to "+selfLatitude+" , "+selfLongitude);
            System.out.println("New Query:"+urlString);
            makeAlertDialog("Getting new time...");
            prevLat = selfLatitude;
            prevLong = selfLongitude;
            prevComputationCompleted = false;
        }
        final HttpPost httppost = new HttpPost(urlString);
        new HTTPRequestTask().execute(new Object[]{httppost});
    }

    void extractAndDisplayLocation(HttpResponse response) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            HttpEntity entity = response.getEntity();
            InputStream stream = entity.getContent();
            int b;
            while ((b = stream.read()) != -1) {
                stringBuilder.append((char) b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject jsonObject;
        JSONObject firstTuple = new JSONObject();
        if (stringBuilder.length() == 0)
            return;
        try {
            jsonObject = new JSONObject(stringBuilder.toString());
//                    makeAlertDialog(jsonObject.toString());

            JSONArray jsonRowsArray = jsonObject.getJSONArray("rows");
//                    makeAlertDialog(jsonRowsArray.toString());

            if (jsonRowsArray.length() < 1) {
                System.err.println("jsonRowsArray Empty: " + firstTuple.toString());
                return;
            }

            JSONObject jsonRowsObject = jsonRowsArray.getJSONObject(0);

            JSONArray jsonElementsArray = jsonRowsObject.getJSONArray("elements");

//                    makeAlertDialog(jsonObject.toString());
            if (jsonElementsArray.length() < 1) {
                System.err.println("jsonElementsArray Empty: " + firstTuple.toString());
                return;
            }

            firstTuple = jsonElementsArray.getJSONObject(0);

            if (!firstTuple.get("status").equals("OK")) {
                System.err.println("Distance Matrix Reply Status not Ok: " + firstTuple.toString());
                return;
            }

            JSONObject jsonDurationObject = firstTuple.getJSONObject("duration");

            lastCapturedSecsToHospital = (Integer) jsonDurationObject.get("value");

            lastCapturedTimeToHospitalString = (String) jsonDurationObject.get("text");
            System.out.println(" ****** Set lastCapturedTimeToHospitalString to :" + lastCapturedTimeToHospitalString + " ********* ");
            prevComputationCompleted = true;
        } catch (JSONException jse) {
            System.out.println(firstTuple.toString());
            jse.printStackTrace();
        }
        updateTimeToHospital();
    }


    boolean isSignificantlyDifferent(double x, double y) {
        return !decimalPlus3String(x).equals(decimalPlus3String(y));
    }

    private String decimalPlus3String(double x) {
        String str = Double.toString(x);
        int indexOfDecimalPlusThree = str.indexOf(".")+3;
        if(str.length() < indexOfDecimalPlusThree)
            return str;
        return str.substring(0, indexOfDecimalPlusThree);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        lastCapturedSecsToHospital = -1;
        lastCapturedTimeToHospitalString = " ~No Connection~ ";
        updateTimeToHospital();
    }

    private class FallGenerator implements Runnable {
        Handler handler = new Handler();

        @Override
        public void run() {
            while (true) {
                double val = Math.random();
                if (val >= 0.2 && val <= 0.4)
                    lastCapturedFall = true;
                else
                    lastCapturedFall = false;
                handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                updateFall();
                            }
                        }
                );
                try {
                    Thread.sleep(1000); // change to 60000
                } catch (InterruptedException e) {}
            }
        }
    }

    private void checkNotificationCriteria() {
        if(lightTooLess() &&
                pressureTooLess() &&
                fallenUser() &&
                userTooFarFromHospital())
            publishNotification();
        else
            killNotification();
    }

    private boolean lightTooLess() {
        return mockLight || lastCapturedLightValue < MIN_LIGHT_FOR_SAFETY;
    }

    private boolean pressureTooLess() {
        return mockPressure || lastCapturedPressureValue < MIN_PRESSURE_FOR_SAFETY;
    }

    private boolean fallenUser() {
        return mockFall || lastCapturedFall;
    }

    private boolean userTooFarFromHospital() {
        return mockTimeToHospital || lastCapturedSecsToHospital > MAX_SECS_TO_HOSPITAL_FOR_SAFETY;
    }

    private void publishNotification() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setSmallIcon(R.drawable.heart_pulse);
//        mBuilder.setContentText(sharedPreferences.getAll().toString());
        mBuilder.setOngoing(true);
        mBuilder.setPriority(Notification.PRIORITY_HIGH);
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        Spannable titleSpannable = new SpannableString(getString(R.string.emergency_health_information));
        titleSpannable.setSpan(new RelativeSizeSpan(0.75f), 0, titleSpannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mBuilder.setContentTitle(titleSpannable);

        Spannable contentSpannable = new SpannableString(getSmallNotificationString());
        contentSpannable.setSpan(new RelativeSizeSpan(0.55f), 0, contentSpannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mBuilder.setContentText(contentSpannable);

        RemoteViews bigContentView = new RemoteViews(getPackageName(), R.layout.notification_big);
        RemoteViews smallContentView = new RemoteViews(getPackageName(), R.layout.notification_small);

        setupBigNotificationText(bigContentView);
        setupSmallNotificationText(smallContentView);

        mBuilder.setCustomBigContentView(bigContentView);
        mBuilder.setContent(smallContentView);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(getSmallNotificationString().replace("|", "\n")));
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(0, (mBuilder.build()));
    }

    private void killNotification() {
        NotificationManager nManager = ((NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE));
        nManager.cancelAll();
    }


    private String getSmallNotificationString() {
        StringBuilder content = new StringBuilder();
        // Set Name
        content.append(sharedPreferences.getString(NAME_KEY, NAME_KEY));
        // Set Blood Group
        content.append(" | "+"Blood Group: "+sharedPreferences.getString(BLOOD_GROUP_KEY, BLOOD_GROUP_KEY));
        // Set Organ Donor String
        String organDonorString = sharedPreferences.getBoolean(ORGAN_DONOR_KEY, false) ? "Is Organ Donor" : "Not an organ Donor";
        content.append(" | "+organDonorString);
        // Set Emergency Contact
        content.append(" | "+
                "Emergency Contact: "+sharedPreferences.getString(EMERGENCY_CONTACT_KEY, EMERGENCY_CONTACT_KEY));
        // Set Age
        int years = 0;
        Calendar dob = Calendar.getInstance();
        String dobArr[] = sharedPreferences.getString(DATE_OF_BIRTH_KEY, "").split("/");
        dob.set(Integer.parseInt(dobArr[2]), Integer.parseInt(dobArr[0]), Integer.parseInt(dobArr[1]));
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR))
            age--;
        years =  age;
        content.append(" | "+"Age: "+ years+" yrs");
        // Set Allergies
        content.append(" | "+"Allergies: "+sharedPreferences.getString(ALLERGIES_KEY, ALLERGIES_KEY));
        // Set Conditions
        content.append(" | "+"Conditions: "+sharedPreferences.getString(CONDITIONS_KEY, CONDITIONS_KEY));
        return content.toString();
    }

    private void setupSmallNotificationText(RemoteViews contentView) {
        // attach to smallNotificationContentTextView
        contentView.setTextViewText(R.id.smallNotificationContentTextView, getSmallNotificationString());
//        (findViewById(R.id.smallNotificationContentTextView)).setSelected(true);
    }

    private void setupBigNotificationText(RemoteViews contentView) {
        // Set Name
        contentView.setTextViewText(R.id.notificationNameTextView, "Name:"+sharedPreferences.getString(NAME_KEY, NAME_KEY));
        // Set Blood Group
        contentView.setTextViewText(R.id.notificationBloodGroupTextView, "Blood Group:"+sharedPreferences.getString(BLOOD_GROUP_KEY, BLOOD_GROUP_KEY));
        // Set Organ Donor String
        String organDonorString = sharedPreferences.getBoolean(ORGAN_DONOR_KEY, false) ? "Is Organ Donor" : "Not an organ Donor";
        contentView.setTextViewText(R.id.notificationOrganDonorTextView, organDonorString);
        // Set Emergency Contact
        contentView.setTextViewText(R.id.notificationEmergencyContactTextView,
                "Emergency Contact:"+sharedPreferences.getString(EMERGENCY_CONTACT_KEY, EMERGENCY_CONTACT_KEY));
        // Set Age
        int years = 0;
        Calendar dob = Calendar.getInstance();
        String dobArr[] = sharedPreferences.getString(DATE_OF_BIRTH_KEY, "").split("/");
        dob.set(Integer.parseInt(dobArr[2]), Integer.parseInt(dobArr[0]), Integer.parseInt(dobArr[1]));
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR))
            age--;
        years =  age;
        contentView.setTextViewText(R.id.notificationAgeTextView, "Age: "+ years+" yrs");
        // Set Allergies
        contentView.setTextViewText(R.id.notificationAlergiesTextView, "Allergies:"+sharedPreferences.getString(ALLERGIES_KEY, ALLERGIES_KEY));
        // Set Conditions
        contentView.setTextViewText(R.id.notificationConditionsTextView, "Conditions:"+sharedPreferences.getString(CONDITIONS_KEY, CONDITIONS_KEY));
    }

    private class HTTPRequestTask extends AsyncTask {

        @Override
        protected void onPostExecute(Object response) {
            if(response == null) {
                makeAlertDialog(" No Response from Google Distance Matrix API");
            }
            extractAndDisplayLocation((HttpResponse)response);
        }

        @Override
        protected Object doInBackground(Object[] params) {
            HttpResponse response = null;
            HttpClient client = new DefaultHttpClient();
            HttpPost httppost = (HttpPost) params[0];
            try {
                response = client.execute(httppost);
            } catch (IOException ioe) {ioe.printStackTrace();}
            return response;
        }
    }
}
