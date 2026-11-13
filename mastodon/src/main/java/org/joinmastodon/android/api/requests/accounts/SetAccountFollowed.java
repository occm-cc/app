package org.joinmastodon.android.api.requests.accounts;

import org.joinmastodon.android.E;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.events.AccountFollowChangedEvent;
import org.joinmastodon.android.model.Relationship;

import java.io.IOException;

import okhttp3.Response;

public class SetAccountFollowed extends MastodonAPIRequest<Relationship>{
	private final String targetAccountID;

	public SetAccountFollowed(String id, boolean followed, boolean showReblogs){
		this(id, followed, showReblogs, false);
	}

	public SetAccountFollowed(String id, boolean followed, boolean showReblogs, boolean notify){
		super(HttpMethod.POST, "/accounts/"+id+"/"+(followed ? "follow" : "unfollow"), Relationship.class);
		targetAccountID=id;
		if(followed)
			setRequestBody(new Request(showReblogs, notify));
		else
			setRequestBody(new Object());
	}

	@Override
	public void validateAndPostprocessResponse(Relationship respObj, Response httpResponse) throws IOException{
		super.validateAndPostprocessResponse(respObj, httpResponse);
		// e.g. follower announcements depend on who we follow (AsyncBus delivers on the main thread)
		E.post(new AccountFollowChangedEvent(targetAccountID, respObj!=null && (respObj.following || respObj.requested)));
	}

	private static class Request{
		public Boolean reblogs, notify;

		public Request(Boolean reblogs, Boolean notify){
			this.reblogs=reblogs;
			this.notify=notify;
		}
	}
}
