package com.group2.boss;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<String> arrList;
    ArrayAdapter adapter;
    Timer myTimer;

    int t = 14;
    XYPlot plot = null;
    final Number[] domainLabels = {1, 2, 3, 6, 7, 8, 9, 10, 13, 14};
    Number[] series1Numbers = {1, 4, 2, 8, 4, 16, 8, 32, 16, 64};
    SimpleXYSeries series1 = new SimpleXYSeries(Arrays.asList(domainLabels),
            Arrays.asList(series1Numbers), "Series1");

    private static int period = 1000;
    //private int count = 0; //Count is just for debugging purposes to ensure names are being updated

    public static final String SPECIFIC_APP_MESSAGE = "com.group2.boss.SPECIFIC";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ProgressBar progressBar = findViewById(R.id.determinateBar);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));
        progressBar.setProgress(0);


        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.plot);
//        plot.setRenderMode(Plot.RenderMode.USE_BACKGROUND_THREAD);

//        // create a couple arrays of y-values to plot:
//        final Number[] domainLabels = {1, 2, 3, 6, 7, 8, 9, 10, 13, 14};
//
//        Number[] series1Numbers = {1, 4, 2, 8, 4, 16, 8, 32, 16, 64};

        // turn the above arrays into XYSeries':
        // (Y_VALS_ONLY means use the element index as the x value)


        // create formatters to use for drawing a series using LineAndPointRenderer
        // and configure them from xml:
        LineAndPointFormatter series1Format =
                new LineAndPointFormatter(Color.RED, null, null, null);


//        // just for fun, add some smoothing to the lines:
//        // see: http://androidplot.com/smooth-curves-and-androidplot/
//        series1Format.setInterpolationParams(
//                new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal));
//
//        series2Format.setInterpolationParams(
//                new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal));

        // add a new series' to the xyplot:
        plot.addSeries(series1, series1Format);

//        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
//            @Override
//            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
//                return null;
////                int i = Math.round(((Number) obj).floatValue());
////                return toAppendTo.append(domainLabels[i]);
//            }
//            @Override
//            public Object parseObject(String source, ParsePosition pos) {
//                return null;
//            }
//        });


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

            System.out.println("BATLEVEL: " + batLevel);
            ProgressBar progressBar = findViewById(R.id.determinateBar);
            progressBar.setProgress(batLevel);


            series1.addLast(++t, batLevel);
            plot.redraw();

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
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));

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
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void notifyUser(Context ctx) {
        // https://stackoverflow.com/questions/17915670/android-push-notification-by-battery-percentage-without-launching-the-app
        // https://www.vogella.com/tutorials/AndroidNotifications/article.html
        System.out.println("Attempting to send notification");
        int NOTIFICATION_ID = 234;
        String CHANNEL_ID = "my_channel_01";
        NotificationManager notificationManager
                = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            CharSequence name = "my_channel";
            String Description = "This is my channel";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.setDescription(Description);
            mChannel.enableLights(true);
            mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            mChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(mChannel);
        }

        Notification.Builder builder = new Notification.Builder(ctx, CHANNEL_ID);
        Intent intent = new Intent(ctx, MainActivity.class);
        // use System.currentTimeMillis() to have a unique ID for the pending intent
        PendingIntent pIntent = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), intent, 0);
        builder
                .setContentTitle("Battery Alert")
                .setContentText("Device battery has fallen below provided threshold")
                .setSmallIcon(R.drawable.boss)
                .setContentIntent(pIntent)
                .setAutoCancel(true).build();


        Notification notif = builder.build();
        notificationManager.notify(NOTIFICATION_ID, notif);
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