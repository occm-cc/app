package org.joinmastodon.android.fragments.dm;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;

import org.joinmastodon.android.E;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.dm.AcceptDmChatRoom;
import org.joinmastodon.android.api.requests.dm.CreateDmChatRoom;
import org.joinmastodon.android.api.requests.dm.LeaveDmChatRoom;
import org.joinmastodon.android.events.DmRoomsChangedEvent;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.dm.DmChatRoom;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.views.TextInputFrameLayout;

import java.util.List;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;

/** occm: shared flows for the server's own DM rooms (create, accept/decline invites, leave). */
public class DmActions{
	private DmActions(){}

	public static void openRoom(Activity activity, String accountID, String roomUuid){
		Bundle args=new Bundle();
		args.putString("account", accountID);
		args.putString("roomUuid", roomUuid);
		Nav.go(activity, DmChatRoomFragment.class, args);
	}

	/**
	 * One account opens (or reuses) the 1:1 room with them; several make a group, after asking
	 * for an optional name.
	 */
	public static void startConversation(Activity activity, String accountID, List<String> accountIds){
		if(accountIds.size()<=1){
			create(activity, accountID, accountIds, "");
			return;
		}
		TextInputFrameLayout input=new TextInputFrameLayout(activity, null, 0, 0, activity.getString(R.string.dm_group_title_prompt), null);
		new M3AlertDialogBuilder(activity)
				.setTitle(R.string.dm_new_chat)
				.setView(input)
				.setPositiveButton(R.string.dm_create, (dlg, which)->create(activity, accountID, accountIds, input.getEditText().getText().toString().trim()))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private static void create(Activity activity, String accountID, List<String> accountIds, String title){
		new CreateDmChatRoom(accountIds, title)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(DmChatRoom room){
						E.post(new DmRoomsChangedEvent(accountID));
						openRoom(activity, accountID, room.uuid);
					}

					@Override
					public void onError(ErrorResponse error){
						error.showToast(activity);
					}
				})
				.wrapProgress(activity, R.string.loading, true)
				.exec(accountID);
	}

	/** Asks whether to join a room we've been invited to; accepting opens it. */
	public static void showInvite(Activity activity, String accountID, DmChatRoom room, Runnable onDeclined){
		Account inviter=room.owner;
		String name=inviter==null ? "" : !TextUtils.isEmpty(inviter.displayName) ? inviter.displayName : inviter.username;
		new M3AlertDialogBuilder(activity)
				.setTitle(R.string.dm_invite_title)
				.setMessage(activity.getString(R.string.dm_invite_message, name))
				.setPositiveButton(R.string.dm_accept, (dlg, which)->new AcceptDmChatRoom(room.uuid)
						.setCallback(new Callback<>(){
							@Override
							public void onSuccess(DmChatRoom result){
								room.accepted=true;
								E.post(new DmRoomsChangedEvent(accountID));
								openRoom(activity, accountID, room.uuid);
							}

							@Override
							public void onError(ErrorResponse error){
								error.showToast(activity);
							}
						})
						.wrapProgress(activity, R.string.loading, true)
						.exec(accountID))
				.setNegativeButton(R.string.dm_decline, (dlg, which)->leave(activity, accountID, room.uuid, onDeclined))
				.setNeutralButton(R.string.cancel, null)
				.show();
	}

	public static void confirmLeave(Activity activity, String accountID, String roomUuid, Runnable onLeft){
		new M3AlertDialogBuilder(activity)
				.setTitle(R.string.dm_leave)
				.setMessage(R.string.dm_leave_confirm)
				.setPositiveButton(R.string.dm_leave, (dlg, which)->leave(activity, accountID, roomUuid, onLeft))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private static void leave(Activity activity, String accountID, String roomUuid, Runnable onLeft){
		new LeaveDmChatRoom(roomUuid)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(Object result){
						E.post(new DmRoomsChangedEvent(accountID));
						if(onLeft!=null) onLeft.run();
					}

					@Override
					public void onError(ErrorResponse error){
						error.showToast(activity);
					}
				})
				.wrapProgress(activity, R.string.loading, true)
				.exec(accountID);
	}
}
