package org.zhenchao.zelus.common.loader;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import opennlp.tools.chunker.ChunkerModel;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * 模型加载器
 * 单例模式，线程安全
 *
 * @author Apache_xiaochao
 */
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor", "checkstyle:ModifierOrder",
        "checkstyle:UncommentedMain", "checkstyle:JavadocMethod"})
public final class ModelLoader {

    private static Logger log = Logger.getLogger(ModelLoader.class);

    private volatile static StanfordCoreNLP pipeline;

    private volatile static StanfordCoreNLP pipeline4wordseg;

    private volatile static ChunkerModel chunkerModel;

    private ModelLoader() {
    }

    /**
     * 加载stanford指代消解模型
     * 单例模式，线程安全
     *
     * @return StanfordCoreNLP实例
     */
    public static StanfordCoreNLP getPipeLine() {
        if (pipeline4wordseg == null) {
            synchronized (ModelLoader.class) {
                if (pipeline4wordseg == null) {
                    Properties props = new Properties();
                    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
                    pipeline4wordseg = new StanfordCoreNLP(props);
                }
            }
        }
        return pipeline4wordseg;
    }

    /**
     * 加载stanford分词模型
     * 单例模式，线程安全
     *
     * @return StanfordCoreNLP实例
     */
    public static StanfordCoreNLP getWordSegPipeLine() {
        if (pipeline == null) {
            synchronized (ModelLoader.class) {
                if (pipeline == null) {
                    Properties props = new Properties();
                    props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
                    pipeline = new StanfordCoreNLP(props);
                }
            }
        }
        return pipeline;
    }

    /**
     * 获取open nlp chunk模型
     * 单例模式，线程安全
     *
     * @return ChunkerModel实例
     * @throws IOException          IO异常
     * @throws URISyntaxException   URI异常
     */
    public static ChunkerModel getChunkerModel() throws IOException, URISyntaxException {
        if (chunkerModel == null) {
            synchronized (ModelLoader.class) {
                if (chunkerModel == null) {
                    log.info("Loading open nlp chunker model");
                    chunkerModel = new ChunkerModel(
                            ModelLoader.class.getClassLoader()
                                    .getResourceAsStream("en-chunker.bin"));
                    log.info("Loading open nlp chunker success!");
                }
            }
        }
        return chunkerModel;
    }

    /** 测试入口 */
    public static void main(String[] args) throws Exception {
        Logger.getLogger(ModelLoader.class).info(String.valueOf(ModelLoader.getChunkerModel()));
    }

}
