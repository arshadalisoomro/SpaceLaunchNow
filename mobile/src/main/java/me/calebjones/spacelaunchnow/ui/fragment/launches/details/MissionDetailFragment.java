package me.calebjones.spacelaunchnow.ui.fragment.launches.details;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.calebjones.spacelaunchnow.R;
import me.calebjones.spacelaunchnow.content.database.ListPreferences;
import me.calebjones.spacelaunchnow.content.models.realm.LaunchRealm;
import me.calebjones.spacelaunchnow.content.models.realm.MissionRealm;
import me.calebjones.spacelaunchnow.content.models.realm.RocketDetailsRealm;
import me.calebjones.spacelaunchnow.ui.activity.LaunchDetailActivity;
import me.calebjones.spacelaunchnow.ui.fragment.BaseFragment;
import me.calebjones.spacelaunchnow.utils.Utils;

public class MissionDetailFragment extends BaseFragment {

    private SharedPreferences sharedPref;
    private static ListPreferences sharedPreference;
    private Context context;
    public static LaunchRealm detailLaunch;
    private RocketDetailsRealm launchVehicle;
    @BindView(R.id.vehicle_spec_view)
    View vehicleSpecView;
    @BindView(R.id.payload_type)
    TextView payloadType;
    @BindView(R.id.payload_description)
    TextView payloadDescription;
    @BindView(R.id.payload_status)
    TextView payloadStatus;
    @BindView(R.id.payload_infoButton)
    TextView payloadInfoButton;
    @BindView(R.id.payload_wikiButton)
    TextView payloadWikiButton;
    @BindView(R.id.launch_vehicle)
    TextView launchVehicleView;
    @BindView(R.id.launch_configuration)
    TextView launchConfiguration;
    @BindView(R.id.launch_family)
    TextView launchFamily;
    @BindView(R.id.launch_vehicle_specs_height)
    TextView launchVehicleSpecsHeight;
    @BindView(R.id.launch_vehicle_specs_diameter)
    TextView launchVehicleSpecsDiameter;
    @BindView(R.id.launch_vehicle_specs_stages)
    TextView launchVehicleSpecsStages;
    @BindView(R.id.launch_vehicle_specs_leo)
    TextView launchVehicleSpecsLeo;
    @BindView(R.id.launch_vehicle_specs_gto)
    TextView launchVehicleSpecsGto;
    @BindView(R.id.launch_vehicle_specs_launch_mass)
    TextView launchVehicleSpecsLaunchMass;
    @BindView(R.id.launch_vehicle_specs_thrust)
    TextView launchVehicleSpecsThrust;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View view;
        this.sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        this.context = getContext();

        sharedPreference = ListPreferences.getInstance(this.context);

        if (sharedPreference.getNightMode()) {
            view = inflater.inflate(R.layout.dark_launch_payload, container, false);
        } else {
            view = inflater.inflate(R.layout.light_launch_payload, container, false);
        }

        detailLaunch = ((LaunchDetailActivity) getActivity()).getLaunch();

        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onResume() {

        setUpViews();
        super.onResume();
    }

    private void setUpViews() {
        detailLaunch = ((LaunchDetailActivity) getActivity()).getLaunch();
        if (detailLaunch.getRocket() != null) {
            getLaunchVehicle(detailLaunch);
        }

        if (detailLaunch.getMissions().size() > 0) {
            final MissionRealm mission = getRealm().where(MissionRealm.class)
                    .equalTo("id", detailLaunch.getMissions().get(0).getId())
                    .findFirst();

            payloadStatus.setText(mission.getName());
            payloadDescription.setText(mission.getDescription());
            payloadType.setText(mission.getTypeName());

            if (mission.getInfoURL() != null && mission.getInfoURL().length() > 0) {

                ((LaunchDetailActivity) context).mayLaunchUrl(Uri.parse(mission.getInfoURL()));

                payloadInfoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Activity activity = (Activity) context;
                        Utils.openCustomTab(activity, context, mission.getInfoURL());
                    }
                });
            } else {
                payloadInfoButton.setVisibility(View.GONE);
            }
            if (mission.getWikiURL() != null && mission.getWikiURL().length() > 0) {

                ((LaunchDetailActivity) context).mayLaunchUrl(Uri.parse(mission.getWikiURL()));

                payloadWikiButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Activity activity = (Activity) context;
                        Utils.openCustomTab(activity, context, mission.getWikiURL());
                    }
                });
            } else {
                payloadWikiButton.setVisibility(View.GONE);
            }
        } else {
            payloadStatus.setText("Unknown Mission or Payload");

            payloadInfoButton.setVisibility(View.GONE);
            payloadWikiButton.setVisibility(View.GONE);
        }

        launchVehicleView.setText(detailLaunch.getRocket().getName());
        launchConfiguration.setText(String.format("Configuration: %s", detailLaunch.getRocket().getConfiguration()));
        launchFamily.setText(String.format("Family: %s", detailLaunch.getRocket().getFamilyname()));
        if (launchVehicle != null) {
            vehicleSpecView.setVisibility(View.VISIBLE);
            launchVehicleSpecsHeight.setText(String.format("Height: %s Meters", launchVehicle.getLength()));
            launchVehicleSpecsDiameter.setText(String.format("Diameter: %s Meters", launchVehicle.getDiameter()));
            launchVehicleSpecsStages.setText(String.format("Stages: %d", launchVehicle.getMax_Stage()));
            launchVehicleSpecsLeo.setText(String.format("Payload to LEO: %s kg", launchVehicle.getLEOCapacity()));
            launchVehicleSpecsGto.setText(String.format("Payload to GTO: %s kg", launchVehicle.getGTOCapacity()));
            launchVehicleSpecsLaunchMass.setText(String.format("Mass at Launch: %s Tons", launchVehicle.getLaunchMass()));
            launchVehicleSpecsThrust.setText(String.format("Thrust at Launch: %s kN", launchVehicle.getTOThrust()));
        } else {
            vehicleSpecView.setVisibility(View.GONE);
        }
    }

    private void getLaunchVehicle(LaunchRealm vehicle) {
        String query;
        if (vehicle.getRocket().getName().contains("Space Shuttle")) {
            query = "Space Shuttle";
        } else {
            query = vehicle.getRocket().getName();
        }

        launchVehicle = getRealm().where(RocketDetailsRealm.class).contains("name", query).findFirst();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    public static Fragment newInstance() {
        return new MissionDetailFragment();
    }

}