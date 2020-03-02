package edu.whu.cs.nlp.mts.base.loader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.InvalidFormatException;

/**
 * 模型加载器<br>
 * 单例模式，线程安全
 *
 * @author Apache_xiaochao
 *
 */
public class ModelLoader {

    private static Logger log = Logger.getLogger(ModelLoader.class);

    /** 启用core nlp的全部功能 */
    private volatile static StanfordCoreNLP pipeline;

    /** 仅启用分词功能 */
    private volatile static StanfordCoreNLP pipeline4wordseg;

    /** open nlp chunk need */
    private volatile static ChunkerModel chunkerModel;

    private ModelLoader() {}

    /**
     * 加载stanford指代消解模型<br>
     * 单例模式，线程安全
     *
     * @return
     */
    public static StanfordCoreNLP getPipeLine() {
        if (pipeline4wordseg == null) {
            synchronized (ModelLoader.class) {
                if (pipeline4wordseg == null) {
                    Properties props = new Properties();
                    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
                    //props.put("annotators", "tokenize, ssplit, pos, lemma");
                    pipeline4wordseg = new StanfordCoreNLP(props);
                }
            }
        }
        return pipeline4wordseg;
    }

    /**
     * 加载stanford指代消解模型<br>
     * 单例模式，线程安全
     *
     * @return
     */
    public static StanfordCoreNLP getWordSegPipeLine() {
        if (pipeline == null) {
            synchronized (ModelLoader.class) {
                if (pipeline == null) {
                    Properties props = new Properties();
                    props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
                    //props.put("annotators", "tokenize, ssplit, pos, lemma");
                    pipeline = new StanfordCoreNLP(props);
                }
            }
        }
        return pipeline;
    }

    /**
     * 获取open nlp chunk模型<br>
     * 单例模式，线程安全
     *
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws InvalidFormatException
     */
    public static ChunkerModel getChunkerModel() throws IOException, URISyntaxException {
        if(chunkerModel == null) {
            synchronized (ModelLoader.class) {
                if(chunkerModel == null) {
                    log.info("Loading open nlp chunker model");
                    chunkerModel = new ChunkerModel(ModelLoader.class.getClassLoader().getResourceAsStream("en-chunker.bin"));
                    log.info("Loading open nlp chunker success!");
                }
            }
        }
        return chunkerModel;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(ModelLoader.getChunkerModel());
    }

}
