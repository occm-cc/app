package org.joinmastodon.android.api;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.joinmastodon.android.E;
import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.DmStreamMessageEvent;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.dm.DmMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * occm: real-time DMs over the server's streaming API ({@code /api/v1/streaming?stream=dm}).
 *
 * DM screens acquire the connection while they're visible and release it when hidden; the socket
 * is closed shortly after the last one goes away, and reconnects with backoff while still in use.
 * Screens keep their polling as a fallback and skip it while {@link #isConnected()}.
 */
public class DmStreamingManager{
	private static final String TAG="DmStreamingManager";
	private static final long DISCONNECT_DELAY_MS=5_000;
	private static final long MAX_RETRY_DELAY_MS=60_000;
	private static final Map<String, DmStreamingManager> instances=new HashMap<>();
	private static OkHttpClient client;

	private final String accountID;
	private final Handler handler=new Handler(Looper.getMainLooper());
	private final Runnable disconnectRunnable=this::disconnectIfUnused;
	private final Runnable reconnectRunnable=this::connectIfNeeded;
	private int refs;
	private int retries;
	private WebSocket socket;
	private boolean connected;

	private DmStreamingManager(String accountID){
		this.accountID=accountID;
	}

	public static synchronized DmStreamingManager get(String accountID){
		return instances.computeIfAbsent(accountID, DmStreamingManager::new);
	}

	/** Called when the setting changes: drops all connections if real-time DMs were turned off. */
	public static synchronized void onSettingChanged(){
		for(DmStreamingManager m:instances.values()){
			if(GlobalUserPreferences.dmRealtime) m.connectIfNeeded();
			else m.disconnect();
		}
	}

	private static synchronized OkHttpClient getClient(){
		if(client==null){
			client=MastodonAPIController.getHttpClient().newBuilder()
					.readTimeout(0, TimeUnit.MILLISECONDS)
					.pingInterval(30, TimeUnit.SECONDS)
					.build();
		}
		return client;
	}

	public void acquire(){
		refs++;
		handler.removeCallbacks(disconnectRunnable);
		connectIfNeeded();
	}

	public void release(){
		refs=Math.max(0, refs-1);
		if(refs==0){
			handler.removeCallbacks(disconnectRunnable);
			handler.postDelayed(disconnectRunnable, DISCONNECT_DELAY_MS);
		}
	}

	public boolean isConnected(){
		return connected;
	}

	private void connectIfNeeded(){
		if(socket!=null || refs==0 || !GlobalUserPreferences.dmRealtime)
			return;
		AccountSession session=AccountSessionManager.getInstance().tryGetAccount(accountID);
		if(session==null || session.token==null)
			return;
		Request req=new Request.Builder()
				.url(getStreamingBaseUrl(session)+"/api/v1/streaming?stream=dm")
				.header("Authorization", "Bearer "+session.token.accessToken)
				.build();
		Log.d(TAG, "connecting for "+accountID);
		socket=getClient().newWebSocket(req, new Listener());
	}

	private static String getStreamingBaseUrl(AccountSession session){
		String url=null;
		Instance instance=session.getInstance().orElse(null);
		if(instance!=null && instance.urls!=null){
			url=instance.urls.get("streaming_api");
			if(TextUtils.isEmpty(url)) url=instance.urls.get("streaming");
		}
		if(TextUtils.isEmpty(url)) url="wss://"+session.domain;
		// OkHttp wants http(s) URLs, it does the ws upgrade itself
		url=url.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
		return url.endsWith("/") ? url.substring(0, url.length()-1) : url;
	}

	private void disconnectIfUnused(){
		if(refs==0) disconnect();
	}

	private void disconnect(){
		handler.removeCallbacks(reconnectRunnable);
		if(socket!=null){
			Log.d(TAG, "disconnecting "+accountID);
			socket.close(1000, null);
			socket=null;
		}
		connected=false;
	}

	private void scheduleReconnect(){
		if(refs==0 || !GlobalUserPreferences.dmRealtime) return;
		long delay=Math.min(MAX_RETRY_DELAY_MS, 1000L<<Math.min(retries, 6));
		retries++;
		handler.removeCallbacks(reconnectRunnable);
		handler.postDelayed(reconnectRunnable, delay);
	}

	private void handleFrame(String text){
		try{
			JsonObject frame=JsonParser.parseString(text).getAsJsonObject();
			if(!frame.has("event") || !"dm_message".equals(frame.get("event").getAsString()))
				return; // read receipts etc. aren't shown by the app
			JsonElement payload=frame.get("payload");
			// the streaming server forwards the payload as a JSON-encoded string
			String json=payload.isJsonPrimitive() ? payload.getAsString() : payload.toString();
			DmMessage message=MastodonAPIController.gson.fromJson(json, DmMessage.class);
			message.postprocess();
			E.post(new DmStreamMessageEvent(accountID, message));
		}catch(Exception x){
			Log.w(TAG, "failed to handle frame", x);
		}
	}

	private class Listener extends WebSocketListener{
		@Override
		public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response){
			handler.post(()->{
				if(webSocket!=socket) return;
				Log.d(TAG, "connected for "+accountID);
				connected=true;
				retries=0;
			});
		}

		@Override
		public void onMessage(@NonNull WebSocket webSocket, @NonNull String text){
			handler.post(()->{
				if(webSocket==socket) handleFrame(text);
			});
		}

		@Override
		public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason){
			handler.post(()->onDropped(webSocket, "closed "+code));
		}

		@Override
		public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response){
			handler.post(()->onDropped(webSocket, t.toString()));
		}

		private void onDropped(WebSocket webSocket, String why){
			if(webSocket!=socket) return;
			Log.d(TAG, "dropped for "+accountID+": "+why);
			socket=null;
			connected=false;
			scheduleReconnect();
		}
	}
}
