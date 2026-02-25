package com.react.agentdemo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Mock 数据服务
 * 从资源配置文件加载 mock 数据
 */
@Service
public class MockDataService {

    private static final Logger logger = LoggerFactory.getLogger(MockDataService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode webSearchData;
    private JsonNode songSearchData;
    private JsonNode songlistSearchData;
    private JsonNode chartsData;
    private JsonNode userAssetsData;

    @PostConstruct
    public void init() {
        try {
            webSearchData = loadJson("mock-data/web-search.json");
            songSearchData = loadJson("mock-data/song-search.json");
            songlistSearchData = loadJson("mock-data/songlist-search.json");
            chartsData = loadJson("mock-data/charts.json");
            userAssetsData = loadJson("mock-data/user-assets.json");
            logger.info("Mock 数据加载成功");
        } catch (IOException e) {
            logger.error("加载 Mock 数据失败", e);
        }
    }

    private JsonNode loadJson(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readTree(is);
        }
    }

    // WebSearch 数据
    public List<JsonNode> getWebSearchResults(List<String> keywords) {
        List<JsonNode> results = new ArrayList<>();
        JsonNode searches = webSearchData.get("searches");
        if (searches != null) {
            for (JsonNode search : searches) {
                JsonNode searchKeywords = search.get("keywords");
                if (matchesKeywords(searchKeywords, keywords)) {
                    JsonNode searchResults = search.get("results");
                    if (searchResults != null) {
                        searchResults.forEach(results::add);
                    }
                }
            }
        }
        return results.isEmpty() ? getDefaultWebSearchResults() : results;
    }

    private List<JsonNode> getDefaultWebSearchResults() {
        List<JsonNode> results = new ArrayList<>();
        results.add(objectMapper.createObjectNode()
            .put("title", "搜索结果")
            .put("content", "找到了一些相关信息"));
        return results;
    }

    // SongSearch 数据
    public List<JsonNode> getAllSongs() {
        List<JsonNode> results = new ArrayList<>();
        JsonNode songs = songSearchData.get("songs");
        if (songs != null) {
            songs.forEach(results::add);
        }
        return results;
    }

    public List<JsonNode> searchSongs(List<String> keywords) {
        List<JsonNode> results = new ArrayList<>();
        JsonNode songs = songSearchData.get("songs");
        if (songs != null) {
            for (JsonNode song : songs) {
                if (matchesSongKeywords(song, keywords)) {
                    results.add(song);
                }
            }
        }
        return results;
    }

    private boolean matchesSongKeywords(JsonNode song, List<String> keywords) {
        String name = song.get("name").asText("").toLowerCase();
        JsonNode artists = song.get("artist");
        String album = song.get("album").asText("").toLowerCase();

        for (String keyword : keywords) {
            String kw = keyword.toLowerCase();
            if (name.contains(kw)) return true;
            if (album.contains(kw)) return true;
            if (artists != null) {
                for (JsonNode artist : artists) {
                    if (artist.asText("").toLowerCase().contains(kw)) return true;
                }
            }
        }
        return false;
    }

    // SongListSearch 数据
    public List<JsonNode> searchSonglists(List<String> keywords) {
        List<JsonNode> results = new ArrayList<>();
        JsonNode songlists = songlistSearchData.get("songlists");
        if (songlists != null) {
            for (JsonNode songlist : songlists) {
                if (matchesSonglistKeywords(songlist, keywords)) {
                    results.add(songlist);
                }
            }
        }
        return results;
    }

    private boolean matchesSonglistKeywords(JsonNode songlist, List<String> keywords) {
        String title = songlist.get("title").asText("").toLowerCase();
        String description = songlist.get("description").asText("").toLowerCase();

        for (String keyword : keywords) {
            String kw = keyword.toLowerCase();
            if (title.contains(kw) || description.contains(kw)) return true;
        }
        return false;
    }

    // Charts 数据
    public List<JsonNode> getChartSongs(String chartType, String date) {
        List<JsonNode> results = new ArrayList<>();
        JsonNode charts = chartsData.get("charts");
        if (charts != null) {
            for (JsonNode chart : charts) {
                if (chartType.equals(chart.get("chartType").asText())
                        && date.equals(chart.get("date").asText())) {
                    JsonNode songs = chart.get("songs");
                    if (songs != null) {
                        songs.forEach(results::add);
                    }
                }
            }
        }
        return results;
    }

    // UserAssets 数据
    public List<JsonNode> getRecentPlays() {
        List<JsonNode> results = new ArrayList<>();
        JsonNode plays = userAssetsData.get("recentPlays");
        if (plays != null) {
            plays.forEach(results::add);
        }
        return results;
    }

    public List<JsonNode> getFavorites() {
        List<JsonNode> results = new ArrayList<>();
        JsonNode favorites = userAssetsData.get("favorites");
        if (favorites != null) {
            favorites.forEach(results::add);
        }
        return results;
    }

    // 辅助方法：匹配关键词
    private boolean matchesKeywords(JsonNode searchKeywords, List<String> keywords) {
        if (searchKeywords == null) return false;
        for (String kw : keywords) {
            String lowerKw = kw.toLowerCase();
            for (JsonNode sk : searchKeywords) {
                if (sk.asText("").toLowerCase().contains(lowerKw)) {
                    return true;
                }
            }
        }
        return false;
    }

    // SlotMatch 数据
    public List<JsonNode> slotMatchSongs(
            List<String> songName, String songNameBool,
            List<String> artist, String artistBool, String artistSex,
            List<String> album, String albumBool,
            List<String> style, String styleBool,
            List<String> language, String languageBool,
            List<String> properties, List<String> years) {
        
        List<JsonNode> allSongs = getAllSongs();
        List<JsonNode> matchedSongs = new ArrayList<>();
        
        for (JsonNode song : allSongs) {
            boolean matches = true;
            
            if (matches && songName != null && !songName.isEmpty()) {
                matches = matchesField(song, "name", songName, songNameBool);
            }
            
            if (matches && artist != null && !artist.isEmpty()) {
                matches = matchesArtist(song, artist, artistBool);
            }
            
            if (matches && artistSex != null && !artistSex.isEmpty()) {
                matches = matchesSingleField(song, "artistSex", artistSex);
            }
            
            if (matches && album != null && !album.isEmpty()) {
                matches = matchesField(song, "album", album, albumBool);
            }
            
            if (matches && style != null && !style.isEmpty()) {
                matches = matchesArrayField(song, "style", style, styleBool);
            }
            
            if (matches && language != null && !language.isEmpty()) {
                matches = matchesArrayField(song, "language", language, languageBool);
            }
            
            if (matches && properties != null && !properties.isEmpty()) {
                matches = matchesArrayField(song, "properties", properties, "or");
            }
            
            if (matches && years != null && !years.isEmpty()) {
                matches = matchesYears(song, years);
            }
            
            if (matches) {
                matchedSongs.add(song);
            }
        }
        
        return matchedSongs;
    }
    
    private boolean matchesField(JsonNode song, String field, List<String> values, String relation) {
        String fieldValue = song.get(field).asText("").toLowerCase();
        
        if ("not".equalsIgnoreCase(relation)) {
            for (String value : values) {
                if (fieldValue.contains(value.toLowerCase())) return false;
            }
            return true;
        } else if ("and".equalsIgnoreCase(relation)) {
            for (String value : values) {
                if (!fieldValue.contains(value.toLowerCase())) return false;
            }
            return true;
        } else { // or
            for (String value : values) {
                if (fieldValue.contains(value.toLowerCase())) return true;
            }
            return false;
        }
    }
    
    private boolean matchesSingleField(JsonNode song, String field, String value) {
        String fieldValue = song.get(field).asText("").toLowerCase();
        return fieldValue.contains(value.toLowerCase());
    }
    
    private boolean matchesArtist(JsonNode song, List<String> artists, String relation) {
        JsonNode songArtists = song.get("artist");
        if (songArtists == null || songArtists.size() == 0) return false;
        
        if ("and".equalsIgnoreCase(relation)) {
            for (String artist : artists) {
                boolean found = false;
                for (JsonNode songArtist : songArtists) {
                    if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        } else if ("not".equalsIgnoreCase(relation)) {
            for (String artist : artists) {
                for (JsonNode songArtist : songArtists) {
                    if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                        return false;
                    }
                }
            }
            return true;
        } else { // or
            for (String artist : artists) {
                for (JsonNode songArtist : songArtists) {
                    if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    
    private boolean matchesArrayField(JsonNode song, String field, List<String> values, String relation) {
        JsonNode fieldArray = song.get(field);
        if (fieldArray == null || fieldArray.size() == 0) return false;
        
        if ("not".equalsIgnoreCase(relation)) {
            for (String value : values) {
                String lowerValue = value.toLowerCase();
                for (JsonNode item : fieldArray) {
                    if (item.asText("").toLowerCase().contains(lowerValue)) {
                        return false;
                    }
                }
            }
            return true;
        } else { // or
            for (String value : values) {
                String lowerValue = value.toLowerCase();
                for (JsonNode item : fieldArray) {
                    if (item.asText("").toLowerCase().contains(lowerValue)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    
    private boolean matchesYears(JsonNode song, List<String> years) {
        String songYear = song.get("year").asText("");
        for (String year : years) {
            if (songYear.equals(year) || songYear.startsWith(year.replace("s", ""))) {
                return true;
            }
        }
        return false;
    }
}
