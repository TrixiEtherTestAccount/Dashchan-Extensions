package com.trixiether.dashchan.chan.warosu;

import android.net.Uri;

import chan.content.FoolFuukaChanLocator;

public class WarosuChanLocator extends FoolFuukaChanLocator {
    public WarosuChanLocator() {
        addChanHost("warosu.org");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }

    @Override
    public Uri createBoardUri(String boardName, int pageNumber) {
        return pageNumber > 0 ? buildPath(boardName, "page", "?task=page&page=" + pageNumber) : buildPath(boardName, "");
    }
}
