package com.trixiether.dashchan.chan.refugedobrochan;

import chan.content.VichanChanLocator;

public class RefugeDobrochanChanLocator extends VichanChanLocator {

    public RefugeDobrochanChanLocator() {
        addChanHost("rf.dobrochan.net");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
        DEFAULT_SEGMENT_PRESET = "vichan";
    }
}
