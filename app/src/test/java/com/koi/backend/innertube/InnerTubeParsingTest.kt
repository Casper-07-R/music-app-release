package com.koi.backend.innertube

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeParsingTest {

    private val client = InnerTubeClient()

    @Test
    fun testIsMissingArtist() {
        assertTrue(InnerTubeClient.isMissingArtist(null))
        assertTrue(InnerTubeClient.isMissingArtist(""))
        assertTrue(InnerTubeClient.isMissingArtist("   "))
        assertTrue(InnerTubeClient.isMissingArtist("Unknown Artist"))
        assertTrue(InnerTubeClient.isMissingArtist("unknown"))
        assertFalse(InnerTubeClient.isMissingArtist("Anirudh Ravichander"))
    }

    @Test
    fun testParsePlaylistAlbumUsesHeaderStraplineAndFixedColumnsDuration() {
        val rawJson = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicResponsiveHeaderRenderer": {
                              "title": { "runs": [{ "text": "Noctera Lento" }] },
                              "straplineTextOne": { "runs": [{ "text": "DJ Blass" }] },
                              "subtitle": { "runs": [{ "text": "Single" }, { "text": " • " }, { "text": "2024" }] }
                            }
                          }]
                        }
                      }
                    }
                  }],
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "abc123vid" },
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Noctera Lento" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "4.2M plays" }] }
                                  }
                                }
                              ],
                              "fixedColumns": [
                                {
                                  "musicResponsiveListItemFixedColumnRenderer": {
                                    "text": { "runs": [{ "text": "3:15" }] }
                                  }
                                }
                              ]
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val playlist = client.parsePlaylistResult("MPREb_testAlbum", root)

        assertEquals("Noctera Lento", playlist.title)
        assertTrue(playlist.isAlbum)
        assertEquals(1, playlist.tracks.size)
        val track = playlist.tracks.first()
        assertEquals("DJ Blass", track.artists)
        assertEquals("Noctera Lento", track.album)
        assertEquals(195, track.durationSeconds)
    }

    @Test
    fun testParseCuratedPlaylistDoesNotLeakPlaylistTitleIntoAlbum() {
        val rawJson = """
            {
              "header": {
                "musicDetailHeaderRenderer": {
                  "title": { "runs": [{ "text": "Today's Top Hits" }] },
                  "subtitle": { "runs": [{ "text": "Playlist • 50 songs" }] }
                }
              },
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicPlaylistShelfRenderer": {
                              "contents": [{
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "xyz789vid" },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Starboy" }] }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [{
                                            "text": "The Weeknd",
                                            "navigationEndpoint": {
                                              "browseEndpoint": { "browseId": "UCartist123" }
                                            }
                                          }]
                                        }
                                      }
                                    }
                                  ]
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val playlist = client.parsePlaylistResult("VLPL_topHits", root)

        assertEquals("Today's Top Hits", playlist.title)
        assertEquals(1, playlist.tracks.size)
        val track = playlist.tracks.first()
        assertEquals("The Weeknd", track.artists)
        assertNull(track.album)
    }

    @Test
    fun testParseNextPrefersShortBylineOverMetricsAndStripsTopic() {
        val rawJson = """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": {
                    "watchNextTabbedResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "musicQueueRenderer": {
                              "content": {
                                "playlistPanelRenderer": {
                                  "contents": [{
                                    "playlistPanelVideoRenderer": {
                                      "videoId": "hukumVid",
                                      "title": { "runs": [{ "text": "Hukum - Thalaivar Alappara" }] },
                                      "shortBylineText": { "runs": [{ "text": "Anirudh Ravichander - Topic" }] },
                                      "longBylineText": {
                                        "runs": [
                                          { "text": "Anirudh Ravichander" },
                                          { "text": " • " },
                                          { "text": "Jailer" },
                                          { "text": " • " },
                                          { "text": "200M views" },
                                          { "text": " • " },
                                          { "text": "1.5M likes" }
                                        ]
                                      },
                                      "lengthText": { "runs": [{ "text": "3:26" }] }
                                    }
                                  }]
                                }
                              }
                            }
                          }
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val next = client.parseNextResult(root)

        assertEquals(1, next.items.size)
        val song = next.items.first()
        assertEquals("Anirudh Ravichander", song.artists)
        assertEquals("Jailer", song.album)
        assertEquals(206, song.durationSeconds)
    }

    @Test
    fun testExtractArtistFromAccessibilityHandlesHyphenInTitleAndSpokenDuration() {
        val rawNode = """
            {
              "overlay": {
                "musicItemThumbnailOverlayRenderer": {
                  "content": {
                    "musicPlayButtonRenderer": {
                      "accessibilityPlayData": {
                        "accessibilityData": {
                          "label": "Play Hukum - Thalaivar Alappara - Anirudh Ravichander - 3 minutes, 26 seconds"
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val node = JsonParser.parseString(rawNode).asJsonObject
        val extracted = client.extractArtistFromAccessibility(node, "Hukum - Thalaivar Alappara")
        assertEquals("Anirudh Ravichander", extracted)
    }

    @Test
    fun testParseSongFromTwoRowItemExtractsArtistAndDurationFromHomeShelf() {
        val rawNode = """
            {
              "title": { "runs": [{ "text": "Blinding Lights" }] },
              "subtitle": {
                "runs": [
                  { "text": "Song" },
                  { "text": " • " },
                  { "text": "The Weeknd - Topic" },
                  { "text": " • " },
                  { "text": "3:20" }
                ]
              },
              "navigationEndpoint": {
                "watchEndpoint": { "videoId": "blinding123" }
              }
            }
        """.trimIndent()

        val node = JsonParser.parseString(rawNode).asJsonObject
        val song = client.parseSongFromTwoRowItem(node)

        assertNotNull(song)
        assertEquals("Blinding Lights", song!!.title)
        assertEquals("The Weeknd", song.artists)
        assertEquals(200, song.durationSeconds)
    }

    @Test
    fun testParseComprehensiveSearchPropagatesTopResultArtistToChildSongs() {
        val rawJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicCardShelfRenderer": {
                              "title": { "runs": [{ "text": "The Weeknd" }] },
                              "subtitle": { "runs": [{ "text": "Artist" }] },
                              "onTap": {
                                "browseEndpoint": { "browseId": "UCweeknd123" }
                              },
                              "contents": [{
                                "musicResponsiveListItemRenderer": {
                                  "playlistItemData": { "videoId": "starboy123" },
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Starboy" }] }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Song • 3:50" }] }
                                      }
                                    }
                                  ]
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val result = client.parseComprehensiveSearchResult("the weeknd", root)

        assertNotNull(result.topResult)
        assertEquals("ARTIST", result.topResult!!.type)
        assertEquals(1, result.songs.size)
        assertEquals("The Weeknd", result.songs.first().artists)
        assertEquals(230, result.songs.first().durationSeconds)
    }

    @Test
    fun testParseLyricsFromBrowseSupportsAndroidMusicCueRangeAndWebRemixFallback() {
        val androidMusicJson = """
            {
              "contents": {
                "elementRenderer": {
                  "newElement": {
                    "type": {
                      "componentType": {
                        "model": {
                          "timedLyricsModel": {
                            "lyricsData": {
                              "timedLyricsData": [
                                {
                                  "lyricLine": "I'm tryna put you in the worst mood, ah",
                                  "cueRange": { "startTimeMilliseconds": "15710" }
                                },
                                {
                                  "lyricLine": "P1 cleaner than your church shoes, ah",
                                  "cueRange": { "startTimeMilliseconds": "18360" }
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val syncedLyrics = client.parseLyricsFromBrowse(androidMusicJson, videoId = "34Na4j8AVgA")
        assertNotNull(syncedLyrics)
        assertTrue(syncedLyrics!!.synced)
        assertEquals(2, syncedLyrics.lines.size)
        assertEquals(15710L, syncedLyrics.lines[0].timeMs)
        assertEquals(18360L, syncedLyrics.lines[1].timeMs)

        val webRemixJson = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [{
                    "musicDescriptionShelfRenderer": {
                      "description": {
                        "runs": [{ "text": "Plain line one\nPlain line two" }]
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val plainLyrics = client.parseLyricsFromBrowse(webRemixJson, videoId = "plainVid")
        assertNotNull(plainLyrics)
        assertFalse(plainLyrics!!.synced)
        assertEquals(2, plainLyrics.lines.size)
        assertNull(plainLyrics.lines[0].timeMs)
    }

    @Test
    fun testParsePlaylistsFromSearchPrefersPlaylistIdOverCreatorChannelId() {
        val rawJson = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicShelfRenderer": {
                              "contents": [{
                                "musicResponsiveListItemRenderer": {
                                  "flexColumns": [
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": { "runs": [{ "text": "Best of Bollywood Hits" }] }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemFlexColumnRenderer": {
                                        "text": {
                                          "runs": [
                                            { "text": "Playlist" },
                                            { "text": " • " },
                                            {
                                              "text": "Creator Channel",
                                              "navigationEndpoint": {
                                                "browseEndpoint": {
                                                  "browseId": "UCcreatorChannel123",
                                                  "browseEndpointContextSupportedConfigs": {
                                                    "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_USER_CHANNEL" }
                                                  }
                                                }
                                              }
                                            },
                                            { "text": " • " },
                                            { "text": "10M views" }
                                          ]
                                        }
                                      }
                                    }
                                  ],
                                  "navigationEndpoint": {
                                    "browseEndpoint": {
                                      "browseId": "VLPLactualPlaylist987",
                                      "browseEndpointContextSupportedConfigs": {
                                        "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_PLAYLIST" }
                                      }
                                    }
                                  }
                                }
                              }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val playlists = client.parsePlaylistsFromSearch(root)

        assertEquals(1, playlists.size)
        assertEquals("VLPLactualPlaylist987", playlists.first().id)
        assertEquals("Best of Bollywood Hits", playlists.first().title)
        assertEquals("Creator Channel • 10M views", playlists.first().author)
    }

    @Test
    fun testParsePlaylistsFromSearchFallsBackToWatchEndpointWhenBrowseEndpointAbsent() {
        val rawJson = """
            {
              "contents": {
                "musicShelfRenderer": {
                  "contents": [{
                    "musicResponsiveListItemRenderer": {
                      "flexColumns": [
                        {
                          "musicResponsiveListItemFlexColumnRenderer": {
                            "text": { "runs": [{ "text": "Workout Mix" }] }
                          }
                        },
                        {
                          "musicResponsiveListItemFlexColumnRenderer": {
                            "text": {
                              "runs": [{
                                "text": "SomeCreator",
                                "navigationEndpoint": {
                                  "browseEndpoint": { "browseId": "UConlyCreatorId" }
                                }
                              }]
                            }
                          }
                        }
                      ],
                      "overlay": {
                        "musicItemThumbnailOverlayRenderer": {
                          "content": {
                            "musicPlayButtonRenderer": {
                              "playNavigationEndpoint": {
                                "watchEndpoint": { "playlistId": "PLwatchFallback123" }
                              }
                            }
                          }
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val playlists = client.parsePlaylistsFromSearch(root)

        assertEquals(1, playlists.size)
        assertEquals("VLPLwatchFallback123", playlists.first().id)
    }

    @Test
    fun testParsePlaylistResultKeepsCommunityTracksIncludingEpPrefixArtist() {
        val rawJson = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicResponsiveHeaderRenderer": {
                              "title": { "runs": [{ "text": "Symphonic Metal Picks" }] },
                              "subtitle": { "runs": [{ "text": "Playlist • 2026" }] }
                            }
                          }]
                        }
                      }
                    }
                  }],
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicPlaylistShelfRenderer": {
                          "contents": [{
                            "musicResponsiveListItemRenderer": {
                              "playlistItemData": { "videoId": "epicaVid01" },
                              "flexColumns": [
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Cry for the Moon" }] }
                                  }
                                },
                                {
                                  "musicResponsiveListItemFlexColumnRenderer": {
                                    "text": { "runs": [{ "text": "Epica" }] }
                                  }
                                }
                              ],
                              "fixedColumns": [
                                {
                                  "musicResponsiveListItemFixedColumnRenderer": {
                                    "text": { "runs": [{ "text": "6:44" }] }
                                  }
                                }
                              ]
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val root = JsonParser.parseString(rawJson).asJsonObject
        val result = client.parsePlaylistResult("VLPLmetal01", root)

        assertFalse(result.isAlbum)
        assertEquals(1, result.tracks.size)
        assertEquals("Cry for the Moon", result.tracks.first().title)
        assertEquals("Epica", result.tracks.first().artists)
        assertEquals(404, result.tracks.first().durationSeconds)
    }
}
