package ca.baosiek.utils;

import org.apache.commons.lang3.StringUtils;

public class UnicodePunctuationNormalizer {

    private final String[] findString = {"，", "。", "、", "’", "”", "“", "∶", "：", "？",
            "《", "》", "）", "！", "（", "s；", "１", "」", "「", "０", "３", "２", "５", "６", "９",
            "７", "８", "４", "～", "…", "━", "〈", "〉", "【", "】", "％", "！","＂", "＃", "＄", "＆", "＊", "＋",
            "＠", "Ａ", "Ｂ", "Ｃ", "Ｄ", "Ｅ", "Ｆ", "Ｇ", "Ｈ", "Ｉ", "Ｊ", "Ｋ", "Ｌ", "Ｍ", "Ｎ", "Ｏ",
            "Ｐ", "Ｑ", "Ｒ", "Ｓ", "Ｔ", "Ｕ", "Ｖ", "Ｗ", "Ｘ", "Ｙ", "Ｚ",
            "ａ", "ｂ", "ｃ", "ｄ", "ｅ", "ｆ", "ｇ", "ｈ", "ｉ", "ｊ", "ｋ", "ｌ", "ｍ", "ｎ", "ｏ", "ｐ",
            "ｑ", "ｒ", "ｓ", "ｔ", "ｕ", "ｖ", "ｗ", "ｘ", "ｙ", "ｚ"};
    private final String[] replaceString = {",", ".", ",", "'", "\"", "\"", ":", ":", "?",
            "\"", "\"", ")", "!", "(", ";", "\"", "\"", "\"", "0", "3", "2", "5", "6", "9",
            "7", "8", "4", "~", "...", "-", "<", ">", "[", "]", "%", "!", "\"", "#", "$", "&", "*", "+",
            "@", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
            "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};

    public String normalize(String text){

        // Converts mapped non ascii characters to ascii
        String newText = StringUtils.replaceEach(text, findString, replaceString);

        // Converts non mapped ascii characters to space
        newText = nonAsciiCharToSpace(newText);

        // erases all the ASCII control characters
        newText = newText.replaceAll("[\\p{Cntrl}]&&[^\t]", "");

        return newText;
    }

    public static String nonAsciiCharToSpace(String str) {
        StringBuilder sb = new StringBuilder();
        if (str != null && !str.isEmpty()) {
            for(char c : str.toCharArray()) {
                if ((c < 0x20 || c > 0x7E) && c != 0x09){
                    sb.append(' ');
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}