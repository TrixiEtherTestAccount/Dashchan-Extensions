package chan.content;

import android.net.Uri;

public class VichanChanLocator extends ChanLocator {

    protected static String DEFAULT_SEGMENT_PRESET = "vichan";

    @Override
    public Uri createThreadUri(String boardName, String threadNumber) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".json");
    }

    public Uri createThreadsUri(String boardName, int page, boolean isCatalog) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, (isCatalog ? "catalog"
                : Integer.toString(page)) + ".json");
    }

    public Uri createBoardsUri() {
        return buildPath(DEFAULT_SEGMENT_PRESET, "boards.json");
    }

    public Uri createFileUri(String boardName, String tim, String ext) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "src", tim + ext);
    }

    public Uri createThumbnailUri(String boardName, String thumbnail) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "thumb", thumbnail);
    }

}
