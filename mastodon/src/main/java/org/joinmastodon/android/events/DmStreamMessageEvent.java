package org.joinmastodon.android.events;

import org.joinmastodon.android.model.dm.DmMessage;

/** A DM message arrived over the streaming connection (including our own, from any device). */
public class DmStreamMessageEvent{
	public final String accountID;
	public final DmMessage message;

	public DmStreamMessageEvent(String accountID, DmMessage message){
		this.accountID=accountID;
		this.message=message;
	}
}
