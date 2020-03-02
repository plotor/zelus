package org.zhenchao.zelus.summary;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.cluster.CalculateSimilarityThread;
import org.zhenchao.zelus.cluster.ChineseWhispersCluster;
import org.zhenchao.zelus.common.global.Constants;
import org.zhenchao.zelus.common.global.GlobalParam;
import org.zhenchao.zelus.common.pojo.Vector;
import org.zhenchao.zelus.common.util.EhcacheUtils;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.extract.EventsExtractBasedOnGraphV2;
import org.zhenchao.zelus.takahe.NomParamSentenceReRanker;
import org.zhenchao.zelus.takahe.SentenceReRanker;

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

/**
 * 驱动类
 *
 * @author ZhenchaoWang 2015-10-20 10:55:06
 */
public class MTSBOEC implements Constants {

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

            EhcacheUtils ehCacheUtil = new EhcacheUtils(GlobalParam.cacheName, GlobalParam.datasource);

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
                EhcacheUtils.close();
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
                log.info("loading question file[" + questionFilename + "]...");
                prop.load(new InputStreamReader(MTSBOEC.class.getClassLoader().getResourceAsStream(questionFilename)));
                log.info("loading question file[" + questionFilename + "] success!");
            } catch (IOException e) {
                log.error("load question file error!", e);
                throw e;
            }

            // 加载词向量
            File wordVecDir = new File(GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_WORDS_VECTOR);
            File[] wordVecFiles = wordVecDir.listFiles();
            Map<String, Vector> wordVecs = new HashMap<String, Vector>();
            for (File wordVecFile : wordVecFiles) {
                log.info("loading word vec[" + wordVecFile.getAbsolutePath() + "]");
                Map<String, Vector> wordVecsInTopic = (Map<String, Vector>) SerializeUtils.readObj(wordVecFile.getAbsolutePath());
                if (MapUtils.isEmpty(wordVecsInTopic)) {
                    log.error("Can't find any vector in file[" + wordVecFile.getAbsolutePath() + "]");
                    throw new Exception("Can't find any vector in file[" + wordVecFile.getAbsolutePath() + "]");
                }
                for (Map.Entry<String, Vector> entry : wordVecsInTopic.entrySet()) {
                    if (wordVecs.containsKey(entry.getKey())) {
                        continue;
                    }
                    wordVecs.put(entry.getKey(), entry.getValue());
                }
            }
            log.info("load word vector finish, vector size[" + wordVecs.size() + "]");

            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            File compressFiles = new File(GlobalParam.workDir + "/" + DIR_SUMMARY_RESULTS + "/" + DIR_SENTENCES_COMPRESSION);
            float alpha = GlobalParam.alpha4summary;
            float beta = GlobalParam.beta4summary;
            String runMode = GlobalParam.runMode;

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
                log.info("loading idf value file[" + idfFIle.getAbsolutePath() + "]");

                LineIterator lineIterator = null;
                try {

                    lineIterator = FileUtils.lineIterator(idfFIle, DEFAULT_CHARSET.toString());
                    while (lineIterator.hasNext()) {
                        String line = lineIterator.nextLine();
                        String[] strs = line.split("###");
                        if (strs.length != 2) {
                            log.warn("line[" + line + "] format is illegal, ignore it!");
                            continue;
                        }
                        idfValues.put(strs[0].trim(), Long.parseLong(strs[1]) / TOTAL_PAGE_COUNT);
                    }
                    log.info("load idf value file[" + idfFIle.getAbsolutePath() + "] finished!");

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
