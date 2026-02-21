package com.kimi.agent.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天气查询工具
 * 根据城市名称返回预置的天气数据
 * 
 * @author Kimi
 */
@Component
public class WeatherTool implements Tool<WeatherTool.WeatherInfo> {

    /**
     * 天气信息数据类
     */
    public static class WeatherInfo {
        private final String city;
        private final String weather;
        private final String temperature;
        private final String humidity;
        private final String wind;

        public WeatherInfo(String city, String weather, String temperature, String humidity, String wind) {
            this.city = city;
            this.weather = weather;
            this.temperature = temperature;
            this.humidity = humidity;
            this.wind = wind;
        }

        public String getCity() {
            return city;
        }

        public String getWeather() {
            return weather;
        }

        public String getTemperature() {
            return temperature;
        }

        public String getHumidity() {
            return humidity;
        }

        public String getWind() {
            return wind;
        }

        @Override
        public String toString() {
            return String.format("%s：%s，温度%s，湿度%s，风力%s", 
                    city, weather, temperature, humidity, wind);
        }
    }

    /** 预置天气数据 */
    private static final Map<String, WeatherInfo> WEATHER_DATA = new LinkedHashMap<>();

    static {
        // 预置北京天气数据
        WEATHER_DATA.put("北京", new WeatherInfo("北京", "晴", "22°C", "45%", "3级"));
        WEATHER_DATA.put("beijing", new WeatherInfo("北京", "晴", "22°C", "45%", "3级"));
        
        // 预置上海天气数据
        WEATHER_DATA.put("上海", new WeatherInfo("上海", "多云", "25°C", "60%", "2级"));
        WEATHER_DATA.put("shanghai", new WeatherInfo("上海", "多云", "25°C", "60%", "2级"));
        
        // 预置杭州天气数据
        WEATHER_DATA.put("杭州", new WeatherInfo("杭州", "小雨", "20°C", "75%", "2级"));
        WEATHER_DATA.put("hangzhou", new WeatherInfo("杭州", "小雨", "20°C", "75%", "2级"));
    }

    @Override
    public String getName() {
        return "get_weather";
    }

    @Override
    public String getDescription() {
        return "天气查询：查询指定城市的天气信息，支持北京、上海、杭州";
    }

    @Override
    public Map<String, String> getParameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", "城市名称，如：北京、上海、杭州");
        return params;
    }

    @Override
    public WeatherInfo execute(Map<String, Object> params, String sessionId) {
        String city = params.getOrDefault("city", "").toString().trim();
        
        if (city.isEmpty()) {
            return new WeatherInfo("未知", "无法获取", "-", "-", "-");
        }
        
        // 查找匹配的天气数据
        WeatherInfo info = WEATHER_DATA.get(city);
        if (info != null) {
            return info;
        }
        
        // 尝试小写匹配
        info = WEATHER_DATA.get(city.toLowerCase());
        if (info != null) {
            return info;
        }
        
        // 默认返回未知
        return new WeatherInfo(city, "暂无数据", "-", "-", "-");
    }
}
