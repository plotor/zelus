package edu.whu.cs.nlp.mts.extraction.graph;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.trees.Tree;
import edu.whu.cs.nlp.mts.base.domain.ChunkPhrase;
import edu.whu.cs.nlp.mts.base.domain.CoreferenceElement;
import edu.whu.cs.nlp.mts.base.domain.Event;
import edu.whu.cs.nlp.mts.base.domain.EventType;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.EventWithWord;
import edu.whu.cs.nlp.mts.base.domain.ParseItem;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.global.GlobalParam;
import edu.whu.cs.nlp.mts.base.loader.ModelLoader;
import edu.whu.cs.nlp.mts.base.nlp.StanfordNLPTools;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.base.utils.Encipher;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.Span;

/**
 * 基于依存关系来构建词图，在词图的基础上基于规则进行事件抽取<br>
 * 规则如下：<br>
 * 1.先进行事件抽取，再进行指代消解
 *
 * @version 2.0
 * @author ZhenchaoWang 2015-10-26 19:38:22
 *
 */
public class EventsExtractBasedOnGraphV2 implements GlobalConstant, Callable<Boolean> {

    private final Logger log = Logger.getLogger(this.getClass());

    /** 输入文件所在目录 */
    private final String topicDir;

    /** 专题名 */
    private final String topicName;

    /**词向量获取器*/
    private final EhCacheUtil ehCacheUtil;

    /**
     * 构造函数
     *
     * @param textDir
     *            输入文件目录
     */
    public EventsExtractBasedOnGraphV2(String topicDir, EhCacheUtil ehCacheUtil) {
        this.topicDir = topicDir;
        this.topicName = this.topicDir.substring(Math.max(this.topicDir.lastIndexOf("\\"), this.topicDir.lastIndexOf("/")));
        this.ehCacheUtil = ehCacheUtil;
    }

    @Override
    public Boolean call() throws Exception {

        this.log.info(Thread.currentThread().getId() + " topic name：" + this.topicDir);

        /* 一个topic下所有词的向量字典 */
        Map<String, Vector> wordvecsInTopic = new HashMap<String, Vector>();

        // 获取指定文件夹下的所有文件
        Collection<File> files = FileUtils.listFiles(FileUtils.getFile(this.topicDir), null, false);
        for (File file : files) {

            // 加载文件
            String absolutePath = file.getAbsolutePath(); // 当前处理的文件的绝对路径
            /*String parentPath = file.getParentFile().getAbsolutePath(); // 文件所属文件夹的路径
*/
            this.log.info(Thread.currentThread().getId() + "正在操作文件：" + absolutePath);

            try {
                // 加载正文
                String text = FileUtils.readFileToString(file, DEFAULT_CHARSET);

                // 利用stanford的nlp核心工具进行处理
                Map<String, Object> coreNlpResults = StanfordNLPTools.coreOperate(text);

                List<Tree> syntacticTrees = (List<Tree>) coreNlpResults.get(StanfordNLPTools.KEY_SYNTACTICTREES);
                File treeFile = new File(GlobalParam.workDir + "/" + OBJ + "/" + DIR_SYNTACTICTREES_OBJ, file.getName() + ".obj");
                try {
                    SerializeUtil.writeObj(syntacticTrees, treeFile);
                } catch (IOException e) {
                    this.log.error("Serialize file error:" + treeFile.getAbsolutePath(), e);
                    throw e;
                }

                // 获取对句子中单词进行对象化后的文本
                List<List<Word>> words = (List<List<Word>>) coreNlpResults.get(StanfordNLPTools.KEY_WORDS);

                // 序列化词集合
                File wordsFile = FileUtils.getFile(parentPath + "/" + OBJ + "/" + DIR_WORDS_OBJ + "/", file.getName() + ".obj");
                try {
                    SerializeUtil.writeObj(words, wordsFile);
                } catch (IOException e) {
                    this.log.error("Serialize file error:" + wordsFile.getAbsolutePath(), e);
                    throw e;
                }

                /*
                 * 构建当前主题下每个词的词向量字典
                 */
                for (List<Word> wordsInSent : words) {
                    for (Word word : wordsInSent) {
                        String key = word.dictKey();
                        if(wordvecsInTopic.containsKey(key)) {
                            continue;
                        }
                        // 获取当前词的词向量
                        Vector vector = this.ehCacheUtil.getMostSimilarVec(word);
                        if(null != vector) {
                            wordvecsInTopic.put(key, vector);
                        }
                    }
                }

                // 以文本形式存储词集合
                StringBuilder sb_words_pos = new StringBuilder();
                for (List<Word> list : words) {
                    StringBuilder sb_words = new StringBuilder();
                    StringBuilder sb_pos = new StringBuilder();
                    for (Word word : list) {
                        sb_words.append(word.getName() + " ");
                        sb_pos.append(word.getPos() + " ");
                    }
                    sb_words_pos.append(sb_words.toString().trim() + LINE_SPLITER);
                    sb_words_pos.append(sb_pos.toString().trim() + LINE_SPLITER);
                }
                /* 中间结果记录：词和词性分开按行存储 */
                FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_SEGDETAIL_TEXT + "/pos2/", file.getName()), CommonUtil.cutLastLineSpliter(sb_words_pos.toString()), DEFAULT_CHARSET);

                // 获取依存分析结果
                @SuppressWarnings("unchecked")
                List<List<ParseItem>> parseItemList = (List<List<ParseItem>>) coreNlpResults.get(StanfordNLPTools.KEY_PARSED_ITEMS);
                // 序列化依存分析对
                File parseItemFile = FileUtils.getFile(parentPath + "/" + OBJ + "/" + DIR_PARSE_TEXT, file.getName() + ".obj");
                try {
                    SerializeUtil.writeObj(words, parseItemFile);
                } catch (IOException e) {
                    this.log.error("Serialize file error:" + parseItemFile.getAbsolutePath(), e);
                    throw e;
                }
                // 以文本形式存储依存分析对
                FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_PARSE_TEXT, file.getName()), CommonUtil.lists2String(parseItemList), DEFAULT_CHARSET);

                /* 中间结果记录：记录依存分析简版结果 */
                StringBuilder simplifyParsedResult = new StringBuilder();
                for (List<ParseItem> parseItems : parseItemList) {
                    for (ParseItem parseItem : parseItems)
                        simplifyParsedResult.append(parseItem.toShortString() + "\t");
                    simplifyParsedResult.append(LINE_SPLITER);
                }
                FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_PARSESIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(simplifyParsedResult.toString()), DEFAULT_CHARSET);

                // 对当前文本进行事件抽取
                Map<Integer, List<EventWithWord>> events = this.extract(parseItemList, words, file.getName());
                /* 中间结果记录：保存事件抽取结果 */
                StringBuilder sb_events = new StringBuilder();
                StringBuilder sb_simplify_events = new StringBuilder();
                for (Entry<Integer, List<EventWithWord>> entry : events.entrySet()) {
                    String eventsInSentence = CommonUtil.list2String(entry.getValue());
                    sb_events.append(entry.getKey() + "\t" + eventsInSentence + LINE_SPLITER);
                    sb_simplify_events.append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                }
                FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_EVENTS, file.getName()), CommonUtil.cutLastLineSpliter(sb_events.toString()), DEFAULT_CHARSET);
                FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_EVENTSSIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(sb_simplify_events.toString()), DEFAULT_CHARSET);

                /**
                 * 指代消解
                 */
                Map<String, CoreferenceElement> crChains = this.cr((Map<Integer, CorefChain>) coreNlpResults.get(StanfordNLPTools.KEY_COREFCHAIN_GRAPH));
                /* 存放指代消解之后的事件，按行组织 */
                Map<Integer, List<EventWithPhrase>> eventsAfterCR = new TreeMap<Integer, List<EventWithPhrase>>();
                try {
                    for (Entry<Integer, List<EventWithWord>> entry : events.entrySet()) {
                        Integer sentNum = entry.getKey();
                        List<EventWithWord> eventsInSentence = entry.getValue();
                        List<EventWithPhrase> eventWithPhraseInSentence = new ArrayList<EventWithPhrase>();
                        for (EventWithWord event : eventsInSentence) {
                            Word leftWord = event.getLeftWord();
                            Word negWord = event.getNegWord();
                            Word middleWord = event.getMiddleWord();
                            Word rightWord = event.getRightWord();
                            List<Word> leftPhrase = this.changeToCorefWord(leftWord, crChains, words);
                            if (CollectionUtils.isEmpty(leftPhrase) && leftWord != null) {
                                leftPhrase.add(leftWord);
                            }
                            List<Word> rightPhrase = this.changeToCorefWord(rightWord, crChains, words);
                            if (CollectionUtils.isEmpty(rightPhrase) && rightWord != null) {
                                rightPhrase.add(rightWord);
                            }
                            List<Word> middlePhrase = new ArrayList<Word>();
                            if (negWord != null) {
                                middlePhrase.add(negWord);
                            }
                            middlePhrase.add(middleWord);
                            eventWithPhraseInSentence.add(new EventWithPhrase(leftPhrase, middlePhrase, rightPhrase, middleWord.getSentenceNum(), event.getFilename()));
                        }
                        eventsAfterCR.put(sentNum, eventWithPhraseInSentence);
                    }

                    /* 中间结果记录：保存指代消解之后的事件 */
                    StringBuilder sb_events_phrase = new StringBuilder();
                    StringBuilder sb_simplify_events_phrase = new StringBuilder();
                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCR.entrySet()) {
                        sb_events_phrase.append(entry.getKey() + "\t" + CommonUtil.list2String(entry.getValue()) + LINE_SPLITER);
                        sb_simplify_events_phrase.append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                    }
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_EVENTS, file.getName()), CommonUtil.cutLastLineSpliter(sb_events_phrase.toString()), DEFAULT_CHARSET);
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_EVENTSSIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(sb_simplify_events_phrase.toString()), DEFAULT_CHARSET);

                } catch (Throwable e) {

                    this.log.error("coreference resolution error", e);
                    throw e;

                }

                /**
                 * 事件修复
                 */
                /* 经过指代消解和事件修复之后的事件 */
                Map<Integer, List<EventWithPhrase>> eventsAfterCRAndRP = new TreeMap<Integer, List<EventWithPhrase>>();
                try {

                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCR.entrySet()) {
                        eventsAfterCRAndRP.put(entry.getKey(), this.eventRepair(entry.getValue(), words.get(entry.getKey() - 1), entry.getKey()));
                    }

                    /* 中间结果记录：保存经过短语扩充之后的事件 */
                    StringBuilder sb_events_phrase = new StringBuilder();
                    StringBuilder sb_simplify_events_phrase = new StringBuilder();
                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCRAndRP.entrySet()) {
                        sb_events_phrase.append(entry.getKey() + "\t" + CommonUtil.list2String(entry.getValue()) + LINE_SPLITER);
                        sb_simplify_events_phrase.append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                    }
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_EVENTS, file.getName()), CommonUtil.cutLastLineSpliter(sb_events_phrase.toString()), DEFAULT_CHARSET);
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_EVENTSSIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(sb_simplify_events_phrase.toString()), DEFAULT_CHARSET);

                } catch (Throwable e) {

                    this.log.error("repair event error!", e);

                }

                /**
                 * 短语扩充
                 */
                /* 经过指代消解、事件修复、短语扩充之后的事件 */
                Map<Integer, List<EventWithPhrase>> eventsAfterCRAndRPAndPE = new TreeMap<Integer, List<EventWithPhrase>>();
                try {

                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCRAndRP.entrySet()) {
                        Integer sentNum = entry.getKey();
                        List<EventWithPhrase> eventWithPhrases = this.phraseExpansion(entry.getValue(), words.get(sentNum - 1));
                        eventsAfterCRAndRPAndPE.put(sentNum, eventWithPhrases);
                    }

                    /* 中间结果记录：保存经过短语扩充之后的事件 */
                    StringBuilder sb_events_phrase = new StringBuilder();
                    StringBuilder sb_simplify_events_phrase = new StringBuilder();
                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCRAndRPAndPE.entrySet()) {
                        sb_events_phrase.append(entry.getKey() + "\t" + CommonUtil.list2String(entry.getValue()) + LINE_SPLITER);
                        sb_simplify_events_phrase.append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                    }
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_PE_EVENTS, file.getName()), CommonUtil.cutLastLineSpliter(sb_events_phrase.toString()), DEFAULT_CHARSET);
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_PE_EVENTSSIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(sb_simplify_events_phrase.toString()), DEFAULT_CHARSET);

                } catch (Throwable e) {

                    this.log.error("exspand word to phrase error!", e);

                }

                /**
                 * 对事件进行过滤
                 */
                Map<Integer, List<EventWithPhrase>> eventsAfterCRAndRPAndPEAndEF = new TreeMap<Integer, List<EventWithPhrase>>();
                try {
                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCRAndRPAndPE.entrySet()) {
                        List<EventWithPhrase> eventsInSentence = new ArrayList<EventWithPhrase>();
                        for (EventWithPhrase event : entry.getValue()) {
                            EventWithPhrase filtEvent = this.eventFilter(event);
                            if (null != filtEvent) {
                                eventsInSentence.add(filtEvent);
                            }
                        }
                        eventsAfterCRAndRPAndPEAndEF.put(entry.getKey(), eventsInSentence);
                    }

                    /* 中间结果记录：保存经过事件过滤之后的事件 */
                    StringBuilder sb_events_phrase = new StringBuilder();
                    StringBuilder sb_simplify_events_phrase = new StringBuilder();
                    for (Entry<Integer, List<EventWithPhrase>> entry : eventsAfterCRAndRPAndPEAndEF.entrySet()) {
                        sb_events_phrase.append(entry.getKey() + "\t" + CommonUtil.list2String(entry.getValue()) + LINE_SPLITER);
                        sb_simplify_events_phrase.append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                    }
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_PE_EF_EVENTS, file.getName()), CommonUtil.cutLastLineSpliter(sb_events_phrase.toString()), DEFAULT_CHARSET);
                    FileUtils.writeStringToFile(FileUtils.getFile(parentPath + "/" + TEXT + "/" + DIR_CR_RP_PE_EF_EVENTSSIMPLIFY, file.getName()), CommonUtil.cutLastLineSpliter(sb_simplify_events_phrase.toString()), DEFAULT_CHARSET);

                    if(MapUtils.isNotEmpty(eventsAfterCRAndRPAndPEAndEF)) {
                        /*
                         * 序列化事件抽取结果
                         */
                        File eventsObjFile = FileUtils.getFile(this.workDir + "/" + DIR_SERIALIZE_EVENTS + "/" + this.topicName, file.getName() + SUFFIX_SERIALIZE_FILE);
                        try{
                            SerializeUtil.writeObj(eventsAfterCRAndRPAndPEAndEF, eventsObjFile.getAbsoluteFile());
                        }catch (IOException e) {
                            this.log.error("Seralize error:" + eventsObjFile.getAbsolutePath(), e);
                        }

                    }

                } catch (Throwable e) {

                    this.log.error("events filt error!", e);

                    throw e;

                }

            } catch (Throwable e) {

                this.log.error("events extract error：" + file.getAbsolutePath(), e);

            }
        }

        if(MapUtils.isNotEmpty(wordvecsInTopic)) {
            /*
             * 序列化词向量字典
             */
            File wordvecFile = FileUtils.getFile(this.workDir + "/" + DIR_WORDS_VECTOR, this.topicName + ".obj");
            try{

                SerializeUtil.writeObj(wordvecsInTopic, wordvecFile);

            } catch(IOException e) {

                this.log.error("Serialize word vector file[" + wordvecFile.getAbsolutePath() + "] error!", e);
                throw e;
            }
        }

        return true;

    }

    /**
     * 将输入单词转换成相应的指代的词
     *
     * @param inWord
     * @param crChains
     * @param words
     * @return
     */
    private List<Word> changeToCorefWord(Word inWord, Map<String, CoreferenceElement> crChains, List<List<Word>> words) {

        List<Word> phrase = new ArrayList<Word>();

        if (inWord == null || MapUtils.isEmpty(crChains)) {
            return phrase;
        }

        try {

            String key = Encipher.MD5(inWord.getName() + inWord.getSentenceNum() + inWord.getNumInLine() + (inWord.getNumInLine() + 1));
            CoreferenceElement corefElement = crChains.get(key);
            if (corefElement != null) {
                CoreferenceElement ref = corefElement.getRef();
                // System.out.println(key + "\t" + inWord.getName() + "\t" +
                // inWord.getSentenceNum() + "\t" + inWord.getNumInLine() + "\t"
                // + (inWord.getNumInLine() + 1) + "\t->\t" + ref.getElement());
                List<Word> wordsInSent = new ArrayList<Word>(words.get(ref.getSentNum() - 1));
                for (int i = ref.getStartIndex(); i < ref.getEndIndex(); i++) {
                    phrase.add(wordsInSent.get(i));
                }

            }

        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {

            this.log.error("MD5 encode error!", e);

        }

        return phrase;

    }

    /**
     * 构建依存关系词图，以依存关系作为边
     *
     * @param parseItems
     * @param wordsCount
     * @return
     */
    public String[][] buildWordGraph(List<ParseItem> parseItems, int wordsCount) {

        String[][] edges = new String[wordsCount][wordsCount]; // 存放图的边信息
        for (ParseItem parseItem : parseItems)
            edges[parseItem.getLeftWord().getNumInLine()][parseItem.getRightWord().getNumInLine()] = parseItem.getDependencyType();

        return edges;

    }

    /**
     * 对输入文本进行指代消解<br>
     * 以指代链中最先出现的指代作为被指代的词（短语)<br>
     * key:MD5(element + sentNum + startIndex + endIndex)
     *
     * @param graph
     *            指代链
     * @return
     */
    private Map<String, CoreferenceElement> cr(Map<Integer, CorefChain> graph) {

        Map<String, CoreferenceElement> result = new HashMap<String, CoreferenceElement>();
        if (MapUtils.isEmpty(graph)) {
            return result;
        }

        Set<Map.Entry<Integer, CorefChain>> set = graph.entrySet();
        for (Iterator<Map.Entry<Integer, CorefChain>> it = set.iterator(); it.hasNext();) {

            Map.Entry<Integer, CorefChain> entry = it.next();

            if (entry.getValue().getMentionsInTextualOrder().size() > 1) {

                CorefMention firstElement = entry.getValue().getMentionsInTextualOrder().get(0);
                /* 以第一个词（短语）作为被指代的词（短语） */
                CoreferenceElement ref = new CoreferenceElement(firstElement.mentionSpan, firstElement.corefClusterID, firstElement.startIndex, firstElement.endIndex, firstElement.sentNum, null);
                for (int k = 1; k < entry.getValue().getMentionsInTextualOrder().size(); k++) {

                    CorefMention mention = entry.getValue().getMentionsInTextualOrder().get(k);

                    if (mention != null) {
                        CoreferenceElement element = new CoreferenceElement(mention.mentionSpan, mention.corefClusterID, mention.startIndex, mention.endIndex, mention.sentNum, ref);
                        try {
                            result.put(Encipher.MD5(element.getElement() + element.getSentNum() + element.getStartIndex() + element.getEndIndex()), element);
                            // System.out.println(Encipher.MD5(element.getElement()
                            // + element.getSentNum() + element.getStartIndex()
                            // + element.getEndIndex()) + "\t" +
                            // element.getElement() + "\t" +
                            // element.getSentNum() + "\t" +
                            // element.getStartIndex() + "\t" +
                            // element.getEndIndex() + "\t->\t" +
                            // element.getRef().getElement());
                        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {

                            this.log.error("MD5 encode error!", e);

                        }
                    }

                }
            }
        }
        return result;
    }

    /**
     * 事件抽取函数<br>
     * 先构建词图，然后基于词图来进行事件抽取
     *
     * @param parsedList
     * @param words
     * @param filename
     * @return
     */
    public Map<Integer, List<EventWithWord>> extract(List<List<ParseItem>> parsedList, List<List<Word>> words, final String filename) {

        Map<Integer, List<EventWithWord>> events = new HashMap<Integer, List<EventWithWord>>();

        if (CollectionUtils.isNotEmpty(parsedList) && CollectionUtils.isNotEmpty(words))
            for (int k = 0; k < parsedList.size(); ++k) {
                /* 当前处理单位：句子 */

                List<ParseItem> parseItems = new ArrayList<ParseItem>(parsedList.get(k));
                List<Word> wordsInSentence = new ArrayList<Word>(words.get(k));
                int wordsCount = wordsInSentence.size();
                // 以依存关系为边构建当前句子的依存关系词图
                String[][] edges = this.buildWordGraph(parseItems, wordsCount);
                List<EventWithWord> eventsInSentence = new ArrayList<EventWithWord>(); // 用于存储从当前句子中抽取到的事件
                // 构建事件
                for (int i = 0; i < wordsCount; ++i) {

                    /* 当前处理单位：词 */

                    List<Integer> agents = new ArrayList<Integer>();
                    List<Integer> objects = new ArrayList<Integer>();
                    Word copWord = null; // cop关系
                    Word prepWord = null; // 前缀词
                    Word negWord = null; // 否定词

                    for (int j = 0; j < wordsCount; ++j) {

                        if (DEPENDENCY_AGENT.contains(edges[i][j]))
                            // 施事
                            agents.add(j);

                        if (DEPENDENCY_OBJECT.contains(edges[i][j]))
                            // 受事
                            objects.add(j);

                        // 缓存cop关系
                        if ("cop".equals(edges[i][j]))
                            copWord = wordsInSentence.get(j);

                        // 缓存prep关系
                        if ("prep".equals(edges[i][j]) || "prepc".equals(edges[i][j])) {
                            prepWord = wordsInSentence.get(j);
                            if (!POS_NOUN.contains(prepWord.getPos()) && "O".equals(prepWord.getNer()))
                                // 如果不是名词或命名实体，则过滤掉
                                prepWord = null;
                        }

                        // 缓存neg关系
                        if ("neg".equals(edges[i][j]))
                            negWord = wordsInSentence.get(j);

                    }

                    Word middleWord = wordsInSentence.get(i);

                    if (CollectionUtils.isNotEmpty(agents) && CollectionUtils.isNotEmpty(objects))
                        for (Integer agent : agents) {

                            Word leftWord = wordsInSentence.get(agent);

                            for (Integer object : objects) {

                                Word rightWord = wordsInSentence.get(object);

                                eventsInSentence.add(new EventWithWord(leftWord, negWord, middleWord, rightWord, filename));

                            }

                        }
                    else if (CollectionUtils.isNotEmpty(agents)) {
                        /**
                         * 宾语缺失
                         */

                        for (Integer agent : agents) {

                            Word leftWord = wordsInSentence.get(agent);

                            if (copWord != null) {
                                /**
                                 * 如果存在依存关系cop，则用cop关系将二元事件补全为三元事件
                                 */
                                eventsInSentence.add(new EventWithWord(leftWord, negWord, copWord, middleWord, filename));
                            } else {
                                /**
                                 * 不存在cop关系的词
                                 */
                                eventsInSentence.add(new EventWithWord(leftWord, negWord, middleWord, null, filename));
                            }
                        }

                    } else if (CollectionUtils.isNotEmpty(objects)) {
                        /**
                         * 主语缺失
                         */

                        for (Integer object : objects) {

                            Word rightWord = wordsInSentence.get(object);
                            if (prepWord != null) {
                                /**
                                 * 用前缀词做补全主语
                                 */
                                eventsInSentence.add(new EventWithWord(prepWord, negWord, middleWord, rightWord, filename));
                            } else {
                                /**
                                 * 不存在符合要求的前缀词
                                 */
                                eventsInSentence.add(new EventWithWord(null, negWord, middleWord, rightWord, filename));
                            }
                        }
                    }
                }
                events.put(k + 1, eventsInSentence);
            }
        return events;
    }

    /**
     * 将事件中的词扩充成短语<br>
     * 按行处理
     *
     * @param events
     *            由词构成的事件
     * @param words
     *            当前句子中的词语
     * @return
     * @throws Exception
     */
    private List<EventWithPhrase> phraseExpansion(List<EventWithPhrase> events, List<Word> words) throws Exception {
        List<EventWithPhrase> eventsInSentence = new ArrayList<EventWithPhrase>();
        if (CollectionUtils.isEmpty(events)) {
            return eventsInSentence;
        }
        try {

            /* 利用open nlp进行chunk */
            List<ChunkPhrase> phrases = this.chunk(words);

            /* chunk 中间结果记录 */
            StringBuilder sb_chunk = new StringBuilder();

            for (ChunkPhrase chunkPhrase : phrases) {

                StringBuilder sb_phrase = new StringBuilder();

                for (Word word : chunkPhrase.getWords()) {
                    sb_phrase.append(word.getName() + ",");
                }

                sb_chunk.append(sb_phrase.substring(0, sb_phrase.length() - 1) + " ");

            }

            FileUtils.writeStringToFile(FileUtils.getFile(this.topicDir + "/" + TEXT + "/" + DIR_CHUNKSIMPILY, events.get(0).getFilename()), sb_chunk.toString() + LINE_SPLITER, DEFAULT_CHARSET, true);

            for (EventWithPhrase eventWithPhrase : events) {
                // 谓语中第一个单词的序号
                int leftVerbIndex = eventWithPhrase.getMiddlePhrases().get(0).getNumInLine();
                // 谓语中第二个单词的序号
                int rightVerbIndex = eventWithPhrase.getMiddlePhrases().get(eventWithPhrase.getMiddlePhrases().size() - 1).getNumInLine();

                List<Word> leftPhrase = eventWithPhrase.getLeftPhrases();
                List<Word> rightPhrase = eventWithPhrase.getRightPhrases();

                if (leftPhrase.size() == 1 || rightPhrase.size() == 1) {
                    /**
                     * 只处理主语或宾语为单词的情形
                     */
                    if (leftPhrase.size() == 1) {
                        // 主语扩充
                        Word leftWord = leftPhrase.get(0);
                        for (ChunkPhrase phrase : phrases) {
                            if (leftWord.getNumInLine() >= phrase.getLeftIndex() && leftWord.getNumInLine() <= phrase.getRightIndex() && (phrase.getRightIndex() - phrase.getLeftIndex()) > 0) {
                                if (leftVerbIndex > phrase.getLeftIndex()) {
                                    leftPhrase = new ArrayList<Word>(words.subList(phrase.getLeftIndex(), Math.min(leftVerbIndex, phrase.getRightIndex() + 1)));
                                } else {
                                    leftPhrase = new ArrayList<Word>(words.subList(phrase.getLeftIndex(), phrase.getRightIndex() + 1));
                                }
                                break;
                            }
                        }
                    }
                    if (rightPhrase.size() == 1) {
                        // 宾语扩充
                        Word rightWord = rightPhrase.get(0);
                        for (ChunkPhrase phrase : phrases) {
                            if (rightWord.getNumInLine() >= phrase.getLeftIndex() && rightWord.getNumInLine() <= phrase.getRightIndex() && (phrase.getRightIndex() - phrase.getLeftIndex()) > 0) {
                                if (phrase.getRightIndex() > rightVerbIndex) {
                                    rightPhrase = new ArrayList<Word>(words.subList(Math.max(rightVerbIndex + 1, phrase.getLeftIndex()), phrase.getRightIndex() + 1));
                                } else {
                                    rightPhrase = new ArrayList<Word>(words.subList(phrase.getLeftIndex(), phrase.getRightIndex() + 1));
                                }
                                break;
                            }
                        }
                    }

                    eventsInSentence.add(new EventWithPhrase(leftPhrase, eventWithPhrase.getMiddlePhrases(), rightPhrase, eventWithPhrase.getSentNum(), eventWithPhrase.getFilename()));

                } else {

                    eventsInSentence.add(eventWithPhrase);

                }

            }

        } catch (IOException e) {
            this.log.error("Load chunk model error!", e);
            throw new Exception(e);
        }
        return eventsInSentence;
    }

    /**
     * 利用open nlp进行chunk，并添加一些修正规则
     *
     * @param words
     * @return
     * @throws IOException
     */
    private List<ChunkPhrase> chunk(List<Word> words) throws Exception {
        List<ChunkPhrase> phrases = new ArrayList<ChunkPhrase>();
        int wordsCount = words.size();
        String[] toks = new String[wordsCount - 1]; // 忽略第一个单词Root
        String[] tags = new String[wordsCount - 1];
        for (int i = 1; i < words.size(); i++) {
            toks[i - 1] = words.get(i).getName();
            tags[i - 1] = words.get(i).getPos();
        }
        // 采用open nlp进行chunk
        ChunkerModel chunkerModel;
        try {
            chunkerModel = ModelLoader.getChunkerModel();
        } catch (Exception e) {
            this.log.error("Failed to load chunk model!", e);
            throw e;
        }
        ChunkerME chunkerME = new ChunkerME(chunkerModel);
        Span[] spans = chunkerME.chunkAsSpans(toks, tags);
        for (Span span : spans) {
            Word word = words.get(span.getStart() + 1);
            if ("'s".equals(word.getName())) {
                ChunkPhrase prePhrase = phrases.get(phrases.size() - 1);
                prePhrase.setRightIndex(span.getEnd());
                prePhrase.getWords().addAll(words.subList(span.getStart() + 1, span.getEnd() + 1));
                phrases.set(phrases.size() - 1, prePhrase);
            } else {
                ChunkPhrase chunkPhrase = new ChunkPhrase(span.getStart() + 1, span.getEnd(), new ArrayList<Word>(words.subList(span.getStart() + 1, span.getEnd() + 1)));
                phrases.add(chunkPhrase);
            }
        }
        return phrases;
    }

    /**
     * 事件完善
     *
     * @param eventWithPhrases
     * @param words
     * @param sentNum
     * @return
     */
    private List<EventWithPhrase> eventRepair(List<EventWithPhrase> eventWithPhrases, List<Word> words, int sentNum) {

        List<EventWithPhrase> result = eventWithPhrases;

        /**
         * 1.如果两个事件，一个是三元事件，一个是二元事件，<br>
         * 如果三元事件的宾语是二元事件的谓语，同时该词不是名词和命名实体，<br>
         * 则将这两个事件合为一个事件
         */

        int i = 0, j = 0;
        EventWithPhrase newEvent = null;
        for (i = 0; i < eventWithPhrases.size(); i++) {
            EventWithPhrase t_event = eventWithPhrases.get(i);
            if (!EventType.TERNARY.equals(t_event.eventType())) {
                continue;
            }
            if (t_event.getRightPhrases().size() > 1) {
                // 过滤掉宾语为短语的事件
                continue;
            }
            Word t_event_right_word = t_event.getRightPhrases().get(0);
            for (j = i + 1; j < eventWithPhrases.size(); j++) {
                EventWithPhrase b_event = eventWithPhrases.get(j);

                if (!EventType.LEFT_MISSING.equals(b_event.eventType())) {
                    continue;
                }

                if (b_event.getMiddlePhrases().size() > 1) {
                    // 过滤掉谓语为短语的事件
                    continue;
                }

                Word b_event_middle_word = b_event.getMiddlePhrases().get(0);
                if (t_event_right_word.equals(b_event_middle_word)) {
                    /**
                     * 三元事件的宾语与二元事件的谓语相同，可以合并为一个事件 合并规则：
                     * 将三元事件的宾语与谓语合并为谓语，将二元事件的宾语作为新事件的宾语 删除原先的二元事件
                     */
                    t_event.getMiddlePhrases().add(t_event_right_word);
                    // 组合成新的事件
                    newEvent = new EventWithPhrase(t_event.getLeftPhrases(), t_event.getMiddlePhrases(), b_event.getRightPhrases(), t_event.getSentNum(), b_event.getFilename());
                    break;
                }
            }
            if (newEvent != null) {
                break;
            }
        }

        // 更新事件
        if (newEvent != null) {
            result.set(i, newEvent);
            result.remove(j);
        }

        /**
         *
         * 2.如果一个事件的主语或者宾语是限定词，<br>
         * 则将该词替换成向后距离最近（不超过标点范围）的名词或命名实体
         *
         * 3.如果一个事件的主语或者宾语是WP或者WP$,<br>
         * 则往前寻找最近的人名
         *
         * 4.如果一个事件缺失主语或者宾语，<br>
         * 则向前向后找最近（不超过标点范围）的名词或命名实体进行补全
         *
         */
        for (int k = 0; k < eventWithPhrases.size(); k++) {
            EventWithPhrase eventWithPhrase = eventWithPhrases.get(k);

            // 谓语中第一个单词的序号
            int leftVerbIndex = eventWithPhrase.getMiddlePhrases().get(0).getNumInLine();
            // 谓语中第二个单词的序号
            int rightVerbIndex = eventWithPhrase.getMiddlePhrases().get(eventWithPhrase.getMiddlePhrases().size() - 1).getNumInLine();

            if (CollectionUtils.isNotEmpty(eventWithPhrase.getLeftPhrases())) {
                // 存在主语
                if (eventWithPhrase.getLeftPhrases().size() == 1) {
                    // 主语是词语
                    Word leftWord = eventWithPhrase.getLeftPhrases().get(0);
                    if (leftWord.getSentenceNum() == sentNum) {
                        if ("DT".equals(leftWord.getPos())) {
                            for (int n = leftWord.getNumInLine() + 1; n < leftVerbIndex; n++) {
                                Word word = words.get(n);
                                if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                                    // 利用名词或命名实体来替换限定词
                                    eventWithPhrase.getLeftPhrases().set(0, word);
                                    break;
                                }
                                if (!EXCLUDE_PUNCTUATION.contains(word.getName()) && word.getName().equals(word.getPos())) {
                                    // 标点的pos等于其本身
                                    break;
                                }
                            }
                        } else if ("WDT".equals(leftWord.getPos())) {
                            // WDT关系
                            for (int n = leftWord.getNumInLine() - 1; n > 0; n--) {
                                Word word = words.get(n);
                                if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                                    // 利用名词或命名实体来替换限定词
                                    eventWithPhrase.getLeftPhrases().set(0, word);
                                    break;
                                }
                                if (!EXCLUDE_PUNCTUATION.contains(word.getName()) && word.getName().equals(word.getPos())) {
                                    // 标点的pos等于其本身
                                    break;
                                }
                            }
                        } else if (POS_PRP.contains(leftWord.getPos())) {
                            // 往前寻找最近的人名来替换当前词
                            for (int n = leftWord.getNumInLine() - 1; n > 0; n--) {
                                Word word = words.get(n);
                                if ("person".equalsIgnoreCase(word.getNer())) {
                                    eventWithPhrase.getLeftPhrases().set(0, word);
                                    break;
                                }
                            }
                        }
                    }

                }
            } else {
                // 主语缺失，向前找标点范围内的最近的名词或命名实体
                for (int n = leftVerbIndex - 1; n > 0; n--) {
                    Word word = words.get(n);
                    if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                        eventWithPhrase.getLeftPhrases().add(word);
                        break;
                    }
                }
            }

            if (CollectionUtils.isNotEmpty(eventWithPhrase.getRightPhrases())) {
                // 宾语存在
                if (eventWithPhrase.getRightPhrases().size() == 1) {
                    // 宾语为单词
                    Word rightWord = eventWithPhrase.getRightPhrases().get(0);

                    if (rightWord.getSentenceNum() == sentNum) {

                        if ("DT".equals(rightWord.getPos())) {
                            for (int n = rightWord.getNumInLine() + 1; n < words.size(); n++) {
                                Word word = words.get(n);
                                if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                                    // 利用名词或命名实体来替换限定词
                                    eventWithPhrase.getRightPhrases().set(0, word);
                                    break;
                                }
                                if (!EXCLUDE_PUNCTUATION.contains(word.getName()) && word.getName().equals(word.getPos())) {
                                    // 当前为标点，标点的pos等于本身
                                    break;
                                }
                            }
                        } else if ("WDT".equals(rightWord.getPos())) {
                            for (int n = rightWord.getNumInLine() - 1; n > rightVerbIndex; n--) {
                                Word word = words.get(n);
                                if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                                    // 利用名词或命名实体来替换限定词
                                    eventWithPhrase.getRightPhrases().set(0, word);
                                    break;
                                }
                                if (!EXCLUDE_PUNCTUATION.contains(word.getName()) && word.getName().equals(word.getPos())) {
                                    // 当前为标点，标点的pos等于本身
                                    break;
                                }
                            }
                        } else if (POS_PRP.contains(rightWord.getPos())) {
                            // 往前寻找最近的人名来替换当前词
                            for (int n = rightWord.getNumInLine() - 1; n > rightVerbIndex; n--) {
                                Word word = words.get(n);
                                if ("person".equalsIgnoreCase(word.getNer())) {
                                    eventWithPhrase.getRightPhrases().set(0, word);
                                    break;
                                }
                            }
                        }
                    }

                }
            } else {
                // 宾语缺失，向后找标点范围内最近的名词或命名实体
                for (int n = rightVerbIndex + 1; n < words.size(); n++) {
                    Word word = words.get(n);
                    if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                        eventWithPhrase.getRightPhrases().add(word);
                        break;
                    }
                }
            }

        }

        return result;
    }

    /**
     * 事件过滤函数，对于不符合要求的事件，返回null
     *
     * @param event
     * @return
     */
    private EventWithPhrase eventFilter(EventWithPhrase event) {
        EventWithPhrase result = event;

        if (event == null) {
            return null;
        }

        /* 过滤谓词为say的事件 */
        if ("say".equalsIgnoreCase(event.getMiddlePhrases().get(0).getLemma())) {
            return null;
        }

        /* 过滤回文事件 */
        if (event.isPalindromicEvent()) {
            return null;
        }

        return result;
    }

    /**
     * 将词语集合转换成句子，
     *
     * @param words
     * @return
     */
    private String words2Sentence(List<Word> words) {
        String sentenceDetail = null;
        final StringBuilder tmp = new StringBuilder();
        for (final Word word : words)
            tmp.append(word.toString() + " ");
        sentenceDetail = tmp.toString().trim();
        return sentenceDetail;
    }

    /**
     * 将词语集合转换成句子，
     *
     * @param words
     * @return
     */
    private String words2SentenceSimply(List<Word> words) {
        String sentenceDetail = null;
        final StringBuilder tmp = new StringBuilder();
        for (final Word word : words)
            tmp.append(word.wordWithPOS() + " ");
        sentenceDetail = tmp.toString().trim();
        return sentenceDetail;
    }

    /**
     * 将事件以精简的形式转化成字符串
     *
     * @param events
     * @return
     */
    private String getSimpilyEvents(List<? extends Event> events) {

        StringBuilder sb = new StringBuilder();

        for (Event event : events) {

            sb.append(event.toShortString() + " ");

        }

        return sb.toString().trim();

    }

    /**
     * 事件抽取测试
     *
     * @param args
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws Exception {

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace();
            }

        });

        ExecutorService es = Executors.newSingleThreadExecutor();
        EhCacheUtil ehCacheUtil = new EhCacheUtil("db_cache_vec", "local");
        Future<Boolean> future = es.submit(new EventsExtractBasedOnGraphV2("E:/workspace/test/example/text", "E:/workspace/test/example", ehCacheUtil));

        if (future.get()) {
            System.out.println("success!");
        } else {
            System.out.println("failed!");
        }

        es.shutdown();
        EhCacheUtil.close();
    }

}
