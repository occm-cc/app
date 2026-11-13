package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;

/** Hides one of your own messages for everyone. */
public class DeleteDmMessage extends MastodonAPIRequest<Object>{
	public DeleteDmMessage(String roomID, String messageID){
		super(HttpMethod.DELETE, "/dm/chat_rooms/"+roomID+"/messages/"+messageID, Object.class);
	}
}
