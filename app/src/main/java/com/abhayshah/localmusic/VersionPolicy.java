package com.abhayshah.localmusic;

import java.util.regex.Pattern;

/** Version labels are evidence of an edit; ordinary words and artist names are not. */
final class VersionPolicy {
    private static final Pattern LABEL=Pattern.compile("(?i)(?:[\\[(]\\s*(?:clean(?:\\s+(?:version|edit))?|censored(?:\\s+version)?|edited|radio\\s+edit)\\s*[\\])]|\\b(?:clean\\s+(?:version|edit)|censored\\s+version|radio\\s+edit)\\b|\\s[-–—]\\s(?:clean|censored|edited)(?:\\s+(?:audio|lyrics))?$)");
    static boolean isEditedVersion(String title){return title!=null&&LABEL.matcher(title).find();}
    static boolean exclude(String title,String query){return isEditedVersion(title)&&!isEditedVersion(query);}
    private VersionPolicy(){}
}
