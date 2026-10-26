package org.cunmin18.breadzero.utils.string;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtils {
    /// 分隔文字并保留分隔符
    public static List<String> splitStringAndSaveSeparator(String str, List<String> delims) {
        String alternation = delims.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern p = Pattern.compile("(" + alternation + ")|((?!" + alternation + ").)+", Pattern.DOTALL);
        Matcher m = p.matcher(str);

        List<String> result = new ArrayList<>();
        while (m.find()) {
            result.add(m.group());
        }
        return result;
    }

    /// 某个索引的字符是否在某个字符串中间
    public static boolean isInRange(String main, String str,int fromIndex) {
        if(fromIndex > main.length() - 1) return false;
        var cToStr = String.valueOf(main.charAt(fromIndex));
        if(str.contains(cToStr) && main.contains(str)) {
            String[] split_main = {main.substring(0,fromIndex + 1),main.substring(fromIndex + 1)};

            var split_main_head = new StringBuilder();
            split_main_head.append(split_main[0]);
            split_main_head.setLength(split_main_head.length() - 1);
            split_main[0] = split_main_head.toString();

            var split_str = str.split(Pattern.quote(cToStr));
            for (int i = 0; i < split_str.length; i++) {
                if(!split_main[i].endsWith(split_str[i])) return false;
            }
            return true;
        }
        else return false;
    }

    /// 包含字符串数
    public static int countCharacter(String str, String subStr) {
        var replaced = str.replace(subStr, "");
        return (str.length() - replaced.length()) / subStr.length();
    }

    /// 获取字符串的某一行
    public static String getALineOfText(String str, int line) {
        if (str == null || line < 1) {
            return "";
        }
        String[] lines = str.split("\n", -1);
        if (line > lines.length) {
            return "";
        }
        return lines[line - 1];
    }

    /// 索引字符在哪一行
    public static int whichLine(String str, int temp) {
        if (str == null || temp < 0 || temp >= str.length()) {
            return 0;
        }
        String[] lines = str.split("\n", -1);
        int currentPos = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineEnd = currentPos + line.length() + 1;
            if (temp <= currentPos + line.length()) {
                return i + 1;
            }
            currentPos = lineEnd;
        }
        return 0;
    }

    /// 将时间戳转化为可读文字形式
    public static String transformTimestampToReadableString(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp * 1000);
        var accurateMonth = calendar.get(Calendar.MONTH) + 1;
        var min = calendar.get(Calendar.MINUTE) < 10 ? "0" + calendar.get(Calendar.MINUTE) : calendar.get(Calendar.MINUTE);
        var sec = calendar.get(Calendar.SECOND) < 10 ? "0" + calendar.get(Calendar.SECOND) : calendar.get(Calendar.SECOND);
        return calendar.get(Calendar.YEAR) + "年" + accurateMonth + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日 " + calendar.get(Calendar.HOUR_OF_DAY) + ":" +  min + ":" + sec;
    }

    /**
     * 从字符串A中移除字符串B的所有出现，返回剩余部分拼接的结果
     * @param a 原字符串
     * @param b 要移除的字符串
     * @return 移除B后A的剩余部分；若A或B为null，返回A本身
     */
    public static String removeAllOccurrences(String a, String b) {
        // 空值处理：若原字符串为null，直接返回null；若要移除的字符串为null或空，返回原字符串
        if (a == null) {
            return null;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        System.out.println("a: |" + a + "|, 长度: " + a.length()); // 应输出 |@NurlMe|, 长度:7
        System.out.println("b: |" + b + "|, 长度: " + b.length()); // 应输出 |@Nu|, 长度:3

        b = b.replaceAll("\n", "").replaceAll(" ","");

        // 使用replace方法移除所有匹配的子串（替换为""）
        return a.replace(b, "");
    }

    /**
     * 检查目标字符串是否包含列表中的某个元素，返回第一个匹配的元素
     * @param target 目标字符串（例如"xxx"）
     * @param strList 待检查的字符串列表
     * @return 第一个被目标字符串包含的元素；若没有匹配则返回null
     */
    public static String findContainedElement(String target, List<String> strList) {
        // 空值处理
        if (target == null || strList == null || strList.isEmpty()) {
            return null;
        }

        // 遍历列表，检查目标字符串是否包含列表元素
        for (String str : strList) {
            // 跳过列表中的null元素
            if (str == null) {
                continue;
            }
            // 若目标字符串包含当前元素，返回该元素
            if (target.contains(str)) {
                return str;
            }
        }

        // 无匹配元素
        return null;
    }

    /**
     * 从列表中找到包含目标字符串、且长度最长的元素（适配"输入前缀返回完整元素"场景）
     * @param target 目标字符串（如@Nur）
     * @param strList 待匹配的字符串列表（如包含@NurlMe）
     * @return 包含目标且最长的元素；无匹配则返回null
     */
    public static String findBestMatchByContainment(String target, List<String> strList) {
        if (target == null || strList == null || strList.isEmpty()) {
            return null;
        }
        target = target.replaceAll("\n","").replaceAll(" ","");

        String bestMatch = null;
        int maxLength = 0;

        for (String str : strList) {
            if (str == null || str.isEmpty()) {
                continue;
            }

            // 关键修改：判断列表元素是否包含目标（而非目标包含列表元素）
            if (str.contains(target)) {
                if (str.length() > maxLength) {
                    maxLength = str.length();
                    bestMatch = str;
                }
            }
        }

        return bestMatch;
    }

    public static boolean hasStartWithElement(String target, List<String> strList) {
        if (target == null || strList == null || strList.isEmpty()) {
            return false;
        }
        target = target.replaceAll("\n","").replaceAll(" ","");
        for (String str : strList) {
            if (str == null) {
                continue;
            }
            if (str.startsWith(target)) {
                return true;
            }
        }
        return false;
    }

}
