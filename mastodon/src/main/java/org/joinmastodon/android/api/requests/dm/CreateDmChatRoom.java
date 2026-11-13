package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.dm.DmChatRoom;

import java.util.List;

/** One participant makes a 1:1 room (the server returns the existing one if there is one); more make a group. */
public class CreateDmChatRoom extends MastodonAPIRequest<DmChatRoom>{
	public CreateDmChatRoom(List<String> accountIds, String title){
		super(HttpMethod.POST, "/dm/chat_rooms", DmChatRoom.class);
		setRequestBody(new Request(accountIds, title));
	}

	private static class Request{
		public List<String> accountIds;
		public String title;

		Request(List<String> accountIds, String title){
			this.accountIds=accountIds;
			this.title=title;
		}
	}
}
