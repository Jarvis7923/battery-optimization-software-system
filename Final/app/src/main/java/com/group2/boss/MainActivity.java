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
import android.content.pm.ApplicationInfo;
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
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.provider.Settings;


public class MainActivity extends AppCompatActivity {

    // redraws a plot whenever an update is received:
    private class PlotUpdater implements Observer {
        Plot plot;
        public PlotUpdater(Plot plot) {
            this.plot = plot;
        }
        // update the plots
        @Override
        public void update(Observable o, Object arg) {
            plot.redraw();
        }
    }
    // update the progress bar
    private class ProgressBarUpdater implements Observer {
        ProgressBar progressBar;
        public ProgressBarUpdater(ProgressBar progressBar) {
            this.progressBar = progressBar;
        }
        @Override
        public void update(Observable o, Object arg) {
            progressBar.setProgress((int)arg);
        }
    }

    class BatteryDataSource implements Runnable {
        // encapsulates management of the observers watching this datasource for update events:
        class BatteryObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers(batLevel);
            }
        }
        private final BatteryObservable notifier = new BatteryObservable();
        private final Context ctx = getApplicationContext();
        private final BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);

        private boolean keepRunning = false;
        private int SAMPLE_SIZE = 31;
        private int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        void stopThread() {
            keepRunning = false;
        }

        @Override
        public void run() {
            try {
                keepRunning = true;
                while (keepRunning) {
                    //System.out.println("BATTERY: " + batLevel);
                    Thread.sleep(100); // decrease or remove to speed up the refresh rate.
                    batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        int getItemCount(int series) {
            return SAMPLE_SIZE;
        }

        int getBatLevel() {
            return batLevel;
        }

        Number getX(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            return index;
        }

        Number getY(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            return batLevel;
        }
        void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }
        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }
    }

    class DynamicSeries implements XYSeries {
        private BatteryDataSource dataSource;
        private int seriesIndex;
        private String title;

        DynamicSeries(BatteryDataSource dataSource, int seriesIndex, String title) {
            this.dataSource = dataSource;
            this.seriesIndex = seriesIndex;
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int size() {
            return dataSource.getItemCount(seriesIndex);
        }

        @Override
        public Number getX(int index) {
            return dataSource.getX(seriesIndex, index);
        }

        @Override
        public Number getY(int index) {
            return dataSource.getY(seriesIndex, index);
        }
    }

    ListView listView;
    ArrayList<String> templateList, uiList;
    ArrayAdapter adapter;
    Timer myTimer;

    int t = 10;
    private int batteryLevel = 0;
    private XYPlot plot;
    private PlotUpdater plotUpdater;
    private ProgressBarUpdater progressBarUpdater;

    private BatteryDataSource batteryDataSource;
    private Thread batteryMonitorThread;
//    final Number[] domainLabels = {1, 2, 3, 6, 7, 8, 9, 10};
//    Number[] series1Numbers = {1, 4, 2, 8, 4, 16, 8, 32};
//    private SimpleXYSeries series1 = new SimpleXYSeries(Arrays.asList(domainLabels), Arrays.asList(series1Numbers), "Battery Usage");

    private static int period = 1000;
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

        ProgressBar progressBar = findViewById(R.id.determinateBar);
        // todo: the progress bar color according to the charging status
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));


        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.plot);
        plot.setRangeBoundaries(0, 100, BoundaryMode.FIXED);
        plotUpdater = new PlotUpdater(plot);
        progressBarUpdater = new ProgressBarUpdater(progressBar);

        // getInstance and position datasets:
        batteryDataSource = new BatteryDataSource();
        batteryDataSource.addObserver(progressBarUpdater);

        DynamicSeries batteryDataSeries = new DynamicSeries(batteryDataSource, 0, "Sine 1");

        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                Color.rgb(0, 200, 0), null, null, null);
        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        formatter1.getLinePaint().setStrokeWidth(10);
        plot.addSeries(batteryDataSeries, formatter1);

        // hook up the plotUpdater to the data model:
        batteryDataSource.addObserver(plotUpdater);


        plot.setDomainStepMode(StepMode.INCREMENT_BY_VAL);
        plot.setDomainStepValue(5);

        plot.setRangeStepMode(StepMode.INCREMENT_BY_VAL);
        plot.setRangeStepValue(10);
//
//        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
//            @Override
//            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
//                int i = Math.round(((Number) obj).floatValue());
//                return toAppendTo.append(domainLabels[i]);
//            }
//            @Override
//            public Object parseObject(String source, ParsePosition pos) {
//                return null;
//            }
//        });

        listView = (ListView)findViewById(R.id.AppList);
        templateList = new ArrayList<>();
        uiList = new ArrayList<>();
        uiList.add("Temp");
        adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, uiList);

        /*listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                System.out.println("Clicked element! ID: " + id + " Position: " + position);
                Intent intent = new Intent(getApplicationContext(), SpecificAppActivity.class);
                String name = uiList.get(position);
                intent.putExtra(SPECIFIC_APP_MESSAGE, name);
                startActivity(intent);
            }
        });*/

        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                TimerMethod();
            }

        }, 0, 1000);

        listView.setAdapter(adapter);
        refreshList();
    }
        @Override
    public void onResume() {
        // kick off the data generating thread:
        batteryMonitorThread = new Thread(batteryDataSource);
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

    private void refreshList() {
        System.out.println("Start refresh list");
        Context ctx = getApplicationContext();
        PackageManager packageManager = ctx.getPackageManager();
        UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -1);
        long startTime = calendar.getTimeInMillis();

        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(startTime, endTime);
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            if (stats.containsKey(app.packageName) && !((String)packageManager.getApplicationLabel(app)).startsWith("com.")) {
                String name = (String)packageManager.getApplicationLabel(app);
                uiList.add(name);
            }
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        Boolean switchPref = sharedPref.getBoolean
                (SettingsActivity.KEY_PREF_SWITCH_1, false);
        String thresholdPref = sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null);
        if (switchPref) {
            Collections.sort(uiList);
        } else {
            Collections.sort(uiList, Collections.reverseOrder());
        }

        for (String name : uiList) {
            System.out.println("Name: " + name);
        }

        System.out.println("End refersh list");
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
        TextView is_charging_view = (TextView)findViewById(R.id.is_charging);
        TextView time_left_view = (TextView)findViewById(R.id.timeToCharge);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void TimerMethod() {
        this.refreshTemplateList();
        batteryLevel = getBatteryLevel();
        this.runOnUiThread(Update_Battery_Charging_Status);
        this.runOnUiThread(Update_List);
    }

    private Runnable Update_Battery_Charging_Status = new Runnable() {
        @Override
        public void run() {
            Context ctx = getApplicationContext();
//            BatteryManager bm = (BatteryManager)ctx.getSystemService(BATTERY_SERVICE);
            TextView is_charging_view = (TextView)findViewById(R.id.is_charging);
            TextView time_left_view = (TextView)findViewById(R.id.timeToCharge);
//            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
//
//            System.out.println("BATLEVEL: " + batLevel);
            ProgressBar progressBar = findViewById(R.id.determinateBar);
            progressBar.setProgress(batteryLevel);
//
//
            //.addLast(++t, batteryLevel);
            //plot.redraw();
//
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = ctx.registerReceiver(null, ifilter);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
//
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
            int battery_threshold_pref;
            try {
                battery_threshold_pref = Integer.parseInt(sharedPref.getString(SettingsActivity.KEY_PREF_THRESHOLD_1, null));
            }
            catch (NumberFormatException e) {
                battery_threshold_pref = 0;
            }
//
//            if (batLevel <= battery_threshold_pref) {
//                System.out.println("BELOW THRESHOLD!!!");
//                notifyUser(ctx);
//            }
//
            if (isCharging) {
               is_charging_view.setText("Currently charging");
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
//
//                long time_left = bm.computeChargeTimeRemaining();
//                //System.out.println("Time left: " + time_left);
//                if (time_left == -1)
//                    time_left_view.setText("Can't calculate time");
//                else if (time_left == 0)
//                    time_left_view.setText("Fully charged");
//                else
//                    time_left_view.setText("Time until charged: " + String.valueOf(time_left / 1000) + " seconds.");
            }
            else {
                is_charging_view.setText("Not currently charging");
                progressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));

                time_left_view.setText(String.valueOf("Time until charged: Never!"));
            }
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
