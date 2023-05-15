package com.trixiether.dashchan.chan.refugedobrochan;

import android.net.Uri;

import chan.content.VichanChanLocator;

public class RefugeDobrochanChanLocator extends VichanChanLocator {

    public RefugeDobrochanChanLocator() {
        addChanHost("rf.dobrochan.net");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
        DEFAULT_SEGMENT_PRESET = "vichan";
    }

    @Override
    public Uri createAntispamUri(String boardName, String threadNumber) {
        return threadNumber != null ? super.buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".html")
                : super.buildPath(DEFAULT_SEGMENT_PRESET, boardName);
    }
}
