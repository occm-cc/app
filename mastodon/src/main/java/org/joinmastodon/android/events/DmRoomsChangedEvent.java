package org.joinmastodon.android.events;

/** A DM room was created, joined, left, renamed or read - room list and unread badge should refresh. */
public class DmRoomsChangedEvent{
	public final String accountID;

	public DmRoomsChangedEvent(String accountID){
		this.accountID=accountID;
	}
}
