package io.whisper.webbrowser;

import io.whisper.core.FriendInfo;
import io.whisper.session.*;
import io.whisper.exceptions.WhisperException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import java.net.ServerSocket;

class PfdServer extends AbstractStreamHandler implements SessionRequestCompleteHandler {
	private static String TAG = "PfServer";

	public static String ACTION_SERVER_STATUS_CHANGED = "ACTION_SERVER_STATUS_CHANGED";

	private FriendInfo mFriendInfo;
	private boolean mOnline;
	private Session mSession;
	private String  mPort;
	private int mPfId;
	private Stream mStream;
	private StreamState mState = StreamState.Closed;

	private String mServiceName = "apache";
	private TransportType mTransport = TransportType.TCP;

	private static final int STATUS_READY   = 0;
	private static final int STATUS_INPROGRESS = 1;
	private static final int STATUS_OFFLINE = 2;
	private static final int STATUS_SERVICE_NULL = 3;
	private static final int STATUS_SESSION_REFUSED = 4;

	PfdServer() {}

	public String getHost() { return "127.0.0.1"; }

	public String getPort() {
		return mPort;
	}

	public String getName() {
		return mFriendInfo.getName();
	}

	public String getServerId() {
		return mFriendInfo.getUserId();
	}

	public String getServiceName() {
		return mServiceName;
	}

	public void setServiceName(String serviceName) {
		mServiceName = serviceName;
		savePreferences();
	}

	public int getTransport() {
		switch(mTransport) {
			case ICE:
				return 0;
			case UDP:
				return 1;
			case TCP:
				return 2;
		}
		return 0;
	}

	public void setTransport(int transport) {
		mTransport = TransportType.valueOf(1 << transport);
		savePreferences();
	}

	public void setInfo(FriendInfo friendInfo) {
		mFriendInfo = friendInfo;
		loadPreferences();
	}

	public void setOnline(boolean online) {
		mOnline = online;
	}

	public boolean isOnline() {
		return mOnline;
	}

	@Override
	public void onCompletion(Session session, int status, String reason, String sdp) {
		if (status != 0) {
			Log.i(TAG, String.format("Session request completion with error (%d:%s", status, reason));

			close();
			notifyPortforwardingStatus(STATUS_SESSION_REFUSED);
			return;
		}

		try {
			session.start(sdp);
			Log.i(TAG, "Session started success.");
		} catch (WhisperException e) {
			Log.e(TAG, "Session start error " + e.getErrorCode());
		}
	}

	private void savePreferences() {
		SharedPreferences preferences = WebBrowserApp.getAppContext()
									.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(getServerId() + ":service", mServiceName);
		editor.putInt(getServerId() + ":transport", mTransport.value());
		editor.commit();
	}

	private void loadPreferences() {
		SharedPreferences preferences = WebBrowserApp.getAppContext()
									.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		String serviceName = preferences.getString(getServerId() + ":service", null);
		if (serviceName != null)
			mServiceName = serviceName;

		int transport = preferences.getInt(getServerId() + ":transport", -1);
		if (transport != -1)
			mTransport = TransportType.valueOf(transport);
	}

	public void clearPreferences() {
		SharedPreferences preferences = WebBrowserApp.getAppContext()
									.getSharedPreferences("whisper", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.remove(getServerId() + ":service");
		editor.remove(getServerId() + ":transport");
		editor.commit();
	}

	@Override
	public void onStateChanged(Stream stream, StreamState state) {
		mState = state;
		try {
			switch (state) {
				case Initialized:
					mSession.request(this);
					Log.i(TAG, "Session request to " + getServerId() + " sent.");
					break;

				case TransportReady:
					Log.i(TAG, "Stream to " + getServerId() + " transport ready");
					break;

				case Connected:
					Log.i(TAG, "Stream to " + getServerId() + " connected.");
					mStream = stream;
					openPortforwarding();
					notifyPortforwardingStatus(STATUS_READY);
					break;

				case Deactivated:
					Log.i(TAG, "Stream deactived");
					close();
					break;
				case Closed:
					Log.i(TAG, "Stream closed");
					close();
					break;
				case Error:
					Log.i(TAG, "Stream error");
					close();
					break;
			}
		} catch (WhisperException e) {
			Log.e(TAG, String.format("Stream error (0x%x)", e.getErrorCode()));
			close();
			notifyPortforwardingStatus(e.getErrorCode());
		}
	}

	private int findFreePort() {
		int port;

		try {
			ServerSocket socket = new ServerSocket(0);
			port = socket.getLocalPort();
			socket.close();

		} catch(Exception e) {
			port = -1;
		}

		return port;
	}

	public void setupPortforwarding() {
		if (!isOnline()) {
			notifyPortforwardingStatus(STATUS_OFFLINE);
			return;
		}

		if (mServiceName == null || mServiceName.isEmpty()) {
			notifyPortforwardingStatus(STATUS_SERVICE_NULL);
			return;
		}

		if (mState == StreamState.Initialized || mState == StreamState.TransportReady
			|| mState == StreamState.Connecting) {
			notifyPortforwardingStatus(STATUS_INPROGRESS);
			return;
		}
		else if (mState == StreamState.Connected) {
			try {
				openPortforwarding();
			} catch (WhisperException e) {
				e.printStackTrace();

				Log.e(TAG, "Portforwarding to " + getServerId() + ":" + mServiceName + " opened error.");
				notifyPortforwardingStatus(e.getErrorCode());
			}
			return;
		}
		else {
			mState = StreamState.Closed;

			int sopt = Stream.PROPERTY_ENCRYPT | Stream.PROPERTY_MULTIPLEXING |
				       Stream.PROPERTY_PORT_FORWARDING;

			if (mTransport == TransportType.ICE)
				sopt |= Stream.PROPERTY_RELIABLE;

			try {
				mSession = PfdAgent.singleton().getSessionManager()
							.newSession(mFriendInfo.getUserId(), mTransport);
				mSession.addStream(StreamType.Application, sopt, this);
			}
			catch (WhisperException e) {
				e.printStackTrace();

				if (mSession == null) {
					Log.e(TAG, String.format("New session to %s error (0x%x)",
						getServerId(), e.getErrorCode()));
				}
				else {
					Log.e(TAG, String.format("Add stream error (0x%x)", e.getErrorCode()));
					mSession.close();
					mSession = null;
				}
				notifyPortforwardingStatus(e.getErrorCode());
			}
		}
	}

	private void openPortforwarding() throws WhisperException {
		if (mPfId > 0) {
			Log.i(TAG, "Portforwarding to " + getName() + ":" + mServiceName + " already opened.");
		}
		else {
			mPort = String.valueOf(findFreePort());
			mPfId = mStream.openPortFowarding(mServiceName, PortForwardingProtocol.TCP,
											"127.0.0.1", mPort);

			Log.i(TAG, "Portforwarding to " + getName() + ":" + mServiceName + " opened.");
		}
	}

	private void notifyPortforwardingStatus(int status) {
		Intent intent = new Intent(ACTION_SERVER_STATUS_CHANGED);
		intent.putExtra("serverId", getServerId());
		intent.putExtra("status", status);
		WebBrowserApp.getAppContext().sendBroadcast(intent);
	}

	public void close() {
		if (mSession != null) {
			//mSession.close();
			mSession = null;
			mStream = null;
			mState  = StreamState.Closed;
			mPfId = -1;
		}
	}
}
