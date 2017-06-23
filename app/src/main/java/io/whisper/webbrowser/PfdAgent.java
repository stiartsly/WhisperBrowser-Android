package io.whisper.webbrowser;

import io.whisper.core.*;
import io.whisper.exceptions.WhisperException;
import io.whisper.session.Manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.Intent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.InputStream;
import java.util.prefs.Preferences;

public class PfdAgent extends AbstractWhisperHandler {
	private static String TAG = "PfdAgent";

	public static final String ACTION_SERVER_LIST_CHANGED  = "ACTION_SERVER_LIST_CHANGED";
	public static final String ACTION_SERVER_INFO_CHANGED  = "ACTION_SERVER_INFO_CHANGED";
	public static final String ACTION_AGENT_STATUS_CHANGED = "ACTION_AGENT_STATUS_CHANGED";

	private static final String mAppId  = "7sRQjDsniyuHdZ9zsQU9DZbMLtQGLBWZ78yHWgjPpTKm";
	private static final String mAppKey = "6tzPPAgSACJdScX79wuzMNPQTWkRLZ4qEdhLcZU6q4B9";
	private static final String mApiServerUrl  = "https://whisper.freeddns.org:8443/web/api";
	private static final String mMqttServerUri = "ssl://whisper.freeddns.org:8883";

	public static PfdAgent pfdAgentInst;

	private Context mContext;
	private Whisper mWhisper;
	private Manager mSessionManager;
	private ConnectionStatus mStatus;
	private boolean mReady;

	private PfdServer mCheckedServer;
	private List<PfdServer> mServerList;
	private Map<String, PfdServer> mServerMap;

	public static int AGENT_READY = 0;


	public static PfdAgent singleton() {
		if (pfdAgentInst == null) {
			pfdAgentInst = new PfdAgent();
		}
		return pfdAgentInst;
	}

	private PfdAgent() {
		mContext = WebBrowserApp.getAppContext();
		mStatus = ConnectionStatus.Disconnected;
		mReady  = false;

		mServerList = new ArrayList<PfdServer>();
		mServerMap  = new HashMap();
	}

	public boolean loadCertFile() {
		String appPath = mContext.getFilesDir().getAbsolutePath();
		File  certFile = new File(appPath, "whisper.pem");
		if (!certFile.exists()) {
			try {
				InputStream is = WebBrowserApp.getAppContext().getAssets().open("whisper.pem");
				FileOutputStream fos = new FileOutputStream(certFile);

				byte[] buffer = new byte[1024*1024];
				int count;

				while ((count = is.read(buffer)) > 0) {
					fos.write(buffer, 0, count);
				}
				fos.close();
				is.close();
			} catch (IOException e) {
				Log.e(TAG, "Generate whisper certification file error: " + e.getMessage());
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	private String getTruestStore() {
		String appPath = mContext.getFilesDir().getAbsolutePath();
		File certFile = new File(appPath, "whisper.pem");
		return certFile.getAbsolutePath();
	}

	public void checkLogin() throws WhisperException {
		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		boolean hasLogin = preferences.getBoolean("hasLogin", false);

		if (hasLogin) {
			String username = preferences.getString("login", null);
			String password = preferences.getString("password", null);

			checkLogin(username, password);
		}
	}

	public void checkLogin(String login, String password) throws WhisperException {
		String  appPath = mContext.getFilesDir().getAbsolutePath();
		String deviceId = ((TelephonyManager)mContext.getSystemService(mContext.TELEPHONY_SERVICE)).getDeviceId();

		loadCertFile();

		Whisper.Options wopt = new Whisper.Options();
		wopt.setAppId(mAppId)
			.setAppKey(mAppKey)
			.setLogin(login)
			.setPassword(password)
			.setApiServerUrl(mApiServerUrl)
			.setMqttServerUri(mMqttServerUri)
			.setTrustStore(getTruestStore())
			.setPersistentLocation(appPath)
			.setDeviceId(deviceId)
			.setConnectTimeout(5)
			.setRetryInterval(1);

		mWhisper = Whisper.getInstance(wopt, this);
		Log.i(TAG, "Agent whisper instance created successfully");

		Manager.Options sopt = new Manager.Options();
		sopt.setTransports(Manager.Options.TRANSPORT_TCP);

		mSessionManager = Manager.getInstance(mWhisper, sopt);
		Log.i(TAG, "Agent session manager created successfully");
	}

	public void start() {
		try {
			if (mWhisper == null) {
				checkLogin();
				mWhisper.start(50);
			} else {
				Log.i(TAG, "Agent whisper instance already created");
				notifyAgentStatus(AGENT_READY);
			}
		} catch (WhisperException e) {
			Log.i(TAG, String.format("checkLogin error (0x%x)", e.getErrorCode()));
			//TODO;
			notifyAgentStatus(-1);
		}
	}

	public void kill() {

		if (mCheckedServer != null)
			storeSelectedServer();

		mServerMap.clear();
		mServerList.clear();

		if (mWhisper != null) {
			mSessionManager.cleanup();
			mWhisper.kill();
		}
		pfdAgentInst = null;
	}

	public Manager getSessionManager() {
		return mSessionManager;
	}

	private void storeSelectedServer() {
		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("checkedServerId", mCheckedServer.getServerId());
		editor.commit();
	}

	private void clearCheckedServer() {
		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = preferences.edit();
		edit.remove("checkedServerId");
		edit.commit();
	}

	public void setCheckedServer(String serverId) {
		PfdServer server = mServerMap.get(serverId);

		if (server != null) {
			Log.i(TAG, "Checked server changed to " + serverId);
			mCheckedServer = server;
			storeSelectedServer();

			if (mStatus == ConnectionStatus.Connected)
				notifyAgentStatus(AGENT_READY);
		}
	}

	public PfdServer getCheckedServer() {
		return mCheckedServer;
	}

	public List<PfdServer> getServerList() {
		return mServerList;
	}

	public PfdServer getServer(String serverId) {
		return mServerMap.get(serverId);
	}

	public void pairServer(String serverId) throws WhisperException {
		if (!mWhisper.isFriend(serverId)) {
			mWhisper.friendRequest(serverId, "WMPFD/2.0/Android");
			Log.i(TAG, "Friend request to portforwarding server " + serverId + " success");
		}
	}

	public void unpairServer(String serverId) throws WhisperException {
		if (mWhisper.isFriend(serverId)) {
			mWhisper.removeFriend(serverId);
			Log.i(TAG, "Removed " + serverId + " friend");
		}
	}

	public UserInfo getInfo() throws WhisperException {
		return mWhisper.getSelfInfo();
	}

	@Override
	public void onConnection(Whisper whisper, ConnectionStatus status) {
		Log.i(TAG, "Agent connection status changed to " + status);

		mStatus = status;

		if (mReady && status == ConnectionStatus.Connected)
			notifyAgentStatus(AGENT_READY);
	}

	@Override
	public void onReady(Whisper whisper) {
		try {
			UserInfo info;
			info = whisper.getSelfInfo();

			if (info.getName().isEmpty()) {
				String manufacturer = Build.MANUFACTURER;
				String name = Build.MODEL;

				if (!name.startsWith(manufacturer))
					name = manufacturer + " " + name;
				if (name.length() > UserInfo.MAX_USER_NAME_LEN)
					name = name.substring(0, UserInfo.MAX_USER_NAME_LEN);

				info.setName(name);

				whisper.setSelfInfo(info);
			}
		} catch (WhisperException e) {
			Log.e(TAG, String.format("Update current user name error (0x%x)", e.getErrorCode()));
			e.printStackTrace();
		}

		Log.i(TAG, "Whisper instance is ready.");

		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		String serverId = preferences.getString("checkedServerId", null);
		if (serverId != null) {
			PfdServer server = mServerMap.get(serverId);
			if (server != null)
				mCheckedServer = server;
		}

		if (mCheckedServer == null) {
			mCheckedServer = mServerList.get(0);
			storeSelectedServer();
		}

		mReady = true;
		notifyAgentStatus(AGENT_READY);
	}

	@Override
	public void onFriends(Whisper whisper, List<FriendInfo> friends) {
		Log.i(TAG, "Client portforwarding agent received friend list ");

		for (FriendInfo info: friends) {
			String serverId = info.getUserId();
			boolean online  = false;
			boolean needAdd = false;
			PfdServer server;

			server = mServerMap.get(serverId);
			if (server == null) {
				server = new PfdServer();
				mServerList.add(server);
				mServerMap.put(serverId, server);
			}

			server.setInfo(info);
			server.setOnline(info.getPresence().equals("online"));
		}

		notifyServerChanged();
	}

	@Override
	public void onFriendInfoChanged(Whisper whisper, String friendId, FriendInfo friendInfo) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server " + friendId + "info changed to " + friendInfo);

		server.setInfo(friendInfo);
		notifyServerInfoChanged(friendInfo.getUserId());
	}

	@Override
	public void onFriendPresence(Whisper whsiper, String friendId, String presence) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server" + friendId + "presence changed to " + presence);

		boolean online = presence.equals("online");
		server.setOnline(online);

		if (server.equals(mCheckedServer))
			notifyAgentStatus(AGENT_READY);

		notifyServerChanged();
	}

	@Override
	public void onFriendAdded(Whisper whisper, FriendInfo friendInfo) {
		PfdServer server = new PfdServer();
		server.setInfo(friendInfo);
		server.setOnline(friendInfo.getPresence().equals("online"));

		mServerList.add(server);
		mServerMap.put(server.getServerId(), server);

		Log.i(TAG, "Server " + server.getServerId() + "added.");

		if (mCheckedServer == null) {
			mCheckedServer = server;
			storeSelectedServer();
			notifyAgentStatus(AGENT_READY);
		}

		notifyServerChanged();
	}

	@Override
	public void onFriendRemoved(Whisper whisper, String friendId) {
		PfdServer server = mServerMap.remove(friendId);
		assert(server != null);

		mServerList.remove(server);
		Log.i(TAG, "Portforwarding server " + friendId + "removed");

		if (mCheckedServer.equals(server)) {
			mCheckedServer = null;
			clearCheckedServer();

			notifyAgentStatus(AGENT_READY);
		}

		notifyServerChanged();
	}

	@Override
	public void onFriendResponse(Whisper whisper, String userId, int status, String reason,
								 boolean entrusted, String expire) {
		if (status == 0) {
			Log.i(TAG, "Friend request with server " + userId + " is confirmed");
		} else {
			Log.i(TAG, "Friend request with server " + userId + " is refused with reason " + reason);
		}
	}

	private void notifyAgentStatus(int status) {
		notifyAgentStatus(status, false);
	}

	private void notifyAgentStatus(int status, boolean onReady) {
		Intent intent = new Intent(ACTION_AGENT_STATUS_CHANGED);
		intent.putExtra("status", status);
		intent.putExtra("onReady", onReady);
		WebBrowserApp.getAppContext().sendBroadcast(intent);
	}

	private void notifyServerChanged() {
		Intent intent = new Intent(ACTION_SERVER_LIST_CHANGED);
		WebBrowserApp.getAppContext().sendBroadcast(intent);
	}

	private void notifyServerInfoChanged(String userName) {
		Intent intent = new Intent(ACTION_SERVER_INFO_CHANGED);
		WebBrowserApp.getAppContext().sendBroadcast(intent);
	}
}
