package org.zhenchao.zelus.pretreat;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.global.GlobalConstant;
import org.zhenchao.zelus.common.loader.FileLoader;
import org.zhenchao.zelus.common.loader.ModelLoader;
import org.zhenchao.zelus.common.util.ZelusUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 对文件进行预处理
 *
 * @author Apache_xiaochao
 */
public class Pretreatment implements GlobalConstant {

    /** 获取句子切分文本的key */
    public static final String KEY_SEG_TEXT = "key_seg_text";
    /** 获取指代消解文本的key */
    public static final String KEY_CR_TEXT = "key_cr_text";

    private final Logger log = Logger.getLogger(this.getClass());

    private final StanfordCoreNLP pipeline;

    public Pretreatment() {

        this.pipeline = ModelLoader.getPipeLine();

    }

    /**
     * 对输入文本进行指代消解，并返回处理后的文本
     *
     * @param input
     * @return
     */
    public Map<String, String> coreferenceResolution(String input) {

        Map<String, String> result = new HashMap<>();

        if (StringUtils.isNotBlank(input)) {

            Annotation document = new Annotation(input);
            this.pipeline.annotate(document);

            // 获取按行切分后的句子
            StringBuilder sb = new StringBuilder();  // 存放分割但未指代消解的句子
            List<CoreMap> sentences = document.get(SentencesAnnotation.class);
            List<String> strSentences = new ArrayList<String>();
            for (CoreMap sentence : sentences) {
                String sent = sentence.toString().replaceAll("\n", " ").replaceAll("\r\n", " ").replaceAll("\\s+", " ");
                sb.append(sent + LINE_SPLITER);
                strSentences.add(sent);
            }

            // 封装句子切分之后的内容
            result.put(KEY_SEG_TEXT, ZelusUtils.cutLastLineSpliter(sb.toString()));

            /*
             * 获取输入文本中的指代链，并执行指代消解
             */
            final Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);

            final Set<Map.Entry<Integer, CorefChain>> set = graph.entrySet();

            for (final Iterator<Map.Entry<Integer, CorefChain>> it = set.iterator(); it.hasNext(); ) {

                final Map.Entry<Integer, CorefChain> entry = it.next();

                if (entry.getValue().getMentionsInTextualOrder().size() > 1) {

                    String sortestPhrase = null; // 记录一个指代链中最短的非指示代词短语

                    final List<CorefMention> pronounWords = new ArrayList<CorefMention>(); // 存放当前指代链中所有的指示代词

                    for (int k = 0; k < entry.getValue().getMentionsInTextualOrder().size(); k++) {

                        final CorefMention mention = entry.getValue().getMentionsInTextualOrder().get(k);

                        if (mention != null) {

                            final String currentWord = mention.mentionSpan;

                            if (!EXCEPTED_DEMONSTRACTIVE_PRONOUN.contains(currentWord.toLowerCase())) {
                                if (sortestPhrase == null || currentWord.length() < sortestPhrase.length()) {
                                    sortestPhrase = currentWord;
                                }
                            }

                            if (DEMONSTRACTIVE_PRONOUN.contains(currentWord.toLowerCase())) {
                                pronounWords.add(mention);
                            }

                        }

                    }

                    if (pronounWords.size() > 0 && sortestPhrase != null) {

                        // 利用指示代词所指短语对原句中的指示代词进行替换
                        for (final CorefMention pronounWord : pronounWords) {
                            final String regex = "^[^\\w]*?" + pronounWord.mentionSpan + "[^\\w]*?$";
                            final Pattern pattern = Pattern.compile(regex);
                            final String[] wordsInSentence = strSentences.get(pronounWord.sentNum - 1).split("\\s+");
                            final StringBuilder replacedSentence = new StringBuilder();
                            for (int i = 0; i < wordsInSentence.length; ++i) {
                                if (pattern.matcher(wordsInSentence[i]).find()) {
                                    final String r_word = wordsInSentence[i].replace(pronounWord.mentionSpan,
                                            sortestPhrase.toLowerCase());
                                    replacedSentence.append(r_word + " ");
                                } else {
                                    replacedSentence.append(wordsInSentence[i] + " ");
                                }
                            }
                            // 用指代消解之后的句子替换原来的句子
                            strSentences.set(pronounWord.sentNum - 1, replacedSentence.toString().trim());
                            // System.out.println(pronounWord.mentionSpan + "("
                            // + pronounWord.sentNum + ")" + " -> " +
                            // sortestPhrase);
                        }
                    }
                }
            }
            final StringBuilder text = new StringBuilder();
            for (final String sentence : strSentences) {
                text.append(sentence + LINE_SPLITER);
            }
            // 封装指代消极的结果内容
            result.put(KEY_CR_TEXT, ZelusUtils.cutLastLineSpliter(text.toString()));
        }
        return result;
    }

    /**
     * 为口哨算法执行的预处理，主要是针对现有算法不支持浮点数据的预处理
     *
     * @throws IOException
     */
    public void pretreatment4ChineseWHispers(String edgeDir) throws IOException {
        final File dirFile = new File(edgeDir);
        final String[] filenames = dirFile.list(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith(".edge")) {
                    return true;
                }
                return false;
            }
        });
        final DecimalFormat df = new DecimalFormat("#0.000000"); // 定义浮点数格式化器
        for (final String filename : filenames) {
            final StringBuilder edgeInfos = new StringBuilder();
            BufferedReader br = null;
            try {
                br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(edgeDir + "/" + filename), DEFAULT_CHARSET));
                this.log.info("Pretreating for chinese whispers:" + edgeDir + "/" + filename);
                String line = null;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!"".equals(line)) {
                        final String[] infos = line.split("\\s+");
                        // 对相似度浮点数进行格式化，按照一定精度转化成整型
                        final int approx_int = (int) (Float.parseFloat(df.format(Double.parseDouble(infos[2]))) * 1000000);
                        edgeInfos.append(infos[0] + "\t" + infos[1] + "\t" + approx_int + LINE_SPLITER);
                    }
                }
                if (edgeInfos.length() > 0) {
                    final String edgeInfosStr = ZelusUtils.cutLastLineSpliter(edgeInfos.toString());
                    FileLoader.write(edgeDir + "/" + DIR_CW_PRETREAT + "/" + filename, edgeInfosStr, DEFAULT_CHARSET);
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
        }
    }

    /**
     * 测试驱动类
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final Pretreatment pret = new Pretreatment();
        final String text = FileLoader.read("F:/test/text/D0745J/APW19981217.0770", Charset.forName("UTF-8"));
        System.out.println(pret.coreferenceResolution(text));
    }

}
