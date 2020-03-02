package org.zhenchao.zelus.common.loader;

import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.global.Constants;
import org.zhenchao.zelus.common.pojo.EventWithWord;
import org.zhenchao.zelus.common.pojo.Word;
import org.zhenchao.zelus.common.util.ZelusUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件操作相关类
 *
 * @author Apache_xiaochao
 */
public class FileLoader implements Constants {

    private static Logger log = Logger.getLogger(FileLoader.class);

    /**
     * 写文件函数，自动创建写路径<br>
     * 不推荐使用，建议使用apache commons工具包
     *
     * @param filepath
     * @param content
     * @param charset
     * @return
     * @throws IOException
     */
    @Deprecated
    public static void write(String filepath, String content, Charset charset) throws IOException {
        if (filepath != null && content != null) {
            //判断写路径是否存在，不存在则创建
            final File file = new File(filepath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            //将内容写入文件
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(filepath), charset));
                bw.write(content);
            } finally {
                if (bw != null) {
                    bw.close();
                }
            }
        }
    }

    /**
     * 加载指定文件中的内容，并返回一个完整的字符串<br>
     * 不推荐使用，建议使用apache commons工具包
     *
     * @param filepath
     * @param charset
     * @return
     * @throws IOException
     */
    @Deprecated
    public static String read(String filepath, Charset charset) throws IOException {
        String text = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(filepath), charset));
            String lineStr = null;
            final StringBuilder textTmp = new StringBuilder();
            while (((lineStr = br.readLine()) != null)) {
                lineStr = lineStr.trim();
                if (!"".equals(lineStr)) {  //只要有内容的行
                    textTmp.append(lineStr.trim() + LINE_SPLITER);
                }
            }
            text = textTmp.toString();
            if (text.length() > 0) {
                text = text.substring(0, text.lastIndexOf(LINE_SPLITER));
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return text;
    }

    /**
     * 加载文本文件
     *
     * @param filepath
     * @return
     * @throws IOException
     */
    @Deprecated
    public static List<EventWithWord> loadEvents(String filepath) throws IOException {
        List<EventWithWord> events = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filepath), DEFAULT_CHARSET));
            String lineStr = null;
            events = new ArrayList<EventWithWord>();
            final String regex_filename = "\\[\\$[\\w\\.]*?\\$\\]";
            final Pattern p_filename = Pattern.compile(regex_filename);
            while (((lineStr = br.readLine()) != null)) {
                final String[] lineAttrs = lineStr.split("\t");  //去除首尾空格，并将连续的多个空格替换成一个空格
                if (lineAttrs.length == 2 && !"".equals(lineAttrs[1].trim())) {  //只要有内容的行
                    final String[] events_str = lineAttrs[1].trim().split("\\s+");
                    for (String event_str : events_str) {
                        Word leftWord = null, middleWord = null, rightWord = null;
                        //提取事件所在文件名
                        String filename = null;
                        final Matcher matcher = p_filename.matcher(event_str);
                        if (matcher.find()) {
                            final String str = matcher.group();
                            filename = str.substring(2, str.length() - 2);
                            event_str = event_str.replaceAll(regex_filename, "");  //删除当前事件中的所属文件名信息
                        }
                        if (filename == null) {
                            log.error("提取事件所属文件名失败：" + event_str);
                        } else {
                            final String[] word_str = event_str.split(WORD_CONNECTOR_IN_EVENTS);
                            //分三种情况来对事件进行封装
                            if (word_str.length == 3 && !event_str.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                leftWord = ZelusUtils.str2Word(word_str[0]);
                                middleWord = ZelusUtils.str2Word(word_str[1]);
                                rightWord = ZelusUtils.str2Word(word_str[2]);
                            } else if (word_str.length == 2 || event_str.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                if (event_str.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                    middleWord = ZelusUtils.str2Word(word_str[1]);
                                    rightWord = ZelusUtils.str2Word(word_str[2]);
                                } else {
                                    leftWord = ZelusUtils.str2Word(word_str[0]);
                                    middleWord = ZelusUtils.str2Word(word_str[1]);
                                }
                            } else {
                                log.error("当前事件类型不支持");
                            }
                            events.add(new EventWithWord(leftWord, null, middleWord, rightWord, filename));
                        }
                    }
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return events;
    }

    /**
     * 加载当前指定文本，并将其转化成词对象
     *
     * @param filepath
     * @param charset
     * @return
     * @throws IOException
     */
    public static List<List<Word>> loadText(String filepath, Charset charset) throws IOException {
        List<List<Word>> text = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(filepath), charset));
            text = new ArrayList<List<Word>>();
            String lineStr = null;
            while (((lineStr = br.readLine()) != null)) {
                lineStr = lineStr.trim();
                if (!"".equals(lineStr)) {  //只要有内容的行
                    final List<Word> words = new ArrayList<Word>();
                    final String[] wordsStr = lineStr.split("\\s+");
                    for (final String wordStr : wordsStr) {
                        words.add(ZelusUtils.string2Word(wordStr));
                    }
                    text.add(words);
                } else {
                    text.add(null);
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return text;
    }

}
