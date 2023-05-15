package com.trixiether.dashchan.chan.refugedobrochan;

import chan.content.VichanChanPerformer;
import chan.http.MultipartEntity;
import chan.text.ParseException;

public class RefugeDobrochanChanPerformer extends VichanChanPerformer {
    @Override
    protected void parseAntispamFields(String text, MultipartEntity entity) throws ParseException {
        RefugeDobrochanAntispamFieldsParser.parseAndApply(text, entity, "board", "thread", "name", "email",
                "subject", "body", "password", "file", "spoiler", "json_response", "reason", "report");
    }
}
