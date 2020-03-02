package edu.whu.cs.nlp.mts.clustering;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.loader.FileLoader;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.base.utils.Encipher;
import edu.whu.cs.nlp.mts.base.utils.WordNetUtil;

/**
 * 句子抽取操作线程
 * @author Apache_xiaochao
 *
 */
@Deprecated
public class SentenceExtractThread implements Callable<Boolean>, GlobalConstant{

    private final Logger log = Logger.getLogger(this.getClass());

    private final String clusterResultDir;  //聚类结果所在路径
    private final String filename_cluster_read;  //主题聚类之后的结果文件名
    private final String extractedSentencesSaveDir;  //操作结果存放文件
    private final String textDir;   //语料正文所在路径
    private final LexicalizedParser lp;  //依存分析模型
    private IDictionary dict;  //wordnet词典对象

    public SentenceExtractThread(String resultDir,
            String filename_cluster_read, String extractedSentencesSaveDir,
            String textDir, LexicalizedParser lp, String dictPath) {
        super();
        this.clusterResultDir = resultDir;
        this.filename_cluster_read = filename_cluster_read;
        this.extractedSentencesSaveDir = extractedSentencesSaveDir;
        this.textDir = textDir;
        this.lp = lp;
        try {
            this.dict = WordNetUtil.openDictionary(dictPath);
        } catch (final IOException e) {
            this.log.error("打开WordNet失败！", e);
            //e.printStackTrace();
        }
    }

    /**
     * 加载聚类结果，key为当前类别下的事件个数， value为当前类别下的事件列表，按照key有大到小进行排序
     *
     * @return
     * @throws IOException
     */
    private Map<Integer, List<String>> loadClusterResult(String cluseterFilePath) throws IOException {
        final Map<Integer, List<String>> map = new TreeMap<Integer, List<String>>(
                new Comparator<Integer>() {

                    @Override
                    public int compare(Integer key_1, Integer key_2) {
                        return key_2 - key_1;
                    }

                });
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(cluseterFilePath), DEFAULT_CHARSET));
            String line = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                final String[] strs = line.split("\t");
                final int key = Integer.parseInt(strs[1]); // 获取当前类别中事件的数目
                final List<String> events = new ArrayList<String>();
                for (final String event : strs[2].split(",\\s+")) {
                    events.add(event);
                }
                map.put(key, events);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return map;
    }

    /**
     * 返回一个s节点下面的完整子句
     * @param tree
     * @param sb
     */
    private void subSentence(Tree tree, StringBuilder sb){
        if(tree.isLeaf()){
            sb.append(tree.nodeString() + " ");
            return;
        }else{
            final List<Tree> childTrees = tree.getChildrenAsList();
            for (final Tree child : childTrees) {
                this.subSentence(child, sb);
            }
        }
    }

    /**
     * 获取一句话中的所有子句集合
     * @param tree
     * @param subSentList
     */
    private void subSentences(Tree tree, List<String> subSentList){
        if(tree.label().toString().equals("S") || tree.label().toString().equals("SINV")){
            final StringBuilder sb = new StringBuilder();
            this.subSentence(tree, sb);
            final String strTmp = sb.toString().trim();
            if(!"".equals(strTmp)){
                //System.out.println("%%\t" + strTmp);
                subSentList.add(strTmp);
            }
        }

        final List<Tree> childTrees = tree.getChildrenAsList();
        for (final Tree childTree : childTrees) {
            this.subSentences(childTree, subSentList);
        }
    }

    /**
     * 采用句法分析树来得到当前事件对应的子句
     * @param event
     * @param textsMap
     * @param detailTextsMap
     * @param topicName
     * @param lp
     * @return
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    private List<Word> event2SubSentence(
            String event, Map<String, List<String>> textsMap,
            Map<String, List<List<Word>>> detailTextsMap, String topicName, LexicalizedParser lp)
                    throws NoSuchAlgorithmException, UnsupportedEncodingException{
        List<Word> subSentence = null;
        if(event != null){
            final String regex_filename = "\\[\\$[\\w\\.]*?\\$\\]";
            final Pattern p_filename = Pattern.compile(regex_filename);
            // 获取当前事件所在的文件名
            final Matcher matcher = p_filename.matcher(event);
            if (matcher.find()) {
                final String str = matcher.group();
                final String filename = str.substring(2, str.length() - 2);
                event = event.replaceAll(regex_filename, "");  //删除当前事件中的所属文件名信息
                final String[] words = event.split(WORD_CONNECTOR_IN_EVENTS);
                Word leftWord = null, middleWord = null, rightWord = null;
                // 分三种情况来对事件进行封装
                if (words.length == 3 && !event.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                    leftWord = CommonUtil.str2Word(words[0]);
                    middleWord = CommonUtil.str2Word(words[1]);
                    rightWord = CommonUtil.str2Word(words[2]);
                } else if (words.length == 2 || event.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                    if (event.startsWith(WORD_CONNECTOR_IN_EVENTS)) {
                        middleWord = CommonUtil.str2Word(words[1]);
                        rightWord = CommonUtil.str2Word(words[2]);
                    } else {
                        leftWord = CommonUtil.str2Word(words[0]);
                        middleWord = CommonUtil.str2Word(words[1]);
                    }
                } else {
                    this.log.error("当前事件类型不支持");
                }

                //从缓存获取当前是在所在文件的正文内容，如果不存在则加载，并存入缓存
                List<String> textList = null;
                List<List<Word>> detailTextList = null;  //更加详细的文本句子集合
                final String key = Encipher.MD5(topicName + filename);
                textList = textsMap.get(key);
                detailTextList = detailTextsMap.get(key);
                if(textList == null){
                    //加载正文
                    try {
                        textList = CommonUtil.str2List(
                                FileLoader.read(this.textDir + "/" + topicName + "/" + DIR_SEG_TEXT + "/" + filename, DEFAULT_CHARSET));
                    } catch (final IOException e) {
                        this.log.error("加载文件失败：" + this.textDir + "/" + topicName + "/" + DIR_SEG_TEXT + "/" + filename, e);
                        //e.printStackTrace();
                    }
                }
                if(detailTextList == null){
                    try {
                        detailTextList =
                                FileLoader.loadText(this.textDir + "/" + topicName + "/" + DIR_SEGDETAIL_TEXT + "/" + filename, DEFAULT_CHARSET);
                    } catch (final IOException e) {
                        this.log.error("加载文件失败：" + this.textDir + "/" + topicName + "/" + DIR_SEGDETAIL_TEXT + "/" + filename, e);
                        //e.printStackTrace();
                    }
                }

                if(textList != null && detailTextList != null){
                    try{
                        //缓存当前正文类容
                        textsMap.put(key, textList);
                        detailTextsMap.put(key, detailTextList);

                        ////获取当前事件所对应的句子
                        final String sentence = textList.get(middleWord.getSentenceNum() - 1);
                        final List<Word> sentenceDetail = detailTextList.get(middleWord.getSentenceNum() - 1);
                        //按照句法树来提取当前事件对应的子句
                        final TreebankLanguagePack tlp = lp.getOp().langpack();
                        final Tokenizer<? extends HasWord> toke =
                                tlp.getTokenizerFactory().getTokenizer(new StringReader(sentence));
                        final List<? extends HasWord> tokeList = toke.tokenize();
                        final Tree parse = lp.parse(tokeList);
                        final List<String> subSentList = new ArrayList<String>();
                        this.subSentences(parse, subSentList);  //获取当前句子所有的子句集合
                        int i = subSentList.size() - 1;
                        String subSentenceStr = null;
                        for (; i >= 0; --i) {
                            final String sentStr = subSentList.get(i);
                            int count = 0;
                            final Set<String> wordSet = new HashSet<String>(Arrays.asList(sentStr.split("\\s+")));
                            if(leftWord != null && wordSet.contains(leftWord.getName())){
                                ++count;
                            }
                            if(middleWord != null && wordSet.contains(middleWord.getName())){
                                ++count;
                            }
                            if(rightWord != null && wordSet.contains(rightWord.getName())){
                                ++count;
                            }
                            if(count >= 2){
                                subSentenceStr = sentStr;
                                break;
                            }
                        }
                        if(subSentenceStr == null){
                            this.log.error("无法映射事件对应的子句:\n左词：" + (leftWord == null ? "" : leftWord.getName()) +
                                    "\n中词：" + (middleWord == null ? "" : middleWord.getName()) +
                                    "\n右词：" + (rightWord == null ? "" : rightWord.getName()) +
                                    "\n文件路径：" + this.textDir + "/" + topicName + "/" + DIR_SEG_TEXT + "/" + filename);
                            final StringBuilder tmp = new StringBuilder();
                            for (final String subSent : subSentList) {
                                tmp.append(subSent + LINE_SPLITER);
                            }
                            this.log.error(CommonUtil.cutLastLineSpliter(tmp.toString()) + "\n" +
                                    event + "\t" + (middleWord.getSentenceNum() - 1) + "\t" + sentence);
                        }else{
                            //对当前子句进行对象化
                            subSentence = new ArrayList<Word>();
                            final String[] wordsInSubSent = subSentenceStr.split("\\s+");
                            int num = 0;
                            for (final Word word: sentenceDetail) {
                                if(word.getName().equals(wordsInSubSent[num])){
                                    subSentence.add((Word) word.clone());
                                    ++num;
                                    if(subSentence.size() == wordsInSubSent.length){
                                        break;
                                    }
                                }else{
                                    //清除
                                    subSentence.clear();
                                    num = 0;
                                }
                            }
                            if(subSentence.size() == 0){
                                this.log.error("句子替换出错：" + subSentenceStr + "(原句)");
                            }
                        }
                    }catch(final Exception e){
                        this.log.error("句子所在行数越界或MD5算法不支持:" + event, e);
                        //e.printStackTrace();
                    }
                }

            }
        }
        return subSentence;
    }

    /*
     * 将词集合转化成句子
     */
    private String words2Sentence(List<Word> subSentence){
        String sentence = "";
        if(subSentence != null && subSentence.size() > 0){
            final StringBuilder sb = new StringBuilder();
            for (final Word word : subSentence) {
                sb.append(word.getName() + " ");
            }
            sentence = sb.toString().trim();
        }
        return sentence;
    }

    @Override
    public Boolean call() throws Exception {
        boolean success = false;
        this.log.info(Thread.currentThread().getId() + "正在抽取句子：" + this.clusterResultDir + "/" + this.filename_cluster_read);
        try {
            //缓存每一个文件对应的正文文本，正文按行组织存放，key命名规则：专题名_文件名
            final Map<String, List<String>> textsMap = new HashMap<String, List<String>>();
            final Map<String, List<List<Word>>> detailTextMap = new HashMap<String, List<List<Word>>>();
            final Set<String> selectedSynonymsWords = new HashSet<String>(512);  //已经选中的同义词集合

            // 将一个主题下面的事件聚类结果按照类的大小从大到小进行排序
            final Map<Integer, List<String>> sortedClusterResult =
                    this.loadClusterResult(this.clusterResultDir + "/" + this.filename_cluster_read);  //当前类别下的事件集合

            final StringBuilder extractedSentenceLemmaGroupByCluster = new StringBuilder();
            final StringBuilder extractedSentenceGroupByCluster = new StringBuilder();
            final StringBuilder taggesSentenceGroupByCluster = new StringBuilder();
            int num = 0;
            //依次对当前主题下每个类别进行处理
            for (final Entry<Integer, List<String>> entry : sortedClusterResult.entrySet()) {
                final Set<String> selectedSentTag = new HashSet<String>();  //存放已经选取的子句的md5标签
                final List<String> sentencesLemmaInClass = new ArrayList<String>(); //存放当前类别下抽取出来的所有子句
                final List<String> sentencesInClass = new ArrayList<String>();  //存放当前类别下抽取出来的经过同义词替换后的所有子句
                for (final String event : entry.getValue()) {
                    //获取当前事件对应的子句
                    final String topicName = this.filename_cluster_read.replace(".read", "");
                    final List<Word> subSentence = this.event2SubSentence(event, textsMap, detailTextMap, topicName, this.lp);
                    if(subSentence != null && subSentence.size() > 0){
                        //记录同义词替换之前的子句
                        final String subSentStrPre = this.words2Sentence(subSentence);
                        //对当前句子进行同义词替换
                        for(int i = 0; i < subSentence.size(); ++i){
                            final Word word = subSentence.get(i);
                            // FIXME 下面这行注释掉，用于通过maven编译2015-11-11 19:26:01
                            //subSentence.set(i, WordNetUtil.getSynonyms(this.dict, word, selectedSynonymsWords));
                        }
                        final String subSentStrAfter = this.words2Sentence(subSentence);
                        try {
                            //计算当前子句的md5值
                            final String sentMd5Val = Encipher.MD5(subSentStrPre);
                            //保证一个类别中同一个子句只出现一次
                            if(!selectedSentTag.contains(sentMd5Val) && !"".equals(subSentStrAfter)){
                                sentencesInClass.add(subSentStrAfter);
                                sentencesLemmaInClass.add(subSentStrPre);
                                selectedSentTag.add(sentMd5Val);
                            }
                        } catch (final NoSuchAlgorithmException e) {
                            this.log.error("找不到对应算法！", e);
                            //e.printStackTrace();
                        }
                    }else{
                        //System.out.println("找不到对应子句，事件：" + event);
                        this.log.error("找不到对应子句，事件：" + event);
                    }
                }

                //当前类别下的句子数量达到一个阈值后才能进入压缩
                if(sentencesInClass.size() >= MIN_SENTENCE_COUNT_FOR_COMPRESS){
                    extractedSentenceLemmaGroupByCluster.append("classes_" + num	+ ":" + LINE_SPLITER);
                    extractedSentenceGroupByCluster.append("classes_" + num	+ ":" + LINE_SPLITER);
                    taggesSentenceGroupByCluster.append("classes_" + num + ":" + LINE_SPLITER);
                    for (final String sentence : sentencesInClass) {
                        extractedSentenceGroupByCluster.append(sentence + LINE_SPLITER);
                        // 对句子中的词进行词性标注
                        final List<String> tagges = OpenNlpPOSTagger.tagger(sentence);
                        final StringBuilder taggedSent = new StringBuilder();
                        for (final String tag : tagges) {
                            taggedSent.append(tag + " ");
                        }
                        taggesSentenceGroupByCluster.append(taggedSent.toString().trim() + LINE_SPLITER);
                    }
                    //对未经过同义词替换的句子也保存一份
                    for (final String sentenceLemma : sentencesLemmaInClass) {
                        extractedSentenceLemmaGroupByCluster.append(sentenceLemma + LINE_SPLITER);
                    }
                }

                ++num;
            }
            final String filename = this.filename_cluster_read.replace(".read", ".sentences");
            if(extractedSentenceLemmaGroupByCluster.length() > 0){
                final String eslgbc = CommonUtil.cutLastLineSpliter(extractedSentenceLemmaGroupByCluster.toString());
                FileLoader.write(this.extractedSentencesSaveDir + "/lemma/" + filename, eslgbc, DEFAULT_CHARSET);
            }
            if (extractedSentenceGroupByCluster.length() > 0) {
                final String esgbc = CommonUtil.cutLastLineSpliter(extractedSentenceGroupByCluster.toString());
                FileLoader.write(
                        this.extractedSentencesSaveDir + "/" + filename, esgbc, DEFAULT_CHARSET);
            }
            if (taggesSentenceGroupByCluster.length() > 0) {
                final String tsgbc = CommonUtil.cutLastLineSpliter(taggesSentenceGroupByCluster.toString());
                FileLoader.write(this.extractedSentencesSaveDir + "/" + DIR_TAGGED + "/" + filename, tsgbc, DEFAULT_CHARSET);
            }
            success = true;
        } catch (IOException | NoSuchAlgorithmException e) {
            this.log.error("加载 " + this.clusterResultDir + "/" + this.filename_cluster_read + " 失败...", e);
            //e.printStackTrace();
        }
        return success;
    }

}
