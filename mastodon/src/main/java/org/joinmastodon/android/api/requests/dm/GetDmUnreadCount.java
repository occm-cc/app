package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;

/** Number of rooms with unread messages (cached by the server for up to a minute). */
public class GetDmUnreadCount extends MastodonAPIRequest<GetDmUnreadCount.Result>{
	public GetDmUnreadCount(){
		super(HttpMethod.GET, "/dm/unread_count", Result.class);
	}

	public static class Result{
		public int count;
	}
}
