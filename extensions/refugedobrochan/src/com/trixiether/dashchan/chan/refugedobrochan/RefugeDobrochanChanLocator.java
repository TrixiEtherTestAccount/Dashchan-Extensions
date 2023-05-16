package com.trixiether.dashchan.chan.refugedobrochan;

import android.net.Uri;

import java.util.regex.Pattern;

import chan.content.VichanChanLocator;

public class RefugeDobrochanChanLocator extends VichanChanLocator {

    private static final Pattern THREAD_PATH = Pattern.compile("/vichan/\\w+/{1,2}res/(\\d+).*\\.html");

    public RefugeDobrochanChanLocator() {
        addChanHost("rf.dobrochan.net");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
        DEFAULT_SEGMENT_PRESET = "vichan";
    }

    @Override
    public boolean isThreadUri(Uri uri) {
        return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
    }

    @Override
    public String getThreadNumber(Uri uri) {
        return uri != null ? getGroupValue(uri.getPath(), THREAD_PATH, 1) : null;
    }

    @Override
    public Uri createAntispamUri(String boardName, String threadNumber) {
        return threadNumber != null ? super.buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".html")
                : super.buildPath(DEFAULT_SEGMENT_PRESET, boardName);
    }
}
