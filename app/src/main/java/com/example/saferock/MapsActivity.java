package com.example.saferock;

import android.content.Context;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private PebbleKit.PebbleDataReceiver mDataReceiver;

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
            if (distanceBetween <= 400) {

                Toast.makeText(this,"DANGER DANGER DANGER", Toast.LENGTH_SHORT);

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
                findRoute();
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

                    callEmergencyContact();
                }

            };
            PebbleKit.registerReceivedDataHandler(getApplicationContext(), mDataReceiver);
        }
    }

    private void callEmergencyContact() {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);

            final String PREFS_NAME = "MyPrefsFile";

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

            String number = settings.getString("phone_number", "");

            if (number.length() > 0) {
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
        //CameraUpdate center=CameraUpdateFactory.newLatLng(new LatLng(mMap.getMyLocation().getLatitude(),mMap.getMyLocation().getLongitude()));
        //CameraUpdate zoom=CameraUpdateFactory.zoomTo(15);
        LatLng firstLatLng = null;
        try {
            JSONObject jsonObject = new JSONObject(loadJSONFromAsset());
            JSONArray jsonArray = jsonObject.getJSONArray("top");
            for (int i = 0; i < jsonArray.length();i++) {
                JSONObject objectInside = jsonArray.getJSONObject(i);
                JSONObject latLng = objectInside.getJSONObject("location");
                String name = objectInside.getString("type");
                double lat = Double.parseDouble(latLng.getString("latitude"));
                double lng = Double.parseDouble(latLng.getString("longitude"));
                markerList.add(mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).title(name)));
                if (i == 0) {
                    firstLatLng = new LatLng(lat, lng);
                }


            }


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

    private void findRoute(){
        String text = searchBar.getText().toString().trim();

        if (text.length() > 0) {
            if (currentLocation != null) {
                new RouteTask(this, currentLocation.getLatitude()+","+currentLocation.getLongitude(), text, mMap).execute();
            } else {
                new RouteTask(this, "Johns Hopkins University", text, mMap).execute();
            }

        }
    }

    private class RouteTask extends AsyncTask<Void, Integer, Boolean> {
        private ArrayList<LatLng> lstLatLng;
        private Context context;
        private String start;
        private String end;
        private GoogleMap googleMap;

        public RouteTask(Context context, String start, String end, GoogleMap googleMap) {
            this.context = context;
            this.lstLatLng = new ArrayList<LatLng>();
            this.start = start;
            this.end = end;
            this.googleMap = googleMap;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                final StringBuilder url = new StringBuilder("http://maps.googleapis.com/maps/api/directions/xml?sensor=false&language=pt");
                url.append("&origin=");
                url.append(start.replace(' ', '+'));
                url.append("&destination=");
                url.append(end.replace(' ', '+'));
                final InputStream stream = new URL(url.toString()).openStream();
                final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setIgnoringComments(true);
                final DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                final Document document = documentBuilder.parse(stream); document.getDocumentElement().normalize();
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
                    }
                }
                return true;
            } catch(final Exception e) {
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
                Toast.makeText(context, "Error in post execute", Toast.LENGTH_SHORT).show();
            } else {
                final PolylineOptions polylines = new PolylineOptions();
                polylines.color(Color.BLUE);
                for(final LatLng latLng : lstLatLng) {
                    polylines.add(latLng);
                }
                final MarkerOptions markerA = new MarkerOptions();
                markerA.position(lstLatLng.get(0));
                markerA.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                final MarkerOptions markerB = new MarkerOptions(); markerB.position(lstLatLng.get(lstLatLng.size()-1));
                markerB.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lstLatLng.get(0), 12));
                googleMap.addMarker(markerA);
                googleMap.addPolyline(polylines);
                googleMap.addMarker(markerB);
            }

        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        private void decodePolylines(final String encodedPoints) {
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
                lstLatLng.add(new LatLng((double)lat/1E5, (double)lng/1E5));
            }
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.contact) {
            Intent intent = new Intent(this, SetEmerContactActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
