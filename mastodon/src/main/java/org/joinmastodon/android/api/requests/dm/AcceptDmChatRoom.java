package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.dm.DmChatRoom;

public class AcceptDmChatRoom extends MastodonAPIRequest<DmChatRoom>{
	public AcceptDmChatRoom(String roomID){
		super(HttpMethod.POST, "/dm/chat_rooms/"+roomID+"/accept", DmChatRoom.class);
		setRequestBody(new Object());
	}
}
