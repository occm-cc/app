package org.joinmastodon.android.fragments;

import static java.util.stream.Collectors.toList;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;

import com.squareup.otto.Subscribe;

import org.joinmastodon.android.E;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.announcements.GetAnnouncements;
import org.joinmastodon.android.api.requests.statuses.CreateStatus;
import org.joinmastodon.android.api.requests.statuses.GetScheduledStatuses;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.events.AccountFollowChangedEvent;
import org.joinmastodon.android.events.ScheduledStatusCreatedEvent;
import org.joinmastodon.android.events.ScheduledStatusDeletedEvent;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Announcement;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.ScheduledStatus;
import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.ui.displayitems.DummyStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.EmojiReactionsStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.HeaderStatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.StatusDisplayItem;
import org.joinmastodon.android.ui.displayitems.TextStatusDisplayItem;
import org.joinmastodon.android.ui.text.HtmlParser;
import org.joinmastodon.android.ui.utils.UiUtils;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.PaginatedList;
import me.grishka.appkit.api.SimpleCallback;

public class AnnouncementsFragment extends BaseStatusListFragment<Announcement> {
	/** occm: server-wide announcements and follower announcements are shown separately, like on the web */
	public static final String SCOPE_SERVER="server", SCOPE_ACCOUNT="account";

	private Instance instance;
	private AccountSession session;
	private List<String> unreadIDs = null;
	private String scope;

	@Override
	public void onAttach(Activity activity){
		super.onAttach(activity);
		scope = SCOPE_ACCOUNT.equals(getArguments().getString("scope")) ? SCOPE_ACCOUNT : SCOPE_SERVER;
		setTitle(isAccountScope() ? R.string.announcements_account : R.string.announcements_server);
		session = AccountSessionManager.getInstance().getAccount(accountID);
		instance = AccountSessionManager.getInstance().getInstanceInfo(session.domain);
		E.register(this);
		loadData();
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		E.unregister(this);
	}

	private boolean isAccountScope(){
		return SCOPE_ACCOUNT.equals(scope);
	}

	private boolean inScope(Announcement a){
		return isAccountScope() != a.isServerWide();
	}

	/** Tells HomeTabFragment which kind's badge to clear. */
	private void setAllReadResult(){
		Bundle result = new Bundle();
		result.putString("scope", scope);
		setResult(true, result);
	}

	@Subscribe
	public void onAccountFollowChanged(AccountFollowChangedEvent ev){
		// unfollowing someone from here (e.g. via their profile) removes their announcements
		if(isAccountScope() && !ev.following) loadData();
	}

	@Override
	protected List<StatusDisplayItem> buildDisplayItems(Announcement a) {
		if(TextUtils.isEmpty(a.content)) return List.of();
		Account author;
		if(a.account != null){
			// follower announcement: show who wrote it, tapping opens their profile
			author = a.account;
		}else{
			author = new Account();
			author.id = author.acct = author.username = session.domain;
			author.displayName = instance.title;
			author.url = "https://"+session.domain+"/about";
			author.avatar = author.avatarStatic = instance.thumbnail;
			author.emojis = List.of();
		}
		Status fakeStatus = a.toStatus(isInstanceIceshrimp());
		TextStatusDisplayItem textItem = new TextStatusDisplayItem(a.id, HtmlParser.parse(a.content, a.emojis, a.mentions, a.tags, accountID), this, fakeStatus, true);
		textItem.textSelectable = true;

		List<StatusDisplayItem> items=new ArrayList<>();
		items.add(HeaderStatusDisplayItem.fromAnnouncement(a, fakeStatus, author, this, accountID, this::onMarkAsRead));
		items.add(textItem);
		if(!isInstanceAkkoma() && !isInstanceIceshrimp()) items.add(new EmojiReactionsStatusDisplayItem(a.id, this, fakeStatus, accountID, false, true));
		return items;
	}

	public void onMarkAsRead(String id) {
		if (unreadIDs == null) return;
		unreadIDs.remove(id);
		if (unreadIDs.isEmpty()) setAllReadResult();
	}

	@Override
	protected void addAccountToKnown(Announcement s) {}

	@Override
	public void onItemClick(String id) {}

	@Override
	protected void doLoadData(int offset, int count){
		currentRequest=new GetAnnouncements(true)
				.setCallback(new SimpleCallback<>(this){
					@Override
					public void onSuccess(List<Announcement> result){
						if(getActivity()==null) return;

						List<Announcement> scoped = result.stream().filter(AnnouncementsFragment.this::inScope).collect(toList());
						// get unread items first
						List<Announcement> data = scoped.stream().filter(a -> !a.read).collect(toList());
						if (data.isEmpty()) setAllReadResult();
						else unreadIDs = data.stream().map(a -> a.id).collect(toList());

						// append read items at the end
						data.addAll(scoped.stream().filter(a -> a.read).collect(toList()));
						onDataLoaded(data, false);
					}
				})
				.exec(accountID);
	}

	@Override
	public Uri getWebUri(Uri.Builder base) {
		return isInstanceAkkoma() ? base.path("/announcements").build() : null;
	}
}
