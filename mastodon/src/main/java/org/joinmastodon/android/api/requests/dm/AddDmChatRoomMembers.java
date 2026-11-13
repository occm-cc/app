package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.dm.DmChatRoom;

import java.util.List;

/** Invites accounts into a group room; only its owner may do this. */
public class AddDmChatRoomMembers extends MastodonAPIRequest<DmChatRoom>{
	public AddDmChatRoomMembers(String roomID, List<String> accountIds){
		super(HttpMethod.POST, "/dm/chat_rooms/"+roomID+"/members", DmChatRoom.class);
		setRequestBody(new Request(accountIds));
	}

	private static class Request{
		public List<String> accountIds;

		Request(List<String> accountIds){
			this.accountIds=accountIds;
		}
	}
}
