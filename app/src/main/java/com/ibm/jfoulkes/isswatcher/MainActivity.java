package com.ibm.jfoulkes.isswatcher;

import android.Manifest;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private String appTag = "ISSWatcher";
    private String ISS_url = "http://api.open-notify.org/iss-pass.json";
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 1;
    private static final int REQUEST_CODE_INTERNET = 2;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private SimpleCursorAdapter cursorAdapter;
//    private double latitude = -1000, longitude = -1000;
    private double latitude = 10, longitude = -74;
    private ArrayList<String> listEntries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final Button scanButton = (Button) findViewById(R.id.button);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callISSAPI(latitude, longitude);
            }
        });
        final ListView listView = (ListView) findViewById(R.id.listview);
        listView.setAdapter(cursorAdapter);
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d(appTag, "GPS location updated");
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };
        getGPSLocation();
    }

    private void getGPSLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ACCESS_FINE_LOCATION);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, null);
        }
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(appTag, "ACCESS_FINE_LOCATION permission granted");
                    getGPSLocation();
                } else {
                    Log.d(appTag, "ACCESS_FINE_LOCATION permission not granted");
                }
                break;
            case REQUEST_CODE_INTERNET:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(appTag, "INTERNET permission granted");
                    callISSAPI(latitude, longitude);
                } else {
                    Log.d(appTag, "INTERNET permission not granted");
                }
                break;
            default:
                break;
        }
    }
    private void callISSAPI(double latitude, double longitude) {
        /*
            Do this with a try/catch like GPS?
            Not having internet permissions for this app should be "exceptional"
         */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, REQUEST_CODE_INTERNET);
        } else {
            Log.d(appTag, "called ISS API with lat: " + latitude + ", long: " + longitude);
            if (latitude > -1000 && longitude > -1000) {
                String urlWithQuery = ISS_url + "?lat=" + latitude + "&lon=" + longitude;
                StringRequest stringRequest = new StringRequest(Request.Method.GET, urlWithQuery, new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(appTag, "ISS successful call");
                        parseISSResponse(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(appTag, "ISS error");

                    }
                });
                stringRequest.setTag(appTag);
                RequestQueue requestQueue = RequestQueueSingleton.getInstance(this.getApplicationContext()).getRequestQueue();
                requestQueue.add(stringRequest);
            } else {
                Log.e(appTag, "GPS data unavailable");
            }
        }
    }

    private void parseISSResponse(String response) {
        Log.d(appTag, "Parsing ISS response");
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray responseArray = jsonObject.getJSONArray("response");
            int responseLength = responseArray.length();
            if (responseLength > 0) {
                String[] issStringArray = new String[responseLength];
                for (int i = 0; i < responseLength; i++) {
                    JSONObject obj = responseArray.getJSONObject(i);
                    String duration = obj.getString("duration");
                    String risetime = obj.getString("risetime");
                    issStringArray[i] = duration + ": " + risetime;
                }
                int[] toViews = {android.R.id.text1};
                cursorAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, null, issStringArray, toViews, 0);

                Log.d(appTag, "JSON object created");
            }


        } catch (JSONException jse) {
            Log.e(appTag, "JSON exception: " + jse.toString());
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }


}
