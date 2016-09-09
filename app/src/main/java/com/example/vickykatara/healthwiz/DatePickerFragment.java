package com.example.vickykatara.healthwiz;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.DatePicker;
import android.widget.TextView;

import java.util.Calendar;

/**
 * Created by Vicky Katara on 31-Aug-16.
 */
public class DatePickerFragment extends DialogFragment
        implements DatePickerDialog.OnDateSetListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int year = 2000, month = 1, day = 1;
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(
                "com.example.vickykatara.healthwiz", Context.MODE_PRIVATE);

        String dateOfBirth = sharedPreferences.getString(MainActivity.DATE_OF_BIRTH_KEY, null);

        if(dateOfBirth == null){
            final Calendar c = Calendar.getInstance();
            year = c.get(Calendar.YEAR);
            month = c.get(Calendar.MONTH);
            day = c.get(Calendar.DAY_OF_MONTH);
        } else {
            String[] arr = dateOfBirth.split("/");
            year = Integer.parseInt(arr[2]);
            month = Integer.parseInt(arr[0]) - 1;
            day = Integer.parseInt(arr[1]);
        }
        // Use the current date as the default date in the picker

        // Create a new instance of DatePickerDialog and return it
        return new DatePickerDialog(getActivity(), this, year, month, day);
    }

    public void onDateSet(DatePicker view, int year, int month, int day) {
        TextView t = (TextView)getActivity().findViewById(R.id.textViewDateOfBirth);
        t.setText((view.getMonth()+1)+"/"+view.getDayOfMonth()+"/"+view.getYear());
    }
}
