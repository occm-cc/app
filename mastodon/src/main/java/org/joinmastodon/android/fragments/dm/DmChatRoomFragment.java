package org.joinmastodon.android.fragments.dm;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joinmastodon.android.E;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.api.requests.dm.AddDmChatRoomMembers;
import org.joinmastodon.android.api.requests.dm.DeleteDmMessage;
import org.joinmastodon.android.api.requests.dm.GetDmChatRoom;
import org.joinmastodon.android.api.requests.dm.UpdateDmChatRoom;
import org.joinmastodon.android.api.requests.dm.GetDmChatRoomMessages;
import org.joinmastodon.android.api.requests.dm.MarkDmChatRoomRead;
import org.joinmastodon.android.api.requests.dm.SendDmMessage;
import org.joinmastodon.android.api.requests.statuses.UploadAttachment;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.DmPushReceivedEvent;
import org.joinmastodon.android.events.DmRoomsChangedEvent;
import org.joinmastodon.android.fragments.ProfileFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.fragments.MastodonRecyclerFragment;
import org.joinmastodon.android.model.Attachment;
import org.joinmastodon.android.model.Emoji;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.dm.DmChatRoom;
import org.joinmastodon.android.model.dm.DmMessage;
import org.joinmastodon.android.ui.CustomEmojiPopupKeyboard;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.views.TextInputFrameLayout;
import org.parceler.Parcels;
import org.joinmastodon.android.ui.OutlineProviders;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.UiUtils;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import com.squareup.otto.Subscribe;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.imageloader.ViewImageLoader;
import me.grishka.appkit.imageloader.requests.UrlImageLoaderRequest;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.utils.V;

public class DmChatRoomFragment extends MastodonRecyclerFragment<DmMessage>{
	private static final long POLL_INTERVAL_MS=10_000;

	// Rooms migrated from the old per-conversation mentions can end up with message IDs whose
	// order doesn't match created_at (conversations were migrated one at a time, each inserting
	// its messages in its own created_at order, so a later-migrated-but-older conversation's
	// messages get newer IDs). Sort strictly by created_at (matching the web client's fix for the
	// same issue), tie-broken by ID for messages created within the same second.
	private static final Comparator<DmMessage> NEWEST_FIRST=(a, b)->{
		int c=b.createdAt.compareTo(a.createdAt);
		if(c!=0) return c;
		if(a.id.length()!=b.id.length())
			return b.id.length()-a.id.length();
		return b.id.compareTo(a.id);
	};

	private static final int REQUEST_CODE_PICK_IMAGE=8471;
	private static final int PICK_MEMBERS_RESULT=8602;
	private static final int MAX_IMAGE_PIXELS=2_073_600; // matches ComposeMediaViewController's status media upload cap

	private String accountID;
	private String roomUuid;
	private String myAccountId;
	private String nextMaxID;
	private DmChatRoom room;
	private DmMessageAdapter adapter;
	private EditText messageInput;
	private ImageButton sendButton;
	private ImageButton attachButton;
	private ImageButton emojiButton;
	private HorizontalScrollView mediaPreviewRow;
	private LinearLayout mediaPreviewContainer;
	private LinearLayout emojiKeyboardContainer;
	private CustomEmojiPopupKeyboard emojiKeyboard;
	private LinearLayoutManager layoutManager;
	private TextView newMessagesPill;
	private int pendingNewMessageCount=0;
	private final List<String> pendingMediaIds=new ArrayList<>();
	private int uploadingCount=0;
	private final Handler pollHandler=new Handler(Looper.getMainLooper());
	private final Runnable pollRunnable=this::pollForNewMessages;

	public DmChatRoomFragment(){
		super(R.layout.fragment_dm_chat_room, 20);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		E.register(this);
		Bundle args=getArguments();
		accountID=args.getString("account");
		roomUuid=args.getString("roomUuid");
		myAccountId=AccountSessionManager.getInstance().getAccount(accountID).self.id;
		setTitle(R.string.mo_app_name);

		new GetDmChatRoom(roomUuid).setCallback(new Callback<>(){
			@Override
			public void onSuccess(DmChatRoom result){
				if(getActivity()==null) return;
				onRoomLoaded(result);
				if(!result.accepted){
					// opened through a notification or link before accepting the invite
					DmActions.showInvite(getActivity(), accountID, result, ()->Nav.finish(DmChatRoomFragment.this));
				}
			}

			@Override
			public void onError(ErrorResponse error){}
		}).exec(accountID);
		setHasOptionsMenu(true);

		markRead();

		String domain=AccountSessionManager.getInstance().getAccount(accountID).domain;
		emojiKeyboard=new CustomEmojiPopupKeyboard(getActivity(), accountID, AccountSessionManager.getInstance().getCustomEmojis(domain), domain);
		emojiKeyboard.setListener(new CustomEmojiPopupKeyboard.Listener(){
			@Override
			public void onEmojiSelected(Emoji emoji){
				// Trailing space so picking several emoji in a row doesn't glue their
				// shortcodes together (":a::b:") - the keyboard stays open between
				// picks here, unlike the web picker which closes after each one.
				insertTextAtCursor(":"+emoji.shortcode+": ");
			}

			@Override
			public void onEmojiSelected(String emoji){
				insertTextAtCursor(emoji+" ");
			}

			@Override
			public void onBackspace(){
				getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
			}
		});
	}

	private void markRead(){
		new MarkDmChatRoomRead(roomUuid).setCallback(new Callback<>(){
			@Override
			public void onSuccess(DmChatRoom result){
				// unread dot in the room list and the tab badge
				E.post(new DmRoomsChangedEvent(accountID));
			}

			@Override
			public void onError(ErrorResponse error){}
		}).exec(accountID);
	}

	private void onRoomLoaded(DmChatRoom result){
		room=result;
		String title=room.displayTitle(myAccountId);
		setTitle(!TextUtils.isEmpty(title) ? title : getString(R.string.mo_app_name));
		invalidateOptionsMenu();
	}

	private boolean isOwner(){
		return room!=null && room.owner!=null && myAccountId.equals(room.owner.id);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		if(room==null) return;
		menu.add(0, R.id.dm_members, 0, R.string.dm_members);
		if(isOwner() && room.roomType==DmChatRoom.RoomType.GROUP_CHAT){
			menu.add(0, R.id.dm_rename, 0, R.string.dm_rename);
			menu.add(0, R.id.dm_add_members, 0, R.string.dm_add_members);
		}
		menu.add(0, R.id.dm_leave, 0, R.string.dm_leave);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		int id=item.getItemId();
		if(id==R.id.dm_members){
			showMembers();
		}else if(id==R.id.dm_rename){
			showRenameDialog();
		}else if(id==R.id.dm_add_members){
			Bundle args=new Bundle();
			args.putString("account", accountID);
			ArrayList<String> current=new ArrayList<>();
			if(room.participants!=null)
				for(Account a:room.participants) current.add(a.id);
			args.putStringArrayList("excludeIds", current);
			Nav.goForResult(getActivity(), DmAccountPickerFragment.class, args, PICK_MEMBERS_RESULT, this);
		}else if(id==R.id.dm_leave){
			DmActions.confirmLeave(getActivity(), accountID, roomUuid, ()->Nav.finish(this));
		}else{
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void showMembers(){
		List<Account> members=room.participants!=null ? room.participants : List.of();
		String[] names=new String[members.size()];
		for(int i=0;i<members.size();i++){
			Account a=members.get(i);
			String name=(!TextUtils.isEmpty(a.displayName) ? a.displayName : a.username)+" (@"+a.acct+")";
			if(room.owner!=null && room.owner.id.equals(a.id))
				name+=" · "+getString(R.string.dm_owner);
			names[i]=name;
		}
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.dm_members)
				.setItems(names, (dlg, which)->{
					Bundle args=new Bundle();
					args.putString("account", accountID);
					args.putParcelable("profileAccount", Parcels.wrap(members.get(which)));
					Nav.go(getActivity(), ProfileFragment.class, args);
				})
				.setPositiveButton(R.string.ok, null)
				.show();
	}

	private void showRenameDialog(){
		TextInputFrameLayout input=new TextInputFrameLayout(getActivity(), null, 0, 0, getString(R.string.dm_group_title_prompt), room.title);
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.dm_rename)
				.setView(input)
				.setPositiveButton(R.string.save, (dlg, which)->new UpdateDmChatRoom(roomUuid, input.getEditText().getText().toString().trim())
						.setCallback(new Callback<>(){
							@Override
							public void onSuccess(DmChatRoom result){
								if(getActivity()==null) return;
								onRoomLoaded(result);
								E.post(new DmRoomsChangedEvent(accountID));
							}

							@Override
							public void onError(ErrorResponse error){
								if(getActivity()!=null) error.showToast(getActivity());
							}
						})
						.wrapProgress(getActivity(), R.string.loading, true)
						.exec(accountID))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@Override
	public void onFragmentResult(int reqCode, boolean success, Bundle result){
		if(reqCode!=PICK_MEMBERS_RESULT || !success || result==null) return;
		ArrayList<String> ids=result.getStringArrayList("accountIds");
		if(ids==null || ids.isEmpty()) return;
		new AddDmChatRoomMembers(roomUuid, ids)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(DmChatRoom updated){
						if(getActivity()==null) return;
						onRoomLoaded(updated);
						E.post(new DmRoomsChangedEvent(accountID));
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()!=null) error.showToast(getActivity());
					}
				})
				.wrapProgress(getActivity(), R.string.loading, true)
				.exec(accountID);
	}

	private void confirmDeleteMessage(DmMessage message){
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.dm_delete_message)
				.setMessage(R.string.dm_delete_message_confirm)
				.setPositiveButton(R.string.delete, (dlg, which)->new DeleteDmMessage(roomUuid, message.id)
						.setCallback(new Callback<>(){
							@Override
							public void onSuccess(Object result){
								if(getActivity()==null) return;
								int index=data.indexOf(message);
								if(index>=0){
									data.remove(index);
									adapter.notifyDataSetChanged();
								}
								E.post(new DmRoomsChangedEvent(accountID));
							}

							@Override
							public void onError(ErrorResponse error){
								if(getActivity()!=null) error.showToast(getActivity());
							}
						})
						.wrapProgress(getActivity(), R.string.loading, true)
						.exec(accountID))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void insertTextAtCursor(String text){
		int start=Math.max(messageInput.getSelectionStart(), 0);
		int end=Math.max(messageInput.getSelectionEnd(), 0);
		messageInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState){
		super.onViewCreated(view, savedInstanceState);
		setRefreshEnabled(true);
		layoutManager=new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, true);
		list.setLayoutManager(layoutManager);
		messageInput=view.findViewById(R.id.dm_message_input);
		sendButton=view.findViewById(R.id.dm_send_button);
		sendButton.setOnClickListener(this::onSendClick);
		attachButton=view.findViewById(R.id.dm_attach_button);
		attachButton.setOnClickListener(v->openImagePicker());
		emojiButton=view.findViewById(R.id.dm_emoji_button);
		emojiButton.setOnClickListener(v->emojiKeyboard.toggleKeyboardPopup(messageInput));
		mediaPreviewRow=view.findViewById(R.id.dm_media_preview_row);
		mediaPreviewContainer=view.findViewById(R.id.dm_media_preview_container);
		emojiKeyboardContainer=view.findViewById(R.id.dm_emoji_keyboard_container);
		emojiKeyboardContainer.addView(emojiKeyboard.getView());
		newMessagesPill=view.findViewById(R.id.dm_new_messages_pill);
		newMessagesPill.setOnClickListener(v->scrollToLatestAndClearPill());
		list.addOnScrollListener(new RecyclerView.OnScrollListener(){
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
				// Only clear the pill here - do NOT call scrollToPosition(0) from a scroll
				// callback, that fights the user's own drag and makes the list feel stuck
				// (scrollToPosition(0) re-triggers a scroll, which re-triggers onScrolled...).
				if(isAtLatestMessage())
					clearPill();
			}
		});
	}

	private boolean isAtLatestMessage(){
		return layoutManager.findFirstVisibleItemPosition()<=0;
	}

	private void clearPill(){
		pendingNewMessageCount=0;
		newMessagesPill.setVisibility(View.GONE);
	}

	private void scrollToLatestAndClearPill(){
		clearPill();
		list.scrollToPosition(0);
	}

	private void sortAndRefreshList(){
		data.sort(NEWEST_FIRST);
		adapter.notifyDataSetChanged();
	}

	private void openImagePicker(){
		Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data){
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode==REQUEST_CODE_PICK_IMAGE && resultCode==Activity.RESULT_OK && data!=null && data.getData()!=null)
			uploadImage(data.getData());
	}

	private void uploadImage(Uri uri){
		mediaPreviewRow.setVisibility(View.VISIBLE);
		View previewItem=LayoutInflater.from(getActivity()).inflate(R.layout.item_dm_media_preview, mediaPreviewContainer, false);
		ImageView previewImage=previewItem.findViewById(R.id.dm_media_preview_image);
		ImageButton removeButton=previewItem.findViewById(R.id.dm_media_preview_remove);
		ProgressBar progress=previewItem.findViewById(R.id.dm_media_preview_progress);
		previewImage.setOutlineProvider(OutlineProviders.roundedRect(12));
		previewImage.setClipToOutline(true);
		ViewImageLoader.load(previewImage, null, new UrlImageLoaderRequest(uri, V.dp(72), V.dp(72)));
		progress.setVisibility(View.VISIBLE);
		removeButton.setVisibility(View.GONE);
		mediaPreviewContainer.addView(previewItem);
		uploadingCount++;
		sendButton.setEnabled(false);

		new UploadAttachment(uri, MAX_IMAGE_PIXELS, null).setCallback(new Callback<>(){
			@Override
			public void onSuccess(Attachment result){
				if(getActivity()==null) return;
				uploadingCount--;
				sendButton.setEnabled(uploadingCount==0);
				progress.setVisibility(View.GONE);
				removeButton.setVisibility(View.VISIBLE);
				pendingMediaIds.add(result.id);
				removeButton.setOnClickListener(v->{
					pendingMediaIds.remove(result.id);
					mediaPreviewContainer.removeView(previewItem);
					if(mediaPreviewContainer.getChildCount()==0)
						mediaPreviewRow.setVisibility(View.GONE);
				});
			}

			@Override
			public void onError(ErrorResponse error){
				if(getActivity()==null) return;
				uploadingCount--;
				sendButton.setEnabled(uploadingCount==0);
				mediaPreviewContainer.removeView(previewItem);
				if(mediaPreviewContainer.getChildCount()==0)
					mediaPreviewRow.setVisibility(View.GONE);
				error.showToast(getContext());
			}
		}).exec(accountID);
	}

	@Override
	protected void onShown(){
		super.onShown();
		if(!loaded && !dataLoading)
			loadData();
		pollHandler.removeCallbacks(pollRunnable);
		pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		pollHandler.removeCallbacks(pollRunnable);
	}

	@Override
	public void onDestroyView(){
		super.onDestroyView();
		pollHandler.removeCallbacks(pollRunnable);
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(this);
	}

	@Subscribe
	public void onDmPushReceived(DmPushReceivedEvent ev){
		if(!ev.accountID.equals(accountID) || !ev.roomUuid.equals(roomUuid))
			return;
		if(getActivity()==null) return;
		// A push already told us there's something new - no need to wait for the next poll tick.
		pollHandler.removeCallbacks(pollRunnable);
		pollForNewMessages();
	}

	/** Lightweight polling stand-in for real-time updates - there's no streaming channel for DMs. */
	private void pollForNewMessages(){
		if(getActivity()==null) return;
		new GetDmChatRoomMessages(roomUuid, null, 20).setCallback(new Callback<>(){
			@Override
			public void onSuccess(HeaderPaginationList<DmMessage> result){
				if(getActivity()==null) return;
				List<DmMessage> freshMessages=new ArrayList<>();
				for(DmMessage m : result){
					boolean alreadyShown=false;
					for(DmMessage existing : data){
						if(existing.id.equals(m.id)){
							alreadyShown=true;
							break;
						}
					}
					if(alreadyShown)
						break;
					freshMessages.add(m);
				}
				if(!freshMessages.isEmpty()){
					boolean wasAtLatest=isAtLatestMessage();
					data.addAll(0, freshMessages);
					sortAndRefreshList();
					if(wasAtLatest){
						list.scrollToPosition(0);
						markRead();
					}else{
						// User scrolled up to read history - don't yank them back down, just
						// let them know new messages arrived below.
						pendingNewMessageCount+=freshMessages.size();
						newMessagesPill.setText(getString(R.string.dm_new_messages, pendingNewMessageCount));
						newMessagesPill.setVisibility(View.VISIBLE);
					}
				}
				pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
			}

			@Override
			public void onError(ErrorResponse error){
				pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
			}
		}).exec(accountID);
	}

	private void onSendClick(View v){
		String text=messageInput.getText().toString().trim();
		if(TextUtils.isEmpty(text) && pendingMediaIds.isEmpty())
			return;
		List<String> mediaIds=new ArrayList<>(pendingMediaIds);
		sendButton.setEnabled(false);
		new SendDmMessage(roomUuid, text, null, mediaIds).setCallback(new Callback<>(){
			@Override
			public void onSuccess(DmMessage message){
				if(getActivity()==null) return;
				sendButton.setEnabled(true);
				messageInput.setText("");
				pendingMediaIds.clear();
				mediaPreviewContainer.removeAllViews();
				mediaPreviewRow.setVisibility(View.GONE);
				data.add(0, message);
				sortAndRefreshList();
				scrollToLatestAndClearPill();
			}

			@Override
			public void onError(ErrorResponse error){
				if(getActivity()==null) return;
				sendButton.setEnabled(true);
				error.showToast(getContext());
			}
		}).exec(accountID);
	}

	@Override
	protected void doLoadData(int offset, int count){
		MastodonAPIRequest<?> request=new GetDmChatRoomMessages(roomUuid, offset==0 ? null : nextMaxID, count)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(HeaderPaginationList<DmMessage> result){
						if(getActivity()==null) return;
						nextMaxID=result.getNextPageMaxID();
						onDataLoaded(result, nextMaxID!=null);
						sortAndRefreshList();
					}
				});
		request.exec(accountID);
		currentRequest=request;
	}

	@Override
	protected RecyclerView.Adapter<DmMessageViewHolder> getAdapter(){
		return adapter=new DmMessageAdapter();
	}

	private class DmMessageAdapter extends RecyclerView.Adapter<DmMessageViewHolder>{
		@NonNull
		@Override
		public DmMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
			return new DmMessageViewHolder();
		}

		@Override
		public void onBindViewHolder(@NonNull DmMessageViewHolder holder, int position){
			// data is newest-first (index 0 = newest, rendered at the bottom via
			// reverseLayout=true), so the chronologically-*older* neighbor of a message is at
			// position+1. A message starts a new "first of the run" whenever that older
			// neighbor is missing (this is the oldest message overall) or from someone else.
			DmMessage item=data.get(position);
			DmMessage olderNeighbor=position+1<data.size() ? data.get(position+1) : null;
			boolean showSenderInfo=olderNeighbor==null || olderNeighbor.account==null || item.account==null
					|| !olderNeighbor.account.id.equals(item.account.id);
			holder.showSenderInfo=showSenderInfo;
			holder.bind(item);
		}

		@Override
		public int getItemCount(){
			return data.size();
		}
	}

	private class DmMessageViewHolder extends BindableViewHolder<DmMessage>{
		private final ImageView avatar;
		private final View bubbleContainer;
		private final TextView senderName;
		private final LinearLayout attachments;
		private final TextView content;
		private final TextView time;
		boolean showSenderInfo=true;

		public DmMessageViewHolder(){
			super(getActivity(), R.layout.item_dm_message, list);
			avatar=findViewById(R.id.dm_message_avatar);
			avatar.setOutlineProvider(OutlineProviders.OVAL);
			avatar.setClipToOutline(true);
			bubbleContainer=findViewById(R.id.dm_message_bubble_container);
			senderName=findViewById(R.id.dm_message_sender_name);
			attachments=findViewById(R.id.dm_message_attachments);
			content=findViewById(R.id.dm_message_content);
			time=findViewById(R.id.dm_message_time);
		}

		@Override
		public void onBind(DmMessage item){
			boolean mine=item.account!=null && item.account.id.equals(myAccountId);
			// own messages can be deleted (hidden for everyone) with a long press
			View.OnLongClickListener deleteListener=mine ? v->{
				confirmDeleteMessage(item);
				return true;
			} : null;
			content.setOnLongClickListener(deleteListener);
			attachments.setOnLongClickListener(deleteListener);
			content.setLongClickable(mine);

			android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams) bubbleContainer.getLayoutParams();
			params.gravity=mine ? android.view.Gravity.END : android.view.Gravity.START;
			// Leave room for the avatar column on incoming messages - it's only actually drawn
			// on the first message of a consecutive run, but the bubbles still need to line up
			// under it whether or not this particular message shows the avatar.
			params.setMarginStart(mine ? 0 : V.dp(40));
			bubbleContainer.setLayoutParams(params);

			android.widget.FrameLayout.LayoutParams avatarParams=(android.widget.FrameLayout.LayoutParams) avatar.getLayoutParams();
			avatarParams.gravity=android.view.Gravity.START|android.view.Gravity.TOP;
			avatar.setLayoutParams(avatarParams);
			if(!mine && showSenderInfo && item.account!=null){
				avatar.setVisibility(View.VISIBLE);
				ViewImageLoader.load(avatar, null, new UrlImageLoaderRequest(item.account.avatar, V.dp(32), V.dp(32)));
			}else{
				avatar.setVisibility(View.INVISIBLE);
			}

			// bubbleContainer is a vertical LinearLayout, so its children (content/time) also
			// need their own gravity set - otherwise they default to the start of whatever width
			// the container ends up with, which doesn't necessarily hug the container's own edge.
			int childGravity=mine ? android.view.Gravity.END : android.view.Gravity.START;
			android.widget.LinearLayout.LayoutParams contentParams=(android.widget.LinearLayout.LayoutParams) content.getLayoutParams();
			contentParams.gravity=childGravity;
			content.setLayoutParams(contentParams);
			android.widget.LinearLayout.LayoutParams timeParams=(android.widget.LinearLayout.LayoutParams) time.getLayoutParams();
			timeParams.gravity=childGravity;
			time.setLayoutParams(timeParams);

			if(!mine && showSenderInfo && item.account!=null){
				senderName.setVisibility(View.VISIBLE);
				senderName.setText(!TextUtils.isEmpty(item.account.displayName) ? item.account.displayName : item.account.username);
			}else{
				senderName.setVisibility(View.GONE);
			}

			attachments.removeAllViews();
			if(item.attachments!=null && !item.attachments.isEmpty()){
				attachments.setVisibility(View.VISIBLE);
				for(Attachment att : item.attachments){
					ImageView img=(ImageView) LayoutInflater.from(itemView.getContext()).inflate(R.layout.item_dm_message_attachment, attachments, false);
					img.setOutlineProvider(OutlineProviders.roundedRect(12));
					img.setClipToOutline(true);
					ViewImageLoader.load(img, att.blurhashPlaceholder, new UrlImageLoaderRequest(att.previewUrl!=null ? att.previewUrl : att.url, V.dp(160), V.dp(160)));
					attachments.addView(img);
				}
			}else{
				attachments.setVisibility(View.GONE);
			}

			// item.content is server-side HTML (always wrapped in <p>...</p>, even when empty -
			// "<p></p>" for an image-only message), never usable as a plain-text fallback here.
			String text=item.contentPlain;
			content.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
			if(!TextUtils.isEmpty(text))
				HtmlParser.setTextWithCustomEmoji(content, text, item.emojis);
			content.getBackground().mutate().setTint(UiUtils.getThemeColor(itemView.getContext(),
					mine ? R.attr.colorM3PrimaryContainer : R.attr.colorM3SurfaceVariant));
			content.setTextColor(UiUtils.getThemeColor(itemView.getContext(),
					mine ? R.attr.colorM3OnPrimaryContainer : R.attr.colorM3OnSurfaceVariant));

			if(item.createdAt!=null){
				time.setText(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
						.withZone(java.time.ZoneId.systemDefault())
						.format(item.createdAt));
			}else{
				time.setText("");
			}
		}
	}
}
