package edu.whu.cs.nlp.mts.extraction.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.whu.cs.nlp.mts.base.domain.EventType;
import edu.whu.cs.nlp.mts.base.domain.EventWithWord;
import edu.whu.cs.nlp.mts.base.domain.ParseItem;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.loader.FileLoader;
import edu.whu.cs.nlp.mts.base.loader.ModelLoader;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.pretreatment.Pretreatment;

/**
 * 基于依存关系来构建词图，在词图的基础上用规则进行事件抽取
 *
 * @author Apache_xiaochao
 *
 */
public class EventsExtractBasedOnGraph implements GlobalConstant, Callable<Boolean> {

    private final Logger          log = Logger.getLogger(this.getClass());

    private final StanfordCoreNLP pipeline;
    private final String          textDir;                                // 输入文件所在目录

    public EventsExtractBasedOnGraph(String textDir) {
        super();
        this.textDir = textDir;
        this.pipeline = ModelLoader.getPipeLine();
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
        for (final Word word : words) {
            tmp.append(word.toString() + " ");
        }
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
        for (final Word word : words) {
            tmp.append(word.wordWithPOS() + " ");
        }
        sentenceDetail = tmp.toString().trim();
        return sentenceDetail;
    }

    /**
     * 利用stanford的nlp处理工具coreNlp对传入的正文进行处理，主要包括： 句子切分；词性标注，命名实体识别，依存分析等 *
     *
     * @param text
     * @return 结果信息全部存在一个map集合中返回，通过key来获取 key如下： segedText：切分后的句子集合，类型List
     *         <String> segedTextDetail：切分后的句子详细信息，类型List
     *         <String> words:所有词的对象信息，按行组织，类型：List<List<Word>>
     *         parseItems：所有词的依存关系集合，按行组织，类型：List<List<ParseItem>>
     */
    public Map<String, Object> coreNlpOperate(String text) {
        final Map<String, Object> coreNlpResults = new HashMap<String, Object>();
        final Annotation document = new Annotation(text);
        this.pipeline.annotate(document);
        final List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        final List<List<Word>> wordsList = new ArrayList<List<Word>>(); // 按照文章的组织结果，将每个词用对象进行存储
        final List<List<ParseItem>> parseItemList = new ArrayList<List<ParseItem>>(); // 存放正文中所有的依存关系
        final StringBuilder textAfterSSeg = new StringBuilder(); // 用来存放经过按行切分后的正文
        final StringBuilder textWithPOS = new StringBuilder(); // 仅包含词性的文本
        final StringBuilder textAfterSsegDetail = new StringBuilder(); // 相对于上面的区别在于每个词都带有详细信息
        // 获取句子中词的详细信息，并封装成对象
        for (int i = 0; i < sentences.size(); ++i) {
            final List<Word> words = new ArrayList<Word>(); // 存放一行中所有词的对象信息
            // 构建一个Root词对象，保证与依存分析中的词顺序统一
            final Word root = new Word();
            root.setName("Root");
            root.setLemma("root");
            root.setPos("PUNT");
            root.setNer("O");
            root.setNumInLine(0);
            root.setSentenceNum(i + 1);
            words.add(0, root);

            final CoreMap sentence = sentences.get(i);
            for (final CoreLabel token : sentence.get(TokensAnnotation.class)) {
                final String name = token.get(TextAnnotation.class);
                final String lemma = token.get(LemmaAnnotation.class);
                final String pos = token.get(PartOfSpeechAnnotation.class);
                final String ne = token.get(NamedEntityTagAnnotation.class);
                final Word word = new Word();
                word.setName(name);
                word.setLemma(lemma);
                word.setPos(pos);
                word.setNer(ne);
                word.setSentenceNum((i + 1));
                word.setNumInLine(token.index());
                words.add(word);
            }
            wordsList.add(words);
            textAfterSSeg.append(sentence.toString() + LINE_SPLITER);
            textAfterSsegDetail.append(this.words2Sentence(words) + LINE_SPLITER);
            textWithPOS.append(this.words2SentenceSimply(words) + LINE_SPLITER);

            // 获取依存依存分析结果
            // Tree tree = sentence.get(TreeAnnotation.class);
            final SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
            final List<TypedDependency> typedDependencies = (List<TypedDependency>) dependencies.typedDependencies();
            final List<ParseItem> parseItems = new ArrayList<ParseItem>(); // 存放一行中的依存信息
            for (final TypedDependency typedDependency : typedDependencies) {
                // 依存关系单元
                // String type = typedDependency.reln().getShortName();
                final Word leftWord = words.get(typedDependency.gov().index());
                final Word rightWord = words.get(typedDependency.dep().index());
                /*
                 * if("nsubj".equals(type) || "dobj".equals(type)){
                 * log.info(type + "(" + typedDependency.gov().value() + "-" +
                 * typedDependency.gov().index() + ", " +
                 * typedDependency.dep().value() + "-" +
                 * typedDependency.dep().index() + ")\t>>\t(" + leftWord + ", "
                 * + rightWord + ")"); }
                 */
                // 构建依存关系单元
                final ParseItem parseItem = new ParseItem();
                parseItem.setDependencyType(typedDependency.reln().getShortName());
                parseItem.setLeftWord(leftWord);
                parseItem.setRightWord(rightWord);
                parseItems.add(parseItem);
            }
            parseItemList.add(parseItems);
        }

        // 缓存处理的结果，用于返回
        coreNlpResults.put("segedText", CommonUtil.cutLastLineSpliter(textAfterSSeg.toString()));
        coreNlpResults.put("segedTextDetail", CommonUtil.cutLastLineSpliter(textAfterSsegDetail.toString()));
        coreNlpResults.put("segedTextPOS", CommonUtil.cutLastLineSpliter(textWithPOS.toString()));
        coreNlpResults.put("words", wordsList);
        coreNlpResults.put("parseItems", parseItemList);

        return coreNlpResults;
    }

    /**
     * 事件过滤函数，对于不符合要求的事件，返回null
     *
     * @param event_in
     * @return
     */
    private EventWithWord eventFilter(EventWithWord event_in) {
        EventWithWord event = null;
        if (event_in != null) {
            /*
             * 对事件进行过滤，过滤规则： 对于三元组事件： 1.如果谓词不是英文单词，则返回null
             * 2.对于三元组事件，如果主语或者宾语有一个不为单词，则将其替换为二元组事件，如果满足二元组事件要求，就将得到的二元组事件返回，
             * 否则返回null 对于二元组事件： 1.如果谓词不是单词，则返回null 2.如果主语或者宾语不是单词，则返回null
             */
            final Pattern pattern_include = Pattern.compile("[a-zA-Z0-9$]+"); // 必须包含的项
            final Pattern pattern_exclude = Pattern.compile("[&']"); // 不能包含的字符
            if (event_in.getMiddleWord() == null || !pattern_include.matcher(event_in.getMiddleWord().getLemma()).find()
                    || pattern_exclude.matcher(event_in.getMiddleWord().getLemma()).find()) {
                // 谓语不是单词
                event = null;
            } else {
                if (event_in.getLeftWord() != null && event_in.getRightWord() != null) {
                    // 当前为三元组事件
                    if (pattern_include.matcher(event_in.getLeftWord().getLemma()).find()
                            && pattern_include.matcher(event_in.getRightWord().getLemma()).find()
                            && !pattern_exclude.matcher(event_in.getLeftWord().getLemma()).find()
                            && !pattern_exclude.matcher(event_in.getRightWord().getLemma()).find()) {
                        event = event_in;
                    } else {
                        if (pattern_include.matcher(event_in.getLeftWord().getLemma()).find()
                                && !pattern_exclude.matcher(event_in.getLeftWord().getLemma()).find()) {
                            // 将当前三元事件降级为二元事件
                            event = new EventWithWord(event_in.getLeftWord(), null, event_in.getMiddleWord(), null,
                                    event_in.getFilename());
                        } else if (pattern_include.matcher(event_in.getRightWord().getLemma()).find()
                                && !pattern_exclude.matcher(event_in.getRightWord().getLemma()).find()) {
                            event = new EventWithWord(null, null, event_in.getMiddleWord(), event_in.getRightWord(),
                                    event_in.getFilename());
                        } else {
                            event = null;
                        }
                    }
                } else {
                    // 当前为二元组事件
                    if (event_in.getLeftWord() != null
                            && pattern_include.matcher(event_in.getLeftWord().getLemma()).find()
                            && !pattern_exclude.matcher(event_in.getLeftWord().getLemma()).find()) {
                        event = event_in;
                    } else if (event_in.getRightWord() != null
                            && pattern_include.matcher(event_in.getRightWord().getLemma()).find()
                            && !pattern_exclude.matcher(event_in.getRightWord().getLemma()).find()) {
                        event = event_in;
                    } else {
                        event = null;
                    }
                }
            }
        }

        /*
         * 2015年6月7日19:43:22新添加过滤规则 三元事件 1.如果主语、宾语中包含代词，则去除代词，降级为二元事件
         * 2.如果谓词为代词，则直接过滤 3.如果主语和宾语相同，则直接过滤 4.如果主语或宾语为be动词，则直接过滤 二元事件
         * 1.如果包含be动词，直接过滤 2.如果包含代词，则直接过滤 通用规则 1.将$全部改为money
         */
        if (event != null) {
            if (EventType.TERNARY.equals(event.eventType())) {
                // 表示当前为三元事件
                if (event.getLeftWord().getName().equalsIgnoreCase(event.getRightWord().getName())
                        || "be".equalsIgnoreCase(event.getLeftWord().getLemma())
                        || "be".equalsIgnoreCase(event.getRightWord().getLemma())
                        || POS_PRP.contains(event.getMiddleWord().getPos())) {
                    // 主语与宾语相同，或者其中一个为be动词，或谓词为代词，直接过滤
                    event = null;
                } else {
                    if (POS_PRP.contains(event.getLeftWord().getPos())) {
                        // 主语为代词，降级为二元事件
                        event.setLeftWord(null);
                    }
                    if (POS_PRP.contains(event.getRightWord().getPos())) {
                        // 宾语为代词，降级为二元事件
                        event.setRightWord(null);
                    }
                }
            }
            if (event != null && EventType.RIGHT_MISSING.equals(event.eventType())) {
                // 表示当前为主谓事件
                if ("be".equalsIgnoreCase(event.getLeftWord().getLemma())
                        || "be".equalsIgnoreCase(event.getMiddleWord().getLemma())) {
                    // 主语或谓语包含be动词，直接过滤
                    event = null;
                } else if (POS_PRP.contains(event.getLeftWord().getPos())
                        || POS_PRP.contains(event.getMiddleWord().getPos())) {
                    // 主语或谓语包含代词，直接过滤
                    event = null;
                }
            }
            if (event != null && EventType.LEFT_MISSING.equals(event.eventType())) {
                // 表示当前为谓宾事件
                if ("be".equalsIgnoreCase(event.getRightWord().getLemma())
                        || "be".equalsIgnoreCase(event.getMiddleWord().getLemma())) {
                    // 谓语或宾语包含be动词，直接过滤
                    event = null;
                } else if (POS_PRP.contains(event.getRightWord().getPos())
                        || POS_PRP.contains(event.getMiddleWord().getPos())) {
                    // 谓语或宾语包含代词，直接过滤
                    event = null;
                }
            }
            if (event != null && EventType.ERROR.equals(event.eventType())) {
                // 对于经过操作之后不能称为事件的事件进行过滤
                event = null;
            }
            if (event != null) {
                // 将美元符号全部替换成单词money
                if (event.getLeftWord() != null && "$".equals(event.getLeftWord().getName())) {
                    event.getLeftWord().setName("money");
                    event.getLeftWord().setLemma("money");
                }
                if (event.getMiddleWord() != null && "$".equals(event.getMiddleWord().getName())) {
                    event.getMiddleWord().setName("money");
                    event.getMiddleWord().setLemma("money");
                }
                if (event.getRightWord() != null && "$".equals(event.getRightWord().getName())) {
                    event.getRightWord().setName("money");
                    event.getRightWord().setLemma("money");
                }
            }
        }

        return event;
    }

    /**
     * 判断当前词是不是名词或命名实体
     *
     * @param word
     * @return
     */
    private boolean isNounOrNE(Word word) {
        if (word != null) {
            if (!"O".equalsIgnoreCase(word.getNer())) {
                return true;
            } else if (word.getPos().contains("NN")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将一个事件中的人称指代，替换成对应的人名 策略：找当前词所在行前面最近的人名
     *
     * @param words
     * @param word
     * @return
     */
    private Word personPronoun2Name(List<Word> words, Word word) {
        Word pronoun = word;
        if (word != null && POS_PRP.contains(word.getPos())) {
            for (int i = word.getNumInLine() - 1; i > 0; --i) {
                final Word curr = words.get(i);
                if ("person".equalsIgnoreCase(curr.getNer())) {
                    pronoun = curr;
                    break;
                }
            }
        }
        return pronoun;
    }

    /**
     * 构建词图，以依存关系作为边
     *
     * @param parseItems
     * @param wordsCount
     * @return
     */
    public String[][] wordGraphBuilder(List<ParseItem> parseItems, int wordsCount) {
        String[][] edges = null; // 图的边信息
        if (parseItems != null && wordsCount > 0) {
            edges = new String[wordsCount][wordsCount];
            for (final ParseItem parseItem : parseItems) {
                edges[parseItem.getLeftWord().getNumInLine()][parseItem.getRightWord().getNumInLine()] = parseItem
                        .getDependencyType();
            }
        }
        return edges;
    }

    /**
     * 事件抽取函数 先构建词图，然后基于词图来进行事件抽取
     *
     * @param parsedList
     * @param words
     * @param filename
     * @return
     */
    public Map<Integer, List<EventWithWord>> extract(List<List<ParseItem>> parsedList, List<List<Word>> words,
            String filename) {
        Map<Integer, List<EventWithWord>> events = null;
        if (parsedList != null && words != null) {
            events = new TreeMap<Integer, List<EventWithWord>>();
            for (int k = 0; k < parsedList.size(); ++k) {
                // 当前处理单位：句子
                final List<ParseItem> parseItems = parsedList.get(k);
                final List<Word> wordsInSentence = words.get(k);
                final int wordsCount = wordsInSentence.size();
                final String[][] edges = this.wordGraphBuilder(parseItems, wordsCount);
                final List<EventWithWord> eventsInSentence = new ArrayList<EventWithWord>();
                // 构建事件
                for (int i = 0; i < wordsCount; ++i) {
                    // 当前处理单位：词
                    final List<Integer> agents = new ArrayList<Integer>();
                    final List<Integer> objects = new ArrayList<Integer>();
                    final List<Integer> cops = new ArrayList<Integer>(); // 记录cop依存关系，用于补全二元事件
                    final List<Integer> preps = new ArrayList<Integer>(); // 记录prep依存关系，当主语缺失时用于补全
                    for (int j = 0; j < wordsCount; ++j) {
                        if (DEPENDENCY_AGENT.contains(edges[i][j])) {
                            // 施事
                            agents.add(j);
                        }
                        if (DEPENDENCY_OBJECT.contains(edges[i][j])) {
                            // 受事
                            objects.add(j);
                        }
                        // 缓存cop关系
                        if ("cop".equals(edges[i][j])) {
                            cops.add(j);
                        }
                        // 缓存prep关系
                        if ("prep".equals(edges[i][j]) || "prepc".equals(edges[i][j])) {
                            preps.add(j);
                        }
                    }
                    Word middleWord = wordsInSentence.get(i);
                    if (agents.size() != 0 && objects.size() != 0) {
                        for (final Integer agent : agents) {
                            final Word leftWord = this.personPronoun2Name(wordsInSentence, wordsInSentence.get(agent));
                            for (final Integer object : objects) {
                                final Word rightWord = this.personPronoun2Name(wordsInSentence,
                                        wordsInSentence.get(object));
                                final EventWithWord event = this.eventFilter(
                                        new EventWithWord(leftWord, null, middleWord, rightWord, filename));
                                if (event != null) {
                                    eventsInSentence.add(event);
                                }
                            }
                        }
                    } else {
                        // 主语或宾语缺失
                        if (agents.size() != 0) {
                            final List<Word> middleWords = new ArrayList<Word>();
                            if (cops.size() > 0) {
                                for (final Integer copNum : cops) {
                                    middleWords.add(wordsInSentence.get(copNum));
                                }
                            }
                            // 从当前词语往后寻找最近的命名实体或名词来作为宾语，效果下降，暂时屏蔽
                            final Word subjWord = null;
                            /*
                             * for(int n = middleWord.getNumInLine() + 1; n <
                             * wordsInSentence.size(); ++n){ Word tmpWord =
                             * wordsInSentence.get(n);
                             * if(POS_NOUN.contains(tmpWord.getPos()) ||
                             * !"O".equals(tmpWord.getNer())){ subjWord =
                             * tmpWord; break; } }
                             */
                            // 缺失宾语的事件
                            for (final Integer agent : agents) {
                                final Word leftWord = this.personPronoun2Name(wordsInSentence,
                                        wordsInSentence.get(agent));
                                if (middleWords.size() > 0) {
                                    // 如果存在依存关系cop，则用cop关系将二元事件补全为三元事件
                                    middleWord = this.personPronoun2Name(wordsInSentence, middleWord);
                                    for (final Word mw : middleWords) {
                                        final EventWithWord event = this.eventFilter(
                                                new EventWithWord(leftWord, null, mw, middleWord, filename));
                                        if (event != null) {
                                            eventsInSentence.add(event);
                                        }
                                    }
                                } else {
                                    final EventWithWord event = this.eventFilter(new EventWithWord(leftWord, null,
                                            middleWord, subjWord == null ? null : subjWord, filename));
                                    if (event != null) {
                                        eventsInSentence.add(event);
                                    }
                                }
                            }
                        } else if (objects.size() != 0) {
                            // 缺失主语的事件
                            final List<Word> leftWords = new ArrayList<Word>();
                            if (preps.size() > 0) {
                                for (final Integer prep : preps) {
                                    final Word word = wordsInSentence.get(prep);
                                    if (POS_NOUN.contains(word.getPos()) || !"O".equals(word.getNer())) {
                                        leftWords.add(wordsInSentence.get(prep));
                                    }
                                }
                            }
                            // 从当前词语往前寻找最近的命名实体或名词来作为主语，效果下降，暂时屏蔽
                            final Word objWord = null;
                            /*
                             * for(int n = middleWord.getNumInLine() - 1; n > 0;
                             * --n){ Word tmpWord = wordsInSentence.get(n);
                             * if(POS_NOUN.contains(tmpWord.getPos()) ||
                             * !"O".equals(tmpWord.getNer())){ objWord =
                             * tmpWord; break; }
                             *
                             * }
                             */
                            for (final Integer object : objects) {
                                final Word rightWord = this.personPronoun2Name(wordsInSentence,
                                        wordsInSentence.get(object));
                                if (leftWords.size() > 0) {
                                    for (final Word leftWord : leftWords) {
                                        final EventWithWord event = this.eventFilter(
                                                new EventWithWord(this.personPronoun2Name(wordsInSentence, leftWord),
                                                        null, middleWord, rightWord, filename));
                                        if (event != null) {
                                            eventsInSentence.add(event);
                                        }
                                    }
                                } else {
                                    final EventWithWord event = this.eventFilter(new EventWithWord(
                                            objWord == null ? null : objWord, null, middleWord, rightWord, filename));
                                    if (event != null) {
                                        eventsInSentence.add(event);
                                    }
                                }
                            }
                        }
                    }
                }
                events.put(k + 1, eventsInSentence);
            }
        }
        return events;
    }

    @Override
    public Boolean call() {
        final boolean success = true;
        // TODO Auto-generated method stub
        final File file = new File(this.textDir);
        final String[] filenames = file.list();
        final Pretreatment pretreatment = new Pretreatment();
        for (final String filename : filenames) {
            // 加载文件
            this.log.info(Thread.currentThread().getId() + "正在操作文件：" + this.textDir + "/" + filename);
            try {
                String text = FileLoader.read(this.textDir + "/" + filename, DEFAULT_CHARSET);

                // 对文本进行句子切分和指代消解
                final Map<String, String> preTreatResult = pretreatment.coreferenceResolution(text);
                FileLoader.write(this.textDir + "/" + DIR_TEXT + "/" + filename,
                        preTreatResult.get(Pretreatment.KEY_SEG_TEXT), DEFAULT_CHARSET);
                text = preTreatResult.get(Pretreatment.KEY_CR_TEXT);

                // 利用stanford的nlp核心工具进行处理
                final Map<String, Object> coreNlpResults = this.coreNlpOperate(text);

                // 获取句子切分后的文本
                final String segedtext = (String) coreNlpResults.get("segedText");
                FileLoader.write(this.textDir + "/" + DIR_SEG_TEXT + "/" + filename, segedtext, DEFAULT_CHARSET);

                // 获取句子切分后的文本详细信息
                final String segedTextDetail = (String) coreNlpResults.get("segedTextDetail");
                FileLoader.write(this.textDir + "/" + DIR_SEGDETAIL_TEXT + "/" + filename, segedTextDetail,
                        DEFAULT_CHARSET);

                // 获取句子切分后的带有词性的文本信息
                final String segedTextPOS = (String) coreNlpResults.get("segedTextPOS");
                FileLoader.write(this.textDir + "/" + DIR_SEGDETAIL_TEXT + "/pos/" + filename, segedTextPOS,
                        DEFAULT_CHARSET);

                // 获取对句子中单词进行对象化后的文本，将字符串表示成Word对象
                @SuppressWarnings("unchecked")
                final List<List<Word>> words = (List<List<Word>>) coreNlpResults.get("words");

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
                // 词和词性分开按行存储
                FileLoader.write(this.textDir + "/" + DIR_SEGDETAIL_TEXT + "/pos2/" + filename,
                        CommonUtil.cutLastLineSpliter(sb_words_pos.toString()), DEFAULT_CHARSET);

                // 获取依存分析结果
                @SuppressWarnings("unchecked")
                final List<List<ParseItem>> parseItemList = (List<List<ParseItem>>) coreNlpResults.get("parseItems");
                FileLoader.write(this.textDir + "/" + DIR_PARSE_TEXT + "/" + filename,
                        CommonUtil.lists2String(parseItemList), DEFAULT_CHARSET);

                // 记录简版的依存分析结果
                final StringBuilder simplifyParsedResult = new StringBuilder();
                for (final List<ParseItem> parseItems : parseItemList) {
                    for (final ParseItem parseItem : parseItems) {
                        simplifyParsedResult.append(parseItem.toShortString() + "\t");
                    }
                    simplifyParsedResult.append(LINE_SPLITER);
                }
                FileLoader.write(this.textDir + "/" + DIR_PARSESIMPLIFY + "/" + filename,
                        CommonUtil.cutLastLineSpliter(simplifyParsedResult.toString()), DEFAULT_CHARSET);

                // 对当前文本进行事件抽取
                final Map<Integer, List<EventWithWord>> events = this.extract(parseItemList, words, filename);

                final StringBuilder sb_events = new StringBuilder();
                final StringBuilder sb_simplify_events = new StringBuilder();
                for (final Entry<Integer, List<EventWithWord>> entry : events.entrySet()) {
                    final String eventsInSentence = CommonUtil.list2String(entry.getValue());
                    sb_events.append(entry.getKey() + "\t" + eventsInSentence + LINE_SPLITER);
                    sb_simplify_events
                    .append(entry.getKey() + "\t" + this.getSimpilyEvents(entry.getValue()) + LINE_SPLITER);
                }
                FileLoader.write(this.textDir + "/" + DIR_EVENTS + "/" + filename,
                        CommonUtil.cutLastLineSpliter(sb_events.toString()), DEFAULT_CHARSET);
                FileLoader.write(this.textDir + "/" + DIR_EVENTSSIMPLIFY + "/" + filename,
                        CommonUtil.cutLastLineSpliter(sb_simplify_events.toString()), DEFAULT_CHARSET);

            } catch (final IOException e) {
                this.log.error("文件读或写失败：" + this.textDir + "/" + filename, e);
                // e.printStackTrace();
            }
        }
        return success;
    }

    /*
     * 将事件以精简的形式转化成字符串
     */
    private String getSimpilyEvents(List<EventWithWord> events) {
        String result = null;
        final StringBuilder sb = new StringBuilder();
        for (final EventWithWord event : events) {
            sb.append(event.toShortString() + " ");
        }
        result = sb.toString().trim();
        return result;
    }

    /**
     * 事件抽取测试
     *
     * @param args
     */
    public static void main(String[] args) {
        final ExecutorService es = Executors.newFixedThreadPool(4);
        es.submit(new EventsExtractBasedOnGraph("E:/workspace/optimization/singleText"));
        es.shutdown();
    }

}
