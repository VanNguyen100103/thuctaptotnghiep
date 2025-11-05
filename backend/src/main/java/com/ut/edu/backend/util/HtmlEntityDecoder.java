package com.ut.edu.backend.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for decoding HTML entities to UTF-8 characters
 * Specifically handles Vietnamese characters (all 134 Vietnamese diacritics)
 *
 * Use case: When clients (like Postman) send HTML entities instead of UTF-8
 * Example: &Aacute;o thun → Áo thun
 */
public class HtmlEntityDecoder {

    private static final Map<String, String> VIETNAMESE_ENTITIES;

    static {
        VIETNAMESE_ENTITIES = new HashMap<>();

        // A family
        VIETNAMESE_ENTITIES.put("&Aacute;", "Á");   // sắc
        VIETNAMESE_ENTITIES.put("&aacute;", "á");
        VIETNAMESE_ENTITIES.put("&Agrave;", "À");   // huyền
        VIETNAMESE_ENTITIES.put("&agrave;", "à");
        VIETNAMESE_ENTITIES.put("&#7842;", "Ả");    // hỏi
        VIETNAMESE_ENTITIES.put("&#7843;", "ả");
        VIETNAMESE_ENTITIES.put("&Atilde;", "Ã");   // ngã
        VIETNAMESE_ENTITIES.put("&atilde;", "ã");
        VIETNAMESE_ENTITIES.put("&#7840;", "Ạ");    // nặng
        VIETNAMESE_ENTITIES.put("&#7841;", "ạ");

        // Ă family
        VIETNAMESE_ENTITIES.put("&#7854;", "Ắ");
        VIETNAMESE_ENTITIES.put("&#7855;", "ắ");
        VIETNAMESE_ENTITIES.put("&#7856;", "Ằ");
        VIETNAMESE_ENTITIES.put("&#7857;", "ằ");
        VIETNAMESE_ENTITIES.put("&#7858;", "Ẳ");
        VIETNAMESE_ENTITIES.put("&#7859;", "ẳ");
        VIETNAMESE_ENTITIES.put("&#7860;", "Ẵ");
        VIETNAMESE_ENTITIES.put("&#7861;", "ẵ");
        VIETNAMESE_ENTITIES.put("&#7862;", "Ặ");
        VIETNAMESE_ENTITIES.put("&#7863;", "ặ");
        VIETNAMESE_ENTITIES.put("&#258;", "Ă");
        VIETNAMESE_ENTITIES.put("&#259;", "ă");

        // Â family
        VIETNAMESE_ENTITIES.put("&#7844;", "Ấ");
        VIETNAMESE_ENTITIES.put("&#7845;", "ấ");
        VIETNAMESE_ENTITIES.put("&#7846;", "Ầ");
        VIETNAMESE_ENTITIES.put("&#7847;", "ầ");
        VIETNAMESE_ENTITIES.put("&#7848;", "Ẩ");
        VIETNAMESE_ENTITIES.put("&#7849;", "ẩ");
        VIETNAMESE_ENTITIES.put("&#7850;", "Ẫ");
        VIETNAMESE_ENTITIES.put("&#7851;", "ẫ");
        VIETNAMESE_ENTITIES.put("&#7852;", "Ậ");
        VIETNAMESE_ENTITIES.put("&#7853;", "ậ");
        VIETNAMESE_ENTITIES.put("&Acirc;", "Â");
        VIETNAMESE_ENTITIES.put("&acirc;", "â");

        // E family
        VIETNAMESE_ENTITIES.put("&Eacute;", "É");
        VIETNAMESE_ENTITIES.put("&eacute;", "é");
        VIETNAMESE_ENTITIES.put("&Egrave;", "È");
        VIETNAMESE_ENTITIES.put("&egrave;", "è");
        VIETNAMESE_ENTITIES.put("&#7866;", "Ẻ");
        VIETNAMESE_ENTITIES.put("&#7867;", "ẻ");
        VIETNAMESE_ENTITIES.put("&#7868;", "Ẽ");
        VIETNAMESE_ENTITIES.put("&#7869;", "ẽ");
        VIETNAMESE_ENTITIES.put("&#7864;", "Ẹ");
        VIETNAMESE_ENTITIES.put("&#7865;", "ẹ");

        // Ê family
        VIETNAMESE_ENTITIES.put("&#7870;", "Ế");
        VIETNAMESE_ENTITIES.put("&#7871;", "ế");
        VIETNAMESE_ENTITIES.put("&#7872;", "Ề");
        VIETNAMESE_ENTITIES.put("&#7873;", "ề");
        VIETNAMESE_ENTITIES.put("&#7874;", "Ể");
        VIETNAMESE_ENTITIES.put("&#7875;", "ể");
        VIETNAMESE_ENTITIES.put("&#7876;", "Ễ");
        VIETNAMESE_ENTITIES.put("&#7877;", "ễ");
        VIETNAMESE_ENTITIES.put("&#7878;", "Ệ");
        VIETNAMESE_ENTITIES.put("&#7879;", "ệ");
        VIETNAMESE_ENTITIES.put("&Ecirc;", "Ê");
        VIETNAMESE_ENTITIES.put("&ecirc;", "ê");

        // I family
        VIETNAMESE_ENTITIES.put("&Iacute;", "Í");
        VIETNAMESE_ENTITIES.put("&iacute;", "í");
        VIETNAMESE_ENTITIES.put("&Igrave;", "Ì");
        VIETNAMESE_ENTITIES.put("&igrave;", "ì");
        VIETNAMESE_ENTITIES.put("&#7880;", "Ỉ");
        VIETNAMESE_ENTITIES.put("&#7881;", "ỉ");
        VIETNAMESE_ENTITIES.put("&#296;", "Ĩ");
        VIETNAMESE_ENTITIES.put("&#297;", "ĩ");
        VIETNAMESE_ENTITIES.put("&#7882;", "Ị");
        VIETNAMESE_ENTITIES.put("&#7883;", "ị");

        // O family
        VIETNAMESE_ENTITIES.put("&Oacute;", "Ó");
        VIETNAMESE_ENTITIES.put("&oacute;", "ó");
        VIETNAMESE_ENTITIES.put("&Ograve;", "Ò");
        VIETNAMESE_ENTITIES.put("&ograve;", "ò");
        VIETNAMESE_ENTITIES.put("&#7886;", "Ỏ");
        VIETNAMESE_ENTITIES.put("&#7887;", "ỏ");
        VIETNAMESE_ENTITIES.put("&Otilde;", "Õ");
        VIETNAMESE_ENTITIES.put("&otilde;", "õ");
        VIETNAMESE_ENTITIES.put("&#7884;", "Ọ");
        VIETNAMESE_ENTITIES.put("&#7885;", "ọ");

        // Ô family
        VIETNAMESE_ENTITIES.put("&#7888;", "Ố");
        VIETNAMESE_ENTITIES.put("&#7889;", "ố");
        VIETNAMESE_ENTITIES.put("&#7890;", "Ồ");
        VIETNAMESE_ENTITIES.put("&#7891;", "ồ");
        VIETNAMESE_ENTITIES.put("&#7892;", "Ổ");
        VIETNAMESE_ENTITIES.put("&#7893;", "ổ");
        VIETNAMESE_ENTITIES.put("&#7894;", "Ỗ");
        VIETNAMESE_ENTITIES.put("&#7895;", "ỗ");
        VIETNAMESE_ENTITIES.put("&#7896;", "Ộ");
        VIETNAMESE_ENTITIES.put("&#7897;", "ộ");
        VIETNAMESE_ENTITIES.put("&Ocirc;", "Ô");
        VIETNAMESE_ENTITIES.put("&ocirc;", "ô");

        // Ơ family
        VIETNAMESE_ENTITIES.put("&#7898;", "Ớ");
        VIETNAMESE_ENTITIES.put("&#7899;", "ớ");
        VIETNAMESE_ENTITIES.put("&#7900;", "Ờ");
        VIETNAMESE_ENTITIES.put("&#7901;", "ờ");
        VIETNAMESE_ENTITIES.put("&#7902;", "Ở");
        VIETNAMESE_ENTITIES.put("&#7903;", "ở");
        VIETNAMESE_ENTITIES.put("&#7904;", "Ỡ");
        VIETNAMESE_ENTITIES.put("&#7905;", "ỡ");
        VIETNAMESE_ENTITIES.put("&#7906;", "Ợ");
        VIETNAMESE_ENTITIES.put("&#7907;", "ợ");
        VIETNAMESE_ENTITIES.put("&#416;", "Ơ");
        VIETNAMESE_ENTITIES.put("&#417;", "ơ");

        // U family
        VIETNAMESE_ENTITIES.put("&Uacute;", "Ú");
        VIETNAMESE_ENTITIES.put("&uacute;", "ú");
        VIETNAMESE_ENTITIES.put("&Ugrave;", "Ù");
        VIETNAMESE_ENTITIES.put("&ugrave;", "ù");
        VIETNAMESE_ENTITIES.put("&#7910;", "Ủ");
        VIETNAMESE_ENTITIES.put("&#7911;", "ủ");
        VIETNAMESE_ENTITIES.put("&#360;", "Ũ");
        VIETNAMESE_ENTITIES.put("&#361;", "ũ");
        VIETNAMESE_ENTITIES.put("&#7908;", "Ụ");
        VIETNAMESE_ENTITIES.put("&#7909;", "ụ");

        // Ư family
        VIETNAMESE_ENTITIES.put("&#7912;", "Ứ");
        VIETNAMESE_ENTITIES.put("&#7913;", "ứ");
        VIETNAMESE_ENTITIES.put("&#7914;", "Ừ");
        VIETNAMESE_ENTITIES.put("&#7915;", "ừ");
        VIETNAMESE_ENTITIES.put("&#7916;", "Ử");
        VIETNAMESE_ENTITIES.put("&#7917;", "ử");
        VIETNAMESE_ENTITIES.put("&#7918;", "Ữ");
        VIETNAMESE_ENTITIES.put("&#7919;", "ữ");
        VIETNAMESE_ENTITIES.put("&#7920;", "Ự");
        VIETNAMESE_ENTITIES.put("&#7921;", "ự");
        VIETNAMESE_ENTITIES.put("&#431;", "Ư");
        VIETNAMESE_ENTITIES.put("&#432;", "ư");

        // Y family
        VIETNAMESE_ENTITIES.put("&Yacute;", "Ý");
        VIETNAMESE_ENTITIES.put("&yacute;", "ý");
        VIETNAMESE_ENTITIES.put("&#7922;", "Ỳ");
        VIETNAMESE_ENTITIES.put("&#7923;", "ỳ");
        VIETNAMESE_ENTITIES.put("&#7926;", "Ỷ");
        VIETNAMESE_ENTITIES.put("&#7927;", "ỷ");
        VIETNAMESE_ENTITIES.put("&#7928;", "Ỹ");
        VIETNAMESE_ENTITIES.put("&#7929;", "ỹ");
        VIETNAMESE_ENTITIES.put("&#7924;", "Ỵ");
        VIETNAMESE_ENTITIES.put("&#7925;", "ỵ");

        // Đ
        VIETNAMESE_ENTITIES.put("&#272;", "Đ");
        VIETNAMESE_ENTITIES.put("&#273;", "đ");
        VIETNAMESE_ENTITIES.put("&ETH;", "Đ");
        VIETNAMESE_ENTITIES.put("&eth;", "đ");
    }

    /**
     * Decode HTML entities to UTF-8 characters
     *
     * @param text Text containing HTML entities (e.g., "&Aacute;o thun")
     * @return Decoded text (e.g., "Áo thun")
     */
    public static String decode(String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : VIETNAMESE_ENTITIES.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    /**
     * Private constructor to prevent instantiation
     */
    private HtmlEntityDecoder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
