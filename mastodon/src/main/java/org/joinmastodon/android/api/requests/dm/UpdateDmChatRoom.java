package org.joinmastodon.android.api.requests.dm;

import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.dm.DmChatRoom;

/** Renames a room; only its owner may do this. */
public class UpdateDmChatRoom extends MastodonAPIRequest<DmChatRoom>{
	public UpdateDmChatRoom(String roomID, String title){
		super(HttpMethod.PATCH, "/dm/chat_rooms/"+roomID, DmChatRoom.class);
		setRequestBody(new Request(title));
	}

	private static class Request{
		public String title;

		Request(String title){
			this.title=title;
		}
	}
}
