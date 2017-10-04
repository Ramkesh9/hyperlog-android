package com.hypertrack.devicelogger.db;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.hypertrack.devicelogger.BuildConfig;
import com.hypertrack.devicelogger.db.Utils.DateTimeUtility;
import com.hypertrack.devicelogger.db.Utils.Utils;
import com.hypertrack.devicelogger.db.Utils.VolleyUtils;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Aman on 04/02/16.
 */
public class SmartLog {

    private static final String TAG = "SmartLog";
    private static int logLevel = Log.WARN;
    private static DeviceLogList mDeviceLogList;
    private static String URL;
    private static String deviceUUID;
    private static final int EXPIRY_TIME = 7 * 24 * 60 * 60;// 7 Days

    /**
     * Call this method to initialize SmartLog.
     * By default, seven days older logs will gets deleted automatically.
     *
     * @param context The current context.
     * @see #initialize(Context, int)
     */
    public static void initialize(@NonNull Context context) {
        initialize(context, EXPIRY_TIME);
    }

    /**
     * Call this method to initialize SmartLog.
     * By default, seven days older logs will get expire automatically. You can change the expiry period of logs by defining expiryTimeInSeconds.
     *
     * @param context             The current context.
     * @param expiryTimeInSeconds Expiry time for logs in seconds.
     * @see #initialize(Context)
     */
    public static void initialize(@NonNull Context context, int expiryTimeInSeconds) {
        if (context == null)
            throw new IllegalArgumentException("Context cannot be null.");

        Context mContext = context.getApplicationContext();
        deviceUUID = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (mDeviceLogList == null) {
            synchronized (SmartLog.class) {
                if (mDeviceLogList == null) {
                    DeviceLogDataSource logDataSource = DeviceLogDatabaseHelper.getInstance(context);
                    mDeviceLogList = new DeviceLogList(logDataSource);
                    mDeviceLogList.clearOldLogs(EXPIRY_TIME);
                }
            }
        }
    }

    private static boolean isInitialize() {
        if (mDeviceLogList == null) {
            SmartLog.e(TAG, "Smart Log isn't initialized.");
            return false;
        }

        return true;
    }

    /**
     * Call this method to set a valid end point URL where logs need to be pushed.
     *
     * @param url URL of the endpoint
     */
    public static void setURL(String url) {
        if (TextUtils.isEmpty(url))
            throw new IllegalArgumentException("API URL cannot be null or empty");
        URL = url;
    }

    /**
     * Sets the level of logging to display, where each level includes all those below it. The default
     * level is LOG_LEVEL_NONE. Please ensure this is set to Log#ERROR
     * or LOG_LEVEL_NONE before deploying your app to ensure no sensitive information is
     * logged. The levels are:
     * <ul>
     * <li>{@link Log#ASSERT}</li>
     * <li>{@link Log#VERBOSE}</li>
     * <li>{@link Log#DEBUG}</li>
     * <li>{@link Log#INFO}</li>
     * <li>{@link Log#WARN}</li>
     * <li>{@link Log#ERROR}</li>
     * </ul>
     *
     * @param logLevel The level of logcat logging that Parse should do.
     */
    public static void setLogLevel(int logLevel) {
        SmartLog.logLevel = logLevel;
    }

    public static void v(String tag, String message, Throwable tr) {
        if (Log.VERBOSE >= logLevel) {
            Log.v(tag, message + '\n' + Log.getStackTraceString(tr));
        }
    }

    public static void v(String tag, String message) {
        if (Log.VERBOSE >= logLevel) {
            Log.v(tag, message);
        }
    }

    public static void d(String tag, String message, Throwable tr) {
        if (Log.DEBUG >= logLevel) {
            Log.d(tag, message + '\n' + Log.getStackTraceString(tr));
        }
    }

    public static void d(String tag, String message) {
        if (Log.DEBUG >= logLevel) {
            Log.d(tag, message);
        }
    }

    public static void i(String tag, String message, Throwable tr) {
        if (Log.INFO >= logLevel) {
            Log.i(tag, message + '\n' + Log.getStackTraceString(tr));
        }

        r(getFormattedMessage(Log.INFO, message));
    }

    /**
     * Info log level that will store into DB.
     */
    public static void i(String tag, String message) {
        i(tag, message, null);
    }

    public static void w(String tag, String message, Throwable tr) {
        if (Log.WARN >= logLevel) {
            Log.w(tag, message + '\n' + Log.getStackTraceString(tr));
        }

        r(getFormattedMessage(Log.WARN, message));
    }

    public static void w(String tag, String message) {
        w(tag, message, null);
    }

    public static void e(String tag, String message, Throwable tr) {
        if (Log.ERROR >= logLevel) {
            Log.e(tag, message + '\n' + Log.getStackTraceString(tr));
        }

        r(getFormattedMessage(Log.ERROR, message));
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }

    public static void exception(String tag, String message, Throwable tr) {
        if (Log.ERROR >= logLevel) {
            Log.e(tag, "**********************************************");
            Log.e(tag, "EXCEPTION: " + getMethodName() + ", " + message + '\n' + Log.getStackTraceString(tr));
            Log.e(tag, "**********************************************");
        }

        r(getFormattedMessage(Log.ERROR, "EXCEPTION: " + getMethodName() + ", " + message));
    }

    public static void exception(String tag, String message) {
        exception(tag, message, null);
    }

    public static void exception(String tag, Exception e) {
        if (e == null)
            return;

        exception(tag, e.getMessage(), null);
    }

    public static void a(String message) {
        r(getFormattedMessage(Log.ASSERT, message));
    }

    private static String getMethodName() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        return e.getMethodName();
    }

    private static String getFormattedMessage(int logLevel, String message) {
        return getLogPrefix() + getLogLevelName(logLevel) + message;
    }

    private static String getLogPrefix() {
        String timeStamp = DateTimeUtility.getCurrentTime();
        String senderName = BuildConfig.VERSION_NAME;
        String osVersion = "Android-" + Build.VERSION.RELEASE;

        if (deviceUUID == null) {
            deviceUUID = "DeviceUUID";
        }

        return timeStamp + " " + senderName + " : " + osVersion + " | " + deviceUUID + " | " + "SmartLog " + " | ";
    }

    private static void r(final String message) {

        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!isInitialize())
                            return;

                        mDeviceLogList.addDeviceLog(message);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();
        } catch (OutOfMemoryError | Exception e) {
            e.printStackTrace();
        }
    }

    private static String getLogLevelName(int messageLogLevel) {

        String logLevelName;
        switch (messageLogLevel) {
            case Log.VERBOSE:
                logLevelName = "VERBOSE";
                break;
            case Log.DEBUG:
                logLevelName = "DEBUG";
                break;
            case Log.INFO:
                logLevelName = "INFO";
                break;
            case Log.WARN:
                logLevelName = "WARN";
                break;
            case Log.ERROR:
                logLevelName = "ERROR";
                break;
            case Log.ASSERT:
                logLevelName = "ASSERT";
                break;
            default:
                logLevelName = "NONE";
        }

        return "[" + logLevelName + "]" + ": ";
    }

    /**
     * Call this method to get a list of stored Device Logs
     *
     * @return List of {@link DeviceLog}
     */
    public static List<DeviceLog> getDeviceLogs() {
        return getDeviceLogs(true);
    }

    /**
     * Call this method to get a list of stored Device Logs
     *
     * @param deleteLogs If true then logs will delete from the device.
     * @return List of {@link DeviceLog}
     */
    public static List<DeviceLog> getDeviceLogs(boolean deleteLogs) {
        return getDeviceLogs(deleteLogs, 1);
    }

    /**
     * Call this method to get a list of stored Device Logs.
     *
     * @param deleteLogs If true then logs will delete from the device.
     * @param batchNo    If there are more than one batch of device log then specify the batch number.
     *                   Batch number should be greater than or equal to 1.
     * @return List of {@link DeviceLog} or empty list if batch number is greater than the {@link SmartLog#getDeviceLogBatchCount()}
     */
    public static List<DeviceLog> getDeviceLogs(boolean deleteLogs, int batchNo) {
        if (!isInitialize())
            return null;

        List<DeviceLog> deviceLogs = mDeviceLogList.getDeviceLogs(batchNo);
        if (deleteLogs) {
            mDeviceLogList.clearDeviceLogs(deviceLogs);
        }

        return deviceLogs;
    }

    /**
     * Call this method to get a list of stored Device Logs.
     * Device logs will gets deleted from device after fetching.
     *
     * @return List of {@link String}
     */
    public static List<String> getDeviceLogsAsStringList() {
        return getDeviceLogsAsStringList(true);
    }

    /**
     * Call this method to get a list of stored Device Logs
     *
     * @param deleteLogs If true then logs will delete from the device.
     * @param batchNo    If there are more than one batch of device log then specify the batch number. Batch number should be greater than or equal to 1.
     * @return List of {@link String} or If batch number is greater than the {@link SmartLog#getDeviceLogBatchCount()} then returns empty list;
     */
    public static List<String> getDeviceLogsAsStringList(boolean deleteLogs, int batchNo) {
        List<String> logsList = new ArrayList<>();
        if (!isInitialize())
            return logsList;

        if (!hasPendingDeviceLogs()) {
            return logsList;
        }

        return getDeviceLogsAsStringList(getDeviceLogs(deleteLogs, batchNo));
    }

    /**
     * Call this method to get a list of stored Device Logs
     *
     * @param deleteLogs If true then logs will delete from the device.
     * @return List of {@link String}
     */
    public static List<String> getDeviceLogsAsStringList(boolean deleteLogs) {
        return getDeviceLogsAsStringList(deleteLogs, 1);
    }

    /**
     * Method to get a list of stored Device Logs
     *
     * @param deviceLogList List of all device logs
     * @return List of {@link String}
     */
    private static List<String> getDeviceLogsAsStringList(List<DeviceLog> deviceLogList) {
        List<String> logsList = new ArrayList<>();

        if (deviceLogList == null) {
            return logsList;
        }

        for (DeviceLog deviceLog : deviceLogList) {
            logsList.add(deviceLog.getDeviceLog());
        }

        return logsList;
    }

    /**
     * Call this method to get a stored Device Logs as a File object.
     * A text file will create in the app folder containing all logs.
     *
     * @param mContext The current context.
     * @param fileName Name of the file and no need to provide the file extension
     * @return {@link File} object, or {@code null if there is not any logs in device.
     */
    public static File getDeviceLogsInFile(Context mContext, String fileName) {

        if (!isInitialize())
            return null;

        File file = null;
        List<String> stringList = getDeviceLogsAsStringList(true);

        if (TextUtils.isEmpty(fileName)) {
            fileName = DateTimeUtility.getCurrentTime() + ".txt";
        }

        if (stringList != null && !stringList.isEmpty()) {
            file = Utils.writeStringsToFile(mContext, stringList, fileName);
        }
        return file;
    }

    /**
     * Call this method to get a stored Device Logs as a File object.
     * A text file will create in the app folder containing all logs with the current date time as name of the file.
     *
     * @param mContext The current context.
     * @return {@link File} object
     */
    public static File getDeviceLogsInFile(Context mContext) {
        return getDeviceLogsInFile(mContext, null);
    }

    /**
     * Call this method to check whether any device logs are available.
     *
     * @return true If device has some pending logs otherwise false.
     */
    public static boolean hasPendingDeviceLogs() {
        if (!isInitialize())
            return false;

        long deviceLogsCount = mDeviceLogList.count();
        return deviceLogsCount > 0L;
    }

    /**
     * Call this method to get the count of stored device logs.
     *
     * @return The number of device logs.
     */
    public static long logCount() {
        if (!isInitialize())
            return 0;

        return mDeviceLogList.count();
    }

    /**
     * Call this method to get number of device logs batches. Each batch contains the 5000 device logs.
     *
     * @return The number of device logs batches.
     */
    public static int getDeviceLogBatchCount() {
        if (!isInitialize())
            return 0;

        return mDeviceLogList.getDeviceLogBatchCount();
    }

    /**
     * Call this method to push logs from device to the server.
     * <p>
     * Logs will get delete from the device once it successfully push to the server.
     * <p>
     * If device log count is greater than {@link DeviceLogTable#DEVICE_LOG_REQUEST_QUERY_LIMIT} then
     * log will push to the server in batches.
     *
     * @param mContext The current context.
     */
    public static void pushLogs(Context mContext) {
        pushLogs(mContext, null, null);
    }

    /**
     * Call this method to push logs from device to the server with custom filename.
     * <p>
     * Logs will get delete from the device once it successfully push to the server.
     * <p>
     * If device log count is greater than {@link DeviceLogTable#DEVICE_LOG_REQUEST_QUERY_LIMIT} then
     * log will push to the server in batches.
     *
     * @param fileName Name of the file that you want to receive on your server.
     * @param mContext The current context.
     */
    public static void pushLogs(Context mContext, String fileName) {
        pushLogs(mContext, fileName, null);
    }

    /**
     * Call this method to push logs from device to the server.
     * <p>
     * Logs will get delete from the device once it successfully push to the server.
     * <p>
     * If device log count is greater than {@link DeviceLogTable#DEVICE_LOG_REQUEST_QUERY_LIMIT} then
     * log will push to the server in batches.
     *
     * @param mContext The current context.
     */
    public static void pushLogs(Context mContext, HashMap<String, String> hashMap) {
        pushLogs(mContext, null, hashMap);
    }

    /**
     * Call this method to push logs from device to the server with custom filename.
     * <p>
     * Logs will get delete from the device once it successfully push to the server.
     * <p>
     * If device log count is greater than {@link DeviceLogTable#DEVICE_LOG_REQUEST_QUERY_LIMIT} then
     * log will push to the server in batches.
     *
     * @param fileName Name of the file that you want to receive on your server.
     * @param mContext The current context.
     */
    public static void pushLogs(Context mContext, String fileName, HashMap<String, String> additionalHeaders) {

        if (!isInitialize())
            return;

        if (TextUtils.isEmpty(URL)) {
            throw new RuntimeException("API endpoint URL is missing. Set URL using SmartLog.setURL method");
        }

        VolleyUtils.cancelPendingRequests(mContext, TAG);

        if (TextUtils.isEmpty(URL)) {
            SmartLog.e(TAG, "URL is missing. Please set the URL to push the logs.");
            return;
        }
        if (!hasPendingDeviceLogs())
            return;

        int logsBatchCount = getDeviceLogBatchCount();

        while (logsBatchCount != 0) {

            final List<DeviceLog> deviceLogs = getDeviceLogs(false);

            byte[] bytes = Utils.getByteData(getDeviceLogsAsStringList(deviceLogs));

            if (TextUtils.isEmpty(fileName)) {
                fileName = DateTimeUtility.getCurrentTime() + ".txt";
            }

            HTTPMultiPartPostRequest httpMultiPartPostRequest = new HTTPMultiPartPostRequest<>(URL, bytes,
                    fileName, additionalHeaders, mContext, JSONArray.class, new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    mDeviceLogList.clearDeviceLogs(deviceLogs);
                    SmartLog.i(TAG, "Log has been pushed");
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    error.printStackTrace();
                    SmartLog.e(TAG, "Error has occured while log pushing.");
                }
            });

            VolleyUtils.addToRequestQueue(mContext, httpMultiPartPostRequest, TAG);
            logsBatchCount--;
        }
    }

    /**
     * Call this method to delete all logs from device.
     */
    public static void deleteLogs() {
        if (!isInitialize())
            return;

        mDeviceLogList.clearSavedDeviceLogs();
    }
}