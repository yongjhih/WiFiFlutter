package com.alternadom.wifiiot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import info.whitebyte.hotspotmanager.ClientScanResult;
import info.whitebyte.hotspotmanager.FinishScanListener;
import info.whitebyte.hotspotmanager.WifiApManager;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.ViewDestroyListener;
import io.flutter.view.FlutterNativeView;

//import android.net.NetworkSpecifier;
//import android.net.wifi.WifiNetworkSpecifier;

/**
 * WifiIotPlugin
 */
public class WifiIotPlugin implements MethodCallHandler, EventChannel.StreamHandler {
    @Nullable
    private WifiManager moWiFi;
    @NonNull
    private Context context;
    @NonNull
    private WifiApManager moWiFiAPManager;
    @Nullable
    private Activity moActivity;
    @Nullable
    private BroadcastReceiver receiver;
    @NonNull
    private List<String> ssidsToBeRemovedOnExit = new ArrayList<>();
    @NonNull
    private List<NetworkCallback> networkCallbacks = new ArrayList<>();
    @NonNull
    private final Registrar registrar;

    private WifiIotPlugin(Registrar registrar) {
        this.registrar = registrar;
        this.moActivity = registrar.activity();
        if (moActivity != null) {
            this.context = moActivity.getApplicationContext();
        } else {
            this.context = registrar.context().getApplicationContext();
        }
        this.moWiFi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.moWiFiAPManager = new WifiApManager(context.getApplicationContext());
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        if (registrar.activity() == null) {
            // When a background flutter view tries to register the plugin, the registrar has no activity.
            // We stop the registration process as this plugin is foreground only.
            return;
        }
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "wifi_iot");
        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "plugins.wififlutter.io/wifi_scan");
        final WifiIotPlugin wifiIotPlugin = new WifiIotPlugin(registrar);
        eventChannel.setStreamHandler(wifiIotPlugin);
        channel.setMethodCallHandler(wifiIotPlugin);

        registrar.addViewDestroyListener(new ViewDestroyListener() {
            @Override
            public boolean onViewDestroy(FlutterNativeView view) {
                removeOnceNetworks(wifiIotPlugin);
                return false;
            }
        });
    }

    public static void removeOnceNetworks(@NonNull final WifiIotPlugin wifiIotPlugin) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wifiIotPlugin.unregisterNetworkCallbacks();
        }
        if (!wifiIotPlugin.ssidsToBeRemovedOnExit.isEmpty()) {
            List<WifiConfiguration> wifiConfigList =
                    wifiIotPlugin.moWiFi.getConfiguredNetworks();
            if (wifiConfigList != null && !wifiConfigList.isEmpty()) {
                for (String ssid : wifiIotPlugin.ssidsToBeRemovedOnExit) {
                    for (WifiConfiguration wifiConfig : wifiConfigList) {
                        if (wifiConfig == null) continue;
                        if (wifiConfig.SSID == null) continue;
                        if (wifiConfig.SSID.equals(ssid)) {
                            wifiIotPlugin.moWiFi.removeNetwork(wifiConfig.networkId);
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void unregisterNetworkCallbacks() {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            for (NetworkCallback networkCallback : networkCallbacks) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }
        networkCallbacks.clear();
    }

    @Override
    public void onMethodCall(MethodCall poCall, Result poResult) {
        switch (poCall.method) {
            case "loadWifiList":
                loadWifiList(poResult);
                break;
            case "forceWifiUsage":
                forceWifiUsage(poCall, poResult);
                break;
            case "isEnabled":
                isEnabled(poResult);
                break;
            case "setEnabled":
                setEnabled(poCall, poResult);
                break;
            case "connect":
                connect(poCall, poResult);
                break;
            case "findAndConnect":
                findAndConnect(poCall, poResult);
                break;
            case "isConnected":
                isConnected(poResult);
                break;
                /*
            case "reconnect":
                reconnect(poResult);
                break;
                */
            case "disconnect":
                disconnect(poResult);
                break;
            case "getSSID":
                getSSID(poResult);
                break;
            case "getBSSID":
                getBSSID(poResult);
                break;
            case "getCurrentSignalStrength":
                getCurrentSignalStrength(poResult);
                break;
            case "getFrequency":
                getFrequency(poResult);
                break;
            case "getIP":
                getIP(poResult);
                break;
            case "removeWifiNetwork":
                removeWifiNetwork(poCall, poResult);
                break;
            case "isRegisteredWifiNetwork":
                isRegisteredWifiNetwork(poCall, poResult);
                break;
            case "isWiFiAPEnabled":
                isWiFiAPEnabled(poResult);
                break;
            case "setWiFiAPEnabled":
                setWiFiAPEnabled(poCall, poResult);
                break;
            case "getWiFiAPState":
                getWiFiAPState(poResult);
                break;
            case "getClientList":
                getClientList(poCall, poResult);
                break;
            case "getWiFiAPSSID":
                getWiFiAPSSID(poResult);
                break;
            case "setWiFiAPSSID":
                setWiFiAPSSID(poCall, poResult);
                break;
            case "isSSIDHidden":
                isSSIDHidden(poResult);
                break;
            case "setSSIDHidden":
                setSSIDHidden(poCall, poResult);
                break;
            case "getWiFiAPPreSharedKey":
                getWiFiAPPreSharedKey(poResult);
                break;
            case "setWiFiAPPreSharedKey":
                setWiFiAPPreSharedKey(poCall, poResult);
                break;
            case "setMACFiltering":
                setMACFiltering(poCall, poResult);
                break;
            default:
                poResult.notImplemented();
                break;
        }
    }

    /**
     *
     * @param poCall
     * @param poResult
     */
    private void setMACFiltering(MethodCall poCall, Result poResult) {
        boolean bEnable = poCall.argument("state");


        /// cat /data/misc/wifi_hostapd/hostapd.accept

        // Log.e(this.getClass().toString(), "TODO : Develop function to enable/disable MAC filtering...");

        poResult.error("TODO", "Develop function to enable/disable MAC filtering...", null);
    }


    /**
     * The network's SSID. Can either be an ASCII string,
     * which must be enclosed in double quotation marks
     * (e.g., {@code "MyNetwork"}), or a string of
     * hex digits, which are not enclosed in quotes
     * (e.g., {@code 01a243f405}).
     */
    private void getWiFiAPSSID(Result poResult) {
        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();
        if (oWiFiConfig != null && oWiFiConfig.SSID != null) {
            poResult.success(oWiFiConfig.SSID);
            return;
        }
        poResult.error("Exception", "SSID not found", null);
    }

    private void setWiFiAPSSID(MethodCall poCall, Result poResult) {
        String sAPSSID = poCall.argument("ssid");

        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();

        oWiFiConfig.SSID = sAPSSID;

        moWiFiAPManager.setWifiApConfiguration(oWiFiConfig);

        poResult.success(null);
    }

    /**
     * This is a network that does not broadcast its SSID, so an
     * SSID-specific probe request must be used for scans.
     */
    private void isSSIDHidden(Result poResult) {
        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();
        if (oWiFiConfig != null && oWiFiConfig.hiddenSSID) {
            poResult.success(oWiFiConfig.hiddenSSID);
            return;
        }
        poResult.error("Exception", "Wifi AP not Supported", null);
    }

    private void setSSIDHidden(MethodCall poCall, Result poResult) {
        boolean isSSIDHidden = poCall.argument("hidden");

        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();

        oWiFiConfig.hiddenSSID = isSSIDHidden;

        moWiFiAPManager.setWifiApConfiguration(oWiFiConfig);

        poResult.success(null);
    }

    /**
     * Pre-shared key for use with WPA-PSK. Either an ASCII string enclosed in
     * double quotation marks (e.g., {@code "abcdefghij"} for PSK passphrase or
     * a string of 64 hex digits for raw PSK.
     * <p/>
     * When the value of this key is read, the actual key is
     * not returned, just a "*" if the key has a value, or the null
     * string otherwise.
     */
    private void getWiFiAPPreSharedKey(Result poResult) {
        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();
        if (oWiFiConfig != null && oWiFiConfig.preSharedKey != null) {
            poResult.success(oWiFiConfig.preSharedKey);
            return;
        }
        poResult.error("Exception", "Wifi AP not Supported", null);
    }

    private void setWiFiAPPreSharedKey(MethodCall poCall, Result poResult) {
        String sPreSharedKey = poCall.argument("preSharedKey");

        WifiConfiguration oWiFiConfig = moWiFiAPManager.getWifiApConfiguration();

        oWiFiConfig.preSharedKey = sPreSharedKey;

        moWiFiAPManager.setWifiApConfiguration(oWiFiConfig);

        poResult.success(null);
    }

    /**
     * Gets a list of the clients connected to the Hotspot
     * *** getClientList :
     * param onlyReachables   {@code false} if the list should contain unreachable (probably disconnected) clients, {@code true} otherwise
     * param reachableTimeout Reachable Timout in miliseconds, 300 is default
     * param finishListener,  Interface called when the scan method finishes
     */
    private void getClientList(MethodCall poCall, final Result poResult) {
        Boolean onlyReachables = false;
        if (poCall.argument("onlyReachables") != null) {
            onlyReachables = poCall.argument("onlyReachables");
        }

        Integer reachableTimeout = 300;
        if (poCall.argument("reachableTimeout") != null) {
            reachableTimeout = poCall.argument("reachableTimeout");
        }

        final Boolean finalOnlyReachables = onlyReachables;
        FinishScanListener oFinishScanListener = new FinishScanListener() {
            @Override
            public void onFinishScan(final ArrayList<ClientScanResult> clients) {
                try {
                    JSONArray clientArray = new JSONArray();

                    for (ClientScanResult client : clients) {
                        JSONObject clientObject = new JSONObject();

                        Boolean clientIsReachable = client.isReachable();
                        Boolean shouldReturnCurrentClient = true;
                        if ( finalOnlyReachables.booleanValue()) {
                            if (!clientIsReachable.booleanValue()){
                                shouldReturnCurrentClient = Boolean.valueOf(false);
                            }
                        }
                        if (shouldReturnCurrentClient.booleanValue()) {
                            try {
                                clientObject.put("IPAddr", client.getIpAddr());
                                clientObject.put("HWAddr", client.getHWAddr());
                                clientObject.put("Device", client.getDevice());
                                clientObject.put("isReachable", client.isReachable());
                            } catch (JSONException e) {
                                poResult.error("Exception", e.getMessage(), null);
                            }
                            clientArray.put(clientObject);
                        }
                    }
                    poResult.success(clientArray.toString());
                } catch (Exception e) {
                    poResult.error("Exception", e.getMessage(), null);
                }
            }
        };

        if (reachableTimeout != null) {
            moWiFiAPManager.getClientList(onlyReachables, reachableTimeout, oFinishScanListener);
        } else {
            moWiFiAPManager.getClientList(onlyReachables, oFinishScanListener);
        }
    }

    /**
     * Return whether Wi-Fi AP is enabled or disabled.
     * *** isWifiApEnabled :
     * return {@code true} if Wi-Fi AP is enabled
     */
    private void isWiFiAPEnabled(Result poResult) {
        poResult.success(moWiFiAPManager.isWifiApEnabled());
    }

    /**
     * Start AccessPoint mode with the specified
     * configuration. If the radio is already running in
     * AP mode, update the new configuration
     * Note that starting in access point mode disables station
     * mode operation
     * *** setWifiApEnabled :
     * param wifiConfig SSID, security and channel details as part of WifiConfiguration
     * return {@code true} if the operation succeeds, {@code false} otherwise
     */
    private void setWiFiAPEnabled(MethodCall poCall, Result poResult) {
        boolean enabled = poCall.argument("state");
        moWiFiAPManager.setWifiApEnabled(null, enabled);
        poResult.success(null);
    }

    /**
     * Gets the Wi-Fi enabled state.
     * *** getWifiApState :
     * return {link WIFI_AP_STATE}
     */
    private void getWiFiAPState(Result poResult) {
        poResult.success(moWiFiAPManager.getWifiApState().ordinal());
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 65655434;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            moActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
        receiver = createReceiver(eventSink);

        context.registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    public void onCancel(Object o) {
        if(receiver != null){
            context.unregisterReceiver(receiver);
            receiver = null;
        }

    }

    private BroadcastReceiver createReceiver(final EventChannel.EventSink eventSink){
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                eventSink.success(handleNetworkScanResult().toString());
            }
        };
    }
    JSONArray handleNetworkScanResult(){
        List<ScanResult> results = moWiFi.getScanResults();
        JSONArray wifiArray = new JSONArray();

        try {
            for (ScanResult result : results) {
                JSONObject wifiObject = new JSONObject();
                if (result.SSID != null && !result.SSID.equals("")) {

                    wifiObject.put("SSID", result.SSID);
                    wifiObject.put("BSSID", result.BSSID);
                    wifiObject.put("capabilities", result.capabilities);
                    wifiObject.put("frequency", result.frequency);
                    wifiObject.put("level", result.level);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        wifiObject.put("timestamp", result.timestamp);
                    } else {
                        wifiObject.put("timestamp", 0);
                    }
                    /// Other fields not added
                    //wifiObject.put("operatorFriendlyName", result.operatorFriendlyName);
                    //wifiObject.put("venueName", result.venueName);
                    //wifiObject.put("centerFreq0", result.centerFreq0);
                    //wifiObject.put("centerFreq1", result.centerFreq1);
                    //wifiObject.put("channelWidth", result.channelWidth);

                    wifiArray.put(wifiObject);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            return wifiArray;
        }
    }

    /// Method to load wifi list into string via Callback. Returns a stringified JSONArray
    private void loadWifiList(final Result poResult) {
        try {

            int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 65655434;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                moActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }

            moWiFi.startScan();

            poResult.success(handleNetworkScanResult().toString());

        } catch (Exception e) {
            poResult.error("Exception", e.getMessage(), null);
        }
    }


    /// Method to force wifi usage if the user needs to send requests via wifi
    /// if it does not have internet connection. Useful for IoT applications, when
    /// the app needs to communicate and send requests to a device that have no
    /// internet connection via wifi.

    /// Receives a boolean to enable forceWifiUsage if true, and disable if false.
    /// Is important to enable only when communicating with the device via wifi
    /// and remember to disable it when disconnecting from device.
    private void forceWifiUsage(MethodCall poCall, Result poResult) {
        boolean canWriteFlag = true;

        boolean useWifi = poCall.argument("useWifi");

        if (useWifi) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    //canWriteFlag = Settings.System.canWrite(context);

                    if (!canWriteFlag) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        context.startActivity(intent);
                    }
                }

                final ConnectivityManager manager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                final NetworkRequest.Builder builder = new NetworkRequest.Builder()
                        /// set the transport type WIFI
                        .removeTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

                if (manager != null) {
                    manager.registerNetworkCallback(builder.build(), new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                manager.bindProcessToNetwork(network);
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                ConnectivityManager.setProcessDefaultNetwork(network);
                            }
                        }
                    });
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final ConnectivityManager manager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                assert manager != null;
                manager.bindProcessToNetwork(null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ConnectivityManager.setProcessDefaultNetwork(null);
            }
        }
        poResult.success(null);
    }

    /// Method to check if wifi is enabled
    private void isEnabled(Result poResult) {
        Log.d("ASDF", "isEnabled");
        poResult.success(moWiFi.isWifiEnabled());
    }

    /// Method to connect/disconnect wifi service
    private void setEnabled(MethodCall poCall, Result poResult) {
        final Boolean enabled = poCall.argument("state");
        Log.d("ASDF", "setEnabled");
        try {
            final Boolean res = moWiFi.setWifiEnabled(enabled);
            poResult.success(res);
        } catch (SecurityException e) {
            Log.d("ASDF", "" + e);
            poResult.error(e.toString(), e.getMessage(), e);
        }
    }

    private void connect(final MethodCall poCall, final Result poResult) {
        mExecutor.execute(() -> {
            String ssid = poCall.argument("ssid");
            String password = poCall.argument("password");
            String security = poCall.argument("security");
            Boolean joinOnce = poCall.argument("join_once");

            Log.d("ASDF", "" + ssid);
            Log.d("ASDF", "" + password);
            Log.d("ASDF", "" + security);
            final Handler handler = new Handler(Looper.getMainLooper());
            try {
                connectTo(ssid, password, security, joinOnce,
                        connected -> handler.post(() -> poResult.success(connected)));
            } catch (Throwable e) {
                if (e != null) {
                    Log.w("ASDF", "e: " + e.getMessage());
                    handler.post(() -> poResult.error("Exception", "e: " + e.getMessage(), null));
                } else {
                    handler.post(() -> poResult.error("Exception", "", null));
                }
            }
        });
    }

    public static final String CAPTIVE_PORTAL_DETECTION_ENABLED = "captive_portal_detection_enabled";
    public static final int CAPTIVE_PORTAL_MODE_IGNORE = 0;

    /**
     * When detecting a captive portal, display a notification that
     * prompts the user to sign in.
     */
    public static final int CAPTIVE_PORTAL_MODE_PROMPT = 1;

    /**
     * When detecting a captive portal, immediately disconnect from the
     * network and do not reconnect to that network in the future.
     */
    public static final int CAPTIVE_PORTAL_MODE_AVOID = 2;

    /**
     * What to do when connecting a network that presents a captive portal.
     * Must be one of the CAPTIVE_PORTAL_MODE_* constants above.
     *
     * The default for this setting is CAPTIVE_PORTAL_MODE_PROMPT.
     */
    public static final String CAPTIVE_PORTAL_MODE = "captive_portal_mode";

    /*
    private void openWifiSettingsSheet(final MethodCall poCall, final Result poResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startActivityForResult(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), INTERNET_SETTINGS_REQUEST);
        } else {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }
    */

    /// Send the ssid and password of a Wifi network into this to connect to the network.
    /// Example:  wifi.findAndConnect(ssid, password);
    /// After 10 seconds, a post telling you whether you are connected will pop up.
    /// Callback returns true if ssid is in the range
    @SuppressLint("PrivateApi")
    private void findAndConnect(final MethodCall poCall, final Result poResult) {
        Log.d("ASDF", "findAndConnect");
        int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 65655434;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            moActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
        final String ssid = poCall.argument("ssid");
        final String password = poCall.argument("password");
        final Boolean joinOnce = poCall.argument("join_once");

        if (ssid == null) {
            Log.d("ASDF", "ssid is null");
            poResult.error("Error", "ssid is null", null);
            return;
        }

        mExecutor.execute(() -> {
            Log.d("ASDF", "getScanResult...");
            final Handler handler = new Handler(Looper.getMainLooper());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                final ScanResult selectedResult = getScanResult(ssid);
                if (selectedResult != null) {
                    Log.i("ASDF", "found: " + selectedResult.SSID);
                    Log.i("ASDF", "password: " + password);

                    try {
                        connectTo(ssid, password, getSecurityType(selectedResult), joinOnce,
                                connected -> handler.post(() -> poResult.success(connected)));
                    } catch (Throwable e) {
                        Log.w("ASDF", "e: " + e.getMessage());
                        handler.post(() -> poResult.error("Exception", "e: " + e.getMessage(), null));
                    }
                } else {
                    Log.d("ASDF", "selectedResult is null");
                    handler.post(() -> poResult.error("Error", ssid + " not found", null));
                }
            } else {
                try {
                    connectTo(ssid, password, null, joinOnce,
                            connected -> handler.post(() -> poResult.success(connected)));
                } catch (Throwable e) {
                    Log.w("ASDF", "e: " + e.getMessage());
                    handler.post(() -> poResult.error("Exception", "e: " + e.getMessage(), null));
                }
            }
        });
    }

    @Nullable
    private ScanResult getScanResult(@NonNull final String ssid) {
        try {
            List<ScanResult> results = moWiFi.getScanResults();
            ScanResult selectedResult = null;

            for (ScanResult result : results) {
                String resultString = "" + result.SSID;
                if (ssid.equals(resultString)) {
                    selectedResult = result;
                    break;
                }
            }

            return selectedResult;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getSecurityType(ScanResult scanResult) {
        String capabilities = scanResult.capabilities;

        if (capabilities.contains("WPA") ||
                capabilities.contains("WPA2") ||
                capabilities.contains("WPA/WPA2 PSK")) {
            return "WPA";
        } else if (capabilities.contains("WEP")) {
            return "WEP";
        } else {
            return null;
        }
    }

    /// Use this method to check if the device is currently connected to Wifi.
    private void isConnected(Result poResult) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager != null ? connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI) : null;
        if (mWifi != null && mWifi.isConnected()) {
            poResult.success(true);
        } else {
            poResult.success(false);
        }
    }

    /// Disconnect current Wifi.
    private void disconnect(Result poResult) {
        Log.i("ASDF", "disconnect");
        moWiFi.disconnect();
        removeOnceNetworks(this);
        moWiFi.reconnect();
        poResult.success(null);
    }

    /// This method will return current ssid
    private void getSSID(Result poResult) {
        poResult.success(getSsid());
    }

    public static String unquote(String s) {
        return s.startsWith("\"") && s.endsWith("\"")
                ? s.substring(1, s.length() - 1)
                : s;
    }

    /// This method will return the basic service set identifier (BSSID) of the current access point
    private void getBSSID(Result poResult) {
        WifiInfo info = moWiFi.getConnectionInfo();

        String bssid = info.getBSSID();

        try {
            poResult.success(bssid.toUpperCase());
        } catch (Exception e) {
            poResult.error("Exception", e.getMessage(), null);
        }
    }

    /// This method will return current WiFi signal strength
    private void getCurrentSignalStrength(Result poResult) {
        int linkSpeed = moWiFi.getConnectionInfo().getRssi();
        poResult.success(linkSpeed);
    }

    /// This method will return current WiFi frequency
    private void getFrequency(Result poResult) {
        WifiInfo info = moWiFi.getConnectionInfo();
        int frequency = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            frequency = info.getFrequency();
        }
        poResult.success(frequency);
    }

    /// This method will return current IP
    private void getIP(Result poResult) {
        WifiInfo info = moWiFi.getConnectionInfo();
        String stringip = longToIP(info.getIpAddress());
        poResult.success(stringip);
    }

    /// This method will remove the WiFi network as per the passed SSID from the device list
    private void removeWifiNetwork(MethodCall poCall, Result poResult) {
        Log.i("ASDF", "removeWifiNetwork");
        String prefix_ssid = poCall.argument("ssid");
        if (prefix_ssid == null || prefix_ssid.equals("")) {
            poResult.error("Error", "No prefix SSID was given!", null);
        }

        List<WifiConfiguration> mWifiConfigList = moWiFi.getConfiguredNetworks();
        if (mWifiConfigList != null && !mWifiConfigList.isEmpty()) {
            for (WifiConfiguration wifiConfig : mWifiConfigList) {
                String comparableSSID = ('"' + prefix_ssid); //Add quotes because wifiConfig.SSID has them
                if (wifiConfig.SSID.startsWith(comparableSSID)) {
                    moWiFi.removeNetwork(wifiConfig.networkId);
                    moWiFi.saveConfiguration();
                    poResult.success(true);
                    return;
                }
            }
        }
        poResult.success(false);
    }

    /// This method will remove the WiFi network as per the passed SSID from the device list
    private void isRegisteredWifiNetwork(MethodCall poCall, Result poResult) {

        String ssid = poCall.argument("ssid");
        if (ssid == null) {
            poResult.success(false);
            return;
        }

        List<WifiConfiguration> mWifiConfigList = moWiFi.getConfiguredNetworks();
        String comparableSSID = ('"' + ssid + '"'); //Add quotes because wifiConfig.SSID has them
        if (mWifiConfigList != null && !mWifiConfigList.isEmpty()) {
            for (WifiConfiguration wifiConfig : mWifiConfigList) {
                if (wifiConfig.SSID != null && wifiConfig.SSID.equals(comparableSSID)) {
                    poResult.success(true);
                    return;
                }
            }
        }
        poResult.success(false);
    }

    private static String longToIP(int longIp) {
        StringBuilder sb = new StringBuilder("");
        String[] strip = new String[4];
        strip[3] = String.valueOf((longIp >>> 24));
        strip[2] = String.valueOf((longIp & 0x00FFFFFF) >>> 16);
        strip[1] = String.valueOf((longIp & 0x0000FFFF) >>> 8);
        strip[0] = String.valueOf((longIp & 0x000000FF));
        sb.append(strip[0]);
        sb.append(".");
        sb.append(strip[1]);
        sb.append(".");
        sb.append(strip[2]);
        sb.append(".");
        sb.append(strip[3]);
        return sb.toString();
    }

    /// Method to connect to WIFI Network
    private void connectTo(
            @NonNull
            final String ssid,
            @Nullable
            final String password,
            @Nullable
            final String security,
            @Nullable
            final Boolean joinOnce,
            Consumer<Boolean> callback
            ) {
        Log.d("ASDF", "Build.VERSION.SDK_INT: " + Build.VERSION.SDK_INT);
        Log.d("ASDF", "Build.VERSION_CODES.Q: " + Build.VERSION_CODES.Q);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final WifiNetworkSpecifier.Builder specBuilder = new WifiNetworkSpecifier.Builder();
            if (!"".equals(ssid)) {
                specBuilder.setSsid(ssid);
            }
            if (password != null && !"".equals(password)) {
                specBuilder.setWpa2Passphrase(password);
            }

            final ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                callback.accept(false);
            } else {
                final NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
                    // Restrict once callback
                    Consumer<Boolean> _callback = callback;
                    @Override
                    public void onAvailable(final Network network) {
                        super.onAvailable(network);
                        Log.d("ASDF", "onAvailable: " + network);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            connectivityManager.bindProcessToNetwork(network);
                        } else {
                            connectivityManager.setProcessDefaultNetwork(network);
                        }
                        if (_callback != null) {
                            _callback.accept(true);
                        }
                        _callback = null;
                    }

                    @Override
                    public void onUnavailable() {
                        super.onUnavailable();
                        Log.d("ASDF", "onUnavailable");
                        if (_callback != null) {
                            callback.accept(false);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                connectivityManager.bindProcessToNetwork(null);
                            } else {
                                connectivityManager.setProcessDefaultNetwork(null);
                            }
                        }
                        _callback = null;
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        super.onLost(network);
                        Log.d("ASDF", "onLost");
                        if (_callback != null) {
                            callback.accept(false);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                connectivityManager.bindProcessToNetwork(null);
                            } else {
                                connectivityManager.setProcessDefaultNetwork(null);
                            }
                        }
                        _callback = null;
                    }
                };
                networkCallbacks.add(networkCallback);
                connectivityManager.requestNetwork(
                        new NetworkRequest.Builder()
                                .removeTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                .setNetworkSpecifier(specBuilder.build())
                                .build(),
                        networkCallback
                );
            }
        } else {
            callback.accept(connectTo(ssid, password, security, joinOnce));
        }
    }

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    /**
     * Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
     */
    private Boolean connectTo(
            @NonNull
            final String ssid,
            @Nullable
            final String password,
            @Nullable
            final String security,
            @Nullable
            final Boolean joinOnce
            ) {
        /// Make new configuration
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + ssid + "\"";
        conf.priority = 100;
        Log.i("ASDF", "0SSID: ");
        Log.i("ASDF", "0SSID: " + conf.SSID);

        final String _security = (security != null) ? security.toUpperCase() : "NONE";

        if (_security.equals("WPA")) {

            /// appropriate ciper is need to set according to security type used,
            /// ifcase of not added it will not be able to connect
            conf.preSharedKey = "\"" + password + "\"";

            conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

            conf.status = WifiConfiguration.Status.ENABLED;

            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);

            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            conf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

            conf.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            conf.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        } else if (_security.equals("WEP")) {
            conf.wepKeys[0] = "\"" + password + "\"";
            conf.wepTxKeyIndex = 0;
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        } else {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        final List<WifiConfiguration> mWifiConfigList = moWiFi.getConfiguredNetworks();

        int updateNetwork = -1;

        Log.i("ASDF", "SSID: " + conf.SSID);
        Log.i("ASDF", "security: " + security);
        if (mWifiConfigList != null && !mWifiConfigList.isEmpty()) {
            for (WifiConfiguration wifiConfig : mWifiConfigList) {
                if (wifiConfig == null) continue;
                if (wifiConfig.SSID == null) continue;
                if (wifiConfig.SSID.equals(conf.SSID)) {
                    Log.i("ASDF", "found networkConfig: " + wifiConfig.SSID);
                    conf.networkId = wifiConfig.networkId;
                    updateNetwork = moWiFi.updateNetwork(conf);
                    if (updateNetwork == -1) {
                        conf = wifiConfig;
                        updateNetwork = conf.networkId;
                        Log.i("ASDF", "update failed: apply existing network: " + conf.networkId);
                    }
                }
            }
        }
        Log.i("ASDF", "updateNetwork: " + updateNetwork);

        /// If network not already in configured networks add new network
        if (!isConnected(ssid) || updateNetwork == -1) {
            Log.i("ASDF", "addNetwork: " + conf.SSID);
            updateNetwork = moWiFi.addNetwork(conf);
            moWiFi.saveConfiguration();
        }
        Log.i("ASDF", "addedNetwork: " + updateNetwork);

        if (joinOnce != null && joinOnce) {
            ssidsToBeRemovedOnExit.add(conf.SSID);
        }

        if (!isConnected(ssid) || updateNetwork != -1) {
            moWiFi.disconnect();
            final boolean enabled = moWiFi.enableNetwork(updateNetwork, true);
            Log.i("ASDF", "enabled: " + enabled);
            //moWiFi.reconnect();
        }

        Log.i("ASDF", "" + Thread.currentThread().getName());

        boolean connected = false;
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                break;
            }
            String connectedSsid = getSsid();
            Log.i("ASDF", "connectedSsid: " + connectedSsid);
            Log.i("ASDF", "connect to ssid: " + ssid);
            connected = isConnected(ssid);
            if (connected) {
                break;
            }
        }

        Log.i("ASDF", "connected: " + connected);

        return connected;
    }

    private boolean isConnected(String ssid) {
        return ssid != null && ssid.equals(getSsid());
    }

    private String getSsid() {
        final WifiInfo info = moWiFi.getConnectionInfo();
        return unquote(info.getSSID());
    }

    public static String sudoForResult(String... strings) {
        String res = "";
        DataOutputStream outputStream = null;
        InputStream response = null;
        try {
            Process su = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(su.getOutputStream());
            response = su.getInputStream();

            for (String s : strings) {
                outputStream.writeBytes(s + "\n");
                outputStream.flush();
            }

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            res = readFully(response);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Closer.closeSilently(outputStream, response);
        }
        return res;
    }

    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = is.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos.toString("UTF-8");
    }

    static class Closer {
        /// closeAll()
        public static void closeSilently(Object... xs) {
            /// Note: on Android API levels prior to 19 Socket does not implement Closeable
            for (Object x : xs) {
                if (x != null) {
                    try {
                        if (x instanceof Closeable) {
                            ((Closeable) x).close();
                        } else if (x instanceof Socket) {
                            ((Socket) x).close();
                        } else if (x instanceof DatagramSocket) {
                            ((DatagramSocket) x).close();
                        } else {
                            throw new RuntimeException("cannot close " + x);
                        }
                    } catch (Throwable e) {
                        if (e != null) {
                            Log.w(Closer.class.toString(), "" + e.getMessage());
                        }
                        // TODO : do something ?
                    }
                }
            }
        }
    }
}

