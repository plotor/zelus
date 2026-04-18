package org.zhenchao.zelus.common.loader;

import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.Constants;
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
 * 文件操作相关Class
 *
 * @author zhenchao
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class FileLoader implements Constants {

    private static final int EVENT_PARTS_THREE = 3;
    private static final int EVENT_PARTS_TWO = 2;
    private static final int LINE_ATTRS_PARTS = 2;

    private static Logger log = Logger.getLogger(FileLoader.class);

    private FileLoader() {
    }

    /**
     * 写文件Function，自动Create写路径
     * 不推荐使用，建议使用apache commons工具包
     *
     * @param filepath 文件路径
     * @param content  内容
     * @param charset  字符编码
     * @throws IOException IO异常
     */
    @Deprecated
    public static void write(String filepath, String content, Charset charset) throws IOException {
        if (filepath != null && content != null) {
            final File file = new File(filepath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

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
     * 加载指定文件中的内容，并返回一个完整的String
     * 不推荐使用，建议使用apache commons工具包
     *
     * @param filepath 文件路径
     * @param charset  字符编码
     * @return 文件内容
     * @throws IOException IO异常
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
                if (!"".equals(lineStr)) {
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
     * @param filepath 文件路径
     * @return EventList
     * @throws IOException IO异常
     */
    @Deprecated
    @SuppressWarnings("checkstyle:MethodLength")
    public static List<EventWithWord> loadEvents(String filepath) throws IOException {
        List<EventWithWord> events = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(filepath), DEFAULT_CHARSET));
            String lineStr = null;
            events = new ArrayList<EventWithWord>();
            final String regexFilename = "\\[\\$[\\w\\.]*?\\$\\]";
            final Pattern pFilename = Pattern.compile(regexFilename);
            while (((lineStr = br.readLine()) != null)) {
                final String[] lineAttrs = lineStr.split("\t");
                if (lineAttrs.length == LINE_ATTRS_PARTS
                        && !"".equals(lineAttrs[1].trim())) {
                    final String[] eventsStr = lineAttrs[1].trim().split("\\s+");
                    for (String eventStr : eventsStr) {
                        Word leftWord = null, middleWord = null, rightWord = null;
                        String filename = null;
                        final Matcher matcher = pFilename.matcher(eventStr);
                        if (matcher.find()) {
                            final String str = matcher.group();
                            filename = str.substring(EVENT_PARTS_TWO, str.length() - EVENT_PARTS_TWO);
                            eventStr = eventStr.replaceAll(regexFilename, "");
                        }
                        if (filename == null) {
                            log.error("提取Event所属文件名Failure：" + eventStr);
                        } else {
                            final String[] wordStr = eventStr.split(WORD_CONNECTOR_IN_EVENTS);
                            if (wordStr.length == EVENT_PARTS_THREE
                                    && !eventStr.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                leftWord = ZelusUtils.str2Word(wordStr[0]);
                                middleWord = ZelusUtils.str2Word(wordStr[1]);
                                rightWord = ZelusUtils.str2Word(wordStr[EVENT_PARTS_TWO]);
                            } else if (wordStr.length == EVENT_PARTS_TWO
                                    || eventStr.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                if (eventStr.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                                    middleWord = ZelusUtils.str2Word(wordStr[1]);
                                    rightWord = ZelusUtils.str2Word(wordStr[EVENT_PARTS_TWO]);
                                } else {
                                    leftWord = ZelusUtils.str2Word(wordStr[0]);
                                    middleWord = ZelusUtils.str2Word(wordStr[1]);
                                }
                            } else {
                                log.error("当前EventClass型不支持");
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
     * 加载当前指定文本，并将其转化成WordPairs象
     *
     * @param filepath 文件路径
     * @param charset  字符编码
     * @return WordPairs象List
     * @throws IOException IO异常
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
                if (!"".equals(lineStr)) {
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
