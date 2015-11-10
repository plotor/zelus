package edu.whu.cs.nlp.mts.base.nlp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
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
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.whu.cs.nlp.mts.base.biz.ModelLoader;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.ParseItem;
import edu.whu.cs.nlp.mts.base.domain.Word;

/**
 * 斯坦福NLP工具类
 *
 * @author ZhenchaoWang 2015-10-27 16:38:29
 *
 */
public class StanfordNLPTools implements SystemConstant{

    private static final Logger log = Logger.getLogger(StanfordNLPTools.class);

    /** 获取所有词的对象信息 */
    public static final String    KEY_WORDS          = "WORDS";

    /** 获取所有词的依存关系集合 */
    public static final String    KEY_PARSED_ITEMS   = "PARSED_ITEMS";

    /** 获取句法分析树 */
    public static final String    KEY_SYNTACTICTREES = "SYNTACTICTREES";

    /** 获取依存分析图 */
    public static final String    KEY_SEMANTICGRAPHS = "SEMANTICGRAPHS";

    /** 获取指代链图 */
    public static final String    KEY_COREFCHAIN_GRAPH = "COREFCHAIN_GRAPH";

    /**
     * 利用stanford的nlp处理工具coreNlp对传入的正文进行处理，主要包括：<br>
     * 句子切分；词性标注，命名实体识别，依存分析等
     *
     * @param text
     *            输入文本
     * @return 结果信息全部存在一个map集合中返回，通过key来获取
     *
     */
    public synchronized static Map<String, Object> coreOperate(final String text) {

        Map<String, Object> coreNlpResults = new HashMap<String, Object>();

        StanfordCoreNLP pipeline = ModelLoader.getPipeLine();

        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        /** 存放文章所有的词，按句子组织 */
        List<List<Word>> wordsList = new ArrayList<List<Word>>();
        /** 存放句子的句法分析树 */
        List<Tree> syntacticTrees = new ArrayList<Tree>();
        /** 存放依存图，按句子组织 */
        List<SemanticGraph> semanticGraphs = new ArrayList<SemanticGraph>();
        /** 存放正文中所有的依存关系 */
        List<List<ParseItem>> parseItemList = new ArrayList<List<ParseItem>>();

        List<CoreMap> sentences = document.get(SentencesAnnotation.class);

        // 获取句子中词的详细信息，并封装成对象
        for (int i = 0; i < sentences.size(); ++i) {

            List<Word> words = new ArrayList<Word>(); // 存放一行中所有词的对象信息

            // 构建一个Root词对象，保证与依存分析中的词顺序统一
            Word root = new Word();
            root.setName("Root");
            root.setLemma("root");
            root.setPos("PUNT");
            root.setNer("O");
            root.setNumInLine(0);
            root.setSentenceNum(i + 1);
            words.add(0, root);

            CoreMap sentence = sentences.get(i);
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {

                // 构建词对象
                Word word = new Word();
                word.setName(token.get(TextAnnotation.class));
                word.setLemma(token.get(LemmaAnnotation.class));
                word.setPos(token.get(PartOfSpeechAnnotation.class));
                word.setNer(token.get(NamedEntityTagAnnotation.class));
                word.setSentenceNum((i + 1));
                word.setNumInLine(token.index());
                words.add(word);

            }

            // 缓存每个句子的处理结果
            wordsList.add(words);

            // 获取当前句子的句法分析树
            syntacticTrees.add(sentence.get(TreeAnnotation.class));

            // 获取依存依存分析结果，构建依存对象对儿
            SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
            semanticGraphs.add(dependencies);

            List<TypedDependency> typedDependencies = (List<TypedDependency>) dependencies.typedDependencies();
            List<ParseItem> parseItems = new ArrayList<ParseItem>(); // 存放一行中的依存信息
            for (TypedDependency typedDependency : typedDependencies) {
                // 依存关系单元
                Word leftWord = words.get(typedDependency.gov().index());
                Word rightWord = words.get(typedDependency.dep().index());
                // 构建依存关系单元
                ParseItem parseItem = new ParseItem();
                parseItem.setDependencyType(typedDependency.reln().getShortName());
                parseItem.setLeftWord(leftWord);
                parseItem.setRightWord(rightWord);
                parseItems.add(parseItem);
            }
            parseItemList.add(parseItems);
        }

        // 缓存处理的结果，用于返回
        /*
         * coreNlpResults.put(KEY_SEGED_TEXT,
         * CommonUtil.cutLastLineSpliter(textAfterSSeg.toString()));
         * coreNlpResults.put(KEY_SEGED_DETAIL_TEXT,
         * CommonUtil.cutLastLineSpliter(textAfterSsegDetail.toString()));
         * coreNlpResults.put(KEY_SEGED_POS_TEXT,
         * CommonUtil.cutLastLineSpliter(textWithPOS.toString()));
         */
        coreNlpResults.put(KEY_WORDS, wordsList);
        coreNlpResults.put(KEY_PARSED_ITEMS, parseItemList);
        coreNlpResults.put(KEY_SEMANTICGRAPHS, semanticGraphs);
        coreNlpResults.put(KEY_SYNTACTICTREES, syntacticTrees);
        // 获取输入文本中的指代链，并执行指代消解
        coreNlpResults.put(KEY_COREFCHAIN_GRAPH, document.get(CorefChainAnnotation.class));

        return coreNlpResults;

    }

}
