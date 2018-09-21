package io.whisper.browser;

import io.whisper.vanilla.*;
import io.whisper.vanilla.exceptions.WhisperException;
import io.whisper.vanilla.session.IceTransportOptions;
import io.whisper.vanilla.session.Manager;
import io.whisper.vanilla.session.TcpTransportOptions;
import io.whisper.vanilla.session.TransportOptions;
import io.whisper.vanilla.session.UdpTransportOptions;

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

public class PfdAgent extends AbstractWhisperHandler {
	private static String TAG = "PfdAgent";

	public static final String ACTION_SERVER_LIST_CHANGED  = "ACTION_SERVER_LIST_CHANGED";
	public static final String ACTION_SERVER_INFO_CHANGED  = "ACTION_SERVER_INFO_CHANGED";
	public static final String ACTION_AGENT_STATUS_CHANGED = "ACTION_AGENT_STATUS_CHANGED";

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
		String deviceId;

		try {
			deviceId = ((TelephonyManager) mContext.getSystemService(mContext.TELEPHONY_SERVICE)).getDeviceId();
		} catch (SecurityException e) {
			e.printStackTrace();
			return;
		}
		String whisperPath = mContext.getFilesDir().getAbsolutePath() + "/whisper";
		File whisperDir = new File(whisperPath);
		if (!whisperDir.exists()) {
			whisperDir.mkdirs();
		}

		loadCertFile();

		Whisper.Options wopt = new Whisper.Options();
		wopt.setAppId(Constant.AppId)
			.setAppKey(Constant.AppKey)
			.setLogin(login)
			.setPassword(password)
			.setApiServerUrl(Constant.ApiServerUrl)
			.setMqttServerUri(Constant.MqttServerUri)
			.setTrustStore(getTruestStore())
			.setPersistentLocation(whisperPath)
			.setDeviceId(deviceId)
			.setConnectTimeout(5)
			.setRetryInterval(1);

		mWhisper = Whisper.getInstance(wopt, this);
		Log.i(TAG, "Agent whisper instance created successfully");

		mSessionManager = Manager.getInstance(mWhisper);

		IceTransportOptions iceOptions = new IceTransportOptions();
		iceOptions.setStunHost(Constant.StunHost)
			.setTurnHost(Constant.StunHost)
			.setTurnUserName(Constant.TurnUsername)
			.setTurnPassword(Constant.TurnPassword)
			.setThreadModel(TransportOptions.SHARED_THREAD);

		mSessionManager.addTransport(iceOptions);

		UdpTransportOptions udpOptions = new UdpTransportOptions();
		udpOptions.setUdpHost("127.0.0.1")
			.setThreadModel(TransportOptions.SHARED_THREAD);
		mSessionManager.addTransport(udpOptions);

		TcpTransportOptions tcpOptions = new TcpTransportOptions();
		tcpOptions.setTcpHost("127.0.0.1")
			.setThreadModel(TransportOptions.SHARED_THREAD);
		mSessionManager.addTransport(tcpOptions);

		Log.i(TAG, "Agent session manager created successfully");
	}

	public boolean isReady() {
		return mReady;
	}

	public void start() {
		try {
			if (mWhisper == null) {
				checkLogin();
			}

			mWhisper.start(50);
		} catch (WhisperException e) {
			Log.i(TAG, String.format("checkLogin error (0x%x)", e.getErrorCode()));
			//TODO;
			notifyAgentStatus(-1);
		}
	}

	public void logout() {
		String whisperPath = mContext.getFilesDir().getAbsolutePath() + "/whisper";
		File whisperDir = new File(whisperPath);
		if (whisperDir.exists()) {
			File[] files = whisperDir.listFiles();
			for (File file : files) {
				file.delete();
			}
		}

		this.kill();
	}

	public void kill() {
		savePreferences();

		for (PfdServer server: mServerList) {
			server.close();
		}

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

	private void loadPreferences() {
		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		String serverId = preferences.getString("checkedServerId", null);
		if (serverId != null) {
			PfdServer server = mServerMap.get(serverId);
			if (server != null)
				mCheckedServer = server;
		}
	}

	private void savePreferences() {
		if (mCheckedServer != null) {
			SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = preferences.edit();

			editor.putString("checkedServerId", mCheckedServer.getServerId());
			editor.commit();
		}
	}

	private void updatePreference() {
		SharedPreferences preferences = mContext.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = preferences.edit();


		if (mCheckedServer == null)
			edit.remove("checkedServerId");
		else
			edit.putString("checkedServerId", mCheckedServer.getServerId());

		edit.commit();
	}

	public void setCheckedServer(String serverId) {
		PfdServer server = mServerMap.get(serverId);

		if (server != null) {
			Log.i(TAG, "Checked server changed to " + serverId);
			mCheckedServer = server;
			savePreferences();

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

	public void pairServer(String serverAddr) throws WhisperException {
		mWhisper.addFriend(serverAddr, "WMPFD/2.0/Android");
		Log.i(TAG, "Friend request to portforwarding server " + serverAddr + " success");
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
			notifyAgentStatus(-1);
			return;
		}

		Log.i(TAG, "Whisper instance is ready.");

		loadPreferences();

		if (mCheckedServer == null) {
			for (PfdServer server: mServerList) {
				if (server.isOnline()) {
					mCheckedServer = server;
					savePreferences();
					break;
				}
			}
		}

		mReady = true;
		notifyAgentStatus(AGENT_READY);
	}

	@Override
	public void onFriends(Whisper whisper, List<FriendInfo> friends) {
		Log.i(TAG, "Client portforwarding agent received friend list ");

		for (FriendInfo info: friends) {
			String serverId = info.getUserId();
			PfdServer server;

			server = mServerMap.get(serverId);
			if (server == null) {
				server = new PfdServer();
				mServerList.add(server);
				mServerMap.put(serverId, server);
			}

			server.setInfo(info);
			server.setOnline(info.getConnectionStatus() == ConnectionStatus.Connected);
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
	public void onFriendConnection(Whisper whisper, String friendId, ConnectionStatus status) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server" + friendId + "status changed to " + status);

		boolean online = (status == ConnectionStatus.Connected);
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
			savePreferences();
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

		server.clearPreferences();

		if (mCheckedServer.equals(server)) {
			mCheckedServer = null;
			for (PfdServer svr: mServerList) {
				if (svr.isOnline())
					mCheckedServer = svr;
			}

			updatePreference();

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

	public void notifyAgentStatus(int status) {
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
