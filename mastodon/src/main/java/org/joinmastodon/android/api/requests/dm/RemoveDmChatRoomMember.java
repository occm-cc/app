package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;

/** Removes someone from a group room; only its owner may do this. */
public class RemoveDmChatRoomMember extends MastodonAPIRequest<Object>{
	public RemoveDmChatRoomMember(String roomID, String accountID){
		super(HttpMethod.DELETE, "/dm/chat_rooms/"+roomID+"/members/"+accountID, Object.class);
	}
}
