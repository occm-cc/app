package org.joinmastodon.android.events;

/** Posted after a follow/unfollow request succeeds, for anything that depends on who we follow. */
public class AccountFollowChangedEvent{
	public final String targetAccountID;
	public final boolean following;

	public AccountFollowChangedEvent(String targetAccountID, boolean following){
		this.targetAccountID=targetAccountID;
		this.following=following;
	}
}
