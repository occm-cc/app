package org.joinmastodon.android.fragments.settings;

import android.os.Bundle;

import androidx.annotation.StringRes;

import org.joinmastodon.android.GlobalUserPreferences;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.accounts.SetAccountProtected;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.StatusPrivacy;
import org.joinmastodon.android.model.viewmodel.CheckableListItem;
import org.joinmastodon.android.model.viewmodel.ListItem;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;

public class SettingsPrivacyFragment extends BaseSettingsFragment<Void>{
	private CheckableListItem<Void> discoverableItem, indexableItem, lockedItem, protectedItem;
	private ListItem<Void> privacyItem;
	private StatusPrivacy privacy=null;
	private Instance instance;

	//MOSHIDON
	private CheckableListItem<Void> unlistedRepliesItem, removeTrackingParams;


	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.settings_privacy);
		AccountSession session=AccountSessionManager.get(accountID);
		Account self=session.self;
		instance=AccountSessionManager.getInstance().getInstanceInfo(session.domain);
		privacy=self.source.privacy;
		onDataLoaded(List.of(
				privacyItem=new ListItem<>(R.string.sk_settings_default_visibility, getPrivacyString(privacy), R.drawable.ic_fluent_eye_24_regular, this::onPrivacyClick, 0, false),
				unlistedRepliesItem=new CheckableListItem<>(R.string.mo_change_default_reply_visibility_to_unlisted, R.string.mo_setting_default_reply_privacy_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.defaultToUnlistedReplies, R.drawable.ic_fluent_lock_open_24_regular, i->toggleCheckableItem(unlistedRepliesItem)),
				removeTrackingParams=new CheckableListItem<>(R.string.mo_settings_remove_tracking_params, R.string.mo_settings_remove_tracking_params_summary, CheckableListItem.Style.SWITCH, GlobalUserPreferences.removeTrackingParams, R.drawable.ic_fluent_eye_tracking_off_24_filled, i->toggleCheckableItem(removeTrackingParams), true),
				protectedItem=new CheckableListItem<>(R.string.protected_account, R.string.protected_account_summary, CheckableListItem.Style.SWITCH, self.protectedAccount, R.drawable.ic_fluent_shield_24_regular, i->onProtectedClick()),
				lockedItem=new CheckableListItem<>(R.string.sk_settings_lock_account, 0, CheckableListItem.Style.SWITCH, self.locked, R.drawable.ic_fluent_person_available_24_regular, i->toggleCheckableItem(lockedItem))
		));
		// tapping the switch itself must not flip it before the user confirms and the server agrees
		protectedItem.checkedChangeListener=checked->{
			rebindItem(protectedItem);
			onProtectedClick();
		};

		if(!instance.isAkkoma()){
			data.addAll(List.of(
					discoverableItem=new CheckableListItem<>(R.string.settings_discoverable, 0, CheckableListItem.Style.SWITCH, self.discoverable, R.drawable.ic_fluent_thumb_like_dislike_24_regular, i->toggleCheckableItem(discoverableItem)),
					indexableItem=new CheckableListItem<>(R.string.settings_indexable, 0, CheckableListItem.Style.SWITCH, self.source.indexable!=null ? self.source.indexable : true, R.drawable.ic_fluent_search_24_regular, i->toggleCheckableItem(indexableItem))
			));
			if(self.source.indexable==null)
				indexableItem.isEnabled=false;
		}
		applyProtectionLocks(self.protectedAccount);
	}

	/** While protected, the server forces locked on and discoverable/indexable off. */
	private void applyProtectionLocks(boolean isProtected){
		Account self=AccountSessionManager.get(accountID).self;
		for(CheckableListItem<Void> item:new CheckableListItem[]{lockedItem, discoverableItem, indexableItem}){
			if(item==null) continue;
			item.isEnabled=!isProtected && !(item==indexableItem && self.source.indexable==null);
			item.subtitleRes=isProtected ? R.string.protected_account_forced : 0;
		}
	}

	private void onProtectedClick(){
		boolean enable=!protectedItem.checked;
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.protected_account)
				.setMessage(enable ? R.string.protected_account_enable_message : R.string.protected_account_disable_message)
				.setPositiveButton(enable ? R.string.protected_account_enable : R.string.protected_account_disable, (dlg, which)->setProtected(enable))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void setProtected(boolean enable){
		new SetAccountProtected(enable)
				.setCallback(new Callback<>(){
					@Override
					public void onSuccess(SetAccountProtected.Result result){
						AccountSession s=AccountSessionManager.get(accountID);
						Account self=s.self;
						self.protectedAccount=result.protectedAccount;
						self.locked=result.locked;
						self.discoverable=result.discoverable;
						if(result.indexable!=null && self.source.indexable!=null) self.source.indexable=result.indexable;
						// the server switches the default visibility: followers-only when protected,
						// unlisted when protection is turned off
						privacy=result.protectedAccount ? StatusPrivacy.PRIVATE : StatusPrivacy.UNLISTED;
						self.source.privacy=privacy;
						if(s.preferences!=null) s.preferences.postingDefaultVisibility=privacy;
						AccountSessionManager.getInstance().writeAccountsFile();

						protectedItem.checked=self.protectedAccount;
						lockedItem.checked=self.locked;
						if(discoverableItem!=null) discoverableItem.checked=self.discoverable;
						if(indexableItem!=null && self.source.indexable!=null) indexableItem.checked=self.source.indexable;
						privacyItem.subtitleRes=getPrivacyString(privacy);
						applyProtectionLocks(self.protectedAccount);
						for(ListItem<?> item:new ListItem[]{protectedItem, lockedItem, discoverableItem, indexableItem, privacyItem}){
							if(item!=null) rebindItem(item);
						}
					}

					@Override
					public void onError(ErrorResponse error){
						if(getActivity()!=null) error.showToast(getActivity());
					}
				})
				.wrapProgress(getActivity(), R.string.loading, true)
				.exec(accountID);
	}

	@Override
	protected void doLoadData(int offset, int count){}

	private @StringRes int getPrivacyString(StatusPrivacy p){
		if(p==null) return R.string.visibility_public;
		return switch(p){
			case PUBLIC -> R.string.visibility_public;
			case UNLISTED -> R.string.sk_visibility_unlisted;
			case PRIVATE -> R.string.visibility_followers_only;
			case DIRECT -> R.string.visibility_private;
			case LOCAL -> R.string.sk_local_only;
		};
	}

	private void onPrivacyClick(ListItem<?> item_){
		Account self=AccountSessionManager.get(accountID).self;
		// protected accounts can't post publicly; the server turns such posts into followers-only
		List<StatusPrivacy> options=self.protectedAccount
				? new ArrayList<>(List.of(StatusPrivacy.PRIVATE, StatusPrivacy.DIRECT))
				: new ArrayList<>(List.of(StatusPrivacy.PUBLIC, StatusPrivacy.UNLISTED, StatusPrivacy.PRIVATE, StatusPrivacy.DIRECT));
		if(instance.isAkkoma()) options.add(StatusPrivacy.LOCAL);
		int selected=options.indexOf(self.source.privacy);
		int[] newSelected={selected};
		new M3AlertDialogBuilder(getActivity())
				.setTitle(R.string.sk_settings_default_visibility)
				.setSingleChoiceItems(options.stream().map(this::getPrivacyString).map(this::getString).toArray(String[]::new),
						selected, (dlg, item)->newSelected[0]=item)
				.setPositiveButton(R.string.ok, (dlg, item)->{
					if(newSelected[0]<0) return;
					privacy=options.get(newSelected[0]);
					privacyItem.subtitleRes=getPrivacyString(privacy);
					rebindItem(privacyItem);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@Override
	public void onPause(){
		super.onPause();
		GlobalUserPreferences.defaultToUnlistedReplies=unlistedRepliesItem.checked;
		GlobalUserPreferences.removeTrackingParams=removeTrackingParams.checked;
		GlobalUserPreferences.save();
		AccountSession s=AccountSessionManager.get(accountID);
		Account self=s.self;
		boolean savePlease=self.locked!=lockedItem.checked
				|| self.source.privacy!=privacy
				|| (discoverableItem!=null && self.discoverable!=discoverableItem.checked)
				|| (indexableItem!=null && self.source.indexable!=null && self.source.indexable!=indexableItem.checked);
		if(savePlease){
			if(discoverableItem!=null) self.discoverable=discoverableItem.checked;
			if(indexableItem!=null) self.source.indexable=indexableItem.checked;
			self.locked=lockedItem.checked;
			s.preferences.postingDefaultVisibility=privacy;
			s.savePreferencesLater();
		}
	}
}
