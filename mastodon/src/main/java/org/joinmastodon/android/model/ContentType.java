package org.joinmastodon.android.model;

import android.view.Menu;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import org.joinmastodon.android.R;

public enum ContentType {
	@SerializedName("text/plain")
	PLAIN,
	@SerializedName("text/html")
	HTML,
	@SerializedName("text/markdown")
	MARKDOWN,
	@SerializedName("text/bbcode")
	BBCODE, // akkoma
	@SerializedName("text/x.misskeymarkdown")
	MISSKEY_MARKDOWN, // akkoma/*key
	@SerializedName("")
	UNSPECIFIED;

	public int getName() {
		return switch(this) {
			case PLAIN -> R.string.sk_content_type_plain;
			case HTML -> R.string.sk_content_type_html;
			case MARKDOWN -> R.string.sk_content_type_markdown;
			case BBCODE -> R.string.sk_content_type_bbcode;
			case MISSKEY_MARKDOWN -> R.string.sk_content_type_mfm;
			case UNSPECIFIED -> R.string.sk_content_type_unspecified;
		};
	}

	/**
	 * Whether this type can be picked in the composer and in the default content type setting.
	 * occm.cc (glitch-soc port) accepts text/plain, text/markdown and text/html. UNSPECIFIED
	 * ("server default") is never offered: it would make the server fall back to the web
	 * setting, which the app can't read through the API, so the result would be unpredictable.
	 */
	public boolean supportedByInstance(Instance i) {
		if(this==UNSPECIFIED) return false;
		return (i!=null && (i.isAkkoma() || i.isIceshrimp())) || (this!=BBCODE && this!=MISSKEY_MARKDOWN);
	}

	/** Maps a type that can't be picked (null, UNSPECIFIED, unsupported) to PLAIN. */
	public static ContentType selectableOrPlain(@Nullable ContentType type, @Nullable Instance i) {
		return type!=null && type.supportedByInstance(i) ? type : PLAIN;
	}
}
