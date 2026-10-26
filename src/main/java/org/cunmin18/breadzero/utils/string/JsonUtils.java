package org.cunmin18.breadzero.utils.string;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonUtils {
    private final JsonObject jsonObject;
    private final JsonArray jsonArray;
    public JsonUtils(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
        this.jsonArray = null;
    }
    public JsonUtils(JsonArray jsonArray) {
        this.jsonObject = null;
        this.jsonArray = jsonArray;
    }
    public JsonUtils() {
        this.jsonObject = null;
        this.jsonArray = null;
    }
    public static JsonUtils of(JsonObject jsonObject) {
        return new JsonUtils(jsonObject);
    }
    public static JsonUtils of(JsonArray jsonArray) {
        return new JsonUtils(jsonArray);
    }
    public JsonObject toJsonObject() {
        return jsonObject;
    }
    public JsonArray toJsonArray() {
        return jsonArray;
    }

    /// 从JSON获取指定元素
    public JsonElement getElement(String key) {
        if(jsonObject == null) {
            return null;
        }
        var e = jsonObject.get(key);
        if(e == null){
            return null;
        }
        return e;
    }

    /// 从JSON获取元素并转为String
    public String getAsString(String key) {
        var element = getElement(key);
        if(element == null) return "";
        else{
            return element.getAsString();
        }
    }

    /// 从JSON获取元素并转为Long
    public long getAsLong(String key) {
        var element = getElement(key);
        if(element == null) return 0;
        else{
            return element.getAsLong();
        }
    }

    /// 从JSON获取元素并转为Int
    public int getAsInt(String key) {
        return (int) getAsLong(key);
    }

    /// 从JSON获取元素并转为JsonObject
    public JsonObject getAsObject(String key) {
        return getElement(key) != null ? getElement(key).getAsJsonObject() : null;
    }

    /// 从JSON获取元素并转为JsonArray
    public JsonArray getAsElementList(String key) {
        var result = new JsonArray();
        if(getElement(key) == null) return result;
        result = getElement(key).getAsJsonArray();
        return result;
    }

    /// 从JsonArray获取元素并转为JsonObject
    public JsonObject getObjectAt(int index) {
        if(jsonArray == null) return null;
        if(jsonArray.isEmpty() || index >= jsonArray.size()) return null;
        var element = jsonArray.get(index);
        if(element == null) {
            System.out.println("Json element is null: " + index);
            return null;
        }
        return element.getAsJsonObject();
    }

    /// 从JSON获取元素并转为JsonUtils 用于链式调用
    public JsonUtils getAsExt(String key) {
        if(isNull()) return new JsonUtils();
        var obj = getAsObject(key);
        if(obj == null) return new JsonUtils();
        return new JsonUtils(getAsObject(key));
    }

    /// 从JSONArray获取元素并转为JsonUtils 用于链式调用
    public JsonUtils getAsExt(int index) {
        if(isNull()) return new JsonUtils();
        var obj = getObjectAt(index);
        if(obj == null) return new JsonUtils();
        return new JsonUtils(getObjectAt(index));
    }

    /// JsonObject和JsonArray是否都为Null
    public boolean isNull(){
        return jsonObject == null &&  jsonArray == null;
    }
}
