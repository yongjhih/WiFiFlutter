import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';

enum WIFI_AP_STATE { WIFI_AP_STATE_DISABLING, WIFI_AP_STATE_DISABLED, WIFI_AP_STATE_ENABLING, WIFI_AP_STATE_ENABLED, WIFI_AP_STATE_FAILED }

const MethodChannel _channel = const MethodChannel('wifi_iot');
const EventChannel _eventChannel = const EventChannel('plugins.wififlutter.io/wifi_scan');

class WiFiForIoTPlugin {

  static Future<bool> isWiFiAPEnabled() async {
    Map<String, String> htArguments = new Map();
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('isWiFiAPEnabled', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static void setWiFiAPEnabled(bool state) async {
    Map<String, bool> htArguments = new Map();
    htArguments["state"] = state;
    try {
      await _channel.invokeMethod('setWiFiAPEnabled', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<bool> isWiFiAPSSIDHidden() async {
    Map<String, String> htArguments = new Map();
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('isSSIDHidden', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static setWiFiAPSSIDHidden(bool hidden) async {
    Map<String, bool> htArguments = new Map();
    htArguments["hidden"] = hidden;
    try {
      await _channel.invokeMethod('setSSIDHidden', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<int> getWiFiAPState() async {
    Map<String, String> htArguments = new Map();
    int iResult;
    try {
      iResult = await _channel.invokeMethod('getWiFiAPState', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return iResult;
  }

  static Future<List<APClient>> getClientList(bool onlyReachables, int reachableTimeout) async {
    Map<String, Object> htArguments = new Map();
    htArguments["onlyReachables"] = onlyReachables;
    htArguments["reachableTimeout"] = reachableTimeout;
    String sResult;
    List<APClient> htResult = new List();
    try {
      sResult = await _channel.invokeMethod('getClientList', htArguments);
      htResult = APClient.parse(sResult);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return htResult;
  }

  static void setWiFiAPConfiguration(Object poWiFiConfig) async {
    Map<String, bool> htArguments = new Map();
    htArguments["wifi_config"] = poWiFiConfig;
    await _channel.invokeMethod('setWiFiAPConfiguration', htArguments);
  }

  static Future<String> getWiFiAPSSID() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    try {
      sResult = await _channel.invokeMethod('getWiFiAPSSID', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return sResult;
  }

  static setWiFiAPSSID(String psSSID) async {
    Map<String, String> htArguments = new Map();
    htArguments["ssid"] = psSSID;
    try {
      await _channel.invokeMethod('setWiFiAPSSID', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<String> getWiFiAPPreSharedKey() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    try {
      sResult = await _channel.invokeMethod('getWiFiAPPreSharedKey', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return sResult;
  }

  static setWiFiAPPreSharedKey(String psPreSharedKey) async {
    Map<String, String> htArguments = new Map();
    htArguments["preSharedKey"] = psPreSharedKey;
    try {
      await _channel.invokeMethod('setWiFiAPPreSharedKey', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Stream<List<WifiNetwork>> _onWifiScanResultReady;

  static Stream<List<WifiNetwork>> get onWifiScanResultReady{
    if(_onWifiScanResultReady == null){
      _onWifiScanResultReady = _eventChannel
          .receiveBroadcastStream()
          .map((dynamic event) => WifiNetwork.parse(event));
    }
    return _onWifiScanResultReady;
  }

  static Future<List<WifiNetwork>> _loadWifiList() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    List<WifiNetwork> htResult = new List();
    try {
      sResult = await _channel.invokeMethod('loadWifiList', htArguments);
      htResult = WifiNetwork.parse(sResult);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return htResult;
  }

  static Future<List<WifiNetwork>> loadWifiList() async {
    List<WifiNetwork> result = (await _loadWifiList() ?? new List<WifiNetwork>());
    if(result.length >= 1) return result;

    result.clear();
    return await WiFiForIoTPlugin.onWifiScanResultReady.first;
  }


  static void forceWifiUsage(bool useWifi) async {
    Map<String, bool> htArguments = new Map();
    htArguments["useWifi"] = useWifi;
    try {
      await _channel.invokeMethod('forceWifiUsage', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<bool> isEnabled() async {
    Map<String, String> htArguments = new Map();
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('isEnabled', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static setEnabled(bool state) async {
    Map<String, bool> htArguments = new Map();
    htArguments["state"] = state;
    try {
      await _channel.invokeMethod('setEnabled', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<bool> connect(String ssid, {String password, NetworkSecurity security = NetworkSecurity.NONE, bool joinOnce = true}) async {
    if (!await isEnabled()) await setEnabled(true);
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('connect', <String, Object>{
        "ssid": ssid,
        "password": password,
        "join_once": joinOnce,
        "security": security?.toString()?.substring('$NetworkSecurity'.length + 1),
      }..removeWhere((k, v) => v == null));
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static Future<bool> findAndConnect(String ssid, {String password, bool joinOnce = true}) async {
    if (!await isEnabled()) {
      await setEnabled(true);
    }
//    Map<String, Object> htArguments = new Map();
//    htArguments["ssid"] = ssid;
//    htArguments["password"] = password;
//    if (joinOnce != null && isWep != null) {
//      htArguments["join_once"] = joinOnce;
//      htArguments["is_wep"] = isWep;
//    }
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('findAndConnect', <String, Object>{
        "ssid": ssid,
        "password": password,
        "join_once": joinOnce,
      }..removeWhere((k, v) => v == null));
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static Future<bool> isConnected() async {
    Map<String, String> htArguments = new Map();
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('isConnected', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static disconnect() async {
    Map<String, bool> htArguments = new Map();
    try {
      await _channel.invokeMethod('disconnect', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
  }

  static Future<String> getSSID() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    try {
      sResult = await _channel.invokeMethod('getSSID', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return sResult;
  }

  static Future<String> getBSSID() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    try {
      sResult = await _channel.invokeMethod('getBSSID', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return sResult;
  }

  static Future<int> getCurrentSignalStrength() async {
    Map<String, String> htArguments = new Map();
    int iResult;
    try {
      iResult = await _channel.invokeMethod('getCurrentSignalStrength', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return iResult;
  }

  static Future<int> getFrequency() async {
    Map<String, String> htArguments = new Map();
    int iResult;
    try {
      iResult = await _channel.invokeMethod('getFrequency', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return iResult;
  }

  static Future<String> getIP() async {
    Map<String, String> htArguments = new Map();
    String sResult;
    try {
      sResult = await _channel.invokeMethod('getIP', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return sResult;
  }

  static Future<bool> removeWifiNetwork(String ssid) async {
    Map<String, String> htArguments = new Map();
    htArguments["ssid"] = ssid;
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('removeWifiNetwork', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }

  static Future<bool> isRegisteredWifiNetwork(String ssid) async {
    Map<String, String> htArguments = new Map();
    htArguments["ssid"] = ssid;
    bool bResult;
    try {
      bResult = await _channel.invokeMethod('isRegisteredWifiNetwork', htArguments);
    } on MissingPluginException catch (e) {
      print("MissingPluginException : ${e.toString()}");
    }
    return (bResult != null && bResult);
  }
}

class APClient {
  String ipAddr;
  String hwAddr;
  String device;
  bool isReachable;

  APClient.fromJson(Map<String, dynamic> json)
      : ipAddr = json['IPAddr'],
        hwAddr = json['HWAddr'],
        device = json['Device'],
        isReachable = json['isReachable'];

  Map<String, dynamic> toJson() => {
    'IPAddr': ipAddr,
    'HWAddr': hwAddr,
    'Device': device,
    'isReachable': isReachable,
  };

  static List<APClient> parse(String psString) {
    /// []

    List<APClient> htList = new List();

    List<dynamic> htMapClients = json.decode(psString);
//    List<Map<String, dynamic>> htMapClients = json.decode(psString);

    htMapClients.forEach((htMapClient) {
//    htMapClients.forEach((Map<String, dynamic> htMapClient) {
      htList.add(new APClient.fromJson(htMapClient));
    });

    return htList;
  }
}

class WifiNetwork {
  /// NOTE Parsing JSON :
  /// http://cogitas.net/parse-json-dart-flutter/

  String ssid;
  String bssid;
  String capabilities;
  int frequency;
  int level;
  int timestamp;
  String password;

  WifiNetwork.fromJson(Map<String, dynamic> json)
      : ssid = json['SSID'],
        bssid = json['BSSID'],
        capabilities = json['capabilities'],
        frequency = json['frequency'],
        level = json['level'],
        timestamp = json['timestamp'];

  Map<String, dynamic> toJson() => {
    'SSID': ssid,
    'BSSID': bssid,
    'capabilities': capabilities,
    'frequency': frequency,
    'level': level,
    'timestamp': timestamp,
  };

  static List<WifiNetwork> parse(String psString) {
    /// [{"SSID":"Florian","BSSID":"30:7e:cb:8c:48:e4","capabilities":"[WPA-PSK-CCMP+TKIP][ESS]","frequency":2462,"level":-64,"timestamp":201307720907},{"SSID":"Pi3-AP","BSSID":"b8:27:eb:b1:fa:e1","capabilities":"[WPA2-PSK-CCMP][ESS]","frequency":2437,"level":-66,"timestamp":201307720892},{"SSID":"AlternaDom-SonOff","BSSID":"b8:27:eb:98:b4:81","capabilities":"[WPA2-PSK-CCMP][ESS]","frequency":2437,"level":-86,"timestamp":201307720897},{"SSID":"SFR_1CF0_2GEXT","BSSID":"9c:3d:cf:58:98:07","capabilities":"[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][WPS][ESS]","frequency":2412,"level":-87,"timestamp":201307720887},{"SSID":"Freebox-5CC952","BSSID":"f4:ca:e5:96:71:c4","capabilities":"[WPA-PSK-CCMP][ESS]","frequency":2442,"level":-90,"timestamp":201307720902}]

    List<WifiNetwork> htList = new List();

    try{
      List<dynamic> htMapNetworks = json.decode(psString);
//    List<Map<String, dynamic>> htMapNetworks = json.decode(psString);

      htMapNetworks.forEach((htMapNetwork) {
//    htMapNetworks.forEach((Map<String, dynamic> htMapNetwork) {
        htList.add(new WifiNetwork.fromJson(htMapNetwork));
      });

    } on FormatException catch (e) {
      print("FormatException : ${e.toString()}");
      print("psString = '$psString'");
    }
    return htList;
  }
}

enum NetworkSecurity {
  WPA, WEP, NONE
}
