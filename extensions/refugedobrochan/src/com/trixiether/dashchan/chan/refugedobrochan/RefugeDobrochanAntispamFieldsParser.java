package com.trixiether.dashchan.chan.refugedobrochan;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import chan.http.RequestEntity;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class RefugeDobrochanAntispamFieldsParser {

    private final HashSet<String> ignoreFields = new HashSet<>();
    private final HashSet<String> hiddenInputsNames = new HashSet<>();
    private final ArrayList<Pair<String, String>> fields = new ArrayList<>();

    private boolean formParsing;
    private String fieldName;

    private RefugeDobrochanAntispamFieldsParser(String source, RequestEntity entity, String... ignoreFields) throws ParseException {
        Collections.addAll(this.ignoreFields, ignoreFields);
        Collections.addAll(this.hiddenInputsNames, "user", "username", "login", "search",
                "q", "url", "firstname", "lastname", "text", "message", "hash");
        PARSER.parse(source, this);
        for (Pair<String, String> field : fields) {
            entity.add(field.first, field.second);
            Log.d("!!!", field.first + " - " + field.second);
        }
    }

    public static void parseAndApply(String source, RequestEntity entity, String... ignoreFields)
            throws ParseException {
        new RefugeDobrochanAntispamFieldsParser(source, entity, ignoreFields);
    }

    private static final TemplateParser<RefugeDobrochanAntispamFieldsParser> PARSER = TemplateParser.<RefugeDobrochanAntispamFieldsParser>builder()
            .equals("form", "name", "post").open((instance, holder, tagName, attributes) -> {
                holder.formParsing = true;
                return false;
            })
            .name("input").open((instance, holder, tagName, attributes) -> {
                if (holder.formParsing) {
                    String name = StringUtils.unescapeHtml(attributes.get("name"));
                    boolean findTag = true;
                    for (String s : holder.ignoreFields) {
                        if (s.equals(name)) {
                            findTag = false;
                            break;
                        }
                    }
                    if (findTag) {
                        if (!name.contains("delete")) {
                            String value = StringUtils.unescapeHtml(attributes.get("value"));
                            holder.fields.add(new Pair<>(StringUtils.unescapeHtml(name), value));
                        }
                    }
                }
                return false;
            })
            .name("textarea").open((instance, holder, tagName, attributes) -> {
                holder.fieldName = StringUtils.unescapeHtml(attributes.get("name"));
                return true;
            })
            .content((instance, holder, source) -> {
                if (holder.formParsing) {
                    if (!source.isEmpty()) {
                        for (String s : holder.hiddenInputsNames)
                            if (s.equals(holder.fieldName))
                                holder.fields.add(new Pair<>(holder.fieldName, StringUtils.unescapeHtml(source)));
                    }
                }
            })
            .prepare();
}
