package org.joinmastodon.android.api.requests.statuses;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.MastodonAPIRequest;
import org.joinmastodon.android.model.ScheduledStatus;

/**
 * occm: edits a scheduled post in place (PUT /api/v2/scheduled_statuses/:id) instead of deleting
 * and re-creating it, so a failure in between can't lose the post.
 *
 * The server merges the request into the stored params, so anything that was removed in the
 * composer has to be cleared explicitly - otherwise the old content warning, media or poll
 * would stay.
 */
public class UpdateScheduledStatus extends MastodonAPIRequest<ScheduledStatus>{
	public UpdateScheduledStatus(String id, CreateStatus.Request req){
		super(HttpMethod.PUT, "/scheduled_statuses/"+id, ScheduledStatus.class);
		JsonObject body=MastodonAPIController.gson.toJsonTree(req).getAsJsonObject();
		if(!body.has("spoiler_text")) body.addProperty("spoiler_text", "");
		if(!body.has("media_ids")) body.add("media_ids", new JsonArray());
		if(!body.has("poll")) body.add("poll", JsonNull.INSTANCE);
		setRequestBody(body);
	}

	@Override
	protected String getPathPrefix(){
		return "/api/v2";
	}
}
