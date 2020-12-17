package com.group2.boss;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.Service;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.os.Message;
import android.view.View;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.provider.Settings;


public class MainActivity extends AppCompatActivity {

    int battery_threshold_pref;
    boolean isNotified;

    private static class BatteryDisplayViews {
        TextView isCharging;
        TextView timeLeft;
        ProgressBar progressBar;
        XYPlot plot;
    }
    private class BatteryInfo {
        int batLevel;
        boolean isCharging;
        long timeLeft;
        public BatteryInfo (int batLevel, boolean isCharging) {
            this.batLevel=batLevel;
            this.isCharging=isCharging;
            this.timeLeft = -1;
        }
    }

    private BatteryDataSource batteryDataSource;
    private BatteryDisplayViews batteryDisplayViews;
    private BatteryInfo batteryInfo;


    private int batteryProfileIndex;
    private final int SAMPLE_SIZE = 100;
    private SimpleXYSeries batteryProfileSeries;
    private Handler updateUIHandler = null;
    private void createUpdateUiHandler() {
        if(updateUIHandler == null) {
            updateUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) { updateUI((BatteryInfo)msg.obj);
                }
            };
        }
    }

    private void updateUI(BatteryInfo batteryInfo) {
        if (batteryInfo.isCharging) {
            isNotified = false;
            batteryDisplayViews.isCharging.setText(String.valueOf("Currently Charging"));
            batteryDisplayViews.progressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
            if (batteryInfo.timeLeft == -1)
                batteryDisplayViews.timeLeft.setText(String.valueOf("Can't calculate time")  );
            else if (batteryInfo.timeLeft == 0)
                batteryDisplayViews.timeLeft.setText(String.valueOf("Fully charged") );
            else
                batteryDisplayViews.timeLeft.setText(String.valueOf("Time until charged: " + String.valueOf(batteryInfo.timeLeft / 1000) + " s." ));
        }
        else {
            batteryDisplayViews.isCharging.setText(String.valueOf("Not Charging"));
            batteryDisplayViews.progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));
            batteryDisplayViews.timeLeft.setText(String.valueOf("Time until charged: Never!"));
        }
        batteryDisplayViews.progressBar.setProgress(batteryInfo.batLevel);
    }
    // update the plot in the new thread and passing message to the main thread to update the UI
    private class BatteryDisplayUpdater implements Observer {
        BatteryDisplayViews batteryDisplayViews;
        public BatteryDisplayUpdater(BatteryDisplayViews batteryDisplayViews) {
            this.batteryDisplayViews = batteryDisplayViews;
        }
        @Override
        public void update(Observable o, Object arg) {
            BatteryInfo batteryInfo = (BatteryInfo)arg;
            Message message = new Message();
            message.obj = batteryInfo;
            if (++batteryProfileIndex > SAMPLE_SIZE) {
                batteryProfileSeries.removeFirst();
                batteryDisplayViews.plot.setDomainBoundaries(0, SAMPLE_SIZE, BoundaryMode.AUTO);
            }
            batteryProfileSeries.addLast(++batteryProfileIndex, batteryInfo.batLevel);
            batteryDisplayViews.plot.redraw();
            updateUIHandler.sendMessage(message);
        }
    }

    class BatteryDataSource implements Runnable {
        class BatteryObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers(batteryInfo);
            }
        }
        private boolean keepRunning = false;
        private final BatteryObservable notifier = new BatteryObservable();

        private final Context ctx = getApplicationContext();
        private final BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
        private final BatteryInfo batteryInfo = new BatteryInfo(0, false);

        void stopThread() {
            keepRunning = false;
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void run() {
            try {
                keepRunning = true;
                IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus;

                int status;

                while (keepRunning) {
                    Thread.sleep(100); // decrease or remove to speed up the refresh rate.
                    batteryInfo.batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

                    batteryStatus = ctx.registerReceiver(null, iFilter);
                    status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                    batteryInfo.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
                    try {
                        battery_threshold_pref = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null));
                    }
                    catch (NumberFormatException e) {
                        battery_threshold_pref = 0;
                    }

                    if (batteryInfo.batLevel <= battery_threshold_pref && !batteryInfo.isCharging && !isNotified ) {
                        System.out.println("BELOW THRESHOLD!!!");
                        notifyUser(ctx);
                        isNotified = true;
                    }
                    if (batteryInfo.isCharging) {
                        batteryInfo.timeLeft = bm.computeChargeTimeRemaining();
                    }
                    else batteryInfo.timeLeft = -1;

                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }
        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }
    }

    ListView listView;
    ArrayList<String> templateList, uiList;
    ArrayAdapter adapter;
    Timer myTimer;

//    private static int period = 1000;
    //private int count = 0; //Count is just for debugging purposes to ensure names are being updated
    public static final String SPECIFIC_APP_MESSAGE = "com.group2.boss.SPECIFIC";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getUsageStatsList(this).isEmpty()){
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        }
        if (savedInstanceState == null) {
            Context ctx = getApplicationContext();
            BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);

            batteryDisplayViews = new BatteryDisplayViews();
            batteryDisplayViews.progressBar = (ProgressBar) findViewById(R.id.determinateBar);
            batteryDisplayViews.isCharging = (TextView) findViewById(R.id.is_charging);
            batteryDisplayViews.timeLeft = (TextView) findViewById(R.id.timeToCharge);
            batteryDisplayViews.progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));
            batteryDisplayViews.plot = (XYPlot) findViewById(R.id.plot);

            BatteryDisplayUpdater batteryDisplayUpdater = new BatteryDisplayUpdater(batteryDisplayViews);

            battery_threshold_pref = 0;
            isNotified = false;

            batteryDisplayViews.plot.setRangeBoundaries(0, 100, BoundaryMode.FIXED);
            batteryDisplayViews.plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 25);
            batteryDisplayViews.plot.setDomainBoundaries(0, SAMPLE_SIZE, BoundaryMode.FIXED);
            batteryDisplayViews.plot.setDomainStep(StepMode.INCREMENT_BY_VAL, SAMPLE_SIZE / 4);

            batteryDataSource = new BatteryDataSource();
            
            batteryProfileIndex = 0;
            Number[] domainLabels = {batteryProfileIndex};
            Number[] series1Numbers = {bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)};
            batteryProfileSeries = new SimpleXYSeries(Arrays.asList(domainLabels), Arrays.asList(series1Numbers), "Battery Profile");

            batteryDataSource.addObserver(batteryDisplayUpdater);

            LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                    Color.rgb(0, 200, 0), null, null, null);
            formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
            formatter1.getLinePaint().setStrokeWidth(10);

            batteryDisplayViews.plot.addSeries(batteryProfileSeries, formatter1);
            createUpdateUiHandler();

            batteryDisplayViews.plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
                @Override
                public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                    int i = Math.round(((Number) obj).floatValue());
                    return toAppendTo.append("");
                }
                @Override
                public Object parseObject(String source, ParsePosition pos) {
                    return null;
                }
            });
        }
        listView = (ListView)findViewById(R.id.AppList);
        templateList = new ArrayList<>();
        uiList = new ArrayList<>();
        uiList.add("Temp");
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, uiList);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                System.out.println("Clicked element! ID: " + id + " Position: " + position);
                Intent intent = new Intent(getApplicationContext(), SpecificAppActivity.class);
                String name = uiList.get(position);
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
    public void onResume() {
        // kick off the data generating thread
        Thread batteryMonitorThread = new Thread(batteryDataSource);
        batteryMonitorThread.start();
        super.onResume();
    }

    @Override
    public void onPause() {
        batteryDataSource.stopThread();
        super.onPause();
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

    private void refreshTemplateList() {
        Context ctx = getApplicationContext();
        PackageManager packageManager = ctx.getPackageManager();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -1);
        long startTime = calendar.getTimeInMillis();

        UsageEvents uEvents = usm.queryEvents(startTime, endTime);
        templateList.clear();
        while (uEvents.hasNextEvent()) {
            // https://stackoverflow.com/questions/21215152/how-to-get-names-of-background-running-apps-in-android
            UsageEvents.Event e = new UsageEvents.Event();
            uEvents.getNextEvent(e);
            String packageName = e.getPackageName();
            try {
                String name = packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString();
                templateList.add(name);
            } catch (NameNotFoundException nameNotFoundException) {

            }
        }
        Set<String> set = new LinkedHashSet<>();
        set.addAll(templateList);
        templateList.clear();
        templateList.addAll(set);
        ListIterator<String> iter = templateList.listIterator();
        while (iter.hasNext()) {
            if (iter.next().startsWith("com.")) {
                iter.remove();
            }
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            Boolean switchPref = sharedPref.getBoolean
                    (SettingsActivity.KEY_PREF_SWITCH_1, false);
            String thresholdPref = sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null);
            if (switchPref) {
                Collections.sort(templateList);
            } else {
                // Sort alphabetically descending
                // Conditional just to not add too much noise to logs
                //if (count % 5 == 0)
                //    System.out.println("Descending pref");
                Collections.sort(templateList, Collections.reverseOrder());
            }
    }

    private int getBatteryLevel() {
        Context ctx = getApplicationContext();
        BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
//        TextView is_charging_view = (TextView)findViewById(R.id.is_charging);
//        TextView time_left_view = (TextView)findViewById(R.id.timeToCharge);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void TimerMethod() {
        this.refreshTemplateList();
//        batteryLevel = getBatteryLevel();
//        this.runOnUiThread(Update_Battery_Charging_Status);
        this.runOnUiThread(Update_List);
    }

    private Runnable Update_Battery_Charging_Status = new Runnable() {
        @Override
        public void run() {
            Context ctx = getApplicationContext();
//            BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
//            TextView is_charging_view = (TextView)findViewById(R.id.is_charging);
//            TextView time_left_view = (TextView)findViewById(R.id.timeToCharge);
//            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
//
//            System.out.println("BATLEVEL: " + batLevel);
//            ProgressBar progressBar = findViewById(R.id.determinateBar);
//            progressBar.setProgress(batteryLevel);
////
//
            //.addLast(++t, batteryLevel);
            //plot.redraw();
//
//            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
//            int battery_threshold_pref;
//            try {
//                battery_threshold_pref = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null));
//            }
//            catch (NumberFormatException e) {
//                battery_threshold_pref = 0;
//            }
//
//            if (batLevel <= battery_threshold_pref) {
//                System.out.println("BELOW THRESHOLD!!!");
//                notifyUser(ctx);
//            }
//

        }
    };

    private final Runnable Update_List = new Runnable() {
        public void run() {
            adapter.clear();
            uiList.clear();
            uiList.addAll(templateList);
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

    private static List<UsageStats> getUsageStatsList(Context context){
        UsageStatsManager usm =  (UsageStatsManager) context.getSystemService(context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -1);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,startTime,endTime);
        return usageStatsList;
    }

    private static boolean isForeground(Context ctx, String myPackage){
        ActivityManager manager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfo = manager.getRunningTasks(1);

        ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
        if(componentInfo.getPackageName().equals(myPackage)) {
            return true;
        }
        return false;
    }
}

//public class MainActivity extends AppCompatActivity {
//
//    // redraws a plot whenever an update is received:
//    private class MyPlotUpdater implements Observer {
//        Plot plot;
//
//        public MyPlotUpdater(Plot plot) {
//            this.plot = plot;
//        }
//
//        @Override
//        public void update(Observable o, Object arg) {
//            plot.redraw();
//        }
//    }
//
//    private XYPlot dynamicPlot;
//    private MyPlotUpdater plotUpdater;
//    SampleDynamicXYDatasource data;
//    private Thread myThread;
//
//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//
//        // android boilerplate stuff
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        // get handles to our View defined in layout.xml:
//        dynamicPlot = (XYPlot) findViewById(R.id.plot);
//
//        plotUpdater = new MyPlotUpdater(dynamicPlot);
//
//        // only display whole numbers in domain labels
//        dynamicPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).
//                setFormat(new DecimalFormat("0"));
//
//        // getInstance and position datasets:
//        data = new SampleDynamicXYDatasource();
//        SampleDynamicSeries sine1Series = new SampleDynamicSeries(data, 0, "Sine 1");
//        SampleDynamicSeries sine2Series = new SampleDynamicSeries(data, 1, "Sine 2");
//
//        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
//                Color.rgb(0, 200, 0), null, null, null);
//        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
//        formatter1.getLinePaint().setStrokeWidth(10);
//        dynamicPlot.addSeries(sine1Series,
//                formatter1);
//
//        LineAndPointFormatter formatter2 =
//                new LineAndPointFormatter(Color.rgb(0, 0, 200), null, null, null);
//        formatter2.getLinePaint().setStrokeWidth(10);
//        formatter2.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
//
//        //formatter2.getFillPaint().setAlpha(220);
//        dynamicPlot.addSeries(sine2Series, formatter2);
//
//        // hook up the plotUpdater to the data model:
//        data.addObserver(plotUpdater);
//
//        // thin out domain tick labels so they dont overlap each other:
//        dynamicPlot.setDomainStepMode(StepMode.INCREMENT_BY_VAL);
//        dynamicPlot.setDomainStepValue(5);
//
//        dynamicPlot.setRangeStepMode(StepMode.INCREMENT_BY_VAL);
//        dynamicPlot.setRangeStepValue(10);
//
//        dynamicPlot.getGraph().getLineLabelStyle(
//                XYGraphWidget.Edge.LEFT).setFormat(new DecimalFormat("###.#"));
//
//        // uncomment this line to freeze the range boundaries:
//        dynamicPlot.setRangeBoundaries(-100, 100, BoundaryMode.FIXED);
//
//        // create a dash effect for domain and range grid lines:
//        DashPathEffect dashFx = new DashPathEffect(
//                new float[] {PixelUtils.dpToPix(3), PixelUtils.dpToPix(3)}, 0);
//        dynamicPlot.getGraph().getDomainGridLinePaint().setPathEffect(dashFx);
//        dynamicPlot.getGraph().getRangeGridLinePaint().setPathEffect(dashFx);
//    }
//
//    @Override
//    public void onResume() {
//        // kick off the data generating thread:
//        myThread = new Thread(data);
//        myThread.start();
//        super.onResume();
//    }
//
//    @Override
//    public void onPause() {
//        data.stopThread();
//        super.onPause();
//    }
//
//    class SampleDynamicXYDatasource implements Runnable {
//
//        // encapsulates management of the observers watching this datasource for update events:
//        class MyObservable extends Observable {
//            @Override
//            public void notifyObservers() {
//                setChanged();
//                super.notifyObservers();
//            }
//        }
//
//        private static final double FREQUENCY = 5; // larger is lower frequency
//        private static final int MAX_AMP_SEED = 100;
//        private static final int MIN_AMP_SEED = 10;
//        private static final int AMP_STEP = 1;
//        static final int SINE1 = 0;
//        static final int SINE2 = 1;
//        private static final int SAMPLE_SIZE = 31;
//        private int phase = 0;
//        private int sinAmp = 1;
//        private MyObservable notifier;
//        private boolean keepRunning = false;
//
//        {
//            notifier = new MyObservable();
//        }
//
//        void stopThread() {
//            keepRunning = false;
//        }
//
//        //@Override
//        public void run() {
//            try {
//                keepRunning = true;
//                boolean isRising = true;
//                while (keepRunning) {
//
//                    Thread.sleep(10); // decrease or remove to speed up the refresh rate.
//                    phase++;
//                    if (sinAmp >= MAX_AMP_SEED) {
//                        isRising = false;
//                    } else if (sinAmp <= MIN_AMP_SEED) {
//                        isRising = true;
//                    }
//
//                    if (isRising) {
//                        sinAmp += AMP_STEP;
//                    } else {
//                        sinAmp -= AMP_STEP;
//                    }
//                    notifier.notifyObservers();
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        int getItemCount(int series) {
//            return SAMPLE_SIZE;
//        }
//
//        Number getX(int series, int index) {
//            if (index >= SAMPLE_SIZE) {
//                throw new IllegalArgumentException();
//            }
//            return index;
//        }
//
//        Number getY(int series, int index) {
//            if (index >= SAMPLE_SIZE) {
//                throw new IllegalArgumentException();
//            }
//            double angle = (index + (phase))/FREQUENCY;
//            double amp = sinAmp * Math.sin(angle);
//            switch (series) {
//                case SINE1:
//                    return amp;
//                case SINE2:
//                    return -amp;
//                default:
//                    throw new IllegalArgumentException();
//            }
//        }
//
//        void addObserver(Observer observer) {
//            notifier.addObserver(observer);
//        }
//
//        public void removeObserver(Observer observer) {
//            notifier.deleteObserver(observer);
//        }
//    }
//
//    class SampleDynamicSeries implements XYSeries {
//        private SampleDynamicXYDatasource datasource;
//        private int seriesIndex;
//        private String title;
//
//        SampleDynamicSeries(SampleDynamicXYDatasource datasource, int seriesIndex, String title) {
//            this.datasource = datasource;
//            this.seriesIndex = seriesIndex;
//            this.title = title;
//        }
//
//        @Override
//        public String getTitle() {
//            return title;
//        }
//
//        @Override
//        public int size() {
//            return datasource.getItemCount(seriesIndex);
//        }
//
//        @Override
//        public Number getX(int index) {
//            return datasource.getX(seriesIndex, index);
//        }
//
//        @Override
//        public Number getY(int index) {
//            return datasource.getY(seriesIndex, index);
//        }
//    }
//}
