package me.calebjones.spacelaunchnow.content.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmObject;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.content.database.SwitchPreferences;
import me.calebjones.spacelaunchnow.content.interfaces.LibraryRequestInterface;
import me.calebjones.spacelaunchnow.content.models.Strings;
import me.calebjones.spacelaunchnow.content.models.realm.LaunchRealm;
import me.calebjones.spacelaunchnow.content.responses.launchlibrary.LaunchResponse;
import me.calebjones.spacelaunchnow.utils.Stopwatch;
import me.calebjones.spacelaunchnow.utils.Utils;
import me.calebjones.spacelaunchnow.content.models.realm.RealmStr;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

public class LaunchDataService extends IntentService {

    private AlarmManager alarmManager;
    private SharedPreferences sharedPref;
    private ListPreferences listPreference;
    private SwitchPreferences switchPreferences;
    private Realm mRealm;
    private Retrofit retrofit;

    public LaunchDataService() {
        super("LaunchDataService");
    }

    public void onCreate() {
        Timber.d("LaunchDataService - UpComingLaunchService onCreate");

        // Note there is a bug in GSON 2.5 that can cause it to StackOverflow when working with RealmObjects.
        // To work around this, use the ExclusionStrategy below or downgrade to 1.7.1
        // See more here: https://code.google.com/p/google-gson/issues/detail?id=440
        Type token = new TypeToken<RealmList<RealmStr>>() {
        }.getType();

        Gson gson = new GsonBuilder()
                .setDateFormat("MMMM dd, yyyy hh:mm:ss zzz")
                .setExclusionStrategies(new ExclusionStrategy() {
                    @Override
                    public boolean shouldSkipField(FieldAttributes f) {
                        return f.getDeclaringClass().equals(RealmObject.class);
                    }

                    @Override
                    public boolean shouldSkipClass(Class<?> clazz) {
                        return false;
                    }
                })
                .registerTypeAdapter(token, new TypeAdapter<RealmList<RealmStr>>() {

                    @Override
                    public void write(JsonWriter out, RealmList<RealmStr> value) throws io.realm.internal.IOException {
                        // Ignore
                    }

                    @Override
                    public RealmList<RealmStr> read(JsonReader in) throws io.realm.internal.IOException, java.io.IOException {
                        RealmList<RealmStr> list = new RealmList<RealmStr>();
                        in.beginArray();
                        while (in.hasNext()) {
                            list.add(new RealmStr(in.nextString()));
                        }
                        in.endArray();
                        return list;
                    }
                })
                .create();

        retrofit = new Retrofit.Builder()
                .baseUrl(Strings.LIBRARY_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        super.onCreate();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        this.listPreference = ListPreferences.getInstance(getApplicationContext());
        this.switchPreferences = SwitchPreferences.getInstance(getApplicationContext());
        this.sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Timber.d("LaunchDataService - Intent received:  %s ", intent.getAction());
        String action = intent.getAction();

        // Init Realm
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder(this)
                .deleteRealmIfMigrationNeeded()
                .build();

        // Create a new empty instance of Realm
        mRealm = Realm.getInstance(realmConfiguration);

        //Usually called on first launch
        if (Strings.ACTION_GET_ALL_WIFI.equals(action)) {
            Timber.v("Intent action received: %s", action);
            if (this.sharedPref.getBoolean("background", true)) {
                scheduleLaunchUpdates();
            }

            if (getUpcomingLaunches()) {
                if (getLaunchesByDate("1950-01-01", Utils.getEndDate(this))) {
                    Intent rocketIntent = new Intent(getApplicationContext(), VehicleDataService.class);
                    rocketIntent.setAction(Strings.ACTION_GET_VEHICLES_DETAIL);
                    startService(rocketIntent);

                    startService(new Intent(this, MissionDataService.class));
                    this.startService(new Intent(this, NextLaunchTracker.class));
                }
            }
        } else if (Strings.ACTION_UPDATE_LAUNCH.equals(action)) {
            int id = intent.getIntExtra("launchID", 0);
            if (id > 0) {
                Timber.v("Updating launch id: %s", id);
                getLaunchById(id);
            }
            // Called from NextLaunchFragment
        } else if (Strings.ACTION_GET_UP_LAUNCHES.equals(action)) {

            Timber.v("Intent action received: %s", action);
            if (this.sharedPref.getBoolean("background", true)) {
                scheduleLaunchUpdates();
            }
            getUpcomingLaunches();
            this.startService(new Intent(this, NextLaunchTracker.class));
            // Called from PrevLaunchFragment
        } else if (Strings.ACTION_GET_PREV_LAUNCHES.equals(action)) {

            Timber.v("Intent action received: %s", action);
            if (intent.getStringExtra("startDate") != null && intent.getStringExtra("endDate") != null) {
                getLaunchesByDate(intent.getStringExtra("startDate"), intent.getStringExtra("endDate"));
            } else {
                getLaunchesByDate("1950-01-01", Utils.getEndDate(this));
            }

        } else if (Strings.ACTION_UPDATE_NEXT_LAUNCH.equals(action)) {

            Timber.v("Intent action received: %s", action);
            getNextLaunches();

            this.startService(new Intent(this, NextLaunchTracker.class));
        } else {
            Timber.e("LaunchDataService - onHandleIntent: ERROR - Unknown Intent %s", action);
        }
        Timber.v("Finished!");
        mRealm.close();
    }

    private boolean getLaunchesByDate(String startDate, String endDate) {
        Stopwatch stopwatch = new Stopwatch();
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<LaunchResponse> call;
        Response<LaunchResponse> launchResponse;
        RealmList<LaunchRealm> items = new RealmList<>();
        int offset = 0;
        int total = 10;
        int count;
        Timber.v("Created objects - elapsed time: %s", stopwatch.getElapsedTimeString());

        try {
            while (total != offset) {
                if (listPreference.isDebugEnabled()) {
                    call = request.getDebugLaunchesByDate(startDate, endDate, offset);
                } else {
                    call = request.getLaunchesByDate(startDate, endDate, offset);
                }
                Timber.v("getLaunchesByDate - elapsed time: %s", stopwatch.getElapsedTimeString());
                launchResponse = call.execute();
                Timber.v("Execute - elapsed time: %s", stopwatch.getElapsedTimeString());
                if (launchResponse.isSuccessful()) {
                    total = launchResponse.body().getTotal();
                    Timber.v("getTotal - elapsed time: %s", stopwatch.getElapsedTimeString());
                    count = launchResponse.body().getCount();
                    Timber.v("getCount - elapsed time: %s", stopwatch.getElapsedTimeString());
                    offset = offset + count;
                    Timber.v("LaunchesByDate Count: %s", offset);
                    Collections.addAll(items, launchResponse.body().getLaunches());
                    Timber.v("add to collection - elapsed time: %s", stopwatch.getElapsedTimeString());
                } else {
                    throw new IOException(launchResponse.errorBody().string());
                }
            }
            Timber.v("Starting list - elapsed time: %s", stopwatch.getElapsedTimeString());
            mRealm.beginTransaction();
            mRealm.copyToRealmOrUpdate(items);
            mRealm.commitTransaction();
            Timber.v("iterated list - elapsed time: %s", stopwatch.getElapsedTimeString());
            Timber.v("committed - elapsed time: %s", stopwatch.getElapsedTimeString());

            Timber.v("Success!");
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Strings.ACTION_SUCCESS_PREV_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
            return true;

        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());
            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra("error", e.getLocalizedMessage());
            broadcastIntent.setAction(Strings.ACTION_FAILURE_PREV_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
            return false;
        }
    }

    private boolean getUpcomingLaunches() {
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<LaunchResponse> call;
        Response<LaunchResponse> launchResponse;
        RealmList<LaunchRealm> items = new RealmList<>();
        int offset = 0;
        int total = 10;
        int count;

        try {
            while (total != offset) {
                if (listPreference.isDebugEnabled()) {
                    call = request.getDebugUpcomingLaunches(offset);
                } else {
                    call = request.getUpcomingLaunches(offset);
                }
                launchResponse = call.execute();
                if (launchResponse.isSuccessful()) {
                    total = launchResponse.body().getTotal();
                    count = launchResponse.body().getCount();
                    offset = offset + count;
                    Timber.v("UpcomingLaunches Count: %s", offset);
                    Collections.addAll(items, launchResponse.body().getLaunches());
                } else {
                    throw new IOException(launchResponse.errorBody().string());
                }
            }
            for (LaunchRealm item : items) {
                LaunchRealm previous = mRealm.where(LaunchRealm.class)
                        .equalTo("id", item.getId())
                        .findFirst();
                if (previous != null) {
                    Timber.v("UpcomingLaunches updating items: %s", previous.getName());
                    item.setFavorite(previous.isFavorite());
                    item.setLaunchTimeStamp(previous.getLaunchTimeStamp());
                    item.setIsNotifiedDay(previous.getIsNotifiedDay());
                    item.setIsNotifiedHour(previous.getIsNotifiedHour());
                    item.setIsNotifiedTenMinute(previous.getIsNotifiedTenMinute());
                }
                item.getLocation().setPrimaryID();
                mRealm.beginTransaction();
                mRealm.copyToRealmOrUpdate(item);
                mRealm.commitTransaction();
            }

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Strings.ACTION_SUCCESS_UP_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
            return true;
        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());
            Intent broadcastIntent = new Intent();

            broadcastIntent.putExtra("error", e.getLocalizedMessage());
            broadcastIntent.setAction(Strings.ACTION_FAILURE_UP_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
            return false;
        }
    }

    private void getNextLaunches() {
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<LaunchResponse> call;
        Response<LaunchResponse> launchResponse;
        RealmList<LaunchRealm> items = new RealmList<>();

        try {
            if (listPreference.isDebugEnabled()) {
                call = request.getDebugMiniNextLaunch();
            } else {
                call = request.getMiniNextLaunch();
            }
            launchResponse = call.execute();
            Collections.addAll(items, launchResponse.body().getLaunches());
            for (LaunchRealm item : items) {
                LaunchRealm previous = mRealm.where(LaunchRealm.class)
                        .equalTo("id", item.getId())
                        .findFirst();
                if (previous != null) {
                    if ((!previous.getNet().equals(item.getNet())
                            || (previous.getStatus().intValue() != item.getStatus().intValue()))) {
                        Timber.v("%s status has changed.", item.getName());
                        mRealm.beginTransaction();
                        previous.resetNotifiers();
                        mRealm.copyToRealmOrUpdate(previous);
                        mRealm.commitTransaction();
                        getLaunchById(item.getId());
                    }
                }
            }

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Strings.ACTION_SUCCESS_UP_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);

        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());
            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra("error", e.getLocalizedMessage());
            broadcastIntent.setAction(Strings.ACTION_FAILURE_UP_LAUNCHES);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
        }
    }

    private void getLaunchById(int id) {
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<LaunchResponse> call;

        if (listPreference.isDebugEnabled()) {
            call = request.getDebugLaunchByID(id);
        } else {
            call = request.getLaunchByID(id);
        }

        Response<LaunchResponse> launchResponse;
        try {
            launchResponse = call.execute();
            if (launchResponse.isSuccessful()) {
                RealmList<LaunchRealm> items = new RealmList<>(launchResponse.body().getLaunches());
                for (LaunchRealm item : items) {
                    LaunchRealm previous = mRealm.where(LaunchRealm.class)
                            .equalTo("id", item.getId())
                            .findFirst();
                    if (previous != null) {
                        item.setFavorite(previous.isFavorite());
                        item.setLaunchTimeStamp(previous.getLaunchTimeStamp());
                        item.setIsNotifiedDay(previous.getIsNotifiedDay());
                        item.setIsNotifiedHour(previous.getIsNotifiedHour());
                        item.setIsNotifiedTenMinute(previous.getIsNotifiedTenMinute());
                    }
                    item.getLocation().setPrimaryID();
                    mRealm.beginTransaction();
                    mRealm.copyToRealmOrUpdate(item);
                    mRealm.commitTransaction();
                    Timber.v("Updated launch: %s", item.getId());
                }
            }
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Strings.ACTION_SUCCESS_LAUNCH);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());

            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra("error", e.getLocalizedMessage());
            broadcastIntent.setAction(Strings.ACTION_FAILURE_LAUNCH);
            LaunchDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
        }
    }

    public void scheduleLaunchUpdates() {
        Timber.d("LaunchDataService - scheduleLaunchUpdates");

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        //Get sync period.
        String notificationTimer = this.sharedPref.getString("notification_sync_time", "24");

        long interval;

        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(notificationTimer);

        if (m.matches()) {
            int hrs = Integer.parseInt(m.group(1));
            interval = (long) hrs * 60 * 60 * 1000;
            Timber.d("LaunchDataService - Notification Timer: %s to millisecond %s", notificationTimer, interval);

            long nextUpdate = Calendar.getInstance().getTimeInMillis() + interval;
            Timber.d("LaunchDataService - Scheduling Alarm at %s with interval of %s", nextUpdate, interval);
            alarmManager.setInexactRepeating(AlarmManager.RTC, nextUpdate, interval,
                    PendingIntent.getBroadcast(this, 165435, new Intent(Strings.ACTION_UPDATE_UP_LAUNCHES), 0));
        } else {
            Timber.e("LaunchDataService - Error setting alarm, failed to change %s to milliseconds", notificationTimer);
        }
    }
}
