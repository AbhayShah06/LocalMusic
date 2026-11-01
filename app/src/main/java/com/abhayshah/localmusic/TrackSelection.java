package com.abhayshah.localmusic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntFunction;

/** Keeps a batch action tied to the songs that were on screen when they were picked. */
final class TrackSelection {
    private final LinkedHashMap<Integer,String> picked=new LinkedHashMap<>();
    private boolean active;
    boolean active(){return active;}
    int size(){return picked.size();}
    boolean contains(int index){return picked.containsKey(index);}
    void toggle(int index,String id){active=true;if(id.equals(picked.get(index)))picked.remove(index);else picked.put(index,id);}
    void clear(){active=false;picked.clear();}
    List<Integer> valid(int count,IntFunction<String> idAt){
        picked.entrySet().removeIf(entry->entry.getKey()<0||entry.getKey()>=count||!entry.getValue().equals(idAt.apply(entry.getKey())));
        return new ArrayList<>(picked.keySet());
    }
}
