package me.calebjones.spacelaunchnow.ui.fragment.launches;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.borax12.materialdaterangepicker.date.DatePickerDialog;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;
import com.crashlytics.android.answers.SearchEvent;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.github.rahatarmanahmed.cpv.CircularProgressView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import butterknife.ButterKnife;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import io.realm.Sort;
import me.calebjones.spacelaunchnow.BuildConfig;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.content.adapter.ListAdapter;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.content.database.SwitchPreferences;
import me.calebjones.spacelaunchnow.content.interfaces.QueryBuilder;
import me.calebjones.spacelaunchnow.content.models.Strings;
import me.calebjones.spacelaunchnow.content.models.realm.LaunchRealm;
import me.calebjones.spacelaunchnow.content.services.LaunchDataService;
import me.calebjones.spacelaunchnow.ui.activity.MainActivity;
import me.calebjones.spacelaunchnow.ui.fragment.BaseFragment;
import me.calebjones.spacelaunchnow.ui.widget.SimpleDividerItemDecoration;
import me.calebjones.spacelaunchnow.utils.SnackbarHandler;
import timber.log.Timber;


public class PreviousLaunchesFragment extends BaseFragment implements SwipeRefreshLayout.OnRefreshListener, SearchView.OnQueryTextListener, DatePickerDialog.OnDateSetListener {

    private View view, empty;
    private RecyclerView mRecyclerView;
    private ListAdapter adapter;
    private FloatingActionMenu menu;
    private String start_date, end_date;
    private RealmResults<LaunchRealm> launchRealms;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ListPreferences listPreferences;
    private SwitchPreferences switchPreferences;
    private SharedPreferences sharedPrefs;
    private FloatingActionButton agency, vehicle, country, location, reset;
    private int mScrollOffset = 4;
    private LinearLayoutManager layoutManager;
    private Context context;

    private CoordinatorLayout coordinatorLayout;
    String getRequestId;

    public PreviousLaunchesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        this.context = getContext();

        listPreferences = ListPreferences.getInstance(this.context);

        if (!BuildConfig.DEBUG) {
            Answers.getInstance().logContentView(new ContentViewEvent()
                    .putContentName("PreviousLaunchesFragment")
                    .putContentType("Fragment"));
        }

        super.onCreateView(inflater, container, savedInstanceState);
        setHasOptionsMenu(true);

        LayoutInflater lf = getActivity().getLayoutInflater();
        view = lf.inflate(R.layout.fragment_previous_launches, container, false);
        ButterKnife.bind(getActivity());

        this.listPreferences = ListPreferences.getInstance(getContext());
        this.switchPreferences = SwitchPreferences.getInstance(getContext());
        this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        adapter = new ListAdapter(getContext());

        agency = (FloatingActionButton) view.findViewById(R.id.agency);
        vehicle = (FloatingActionButton) view.findViewById(R.id.vehicle);
        country = (FloatingActionButton) view.findViewById(R.id.country);
        location = (FloatingActionButton) view.findViewById(R.id.launch_location);
        reset = (FloatingActionButton) view.findViewById(R.id.reset);
        menu = (FloatingActionMenu) view.findViewById(R.id.menu);
        empty = view.findViewById(R.id.empty_launch_root);
        coordinatorLayout = (CoordinatorLayout) view.findViewById(R.id.coordinatorLayout);


        /*Set up Pull to refresh*/
        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.previous_swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        RecyclerView hideView = (RecyclerView) view.findViewById(R.id.recycler_view_staggered);
        if (mRecyclerView.getVisibility() != View.VISIBLE) {
            hideView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
        layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(new SimpleDividerItemDecoration(context));
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (Math.abs(dy) > mScrollOffset) {
                    if (dy > 0) {
                        menu.hideMenu(true);
                    } else {
                        menu.showMenu(true);
                    }
                }
                mSwipeRefreshLayout.setEnabled(layoutManager.findFirstCompletelyVisibleItemPosition() == 0);
            }
        });
        setUpFab();

        CircularProgressView progressView = (CircularProgressView)
                view.findViewById(R.id.progress_View);
        progressView.setVisibility(View.VISIBLE);
        progressView.startAnimation();
        return view;
    }

    public static PreviousLaunchesFragment newInstance(String text) {

        PreviousLaunchesFragment p = new PreviousLaunchesFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        p.setArguments(b);

        return p;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void setUpFab() {
        menu.setClosedOnTouchOutside(true);

        createCustomAnimation();

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchPreferences.resetAllPrevFilters(context);
                switchPreferences.setPrevFiltered(false);
                getDefaultDateRange();
                loadLaunches();
            }
        });

        agency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAgencyDialog();
            }
        });
        vehicle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVehicleDialog();
            }
        });
        country.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCountryDialog();
            }
        });
        location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLocationDialog();
            }
        });

    }

    private void showCountryDialog() {
        new MaterialDialog.Builder(getContext())
                .title("Select a Country")
                .content("Check a country below, to remove all filters use reset icon in the toolbar.")
                .items(R.array.country)
                .buttonRippleColorRes(R.color.colorAccentLight)
                .itemsCallbackMultiChoice(switchPreferences.getPrevCountryFiltered(),
                        new MaterialDialog.ListCallbackMultiChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, Integer[] which,
                                                       CharSequence[] text) {
                                switchPreferences.setPrevCountryFiltered(which);
                                ArrayList<String> keyArray = new ArrayList<>();
                                for (int i = 0; i < which.length; i++) {
                                    keyArray.add(text[i].toString());
                                }
                                if (keyArray.size() > 0) {
                                    switchPreferences.setPrevCountryFilteredArray(keyArray);
                                    switchPreferences.setPrevFiltered(true);
                                } else {
                                    switchPreferences.resetCountryPrevFilters();
                                }
                                fetchDataFiltered();
                                menu.toggle(false);
                                return true;
                            }
                        })
                .positiveText("Filter")
                .negativeText("Close")
                .icon(ContextCompat.getDrawable(getContext(), R.mipmap.ic_launcher))
                .show();
    }

    private void showLocationDialog() {
        new MaterialDialog.Builder(getContext())
                .title("Select a Location")
                .content("Check an location below, to remove all filters use reset icon in the toolbar.")
                .items(R.array.location)
                .buttonRippleColorRes(R.color.colorAccentLight)
                .itemsCallbackMultiChoice(switchPreferences.getPrevLocationFiltered(), new MaterialDialog.ListCallbackMultiChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                        switchPreferences.setPrevLocationFiltered(which);
                        ArrayList<String> keyArray = new ArrayList<>();
                        for (int i = 0; i < which.length; i++) {
                            keyArray.add(text[i].toString());
                        }
                        if (keyArray.size() > 0) {
                            switchPreferences.setPrevLocationFilteredArray(keyArray);
                            switchPreferences.setPrevFiltered(true);
                        } else {
                            switchPreferences.resetLocationPrevFilters();
                        }
                        fetchDataFiltered();
                        menu.toggle(false);
                        return true;
                    }
                })
                .positiveText("Filter")
                .negativeText("Close")
                .icon(ContextCompat.getDrawable(getContext(), R.mipmap.ic_launcher))
                .show();
    }

    private void showAgencyDialog() {
        new MaterialDialog.Builder(getContext())
                .title("Select an Agency")
                .content("Check an agency below, to remove all filters use reset icon in the toolbar.")
                .items(R.array.agencies)
                .buttonRippleColorRes(R.color.colorAccentLight)
                .itemsCallbackMultiChoice(switchPreferences.getPrevAgencyFiltered(), new MaterialDialog.ListCallbackMultiChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                        switchPreferences.setPrevAgencyFiltered(which);
                        ArrayList<String> keyArray = new ArrayList<>();
                        for (int i = 0; i < which.length; i++) {
                            keyArray.add(text[i].toString());
                        }
                        if (keyArray.size() > 0) {
                            switchPreferences.setPrevAgencyFilterArray(keyArray);
                            switchPreferences.setPrevFiltered(true);
                        } else {
                            switchPreferences.resetAgencyPrevFilters();
                        }
                        fetchDataFiltered();
                        menu.toggle(false);
                        return true;
                    }
                })
                .positiveText("Filter")
                .negativeText("Close")
                .icon(ContextCompat.getDrawable(getContext(), R.mipmap.ic_launcher))
                .show();
    }

    private void showVehicleDialog() {
        new MaterialDialog.Builder(getContext())
                .title("Select a Launch Vehicle")
                .content("Check a vehicle below, to remove all filters use reset icon in the toolbar.")
                .items(R.array.vehicles)
                .buttonRippleColorRes(R.color.colorAccentLight)
                .itemsCallbackMultiChoice(switchPreferences.getPrevVehicleFiltered(), new MaterialDialog.ListCallbackMultiChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                        switchPreferences.setPrevVehicleFiltered(which);
                        ArrayList<String> keyArray = new ArrayList<>();
                        for (int i = 0; i < which.length; i++) {
                            keyArray.add(text[i].toString());
                        }
                        if (keyArray.size() > 0) {
                            switchPreferences.setPrevVehicleFilteredArray(keyArray);
                            switchPreferences.setPrevFiltered(true);
                        } else {
                            switchPreferences.resetVehiclePrevFilters();
                        }
                        fetchDataFiltered();
                        menu.toggle(false);
                        return true;
                    }
                })
                .positiveText("Filter")
                .negativeText("Close")
                .icon(ContextCompat.getDrawable(getContext(), R.mipmap.ic_launcher))
                .show();
    }

    private void setTitle() {
        ((MainActivity) getActivity()).setActionBarTitle(this.listPreferences.getPreviousTitle());
    }

    public void recreate() {
        recreate();
    }

    private String formatDatesForTitle(String start_date) {
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat out = new SimpleDateFormat("LLL yyyy");

        Date sDate;

        try {
            sDate = in.parse(start_date);
            return out.format(sDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private RealmChangeListener callback = new RealmChangeListener<RealmResults<LaunchRealm>>() {
        @Override
        public void onChange(RealmResults<LaunchRealm> results) {
            Timber.v("Data changed - size: %s", results.size());
            if (results.size() > 0) {
                adapter.clear();
                results.sort("net", Sort.DESCENDING);
                adapter.addItems(results);
            } else {
                adapter.clear();
            }
            hideLoading();
            launchRealms.removeChangeListeners();
        }
    };

    public void loadLaunches() {
        try {
            launchRealms = QueryBuilder.buildPrevQuery(context, getRealm());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        launchRealms.addChangeListener(callback);
    }

    public void getPrevLaunchData() {
        new MaterialDialog.Builder(context)
                .title("Refresh Launch Data?")
                .content("This can take a bit to refresh, make sure you are on Wi-Fi to save data.")
                .positiveText("Ok")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        showLoading();
                        getRealm().removeAllChangeListeners();
                        Intent intent = new Intent(getContext(), LaunchDataService.class);
                        intent.setAction(Strings.ACTION_GET_PREV_LAUNCHES);
                        getContext().startService(intent);
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        hideLoading();
                    }
                })
                .negativeText("Not Now")
                .show();
    }

    private void showLoading() {
        if (!mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setRefreshing(true);
        }
    }

    private void hideLoading() {
        if (mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setRefreshing(false);
        }
        CircularProgressView progressView = (CircularProgressView)
                view.findViewById(R.id.progress_View);
        progressView.setVisibility(View.GONE);
        progressView.resetAnimation();
    }

    public void fetchDataFiltered() {
        loadLaunches();
        rebuildTitle();
    }

    private void rebuildTitle() {
        String title = "";
        ArrayList<String> agency = switchPreferences.getPrevAgencyFilteredArray();
        ArrayList<String> country = switchPreferences.getPrevCountryFilteredArray();
        ArrayList<String> location = switchPreferences.getPrevLocationFilteredArray();
        ArrayList<String> vehicle = switchPreferences.getPrevVehicleFilteredArray();
        getDateRange();

        if (switchPreferences.isDateFiltered()) {
            title = formatDatesForTitle(start_date) + " - " + formatDatesForTitle(end_date);
        }

        if (agency != null) {
            for (String key : agency) {
                if (title.length() == 0) {
                    title = key;
                } else {
                    title = title + " | " + key;
                }
            }
        }

        if (country != null) {
            for (String key : country) {
                if (title.length() == 0) {
                    title = key;
                } else {
                    title = title + " | " + key;
                }
            }
        }


        if (location != null) {
            for (String key : location) {
                if (title.length() == 0) {
                    title = key;
                } else {
                    title = title + " | " + key;
                }
            }
        }

        if (vehicle != null) {
            for (String key : vehicle) {
                if (title.length() == 0) {
                    title = key;
                } else {
                    title = title + " | " + key;
                }
            }
        }

        if (title.length() > 0) {
            listPreferences.setPreviousTitle(title);
        } else {
            listPreferences.resetPreviousTitle();
        }
        setTitle();
    }

    private void createCustomAnimation() {

        AnimatorSet set = new AnimatorSet();

        ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(menu.getMenuIconView(), "scaleX", 1.0f, 0.2f);
        ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(menu.getMenuIconView(), "scaleY", 1.0f, 0.2f);

        ObjectAnimator scaleInX = ObjectAnimator.ofFloat(menu.getMenuIconView(), "scaleX", 0.2f, 1.0f);
        ObjectAnimator scaleInY = ObjectAnimator.ofFloat(menu.getMenuIconView(), "scaleY", 0.2f, 1.0f);

        scaleOutX.setDuration(50);
        scaleOutY.setDuration(50);

        scaleInX.setDuration(150);
        scaleInY.setDuration(150);

        scaleInX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                menu.getMenuIconView().setImageResource(menu.isOpened()
                        ? R.drawable.ic_sort : R.drawable.ic_close);
            }
        });

        set.play(scaleOutX).with(scaleOutY);
        set.play(scaleInX).with(scaleInY).after(scaleOutX);
        set.setInterpolator(new OvershootInterpolator(2));

        menu.setIconToggleAnimatorSet(set);
    }

    //Required for DateRange Dialogue that returns data from the dialogue.
    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth, int yearEnd, int monthOfYearEnd, int dayOfMonthEnd) {
        monthOfYear = monthOfYear + 1;
        monthOfYearEnd = monthOfYearEnd + 1;

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        switchPreferences.setDateFiltered(true);

        if (monthOfYear == 0) {
            monthOfYear = 1;
        }
        if (monthOfYearEnd == 0) {
            monthOfYearEnd = 1;
        }
        String dayDateStart = dayOfMonth < 10 ? "0" + dayOfMonth : "" + dayOfMonth;
        String monthDateStart = monthOfYear < 10 ? "0" + monthOfYear : "" + monthOfYear;
        String dayDateEnd = dayOfMonthEnd < 10 ? "0" + dayOfMonthEnd : "" + dayOfMonthEnd;
        String monthDayEnd = monthOfYearEnd < 10 ? "0" + monthOfYearEnd : "" + monthOfYearEnd;

        start_date = year + "-" + monthDateStart + "-" + dayDateStart;
        end_date = yearEnd + "-" + monthDayEnd + "-" + dayDateEnd;

        listPreferences.setStartDate(start_date);
        listPreferences.setEndDate(end_date);

        Date startDate;
        Date endDate;
        try {
            startDate = df.parse(start_date);
            endDate = df.parse(end_date);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        rebuildTitle();
        loadLaunches();
    }

    public void getDateRange() {
        start_date = this.listPreferences.getStartDate();
        end_date = this.listPreferences.getEndDate();
    }

    public void getDefaultDateRange() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, 1);

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String formattedDate = df.format(c.getTime());

        start_date = listPreferences.getStartDate();
        end_date = String.valueOf(formattedDate);
        listPreferences.setEndDate(end_date);
        listPreferences.resetPreviousTitle();
        switchPreferences.setDateFiltered(false);
        setTitle();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.previous_menu, menu);

        final MenuItem item = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
        searchView.setOnQueryTextListener(this);
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean queryTextFocused) {
                if (!queryTextFocused) {
                    item.collapseActionView();
                    searchView.setQuery("", false);
                    loadLaunches();
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_date) {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = com.borax12.materialdaterangepicker.date.DatePickerDialog.newInstance(
                    PreviousLaunchesFragment.this,
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
            );
            dpd.show(getActivity().getFragmentManager(), "DatePicker");
            return true;
        }
        if (id == R.id.action_refresh) {
            onRefresh();
            return true;
        }

        if (id == R.id.return_home) {
            mRecyclerView.scrollToPosition(0);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextChange(String query) {
        switchPreferences.setPrevFiltered(true);
        // Here is where we are going to implement our filter logic
        Answers.getInstance().logSearch(new SearchEvent()
                .putQuery(query));
        final List<LaunchRealm> filteredModelList = filter(launchRealms, query);
        if (filteredModelList.size() > 50) {
            adapter.clear();
            adapter.addItems(filteredModelList);
        } else {
            adapter.animateTo(filteredModelList);
        }
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private List<LaunchRealm> filter(List<LaunchRealm> models, String query) {
        query = query.toLowerCase();

        final List<LaunchRealm> filteredModelList = new ArrayList<>();
        for (LaunchRealm model : models) {
            final String name = model.getName().toLowerCase();
            final String rocketName = model.getRocket().getName().toLowerCase();
            final String locationName = model.getLocation().getName().toLowerCase();
            String missionName = null;
            String missionDescription = null;
            String agencyName = null;

            if (model.getRocket().getAgencies() != null && model.getRocket().getAgencies().size() > 0) {
                agencyName = model.getRocket().getAgencies().get(0).getName().toLowerCase();
            }

            if (model.getMissions().size() > 0) {
                missionName = model.getMissions().get(0).getName().toLowerCase();
                missionDescription = model.getMissions().get(0).getDescription().toLowerCase();
            }

            if (rocketName.contains(query) || locationName.contains(query) || (agencyName != null && agencyName.contains(query)) || name.contains(query)) {
                filteredModelList.add(model);
            } else {
                if (missionName != null && (missionName.contains(query) || missionDescription.contains(query))) {
                    filteredModelList.add(model);
                }
            }

        }
        return filteredModelList;
    }

    private final BroadcastReceiver nextLaunchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.v("Received: %s", intent.getAction());
            hideLoading();
            if (intent.getAction().equals(Strings.ACTION_SUCCESS_PREV_LAUNCHES)) {
                loadLaunches();
            } else if (intent.getAction().equals(Strings.ACTION_FAILURE_PREV_LAUNCHES)) {
                SnackbarHandler.showErrorSnackbar(context, coordinatorLayout, intent);
            }
        }
    };


    @Override
    public void onResume() {
        Timber.d("OnResume!");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Strings.ACTION_SUCCESS_PREV_LAUNCHES);
        intentFilter.addAction(Strings.ACTION_FAILURE_PREV_LAUNCHES);

        getActivity().registerReceiver(nextLaunchReceiver, intentFilter);

        if (listPreferences.getPreviousFirstBoot()) {
            listPreferences.setPreviousFirstBoot(false);
            Timber.d("Previous Launch Fragment: First Boot.");
            getDefaultDateRange();
        } else {
            Timber.d("Previous Launch Fragment: Not First Boot.");
            getDateRange();
        }
        loadLaunches();
        super.onResume();
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(nextLaunchReceiver);
        super.onPause();
    }

    @Override
    public void onRefresh() {
        launchRealms.removeChangeListener(callback);
        if (!switchPreferences.isPrevFiltered()) {
            getDefaultDateRange();
            getPrevLaunchData();
        } else {
            switchPreferences.setPrevFiltered(false);
            switchPreferences.resetAllPrevFilters(context);
            getDefaultDateRange();
            loadLaunches();
        }
    }

    private void fabSlideOut() {
        menu.animate().translationX(menu.getWidth() + 250).setInterpolator(new AccelerateInterpolator(1)).start();
    }

    private void fabSlideIn() {
        menu.animate().translationX(0).setInterpolator(new DecelerateInterpolator(4)).start();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }


}


