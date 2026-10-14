package com.abhayshah.localmusic;

import java.util.List;

/** Stable source boundary for local and future authorized remote catalogs. */
interface CatalogSource {
    List<Track> page(String query, int offset, int limit);
}
