package org.joinmastodon.android.model;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.joinmastodon.android.api.MastodonAPIController;
import org.joinmastodon.android.api.requests.statuses.CreateStatus;
import org.junit.Test;

public class ContentTypeTest {

	private static Instance mastodonInstance() {
		Instance i = new Instance();
		i.version = "4.5.0";
		return i;
	}

	@Test
	public void unspecifiedIsNeverSelectable() {
		assertFalse(ContentType.UNSPECIFIED.supportedByInstance(mastodonInstance()));
		assertFalse(ContentType.UNSPECIFIED.supportedByInstance(null));
	}

	@Test
	public void occmOffersPlainMarkdownHtmlOnly() {
		Instance i = mastodonInstance();
		assertTrue(ContentType.PLAIN.supportedByInstance(i));
		assertTrue(ContentType.MARKDOWN.supportedByInstance(i));
		assertTrue(ContentType.HTML.supportedByInstance(i));
		assertFalse(ContentType.BBCODE.supportedByInstance(i));
		assertFalse(ContentType.MISSKEY_MARKDOWN.supportedByInstance(i));
	}

	@Test
	public void selectableOrPlainFallsBackToPlain() {
		Instance i = mastodonInstance();
		assertEquals(ContentType.PLAIN, ContentType.selectableOrPlain(null, i));
		assertEquals(ContentType.PLAIN, ContentType.selectableOrPlain(ContentType.UNSPECIFIED, i));
		assertEquals(ContentType.PLAIN, ContentType.selectableOrPlain(ContentType.BBCODE, i));
		assertEquals(ContentType.MARKDOWN, ContentType.selectableOrPlain(ContentType.MARKDOWN, i));
		assertEquals(ContentType.HTML, ContentType.selectableOrPlain(ContentType.HTML, null));
	}

	@Test
	public void requestSerializesContentTypeAndOmitsUnsetSensitive() {
		CreateStatus.Request req = new CreateStatus.Request();
		req.status = "**bold**";
		req.contentType = ContentType.MARKDOWN;
		JsonObject json = MastodonAPIController.gson.toJsonTree(req).getAsJsonObject();
		assertEquals("text/markdown", json.get("content_type").getAsString());
		assertFalse("sensitive must not be sent when unset", json.has("sensitive"));

		req.sensitive = false;
		json = MastodonAPIController.gson.toJsonTree(req).getAsJsonObject();
		assertFalse(json.get("sensitive").getAsBoolean());
	}
}
