package com.example.saferock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MapsActivity extends FragmentActivity implements LocationListener{

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationManager locationManager;
    private Location currentLocation;
    private String provider;
    private static final UUID APP_UUID = UUID.fromString("2b1ccd55-fda2-4b6c-ac1a-5101bd6c6237");
    private Button searchButton;
    private EditText searchBar;
    private ArrayList<Marker> markerList;
    private ArrayList<LatLng> latLngList;
    private PebbleKit.PebbleDataReceiver mDataReceiver;
    private Context context = this;
    private String previousSignal;
    private Switch theSwitch;
    private HeatmapTileProvider heatMapProvider;
    private TileOverlay heatMapOverlay;

    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude()),
                12));

        for (int i = 0; i < markerList.size(); i++) {
            Marker tempMarker = markerList.get(i);
            LatLng latLng = tempMarker.getPosition();
            Location tempLoc = new Location(provider);
            tempLoc.setLatitude(latLng.latitude);
            tempLoc.setLongitude(latLng.longitude);

            float distanceBetween = currentLocation.distanceTo(tempLoc);
            if (distanceBetween <= 100) {

                sendNotification(null);
                break;
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {
            currentLocation = location;
        }
        setUpMapIfNeeded();

        searchBar = (EditText)findViewById(R.id.edit_text);
        searchButton = (Button)findViewById(R.id.search);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                findRoute();
            }
        });

        theSwitch = (Switch)findViewById(R.id.theSwitch);
        theSwitch.setChecked(true);

        theSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    //Set Markers
                    mMap.clear();
                    try {

                        startJSON();

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    //Set Heat Map
                    mMap.clear();
                    try {

                        startHeatMapJSON();

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        Context context = getApplicationContext();

        boolean isConnected = PebbleKit.isWatchConnected(context);

        if(isConnected) {
            PebbleKit.startAppOnPebble(context, APP_UUID);

            Toast.makeText(context, "Launching...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Watch is not connected!", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        locationManager.requestLocationUpdates(provider, 400, 1, this);
        setUpMapIfNeeded();

        if(mDataReceiver == null) {
            mDataReceiver = new PebbleKit.PebbleDataReceiver(APP_UUID) {

                @Override
                public void receiveData(Context context, int transactionId, PebbleDictionary dict) {
                    // Message received, over!
                    PebbleKit.sendAckToPebble(context, transactionId);
                    Log.i("receiveData", "Got message from Pebble!");
                    Log.i("dictionary", "dict: " + dict.toJsonString());

                    try {
                        JSONArray jsonArray = new JSONArray(dict.toJsonString());
                        JSONObject insideJson = jsonArray.getJSONObject(0);

                        String value = insideJson.getString("value");

                        if (value.equals("CwAIAAMAAAA=")) { //Back Button
                            previousSignal = value;
                        } else if (value.contains("GwAG")) { //Vibrate (Long)
                            if (previousSignal.contains("GwAG")) { //2 Vibrates
                                Log.i("vibrate", "call emergency contact");
                                callEmergencyContact(null);
                            }
                            previousSignal = value;
                        } else if (value.equals("JgAIAAAAAAA=")) { //Select
                            previousSignal = value;
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            };
            PebbleKit.registerReceivedDataHandler(getApplicationContext(), mDataReceiver);
        }
    }

    public void callEmergencyContact(View v) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);

            final String PREFS_NAME = "MyPrefsFile";

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

            String number = settings.getString("phone_number", "0");

            if (number.length() > 1) {
                intent.setData(Uri.parse("tel:" + number));
                startActivity(intent);
            }

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),
                    "Call failed, please try again later!",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }

    }

    public String loadHeatMapFromAsset() {
        String json = null;
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open("baltimore_crime_data_recent.json");

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            AssetManager assetManager = getAssets();
            InputStream is = assetManager.open("baltimore_crime_data_small.json");

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;

    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        //mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));

        mMap.setMyLocationEnabled(true);
        markerList = new ArrayList<>();
        latLngList = new ArrayList<>();

        LatLng firstLatLng = new LatLng(39.3299013, -76.6205177);;
        try {
            mMap.clear();
            startJSON();

        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (currentLocation!=null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude()),
                        12));
            } else {
                if (firstLatLng != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstLatLng,
                            12));
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        //mMap.moveCamera(center);
        //mMap.animateCamera(zoom);

    }

    public void startHeatMapJSON() throws JSONException {
        //TODO: Test HeatMap
        //TODO: Test remove heat map (mMap.clear())
        //if it doesn't work, then use heatMapOverlay.remove()
        JSONObject jsonObject = new JSONObject(loadHeatMapFromAsset());
        JSONArray jsonArray = jsonObject.getJSONArray("top");
        List<LatLng> latLngList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length();i++) {
            JSONObject objectInside = jsonArray.getJSONObject(i);
            JSONObject latLng = objectInside.getJSONObject("location");
            double lat = Double.parseDouble(latLng.getString("latitude"));
            double lng = Double.parseDouble(latLng.getString("longitude"));

            latLngList.add(new LatLng(lat,lng));

            /*String color = latLng.getString("color");

            switch (color) {
                case "red":
                    markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_square_medium))));
                    latLngList.add(new LatLng(lat, lng));
                    break;
                case "orange":
                    markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.orange_square_medium))));
                    break;
                case "yellow":
                    markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.yellow_square_medium))));
                    break;
                case "green":
                    markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.green_square_medium))));
                    break;
                default:
                    markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_dot))));
                    break;
            }*/

        }

        heatMapProvider = new HeatmapTileProvider.Builder().data(latLngList).build();
        heatMapOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(heatMapProvider));
    }

    public void startJSON() throws JSONException{
        JSONObject jsonObject = new JSONObject(loadJSONFromAsset());
        JSONArray jsonArray = jsonObject.getJSONArray("top");
        for (int i = 0; i < jsonArray.length();i++) {
            JSONObject objectInside = jsonArray.getJSONObject(i);
            JSONObject latLng = objectInside.getJSONObject("location");
            String name = objectInside.getString("type");
            double lat = Double.parseDouble(latLng.getString("latitude"));
            double lng = Double.parseDouble(latLng.getString("longitude"));

            markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).title(name).flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.red_dot))));
            latLngList.add(new LatLng(lat, lng));
        }
    }

    private void findRoute(){


        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Route Options")
                .setPositiveButton("Safest", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMap.clear();
                        String text = searchBar.getText().toString().trim();
                        if (text.length() > 0) {

                            if (currentLocation != null) {
                                new RouteTask(context, currentLocation.getLatitude() + "," + currentLocation.getLongitude(), text, mMap, false).execute();
                                dialog.cancel();
                            } else {
                                new RouteTask(context, "Johns Hopkins University", text, mMap, false).execute();
                                dialog.cancel();
                            }

                        }
                    }
                })
                .setNegativeButton("Fastest", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMap.clear();
                        String text = searchBar.getText().toString().trim();
                        if (text.length() > 0) {
                            if (currentLocation != null) {
                                new RouteTask(context, currentLocation.getLatitude() + "," + currentLocation.getLongitude(), text, mMap, true).execute();
                                dialog.cancel();
                            } else {
                                new RouteTask(context, "Johns Hopkins University", text, mMap, true).execute();
                                dialog.cancel();
                            }
                        }
                    }
                })
                .setCancelable(true);
        AlertDialog alertDialog = alert.create();
        alertDialog.show();

    }

    private class RouteTask extends AsyncTask<Void, Integer, Boolean> {
        private ArrayList<LatLng> lstLatLng;
        private Context context;
        private String start;
        private String end;
        private GoogleMap googleMap;
        private boolean fastest;

        public RouteTask(Context context, String start, String end, GoogleMap googleMap, boolean fastest) {
            this.context = context;
            this.lstLatLng = new ArrayList<LatLng>();
            this.start = start;
            this.end = end;
            this.googleMap = googleMap;
            this.fastest = fastest;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {

                boolean result = callDirectionsServer();
                return result;
            } catch(final Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if(!result) {
                Log.i("Error", "Error in post execute");
                //Toast.makeText(context, "Error in post execute", Toast.LENGTH_SHORT).show();
            } else {
                final PolylineOptions polylines = new PolylineOptions();
                polylines.color(Color.BLUE);

                for(final LatLng latLng : lstLatLng) {
                    polylines.add(latLng);
                }
                final MarkerOptions markerA = new MarkerOptions();
                markerA.position(lstLatLng.get(0));
                markerA.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                final MarkerOptions markerB = new MarkerOptions();
                markerB.position(lstLatLng.get(lstLatLng.size()-1));
                markerB.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lstLatLng.get(0), 12));
                googleMap.addMarker(markerA);
                googleMap.addPolyline(polylines);
                googleMap.addMarker(markerB);
            }

            try {
                if (theSwitch.isChecked()) {
                    startJSON();
                } else {
                    startHeatMapJSON();
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        private boolean decodePolylines(final String encodedPoints) throws Exception{
            int index = 0;
            int lat = 0, lng = 0;
            while (index < encodedPoints.length()) {
                int b, shift = 0, result = 0;
                do {
                    b = encodedPoints.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat; shift = 0; result = 0;
                do {
                    b = encodedPoints.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift; shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                if (!fastest) {
                    Location theLocation = new Location(provider);
                    theLocation.setLatitude((double) lat / 1E5);
                    theLocation.setLongitude((double) lng / 1E5);

                    boolean added = false;

                    for (int i = 0; i < latLngList.size(); i++) {

                        LatLng latLng = latLngList.get(i);
                        Location tempLoc = new Location(provider);
                        tempLoc.setLatitude(latLng.latitude);
                        tempLoc.setLongitude(latLng.longitude);

                        float distanceBetween = theLocation.distanceTo(tempLoc);
                        if (distanceBetween <= 300) {
                            //TODO: Test if this is better
                            double newLat = 0;
                            double newLng = 0;
                            if (theLocation.getLongitude() < tempLoc.getLongitude()) {
                                newLat = ((double) lat / 1E5) - .001;//.00005;
                                newLng = (double) lng / 1E5 - .001;//.00005;
                            } else if (theLocation.getLongitude() >= tempLoc.getLongitude()) {
                                newLat = ((double) lat / 1E5) + .001;//.00005;
                                newLng = (double) lng / 1E5 + .001;//.00005;
                            }

                            lstLatLng.add(new LatLng(newLat, newLng));
                            added = true;
                            break;
                        }
                    }

                    if (!added) {
                        lstLatLng.add(new LatLng((double) lat / 1E5, (double) lng / 1E5));
                    }
                } else {
                    lstLatLng.add(new LatLng((double) lat / 1E5, (double) lng / 1E5));
                }


            }
            return true;
        }

        private boolean callDirectionsServer() throws Exception {
            final StringBuilder url = new StringBuilder("http://maps.googleapis.com/maps/api/directions/xml?sensor=false&language=pt");
            url.append("&origin=");
            url.append(start.replace(' ', '+'));
            url.append("&destination=");
            url.append(end.replace(' ', '+'));
            url.append("&mode=walking");
            /*if (!this.fastest && waypoint.length() > 0) {
                url.append("&waypoints=optimize:true");
                //url.append("&waypoints=");
                url.append(waypoint.replace(' ', '+'));
            }*/

            final InputStream stream = new URL(url.toString()).openStream();
            final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setIgnoringComments(true);
            final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            final Document document = documentBuilder.parse(stream);
            document.getDocumentElement().normalize();
            final String status = document.getElementsByTagName("status").item(0).getTextContent();
            if(!"OK".equals(status)) {
                return false;
            }
            final Element elementLeg = (Element) document.getElementsByTagName("leg").item(0);
            final NodeList nodeListStep = elementLeg.getElementsByTagName("step");
            final int length = nodeListStep.getLength();
            for(int i=0; i<length; i++) {
                final Node nodeStep = nodeListStep.item(i);
                if(nodeStep.getNodeType() == Node.ELEMENT_NODE) {
                    final Element elementStep = (Element) nodeStep;
                    decodePolylines(elementStep.getElementsByTagName("points").item(0).getTextContent());
                    /*boolean theResult = decodePolylines(elementStep.getElementsByTagName("points").item(0).getTextContent());
                    if (!theResult) {
                        return false;
                    }*/
                }
            }
            return true;
        }

    }

    public void changeEmerContact(View v) {
        Intent intent = new Intent(this, SetEmerContactActivity.class);
        startActivity(intent);
    }

    public void sendNotification(View v) {
        Toast.makeText(this,"DANGER DANGER DANGER", Toast.LENGTH_SHORT).show();

        final Intent intent = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map data = new HashMap();
        data.put("title", "Warning");
        data.put("body", "Dangerous Area!");
        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();

        intent.putExtra("messageType", "PEBBLE_ALERT");
        intent.putExtra("sender", "PebbleKit Android");
        intent.putExtra("notificationData", notificationData);
        sendBroadcast(intent);
    }

}
