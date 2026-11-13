package org.joinmastodon.android.fragments.dm;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;

import org.joinmastodon.android.R;
import org.joinmastodon.android.api.requests.accounts.GetAccountFollowing;
import org.joinmastodon.android.api.requests.accounts.SearchAccounts;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.account_list.AccountSearchFragment;
import org.joinmastodon.android.model.Account;
import org.joinmastodon.android.model.HeaderPaginationList;
import org.joinmastodon.android.model.viewmodel.AccountViewModel;
import org.joinmastodon.android.ui.viewholders.AccountViewHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.SimpleCallback;
import me.grishka.appkit.utils.V;

/**
 * Picks the people for a new DM room or to invite into an existing group room. Shows the
 * accounts you follow until you type, then searches. Only accounts on this server can take part
 * in DMs, so others are left out.
 *
 * Args: "account", optional "excludeIds" (ArrayList&lt;String&gt;, e.g. current members).
 * Result: "accountIds" (ArrayList&lt;String&gt;), in the order they were picked.
 */
public class DmAccountPickerFragment extends AccountSearchFragment{
	private final Set<String> selected=new LinkedHashSet<>();
	private final Set<String> excluded=new HashSet<>();
	private String maxID;
	private MenuItem doneItem;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setTitle(R.string.dm_pick_people);
		ArrayList<String> excludeIds=getArguments().getStringArrayList("excludeIds");
		if(excludeIds!=null) excluded.addAll(excludeIds);
		excluded.add(AccountSessionManager.get(accountID).self.id);
		setHasOptionsMenu(true);
		loadData();
	}

	@Override
	protected void doLoadData(int offset, int count){
		if(TextUtils.isEmpty(currentQuery)){
			currentRequest=new GetAccountFollowing(AccountSessionManager.get(accountID).self.id, offset>0 ? maxID : null, count)
					.setCallback(new SimpleCallback<>(this){
						@Override
						public void onSuccess(HeaderPaginationList<Account> result){
							setEmptyText("");
							maxID=result.getNextPageMaxID();
							onDataLoaded(toViewModels(result), result.nextPageUri!=null);
						}
					})
					.exec(accountID);
		}else{
			refreshing=true;
			currentRequest=new SearchAccounts(currentQuery, 0, 0, false, false)
					.setCallback(new SimpleCallback<>(this){
						@Override
						public void onSuccess(List<Account> result){
							setEmptyText(R.string.no_search_results);
							onDataLoaded(toViewModels(result), false);
						}
					})
					.exec(accountID);
		}
	}

	private List<AccountViewModel> toViewModels(List<Account> accounts){
		return accounts.stream()
				.filter(a->a.isLocal() && !excluded.contains(a.id))
				.map(a->new AccountViewModel(a, accountID))
				.collect(Collectors.toList());
	}

	@Override
	protected String getSearchViewPlaceholder(){
		return getString(R.string.search_hint);
	}

	@Override
	protected void onConfigureViewHolder(AccountViewHolder holder){
		holder.setStyle(AccountViewHolder.AccessoryType.CUSTOM_BUTTON, false);
		holder.setOnLongClickListener(vh->false);
		holder.setOnClickListener(this::toggle);
		Button button=holder.getButton();
		button.setPadding(V.dp(24), 0, V.dp(24), 0);
		button.setMinimumWidth(0);
		button.setMinWidth(0);
		button.setOnClickListener(v->toggle(holder));
	}

	private void toggle(AccountViewHolder holder){
		String id=holder.getItem().account.id;
		if(!selected.remove(id))
			selected.add(id);
		onBindViewHolder(holder);
		updateDoneItem();
	}

	@Override
	protected void onBindViewHolder(AccountViewHolder holder){
		Button button=holder.getButton();
		boolean isSelected=selected.contains(holder.getItem().account.id);
		int styleRes=isSelected ? R.style.Widget_Mastodon_M3_Button_Tonal : R.style.Widget_Mastodon_M3_Button_Filled;
		button.setText(isSelected ? R.string.dm_picked : R.string.add);
		TypedArray ta=button.getContext().obtainStyledAttributes(styleRes, new int[]{android.R.attr.background});
		button.setBackground(ta.getDrawable(0));
		ta.recycle();
		ta=button.getContext().obtainStyledAttributes(styleRes, new int[]{android.R.attr.textColor});
		button.setTextColor(ta.getColorStateList(0));
		ta.recycle();
	}

	@Override
	protected void loadRelationships(List<AccountViewModel> accounts){
		// not needed here
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
		doneItem=menu.add(R.string.done);
		doneItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		updateDoneItem();
	}

	private void updateDoneItem(){
		if(doneItem==null) return;
		doneItem.setEnabled(!selected.isEmpty());
		doneItem.setTitle(selected.isEmpty() ? getString(R.string.done) : getString(R.string.dm_done_with_count, selected.size()));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(item==doneItem && !selected.isEmpty()){
			Bundle res=new Bundle();
			res.putStringArrayList("accountIds", new ArrayList<>(selected));
			setResult(true, res);
			Nav.finish(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
