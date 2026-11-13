package org.joinmastodon.android.model;

import static org.junit.Assert.*;

import org.joinmastodon.android.api.MastodonAPIController;
import org.junit.Test;

public class AnnouncementTest {

	private static Announcement parse(String json) throws Exception {
		Announcement a = MastodonAPIController.gson.fromJson(json, Announcement.class);
		a.postprocess();
		return a;
	}

	@Test
	public void serverWideAnnouncementHasNoAccount() throws Exception {
		Announcement a = parse("{\"id\":\"1\",\"content\":\"<p>hi</p>\",\"read\":false,\"account\":null}");
		assertTrue(a.isServerWide());
		assertNull(a.account);
	}

	@Test
	public void followerAnnouncementCarriesAuthor() throws Exception {
		Announcement a = parse("{\"id\":\"2\",\"content\":\"<p>hi</p>\",\"read\":true,"
				+ "\"account\":{\"id\":\"42\",\"username\":\"alice\",\"acct\":\"alice\",\"display_name\":\"Alice\","
				+ "\"url\":\"https://occm.cc/@alice\",\"avatar\":\"a\",\"avatar_static\":\"a\",\"header\":\"h\",\"header_static\":\"h\","
				+ "\"emojis\":[],\"fields\":[],\"created_at\":\"2024-01-01T00:00:00.000Z\"}}");
		assertFalse(a.isServerWide());
		assertEquals("42", a.account.id);
		assertEquals("Alice", a.account.displayName);
	}
}
