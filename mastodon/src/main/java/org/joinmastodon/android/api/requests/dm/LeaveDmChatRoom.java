package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;

/** Leaves the room (also used to decline an invite) - the room itself stays for the others. */
public class LeaveDmChatRoom extends MastodonAPIRequest<Object>{
	public LeaveDmChatRoom(String roomID){
		super(HttpMethod.DELETE, "/dm/chat_rooms/"+roomID, Object.class);
	}
}
