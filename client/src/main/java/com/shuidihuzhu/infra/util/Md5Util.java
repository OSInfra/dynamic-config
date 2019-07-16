package com.shuidihuzhu.infra.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;

import java.nio.charset.Charset;

@SuppressWarnings("UnstableApiUsage")
public class Md5Util {
    private static HashFunction hf = Hashing.md5();
    private static Charset defaultCharset = Charset.forName("UTF-8");

    /**
     * 将其转换为json后，再进行md5
     *
     * @param object 数据源可以是任何类型的对象
     */
    public static String md5(Object object) {
        return md5(object, false, defaultCharset);
    }

    /**
     * 将其转换为json后，再进行md5
     *
     * @param object      数据源可以是任何类型的对象
     * @param isUpperCase 结果是否大写
     * @param charset     涉及到字符串时的操作编码，默认是utf-8
     */
    public static String md5(Object object, boolean isUpperCase, Charset charset) {
        Hasher hasher = hf.newHasher();
        Gson gson = new Gson();
        String json = gson.toJson(object);

        HashCode hash = hasher.putString(json, charset == null ? defaultCharset : charset).hash();
        return isUpperCase ? hash.toString().toUpperCase() : hash.toString();
    }

}