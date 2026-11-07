import importlib.util
import sys
import types
import unittest
from pathlib import Path

sys.modules.setdefault("ytmusicapi", types.SimpleNamespace(YTMusic=object))
spec = importlib.util.spec_from_file_location("localmusic_search", Path(__file__).parents[1] / "api" / "search.py")
search = importlib.util.module_from_spec(spec)
spec.loader.exec_module(search)


class SearchRankingTest(unittest.TestCase):
    def test_and_ampersand_and_feature_credit(self):
        self.assertEqual(search.normalized("Shaq and Kobe"), search.normalized("SHAQ & KOBE"))
        self.assertEqual(search.song_title("Rather Be (feat. Jess Glynne)"), "rather be")

    def test_official_top_result_beats_obscure_exact_title(self):
        official = {"title": "Rick Ross, Meek Mill - SHAQ & KOBE (Official Music Video)",
                    "artists": [{"name": "Rick Ross"}, {"name": "Meek Mill"}],
                    "category": "Top result", "resultType": "video",
                    "videoType": "MUSIC_VIDEO_TYPE_OMV", "views": "20M"}
        obscure = {"title": "Shaq and Kobe", "artists": [{"name": "BiggJoe"}], "resultType": "song"}
        self.assertGreater(search.search_score("Shaq and Kobe", official),
                           search.search_score("Shaq and Kobe", obscure))

    def test_popular_official_video_identifies_artist(self):
        obscure = {"title": "Shaq & Kobe", "artists": [{"name": "BiggJoe"}],
                   "videoType": "MUSIC_VIDEO_TYPE_UGC", "views": "10M"}
        official = {"title": "SHAQ & KOBE", "artists": [{"name": "Rick Ross"},
                    {"name": "Meek Mill"}], "videoType": "MUSIC_VIDEO_TYPE_OMV",
                    "views": "43M"}
        self.assertIs(search.best_anchor("Shaq and Kobe", [obscure, official]), official)


if __name__ == "__main__":
    unittest.main()
