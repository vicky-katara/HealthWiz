package com.example.vickykatara.healthwiz;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    public static String USA_ISO = "US";
    public static String DEFAULT_NAME = "Vicky Katara";
    public static String DEFAULT_BLOOD_GROUP = "B+";
    public static boolean DEFAULT_ORGAN_DONOR = true;
    public static String DEFAULT_EMERGENCY_CONTACT = "(984) 215-8067";
    public static String DEFAULT_DATE_OF_BIRTH = "12/31/1990";
    public static String DEFAULT_ALLERGIES = "Soya, peanuts";
    public static String DEFAULT_CONDITIONS = "Lactose Intolerant";

    public static String NAME_KEY = "NAME";
    public static String BLOOD_GROUP_KEY = "BLOOD_GROUP";
    public static String ORGAN_DONOR_KEY = "ORGAN_DONOR";
    public static String EMERGENCY_CONTACT_KEY = "EMERGENCY_CONTACT";
    public static String DATE_OF_BIRTH_KEY = "DATE_OF_BIRTH";
    public static String ALLERGIES_KEY = "ALLERGIES";
    public static String CONDITIONS_KEY = "CONDITIONS";

    public static SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = this.getSharedPreferences(
                "com.example.vickykatara.healthwiz", Context.MODE_PRIVATE);
        saveDataIfDoesntExist();
        System.out.println("Defaults: "+sharedPreferences.getAll());
        setContentView(R.layout.activity_main);
        setDefaults();
        // while saving, purposefully run the two commands below, pass v = null
    }

    private void saveDataIfDoesntExist() {
        boolean exists = sharedPreferences.contains("DEFAULT_VALUES_ADDED");
        if(!exists) {
            sharedPreferences.edit().putBoolean("DEFAULT_VALUES_ADDED", true).apply();
            sharedPreferences.edit().putString(NAME_KEY, DEFAULT_NAME).apply();
            sharedPreferences.edit().putString(BLOOD_GROUP_KEY, DEFAULT_BLOOD_GROUP).apply();
            sharedPreferences.edit().putBoolean(ORGAN_DONOR_KEY, DEFAULT_ORGAN_DONOR).apply();
            sharedPreferences.edit().putString(EMERGENCY_CONTACT_KEY, DEFAULT_EMERGENCY_CONTACT).apply();
            sharedPreferences.edit().putString(DATE_OF_BIRTH_KEY, DEFAULT_DATE_OF_BIRTH).apply();
            sharedPreferences.edit().putString(ALLERGIES_KEY, DEFAULT_ALLERGIES).apply();
            sharedPreferences.edit().putString(CONDITIONS_KEY, DEFAULT_CONDITIONS).apply();
        }
    }

    private void setDefaults() {
        // Set name
        EditText editText = (EditText)findViewById(R.id.editTextName);
        editText.setText(sharedPreferences.getString(NAME_KEY, DEFAULT_NAME));

        // Set Blood Group
        Spinner spinner = (Spinner) findViewById(R.id.bloodGroupSpinner);
        SpinnerAdapter spinnerAdapter = spinner.getAdapter();
        String bloodGroup = sharedPreferences.getString(BLOOD_GROUP_KEY, "");
        int numItems = spinnerAdapter.getCount(), index = 0;
        for(int i=0; i<numItems; i++) {
            if(((String)spinnerAdapter.getItem(i)).equals(bloodGroup)) {
                index = i;
                break;
            }
        }
        spinner.setSelection(index);

        // set Organ Donor Check Box
        CheckBox organDonorCheckBox = (CheckBox)findViewById(R.id.organDonorCheckBox);
        organDonorCheckBox.setChecked(sharedPreferences.getBoolean(ORGAN_DONOR_KEY, true));

        // set Emergency Contact
        editText = (EditText)findViewById(R.id.editTextEmergencyContact);
        editText.setText(sharedPreferences.getString(EMERGENCY_CONTACT_KEY, DEFAULT_EMERGENCY_CONTACT));

        // set Date Of Birth
        TextView textView = (TextView)findViewById(R.id.textViewDateOfBirth);
        textView.setText(sharedPreferences.getString(DATE_OF_BIRTH_KEY, DEFAULT_DATE_OF_BIRTH));

        // set Allergies
        editText = (EditText)findViewById(R.id.editTextAlergies);
        editText.setText(sharedPreferences.getString(ALLERGIES_KEY, DEFAULT_ALLERGIES));

        // set Conditions
        editText = (EditText)findViewById(R.id.editTextConditions);
        editText.setText(sharedPreferences.getString(CONDITIONS_KEY, DEFAULT_CONDITIONS));
    }


    public void showDatePickerDialog(View v) {
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getSupportFragmentManager(), "datePicker");
    }

    public void formatTextEmergencyContact(View v) {
        TextView t = (TextView)findViewById(R.id.editTextEmergencyContact);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            t.setText(PhoneNumberUtils.formatNumber(t.getText().toString(), USA_ISO));
        } else {
            PhoneNumberUtils.formatNumber(t.getEditableText(), PhoneNumberUtils.FORMAT_NANP);
        }
    }

    public void updateSave(View v) {
        // save name
        sharedPreferences.edit().putString(NAME_KEY, getTextFromEditText(R.id.editTextName)).apply();
        // save blood group
        sharedPreferences.edit().putString(BLOOD_GROUP_KEY,
                (String)((Spinner)findViewById(R.id.bloodGroupSpinner)).getSelectedItem()).apply();
        // save Organ Donor
        sharedPreferences.edit().putBoolean(ORGAN_DONOR_KEY,
                ((CheckBox)findViewById(R.id.organDonorCheckBox)).isChecked()).apply();
        // save Emergency Contact
        sharedPreferences.edit().putString(EMERGENCY_CONTACT_KEY, getTextFromEditText(R.id.editTextEmergencyContact)).apply();
        // save Date of Birth
        sharedPreferences.edit().putString(DATE_OF_BIRTH_KEY, getTextFromTextView(R.id.textViewDateOfBirth)).apply();
        // save Allergies
        sharedPreferences.edit().putString(ALLERGIES_KEY, getTextFromEditText(R.id.editTextAlergies)).apply();
        // save Conditions
        sharedPreferences.edit().putString(CONDITIONS_KEY, getTextFromEditText(R.id.editTextConditions)).apply();
        // show
//        makeAlertDialog(sharedPreferences.getAll().toString());
//        notifyMe();
    }

    public void startTestingActivity(View v) {
        Intent intent = new Intent(this, TestingActivity.class);
        startActivity(intent);
    }

    private void notifyMe() {
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

    private String getTextFromEditText(int id) {
        return ((EditText)findViewById(id)).getText().toString();
    }

    private String getTextFromTextView(int id) {
        return ((TextView)findViewById(id)).getText().toString();
    }

    public void makeAlertDialog(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

