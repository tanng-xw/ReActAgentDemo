package com.react.agentdemo.tools;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 歌曲搜索工具
 * 根据关键词搜索歌曲列表
 * 
 * @author Kimi
 */
@Component
public class SongSearchTool implements Tool<List<SongSearchTool.Song>> {

    /**
     * 歌曲数据类
     */
    public static class Song {
        private final String id;
        private final String name;
        private final String artist;
        private final String album;

        public Song(String id, String name, String artist, String album) {
            this.id = id;
            this.name = name;
            this.artist = artist;
            this.album = album;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        @Override
        public String toString() {
            return String.format("%s - %s（专辑：%s）", name, artist, album);
        }
    }

    /** 预置歌曲数据库 */
    private static final List<Song> SONG_DATABASE = new ArrayList<>();

    static {
        // 周杰伦歌曲
        SONG_DATABASE.add(new Song("S001", "晴天", "周杰伦", "叶惠美"));
        SONG_DATABASE.add(new Song("S002", "稻香", "周杰伦", "魔杰座"));
        SONG_DATABASE.add(new Song("S003", "七里香", "周杰伦", "七里香"));
        SONG_DATABASE.add(new Song("S004", "告白气球", "周杰伦", "周杰伦的床边故事"));
        
        // 陈奕迅歌曲
        SONG_DATABASE.add(new Song("S005", "十年", "陈奕迅", "黑·白·灰"));
        SONG_DATABASE.add(new Song("S006", "富士山下", "陈奕迅", "What's Going On...?"));
        SONG_DATABASE.add(new Song("S007", "浮夸", "陈奕迅", "U87"));
        SONG_DATABASE.add(new Song("S008", "K歌之王", "陈奕迅", "打得火热"));
        
        // 林俊杰歌曲
        SONG_DATABASE.add(new Song("S009", "江南", "林俊杰", "第二天堂"));
        SONG_DATABASE.add(new Song("S010", "可惜没如果", "林俊杰", "新地球"));
        SONG_DATABASE.add(new Song("S011", "她说", "林俊杰", "她说 概念自选辑"));
        
        // 邓紫棋歌曲
        SONG_DATABASE.add(new Song("S012", "光年之外", "邓紫棋", "光年之外"));
        SONG_DATABASE.add(new Song("S013", "泡沫", "邓紫棋", "Xposed"));
        
        // 毛不易歌曲
        SONG_DATABASE.add(new Song("S014", "消愁", "毛不易", "平凡的一天"));
        SONG_DATABASE.add(new Song("S015", "像我这样的人", "毛不易", "平凡的一天"));
        
        // 薛之谦歌曲
        SONG_DATABASE.add(new Song("S016", "演员", "薛之谦", "绅士"));
        SONG_DATABASE.add(new Song("S017", "刚刚好", "薛之谦", "初学者"));
    }

    @Override
    public String getName() {
        return "search_songs";
    }

    @Override
    public String getDescription() {
        return "歌曲搜索：根据关键词搜索歌曲，返回歌曲ID、歌名、歌手名、专辑名";
    }

    @Override
    public Map<String, String> getParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("keyword", "搜索关键词，可以是歌名、歌手名或专辑名");
        return params;
    }

    @Override
    public List<Song> execute(Map<String, Object> params, String sessionId) {
        String keyword = params.getOrDefault("keyword", "").toString().trim().toLowerCase();
        
        if (keyword.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 搜索匹配的歌曲
        return SONG_DATABASE.stream()
                .filter(song -> 
                    song.getName().toLowerCase().contains(keyword) ||
                    song.getArtist().toLowerCase().contains(keyword) ||
                    song.getAlbum().toLowerCase().contains(keyword))
                .collect(Collectors.toList());
    }

    /**
     * 格式化搜索结果为字符串
     * 
     * @param songs 歌曲列表
     * @return 格式化后的字符串
     */
    public static String formatSongs(List<Song> songs) {
        if (songs.isEmpty()) {
            return "未找到相关歌曲";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(songs.size()).append(" 首歌曲：\n");
        
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            sb.append(i + 1).append(". ")
              .append(song.getName())
              .append(" - ").append(song.getArtist())
              .append("（专辑：").append(song.getAlbum()).append("）")
              .append(" [ID: ").append(song.getId()).append("]");
            
            if (i < songs.size() - 1) {
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
}
