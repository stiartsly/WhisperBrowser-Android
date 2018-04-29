package io.whisper.webbrowser;

import io.whisper.session.TransportType;

public class Constant {
	public static final String AppId  = "5guWk5ftzCzMvpxQEfPVWjKXimY4Xg973E33nph15uug";
	public static final String AppKey = "DCNCU7HfGyFx7HrnJSpZZcbCREAppv1uZy4JCbqQHM1C";
	public static final String ApiServerUrl  = "https://ws.iwhisper.io/api";
	public static final String MqttServerUri = "ssl://mqtt.iwhisper.io:8883";

	public static final String StunHost = "ws.iwhisper.io";
	public static final String TurnHost  = "ws.iwhisper.io";
	public static final String TurnUsername  = "whisper";
	public static final String TurnPassword = "io2016whisper";

    public static final TransportType defaultTransportType = TransportType.ICE;
	public static final String defaultServiceName = "web";
}