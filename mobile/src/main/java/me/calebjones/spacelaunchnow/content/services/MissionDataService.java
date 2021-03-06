package me.calebjones.spacelaunchnow.content.services;

import android.app.IntentService;
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
import java.util.Collections;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmObject;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.content.interfaces.LibraryRequestInterface;
import me.calebjones.spacelaunchnow.content.models.Strings;
import me.calebjones.spacelaunchnow.content.models.realm.MissionRealm;
import me.calebjones.spacelaunchnow.content.models.realm.RealmStr;
import me.calebjones.spacelaunchnow.content.responses.launchlibrary.MissionResponse;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;


/**
 * Created by cjones on 11/10/15.
 * If it is a new post then notify the user and save to DB.
 */

//TODO point to library data service
public class MissionDataService extends IntentService {
    private SharedPreferences sharedPref;
    private ListPreferences listPreference;

    private Realm mRealm;

    private Retrofit retrofit;

    public MissionDataService() {
        super("MissionDataService");
    }

    public void onCreate() {
        Timber.d("MissionDataService - onCreate");

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
        this.sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return super.onStartCommand(intent, flags, startId);
    }

    //TODO Write two handle cases for getMissionsLaunches() and getMissionByID()
    @Override
    protected void onHandleIntent(Intent intent) {
        Timber.d("MissionDataService - Intent received:  %s ", intent.getAction());

        // Init Realm
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder(this)
                .deleteRealmIfMigrationNeeded()
                .build();

        // Create a new empty instance of Realm
        mRealm = Realm.getInstance(realmConfiguration);
        getMissionLaunches();
        mRealm.close();
    }

    private void getMissionLaunches() {
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<MissionResponse> call;
        Response<MissionResponse> launchResponse;
        RealmList<MissionRealm> items = new RealmList<>();
        int offset = 0;
        int total = 10;
        int count;

        try {
            while (total != offset) {
                if (listPreference.isDebugEnabled()) {
                    call = request.getDebugAllMissions(offset);
                } else {
                    call = request.getAllMisisons(offset);
                }
                launchResponse = call.execute();
                total = launchResponse.body().getTotal();
                count = launchResponse.body().getCount();
                offset = offset + count;
                Timber.v("Count: %s", offset);
                Collections.addAll(items, launchResponse.body().getMissions());

                mRealm.beginTransaction();
                mRealm.copyToRealmOrUpdate(items);
                mRealm.commitTransaction();
            }

            Timber.v("Success!");

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(Strings.ACTION_SUCCESS_MISSIONS);

            MissionDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);

        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());

            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra("error", e.getLocalizedMessage());
            broadcastIntent.setAction(Strings.ACTION_FAILURE_MISSIONS);

            MissionDataService.this.getApplicationContext().sendBroadcast(broadcastIntent);
        }
    }

    private void getMissionById(int id) {
        LibraryRequestInterface request = retrofit.create(LibraryRequestInterface.class);
        Call<MissionResponse> call;

        if (listPreference.isDebugEnabled()) {
            call = request.getDebugMissionByID(id);
        } else {
            call = request.getMissionByID(id);
        }

        Response<MissionResponse> launchResponse;
        try {
            launchResponse = call.execute();
            if (launchResponse.isSuccessful()) {
                RealmList<MissionRealm> items = new RealmList<>(launchResponse.body().getMissions());

                mRealm.beginTransaction();
                mRealm.copyToRealmOrUpdate(items);
                mRealm.commitTransaction();
            }
        } catch (IOException e) {
            Timber.e("Error: %s", e.getLocalizedMessage());
        }
    }
}
