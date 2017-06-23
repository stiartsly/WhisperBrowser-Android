package io.whisper.webbrowser;

import io.whisper.core.FriendInfo;
import io.whisper.session.*;
import io.whisper.exceptions.WhisperException;

import android.content.Intent;
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
	private String mSdp;

	private String mServiceName = "web";

	private static final int STATUS_READY   = 0;
	private static final int STATUS_OFFLINE = 1;
	private static final int STATUS_SERVICE_NULL = 2;
	private static final int STATUS_SESSION_REFUSED = 3;

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
	}

	public void setInfo(FriendInfo friendInfo) {
		mFriendInfo = friendInfo;
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
			mSession.start(sdp);
			Log.i(TAG, "Session started ---> success");
		} catch (WhisperException e) {
			Log.e(TAG, "Session start error " + e.getErrorCode());
		}

		mSdp = sdp;
	}

	@Override
	public void onStateChanged(Stream stream, StreamState state) {
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

		if (mSession != null) {
			try {
				openPortforwarding();
			}
			catch (WhisperException e) {
				e.printStackTrace();

				Log.i(TAG, "Portforwarding to " + getServerId() + ":" + mServiceName + " opened error");
				notifyPortforwardingStatus(e.getErrorCode());
			}
			return;
		}
		else {
			int sopt = Stream.PROPERTY_ENCRYPT | Stream.PROPERTY_MULTIPLEXING |
				       Stream.PROPERTY_PORT_FORWARDING;

			try {
				mSession = PfdAgent.singleton().getSessionManager()
							.newSession(mFriendInfo.getUserId(), TransportType.TCP);
				mStream  = mSession.addStream(StreamType.Application, sopt, this);
			}
			catch (WhisperException e) {
				e.printStackTrace();

				if (mSession == null) {
					Log.e(TAG, String.format("New session to %s error (0x%x)",
						getServerId(), e.getErrorCode()));
				}
				else {
					Log.e(TAG, String.format("Add stream error (0x%x)", e.getErrorCode()));
				}
				notifyPortforwardingStatus(e.getErrorCode());
			}
		}
	}

	private void openPortforwarding() throws WhisperException {
		if (mPfId > 0) {
			Log.i(TAG, "Portforwarding to " + getServerId() + ":" + mServiceName + " already opened.");
		}
		else {
			mPort = String.valueOf(findFreePort());
			mPfId = mStream.openPortFowarding(mServiceName, PortForwardingProtocol.TCP,
											"127.0.0.1", mPort);

			Log.i(TAG, "Portforwarding to " + getServerId() + ":" + mServiceName + " opened.");
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
			mPfId = -1;
		}
	}
}
