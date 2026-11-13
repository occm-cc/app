package org.joinmastodon.android.api.requests.accounts;

import org.joinmastodon.android.api.MastodonAPIRequest;

/**
 * occm: turns the protected account mode on or off via PATCH /api/v1/profile. The server also
 * forces locked/discoverable/indexable and the default visibility, so the response carries those.
 */
public class SetAccountProtected extends MastodonAPIRequest<SetAccountProtected.Result>{
	public SetAccountProtected(boolean protectedAccount){
		super(HttpMethod.PATCH, "/profile", Result.class);
		setRequestBody(new Request(protectedAccount));
	}

	private static class Request{
		public boolean protectedAccount;

		Request(boolean protectedAccount){
			this.protectedAccount=protectedAccount;
		}
	}

	/** Subset of the REST::ProfileSerializer response. */
	public static class Result{
		public boolean protectedAccount;
		public boolean locked;
		public boolean discoverable;
		public Boolean indexable;
	}
}
