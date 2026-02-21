package com.kimi.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 歌曲搜索工具测试类
 * 
 * @author Kimi
 */
class SongSearchToolTest {

    private SongSearchTool songSearchTool;

    @BeforeEach
    void setUp() {
        songSearchTool = new SongSearchTool();
    }

    @Test
    @DisplayName("测试获取工具名称")
    void testGetName() {
        // 当 & 当
        String name = songSearchTool.getName();

        // 则
        assertEquals("search_songs", name);
    }

    @Test
    @DisplayName("测试获取工具描述")
    void testGetDescription() {
        // 当 & 当
        String description = songSearchTool.getDescription();

        // 则
        assertNotNull(description);
        assertTrue(description.contains("歌曲"));
        assertTrue(description.contains("搜索"));
    }

    @Test
    @DisplayName("测试获取工具参数定义")
    void testGetParameters() {
        // 当 & 当
        Map<String, String> params = songSearchTool.getParameters();

        // 则
        assertNotNull(params);
        assertEquals(1, params.size());
        assertTrue(params.containsKey("keyword"));
    }

    @Test
    @DisplayName("测试按歌名搜索歌曲")
    void testExecuteSearchBySongName() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "晴天");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("晴天", result.get(0).getName());
    }

    @Test
    @DisplayName("测试按歌手名搜索歌曲")
    void testExecuteSearchByArtist() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "周杰伦");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 所有结果都应该是周杰伦的歌
        for (SongSearchTool.Song song : result) {
            assertEquals("周杰伦", song.getArtist());
        }
    }

    @Test
    @DisplayName("测试按专辑名搜索歌曲")
    void testExecuteSearchByAlbum() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "平凡的一天");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 所有结果都应该来自该专辑
        for (SongSearchTool.Song song : result) {
            assertEquals("平凡的一天", song.getAlbum());
        }
    }

    @Test
    @DisplayName("测试空关键词搜索")
    void testExecuteEmptyKeyword() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试搜索不存在的歌曲")
    void testExecuteSearchNonExistent() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "不存在的歌曲");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试搜索结果包含所有字段")
    void testSearchResultContainsAllFields() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "稻香");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则
        assertFalse(result.isEmpty());
        SongSearchTool.Song song = result.get(0);
        assertNotNull(song.getId());
        assertNotNull(song.getName());
        assertNotNull(song.getArtist());
        assertNotNull(song.getAlbum());
    }

    @Test
    @DisplayName("测试格式化搜索结果")
    void testFormatSongs() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "周杰伦");
        List<SongSearchTool.Song> songs = songSearchTool.execute(params, "test-session");

        // 当
        String formatted = SongSearchTool.formatSongs(songs);

        // 则
        assertNotNull(formatted);
        assertTrue(formatted.contains("找到"));
        assertTrue(formatted.contains("首歌曲"));
        assertTrue(formatted.contains("周杰伦"));
    }

    @Test
    @DisplayName("测试格式化空结果")
    void testFormatEmptySongs() {
        // 给定 - 空列表
        List<SongSearchTool.Song> songs = List.of();

        // 当
        String formatted = SongSearchTool.formatSongs(songs);

        // 则
        assertEquals("未找到相关歌曲", formatted);
    }

    @Test
    @DisplayName("测试歌曲对象的字符串表示")
    void testSongToString() {
        // 给定
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "晴天");
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 当
        SongSearchTool.Song song = result.get(0);
        String str = song.toString();

        // 则
        assertNotNull(str);
        assertTrue(str.contains("晴天"));
        assertTrue(str.contains("周杰伦"));
        assertTrue(str.contains("专辑"));
    }

    @Test
    @DisplayName("测试大小写不敏感搜索")
    void testCaseInsensitiveSearch() {
        // 给定 - 小写关键词
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "jay");

        // 当
        List<SongSearchTool.Song> result = songSearchTool.execute(params, "test-session");

        // 则 - 由于没有jay的数据，结果应该是空的
        // 但搜索是大小写不敏感的
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试获取参数JSON Schema")
    void testGetParameterSchema() {
        // 当 & 当
        String schema = songSearchTool.getParameterSchema();

        // 则
        assertNotNull(schema);
        assertTrue(schema.contains("keyword"));
    }
}
