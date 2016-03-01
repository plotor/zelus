package edu.whu.cs.nlp.mts.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.msc.NomParamSentenceReRanker;
import edu.whu.cs.nlp.msc.SentenceReRanker;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.global.GlobalParam;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.clustering.CalculateSimilarityThread;
import edu.whu.cs.nlp.mts.clustering.ChineseWhispersCluster;
import edu.whu.cs.nlp.mts.extraction.graph.EventsExtractBasedOnGraphV2;

/**
 * 驱动类
 *
 * @author ZhenchaoWang 2015-10-20 10:55:06
 *
 */
public class MTSBOEC implements GlobalConstant {

    private static final Logger log = Logger.getLogger(MTSBOEC.class);

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            log.error("请指定配置文件！");
            return;
        }

        String propFilePath = args[0]; // 配置文件所在路径

        // 以文件名最后数字作为文件名，用于同时跑多个任务
        String numDir = propFilePath.substring(propFilePath.lastIndexOf(".") + 1, propFilePath.length());
        if (!numDir.matches("\\d+")) {
            numDir = "";
        }

        /*
         * 加载配置文件
         */
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propFilePath));
        } catch (IOException e) {
            log.error("load properties failed!", e);
            return;
        }

        // 获取线程数
        int threadNum = Integer.parseInt(properties.getProperty("thread_num", "2").trim());

        // 设置参数到全局
        GlobalParam.setWorkDir(properties.getProperty("work_dir"));
        GlobalParam.setCacheName(properties.getProperty("cachename"));
        GlobalParam.setDatasource(properties.getProperty("datasource"));
        GlobalParam.setWordnetDictPath(properties.getProperty("wordnet_dict_path"));
        GlobalParam.setEdgeWeightThresh(Float.parseFloat(properties.getProperty("edge_weight_thresh")));
        GlobalParam.setNgramModelPath(properties.getProperty("ngram_model_path"));
        GlobalParam.setQuestionFilename(properties.getProperty("question_filename"));
        GlobalParam.setIdfFilename(properties.getProperty("idf_filename"));
        GlobalParam.setVecFilename(properties.getProperty("vec_filename"));
        GlobalParam.setRunMode(properties.getProperty("run_mode"));
        GlobalParam.setSentenceCountThresh(Integer.parseInt(properties.getProperty("sentence_count_thresh").trim()));
        GlobalParam.setSimilarityThresh(Float.parseFloat(properties.getProperty("similarity_thresh").trim()));
        GlobalParam.setAlpha4summary(Float.parseFloat(properties.getProperty("alpha_summary").trim()));
        GlobalParam.setBeta4summary(Float.parseFloat(properties.getProperty("beta_summary").trim()));

        /**
         * 1.执行事件抽取操作
         */
        if ("y".equalsIgnoreCase(properties.getProperty("is_extract_event"))) {

            String corpusDir = GlobalParam.workDir + "/" + DIR_CORPUS;

            log.info("Extracting event[" + corpusDir + "]");

            File textDirFile = new File(corpusDir);

            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();

            EhCacheUtil ehCacheUtil = new EhCacheUtil(GlobalParam.cacheName, GlobalParam.datasource);

            for (File dir : textDirFile.listFiles()) {
                String absoluteDir = dir.getAbsolutePath();
                String topicName = absoluteDir.substring(Math.max(absoluteDir.lastIndexOf("\\"), absoluteDir.lastIndexOf("/")) + 1);
                tasks.add(new EventsExtractBasedOnGraphV2(topicName, ehCacheUtil));
            }

            /* 执行完成之前，主线程阻塞 */
            ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
            try {
                List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    future.get();
                }
            } catch (Throwable e) {
                log.error("There is an exception when extract events!", e);
                return;
            } finally {
                EhCacheUtil.close();
                executorService.shutdown();
            }

        } else {

            log.info("Events extract is not enabled!");

        }

        /**
         * 2.计算事件之间的相似度
         */
        if ("y".equalsIgnoreCase(properties.getProperty("is_calculate_similarity"))) {

            log.info("Calculating events similarity...");

            File objFile = new File(GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_SERIALIZE_EVENTS);
            File[] topicDirs = objFile.listFiles();
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            for (File topicDir : topicDirs) {
                tasks.add(new CalculateSimilarityThread(topicDir.getAbsolutePath()));
            }
            if (CollectionUtils.isNotEmpty(tasks)) {
                ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
                try {
                    List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                    for (Future<Boolean> future : futures) {
                        future.get();
                    }
                } catch (Throwable e) {
                    log.error("There is an exception when calculate events similarity!", e);
                } finally {
                    executorService.shutdown();
                }
            }
        } else {
            log.info("Events similarity calculate is not enabled!");
        }

        /**
         * 3.对事件进行聚类，同时按类别抽取事件所在子句
         */
        if ("y".equalsIgnoreCase(properties.getProperty("is_event_cluster"))) {

            log.info("Starting events clusting & sub sentences extracting...");

            File nodeFile = new File(GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + OBJ + "/" + DIR_NODES);
            File[] nodes = nodeFile.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".node.obj")) {
                        return true;
                    }
                    return false;
                }
            });

            ExecutorService es = null;
            try {

                es = Executors.newFixedThreadPool(threadNum);
                List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
                for (File file : nodes) {
                    String nodePath = file.getAbsolutePath();
                    String edgePath = GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + OBJ + "/" + DIR_EDGES + "/" + file.getName().replace("node", "edge");
                    String wordvecDictPath = GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_WORDS_VECTOR + "/" + file.getName().replace(".node", "");
                    String topicPath = GlobalParam.workDir + "/" + DIR_CORPUS + "/" + file.getName().substring(0, file.getName().indexOf("."));
                    tasks.add(new ChineseWhispersCluster(topicPath, nodePath, edgePath, wordvecDictPath));
                }

                List<Future<Boolean>> futures = es.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    future.get();
                }

            } catch (Throwable e) {
                log.error("Event cluster & Sentence Extract error!", e);
            } finally {
                if (es != null) {
                    es.shutdown();
                }
            }

        } else {
            log.info("Events clusting is not enabled!");
        }

        /**
         * 4.摘要生成
         */
        if ("y".equalsIgnoreCase(properties.getProperty("is_build_summary"))) {

            // 加载question文件
            Properties prop = new Properties();
            try {
                String questionFilename = GlobalParam.questionFilename;
                log.info("Loading question file[" + questionFilename + "]...");
                prop.load(new InputStreamReader(MTSBOEC.class.getClassLoader().getResourceAsStream(questionFilename)));
                log.info("Loading question file[" + questionFilename + "] success!");
            } catch (IOException e) {
                log.error("Load question file error!", e);
                throw e;
            }

            String runMode = GlobalParam.runMode;

            float alpha = GlobalParam.alpha4summary;
            float beta = GlobalParam.beta4summary;

            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            File compressFiles = new File(GlobalParam.workDir + "/" + DIR_SENTENCES_COMPRESSION);

            // 加载词向量
            File vecFile = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_VEC_FILE, GlobalParam.vecFilename);
            log.info("Loading word vec[" + vecFile.getAbsolutePath() + "]");
            Map<String, Vector> wordVecs = new HashMap<String, Vector>();
            try {
                wordVecs = (Map<String, Vector>) SerializeUtil.readObj(vecFile.getAbsolutePath());
            } catch (ClassNotFoundException e) {
                log.error("Load word vec[" + vecFile.getAbsolutePath() + "] error!", e);
                throw e;
            }
            log.info("Load word vec[" + vecFile.getAbsolutePath() + "] success!");

            if (MapUtils.isEmpty(wordVecs)) {
                log.error("Can't load any word vec[" + vecFile.getAbsolutePath() + "]");
                throw new Exception("Can't load any word vec[" + vecFile.getAbsolutePath() + "]");
            }

            if ("old-np".equalsIgnoreCase(runMode)) {
                // 采用老的reranker策略构建摘要
                log.info("Summary build mode: old-np");
                for (File file : compressFiles.listFiles()) {
                    String topicName = file.getName().substring(0, file.getName().lastIndexOf("."));
                    tasks.add(new NomParamSentenceReRanker(prop.getProperty(topicName), file.getAbsolutePath(), numDir, wordVecs, GlobalParam.ngramModelPath));
                }

            } else if ("old".equalsIgnoreCase(runMode)) {

                // 采用老的reranker策略构建摘要
                for (File file : compressFiles.listFiles()) {
                    String topicName = file.getName().substring(0, file.getName().lastIndexOf("."));
                    tasks.add(new SentenceReRanker(prop.getProperty(topicName), file.getAbsolutePath(), numDir, wordVecs, GlobalParam.ngramModelPath, alpha, beta));
                }

            } else if ("new".equalsIgnoreCase(runMode)) {
                // 采用子模函数构建摘要
                log.info("Summary build mode: new");

                // 加载每个词的IDF值
                /** Google 总页面数估值 */
                final double TOTAL_PAGE_COUNT = 30000000000.0D;

                Map<String, Double> idfValues = new HashMap<String, Double>();
                File idfFIle = FileUtils.getFile(GlobalParam.workDir + "/" + DIR_IDF_FILE, GlobalParam.idfFilename);
                log.info("Loading idf value file[" + idfFIle.getAbsolutePath() + "]");
                LineIterator lineIterator = null;
                try {
                    lineIterator = FileUtils.lineIterator(idfFIle, DEFAULT_CHARSET.toString());
                    while (lineIterator.hasNext()) {
                        String line = lineIterator.nextLine();
                        String[] strs = line.split("###");
                        if (strs.length != 2) {
                            log.warn("Line[" + line + "] format is illegal, ignore it!");
                            continue;
                        }
                        idfValues.put(strs[0].trim(), Long.parseLong(strs[1]) / TOTAL_PAGE_COUNT);
                    }
                    log.info("Load idf value file[" + idfFIle.getAbsolutePath() + "] finished!");
                } catch (IOException e) {
                    log.error("Load idf value file[" + idfFIle.getAbsolutePath() + "] error!", e);
                    throw e;
                } finally {
                    if (lineIterator != null) {
                        lineIterator.close();
                    }
                }

                for (File file : compressFiles.listFiles()) {
                    String topicName = file.getName().substring(0, file.getName().lastIndexOf("."));
                    tasks.add(new SummaryBuilderByPreVectorReRanker(numDir, file.getName(), GlobalParam.sentenceCountThresh, idfValues, prop.getProperty(topicName), wordVecs, alpha, beta, GlobalParam.similarityThresh));
                }

            }

            ExecutorService es = null;
            try {
                es = Executors.newFixedThreadPool(threadNum);
                List<Future<Boolean>> futures = es.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    future.get();
                }
            } catch (Throwable e) {
                log.error("Build summary error!", e);
            } finally {
                if (es != null) {
                    es.shutdown();
                }
            }

        } else {
            log.info("Build summary is not enabled!");
        }

        log.info("-----------------------finished-----------------------");

    }

}
