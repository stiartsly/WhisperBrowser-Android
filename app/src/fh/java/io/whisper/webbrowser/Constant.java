package io.whisper.webbrowser;

import io.whisper.session.TransportType;

public class Constant {
	public static final String AppId  = "7sRQjDsniyuHdZ9zsQU9DZbMLtQGLBWZ78yHWgjPpTKm";
	public static final String AppKey = "6tzPPAgSACJdScX79wuzMNPQTWkRLZ4qEdhLcZU6q4B9";
    public static final String ApiServerUrl  = "https://fhrain.vicp.cc:8443/web/api";
    public static final String MqttServerUri = "ssl://fhrain.vicp.cc:8883";

	public static final String StunHost = "whisper.freeddns.org";
	public static final String TurnHost  = "whisper.freeddns.org";
	public static final String TurnUsername  = "whisper";
	public static final String TurnPassword = "io2016whisper";

    public static final TransportType defaultTransportType = TransportType.ICE;
    public static final String defaultServiceName = "owncloud";
}