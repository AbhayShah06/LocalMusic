package com.abhayshah.localmusic;

import java.util.regex.Pattern;

/** Version labels are evidence of an edit; ordinary words and artist names are not. */
final class VersionPolicy {
    private static final Pattern LABEL=Pattern.compile("(?i)(?:[\\[(]\\s*(?:clean(?:\\s+(?:version|edition|edit))?|censored(?:\\s+(?:version|edition))?|edited|radio\\s+edit)\\s*[\\])]|\\b(?:clean\\s+(?:version|edition|edit)|censored\\s+(?:version|edition)|radio\\s+edit)\\b|\\s[-–—]\\s(?:clean|censored|edited)(?:\\s+(?:audio|lyrics))?$)");
    static boolean isMusicVideo(String title){return title!=null&&Pattern.compile("(?i)\\b(?:official\\s+(?:music\\s+)?video|music\\s+video|video\\s+clip|visuali[sz]er)\\b|[\\[(]\\s*video\\s*[\\])]").matcher(title).find();}
    static boolean isEditedVersion(String title){return title!=null&&LABEL.matcher(title).find();}
    static boolean exclude(String title,String query){return isEditedVersion(title)&&!isEditedVersion(query);}
    private VersionPolicy(){}
}
