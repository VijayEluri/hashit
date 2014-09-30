/*
 * This file is part of Hash It!.
 * 
 * Copyright (C) 2009-2011 Thilo-Alexander Ginkel.
 * Copyright (C) 2011-2013 TG Byte Software GmbH.
 * 
 * Hash It! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Hash It! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Hash It!.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ginkel.hashit;

public interface Constants {
    final String HASH_WORD_SIZE = "HashWordSize";

    final String RESTRICT_SPECIAL_CHARS = "RestrictSpecialChars";
    final String RESTRICT_DIGITS = "RestrictDigits";

    final String REQUIRE_MIXED_CASE = "RequireMixedCase";
    final String REQUIRE_DIGITS = "RequireDigits";
    final String REQUIRE_PUNCTUATION = "RequirePunctuation";

    final String SITE_MAP = "Site:%s";

    final String HIDE_WELCOME_SCREEN = "HideWelcomeScreen";

    final String STATE_SITE_TAG = "SiteTag";
    final String STATE_WELCOME_DISPLAYED = "WelcomeScreenDisplayed";

    final String SITE_TAGS = "SiteTags";
    final String ENABLE_HISTORY = "EnableHistory";
    final String AUTO_EXIT = "AutoExit";
    final String CACHE_DURATION = "CacheDuration";
    final String USE_DARK_THEME = "DarkTheme";
    final String SHOW_MASTER_KEY_DIGEST = "ShowMasterKeyDigest";

    final String COMPATIBILITY_MODE = "CompatibilityMode";
    final String COMPATIBILITY_PREFIX = "compatible:";
    final String SEED = "Seed";

    final String DISABLE_CLIPBOARD = "DisableClipboard";

    final String APP_VERSION = "AppVersion";
    final String CREATION_DATE = "CreationDate";

    final String ACTION_GLOBAL_PREFS = "com.ginkel.hashit.GLOBAL_PREFS";
    final String ACTION_SITE_PREFS = "com.ginkel.hashit.SITE_PREFS";

    final String LOG_TAG = "HashIt";

    final String MASTER_KEY_CACHE = "MasterKey";

    enum FocusRequest {
        NONE, SITE_TAG, MASTER_KEY
    }
}
