package com.group2.boss;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> arrList;
    ArrayAdapter adapter;
    Timer myTimer;

    private static int period = 1000;
    //private int count = 0; //Count is just for debugging purposes to ensure names are being updated

    public static final String SPECIFIC_APP_MESSAGE = "com.group2.boss.SPECIFIC";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listView = (ListView)findViewById(R.id.AppList);
        arrList = new ArrayList<>();
        arrList.add("Temp");
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, arrList);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                System.out.println("Clicked element! ID: " + id + " Position: " + position);
                Intent intent = new Intent(getApplicationContext(), SpecificAppActivity.class);
                String name = arrList.get(position);
                intent.putExtra(SPECIFIC_APP_MESSAGE, name);
                startActivity(intent);
            }
        });

        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                TimerMethod();
            }

        }, 0, 1000);

        listView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void TimerMethod() {
        this.runOnUiThread(Update_List);
        this.runOnUiThread(Update_Battery_Charging_Status);
    }

    private Runnable Update_Battery_Charging_Status = new Runnable() {
        @Override
        public void run() {
            Context ctx = getApplicationContext();
            BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
            TextView is_charging_view = (TextView)findViewById(R.id.is_charging);
            TextView time_left_view = (TextView)findViewById(R.id.timeToCharge);
            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            //System.out.println("BATLEVEL: " + batLevel);

            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = ctx.registerReceiver(null, ifilter);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            int battery_threshold_pref;
            try {
                battery_threshold_pref = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null));
            }
            catch (NumberFormatException e) {
                battery_threshold_pref = 0;
            }

            if (batLevel <= battery_threshold_pref) {
                System.out.println("BELOW THRESHOLD!!!");
                notifyUser(ctx);
            }

            if (isCharging) {
                is_charging_view.setText("Currently charging");
                long time_left = bm.computeChargeTimeRemaining();
                //System.out.println("Time left: " + time_left);
                if (time_left == -1)
                    time_left_view.setText("Can't calculate time");
                else if (time_left == 0)
                    time_left_view.setText("Fully charged");
                else
                    time_left_view.setText("Time until charged: " + String.valueOf(time_left / 1000) + " seconds.");
            }
            else {
                is_charging_view.setText("Not currently charging");
                time_left_view.setText(String.valueOf("Time until charged: Never!"));
            }
        }
    };

    private Runnable Update_List = new Runnable() {
        public void run() {
            System.out.println("Tick!");
            adapter.clear();
            Context ctx = getApplicationContext();
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfo = am.getRunningAppProcesses();

            for (int i = 0; i < runningAppProcessInfo.size(); i++) {
                String processName = runningAppProcessInfo.get(i).processName;
                //System.out.println(processName);
                if (isForeground(ctx, processName)) {

                    adapter.add(processName + "             -      Foreground");
                }
                else {
                    adapter.add(processName);
                }
            }
            if (runningAppProcessInfo.size() < 5) {
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");
                adapter.add("Padding!");

            }
            //count++;

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            Boolean switchPref = sharedPref.getBoolean
                    (SettingsActivity.KEY_PREF_SWITCH_1, false);
            String thresholdPref = sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null);
            //if (thresholdPref != null)
             //   System.out.println("Threshold value: " + thresholdPref);
            if (switchPref) {
                // Sort alphabetically ascending
                // Conditional just to not add too much noise to logs
                //if (count % 5 == 0)
                //   System.out.println("Ascending pref");
            }
            else {
                // Sort alphabetically descending
                // Conditional just to not add too much noise to logs
                //if (count % 5 == 0)
                //    System.out.println("Descending pref");
            }

            adapter.notifyDataSetChanged();
        }
    };

    private void notifyUser(Context ctx) {
        // https://stackoverflow.com/questions/17915670/android-push-notification-by-battery-percentage-without-launching-the-app
        // https://www.vogella.com/tutorials/AndroidNotifications/article.html
        System.out.println("Attempting to send notification");
        Notification.Builder builder = new Notification.Builder(ctx);
        Intent intent = new Intent(ctx, MainActivity.class);
        // use System.currentTimeMillis() to have a unique ID for the pending intent
        PendingIntent pIntent = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), intent, 0);
        Notification notif  = new Notification.Builder(this)
                .setContentTitle("Battery Alert")
                .setContentText("Device battery has fallen below provided threshold")
                .setSmallIcon(R.drawable.boss)
                .setContentIntent(pIntent)
                .setAutoCancel(true).build();

        NotificationManager notificationManager
                = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0, notif);
    }

    private static boolean isForeground(Context ctx, String myPackage){
        ActivityManager manager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        List< ActivityManager.RunningTaskInfo > runningTaskInfo = manager.getRunningTasks(1);

        ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
        if(componentInfo.getPackageName().equals(myPackage)) {
            return true;
        }
        return false;
    }
}