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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
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
import static com.example.vickykatara.healthwiz.MainActivity.*;

public class TestingActivity extends AppCompatActivity implements SensorEventListener {

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

    private static double DEFAULT_LATTITUDE = 35.769945;
    private static double DEFAULT_LONGITUDE = -78.676402;

    private SensorManager mSensorManager;
    private Sensor mPressure, mLight;

    private Thread fallGenerator;
    private Thread distanceFetcher;

    private boolean noPressureSensor = false;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
//    private GoogleApiClient client;
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

        distanceFetcher = new Thread(new DistanceFetcher(getApplicationContext()));
        distanceFetcher.start();
//        makeAlertDialog(mSensorManager.getSensorList(Sensor.TYPE_PRESSURE).toString());

//        System.out.println(mSensorManager.getSensorList(Sensor.TYPE_PRESSURE).toString());

        updateLight();
        updatePressure();
        updateFall();
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
                        }
                    }
                }
        );
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
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        mSensorManager.unregisterListener(this);
        fallGenerator.interrupt();
        distanceFetcher.interrupt();
    }

    public void makeAlertDialog(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

    private class DistanceFetcher extends FragmentActivity
            implements Runnable,
            GoogleApiClient.OnConnectionFailedListener,
            GoogleApiClient.ConnectionCallbacks,
            LocationListener {
        Handler handler = new Handler(Looper.getMainLooper());
        private boolean connectionActive = true;
        private GoogleApiClient mGoogleApiClient;

        LocationRequest locationRequest;
        Location mLastLocation;

        private Context context;
        String prevUrl;


        double prevLat = -1999, prevLong = -19999;
        boolean prevComputationCompleted = false;

        DistanceFetcher(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            StringBuilder stringBuilder = new StringBuilder();
            // Create an instance of GoogleAPIClient.
            if (mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(context)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
            }

            locationRequest = new LocationRequest();
            locationRequest.setInterval(10*1000);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


            if (mGoogleApiClient != null && !mGoogleApiClient.isConnected() ) {
                mGoogleApiClient.connect();
            }

            double selfLatitude = DEFAULT_LATTITUDE, selfLongitude = DEFAULT_LONGITUDE;

            final LocationListener listener = this;

            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                        }
                        if (mGoogleApiClient.isConnected()) {
                            LocationServices
                                    .FusedLocationApi
                                    .requestLocationUpdates
                                            (mGoogleApiClient, locationRequest, listener);
                        } else {
                            System.err.println(" Not Connected ");
                        }
                        }
                    }
            );

            while (true) {
                try {
                    Thread.sleep(10000); // change to 60000
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(mockTimeToHospital) {
                    continue;
                }

                if(mLastLocation != null) {
                    selfLatitude = mLastLocation.getLatitude();
                    selfLongitude = mLastLocation.getLongitude();
                }

                String urlString =
                        "https://maps.googleapis.com/maps/api/distancematrix/json?units=imperial&origins="
                                +selfLatitude+","+selfLongitude
                                +"&destinations="+
                                REX_LATTITUDE+","+REX_LONGITUDE
                                +"&key=AIzaSyCxps88hh-xEcbDLMkBtVblla6xmMVlXN4";

                if(urlString.equals(prevUrl) && prevComputationCompleted ||
                        differBy1000s(prevLat, selfLatitude) &&
                                differBy1000s(prevLong, selfLongitude) &&
                                prevComputationCompleted) {
                    System.out.println(" No change in location. Using old Time");
                    continue;
                } else {
                    System.out.println("New Query:"+urlString);
                    prevUrl = urlString;
                    prevLat = selfLatitude;
                    prevLong = selfLongitude;
                }

                try {

                    HttpPost httppost = new HttpPost(urlString);

                    HttpClient client = new DefaultHttpClient();
                    HttpResponse response;
                    stringBuilder = new StringBuilder();


                    response = client.execute(httppost);
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
                if(stringBuilder.length() == 0)
                    continue;
                try {
                    jsonObject = new JSONObject(stringBuilder.toString());
//                    makeAlertDialog(jsonObject.toString());

                    JSONArray jsonRowsArray = jsonObject.getJSONArray("rows");
//                    makeAlertDialog(jsonRowsArray.toString());

                    if(jsonRowsArray.length() < 1) {
                        System.err.println("jsonRowsArray Empty: " + firstTuple.toString());
                        continue;
                    }

                    JSONObject jsonRowsObject = jsonRowsArray.getJSONObject(0);

                    JSONArray jsonElementsArray = jsonRowsObject.getJSONArray("elements");

//                    makeAlertDialog(jsonObject.toString());
                    if(jsonElementsArray.length() < 1) {
                        System.err.println("jsonElementsArray Empty: " + firstTuple.toString());
                        continue;
                    }

                    firstTuple = jsonElementsArray.getJSONObject(0);

                    if(!firstTuple.get("status").equals("OK")) {
                        System.err.println("Distance Matrix Reply Status not Ok: " + firstTuple.toString());
                        continue;
                    }

                    JSONObject jsonDurationObject = firstTuple.getJSONObject("duration");

                    lastCapturedSecsToHospital = (Integer) jsonDurationObject.get("value");

                    lastCapturedTimeToHospitalString = (String)jsonDurationObject.get("text");

                    prevComputationCompleted = true;
                } catch (JSONException jse) {
                    System.out.println(firstTuple.toString());
                    jse.printStackTrace();
                }
                handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                updateTimeToHospital();
                            }
                        }
                );
            }
        }

        boolean differBy1000s(double x, double y) {
            return decimalPlus3String(x).equals(decimalPlus3String(x));
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
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            lastCapturedSecsToHospital = -1;
                            lastCapturedTimeToHospitalString = " ~No Connection~ ";
                            updateTimeToHospital();
                        }
                    }
            );
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) { connectionActive = true; }

        @Override
        public void onConnectionSuspended(int i) { connectionActive = false; }

        @Override
        public void onLocationChanged(Location location) {
            mLastLocation = location;
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
}
