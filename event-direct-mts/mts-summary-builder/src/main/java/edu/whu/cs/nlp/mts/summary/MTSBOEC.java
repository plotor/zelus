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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.msc.SentenceReRanker;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.clustering.CalculateSimilarityThread;
import edu.whu.cs.nlp.mts.clustering.ChineseWhispersCluster;
import edu.whu.cs.nlp.mts.extraction.graph.EventsExtractBasedOnGraphV2;

/**
 * 驱动类
 *
 * @author ZhenchaoWang 2015-10-20 10:55:06
 *
 */
public class MTSBOEC implements SystemConstant {

    private static final Logger log = Logger.getLogger(MTSBOEC.class);

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            System.err.println("请指定配置文件！");
            return;
        }

        String propFilePath = args[0]; // 配置文件所在路径

        /*
         * 加载配置文件
         */
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propFilePath));
        } catch (IOException e) {
            MTSBOEC.log.error("load properties failed!", e);
            return;
        }

        // 获取线程数
        int threadNum = Integer.parseInt(properties.getProperty("threadNum", "2"));
        String textDir = properties.getProperty("textDir");
        String workDir = properties.getProperty("workDir");
        String cacheName = properties.getProperty("cacheName");
        String datasource = properties.getProperty("datasource");

        /**
         * 执行事件抽取操作
         */
        if ("y".equalsIgnoreCase(properties.getProperty("isExtractEvent"))) {

            MTSBOEC.log.info("Starting event extract: " + textDir);

            File textDirFile = new File(textDir);

            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();

            EhCacheUtil ehCacheUtil = new EhCacheUtil(cacheName, datasource);

            for (File dir : textDirFile.listFiles()) {

                tasks.add(new EventsExtractBasedOnGraphV2(dir.getAbsolutePath(), workDir, ehCacheUtil));

            }

            /* 执行完成之前，主线程阻塞 */
            ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
            try {
                List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                MTSBOEC.log.error("There is an exception when extract events!", e);
                return;
            } finally {
                EhCacheUtil.close();
                executorService.shutdown();
            }

        } else {

            MTSBOEC.log.info("Events extract is not enabled!");

        }

        /**
         * 计算事件之间的相似度
         */
        int nThreadSimiarity = Integer.parseInt(properties.getProperty("nThreadSimiarity")); // 计算事件相似度的线程数量
        if ("y".equalsIgnoreCase(properties.getProperty("isCalculateSimilarity"))) {

            MTSBOEC.log.info("Starting calculate events similarity...");

            File eventDirFile = new File(workDir + "/" + SystemConstant.DIR_SERIALIZE_EVENTS);
            File[] topicDirs = eventDirFile.listFiles();
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            for (File topicDir : topicDirs) {
                tasks.add(new CalculateSimilarityThread(topicDir.getAbsolutePath(), workDir));
            }
            if (CollectionUtils.isNotEmpty(tasks)) {
                ExecutorService executorService = Executors.newFixedThreadPool(nThreadSimiarity);
                try {
                    List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                    for (Future<Boolean> future : futures) {
                        future.get();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    MTSBOEC.log.error("There is an exception when calculate events similarity!", e);
                } finally {
                    executorService.shutdown();
                }
            }
        } else {
            MTSBOEC.log.info("Events similarity calculate is not enabled!");
        }

        /**
         * 对事件进行聚类，同时按类别抽取事件所在子句
         */
        if ("y".equalsIgnoreCase(properties.getProperty("isEventCluster"))) {

            MTSBOEC.log.info("Starting events clusting & sub sentences extracting...");

            String dictPath = properties.getProperty("dictPath");

            File nodeFile = new File(workDir + "/" + SystemConstant.DIR_NODES);
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
                    String edgePath = workDir + "/" + SystemConstant.DIR_EDGES + "/" + file.getName().replace("node", "edge");
                    String wordvecDictPath = workDir + "/" + SystemConstant.DIR_WORDS_VECTOR + "/" + file.getName().replace(".node", "");
                    String topicPath = textDir + "/" + file.getName().substring(0, file.getName().indexOf("."));
                    tasks.add(new ChineseWhispersCluster(topicPath, nodePath, edgePath, wordvecDictPath, dictPath));
                }

                List<Future<Boolean>> futures = es.invokeAll(tasks);
                for (Future<Boolean> future : futures) {
                    future.get();
                }

            } catch (Throwable e) {

                MTSBOEC.log.error("Event cluster & Sentence Extract error!", e);

            } finally {
                if (es != null) {
                    es.shutdown();
                }
            }

        } else {
            MTSBOEC.log.info("Events clusting is not enabled!");
        }

        /**
         * 摘要生成
         */
        if ("y".equalsIgnoreCase(properties.getProperty("isBuildSummary"))) {

            // 加载question文件
            Properties prop = new Properties();
            try {
                String questionFilename = properties.getProperty("question_filename");
                MTSBOEC.log.info("Loading question file[" + questionFilename + "]...");
                prop.load(new InputStreamReader(MTSBOEC.class.getClassLoader().getResourceAsStream(questionFilename)));
                MTSBOEC.log.info("Loading question file[" + questionFilename + "] success!");
            } catch (IOException e) {
                MTSBOEC.log.error("Load question file error!", e);
                throw e;
            }

            String buildMode = properties.getProperty("buildMode");

            float alpha = Float.parseFloat(properties.getProperty("alpha"));
            float beta = Float.parseFloat(properties.getProperty("alpha"));

            EhCacheUtil ehCacheUtil = new EhCacheUtil(properties.getProperty("cacheName"), properties.getProperty("datasource"));
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            File compressFiles = new File(workDir + "/" + SystemConstant.DIR_SENTENCES_COMPRESSION);

            if ("old".equalsIgnoreCase(buildMode)) {
                // 采用老的reranker策略构建摘要
                log.info("Summary build mode: old");
                // ngram 模型所在路径
                String ngramModelPath = properties.getProperty("ngramModelPath");
                for (File file : compressFiles.listFiles()) {
                    String topicName = file.getName().substring(0, file.getName().lastIndexOf("."));
                    tasks.add(new SentenceReRanker(prop.getProperty(topicName), file.getAbsolutePath(), ehCacheUtil, ngramModelPath, alpha, beta));
                }

            } else if ("new".equalsIgnoreCase(buildMode)) {
                // 采用子模函数构建摘要
                log.info("Summary build mode: new");

                int sentenceCount = Integer.parseInt(properties.getProperty("sentenceCount"));
                String idfFilename = properties.getProperty("idf_filename");

                // 加载每个词的IDF值
                /** Google 总页面数估值 */
                final double TOTAL_PAGE_COUNT = 30000000000.0D;

                Map<String, Double> idfValues = new HashMap<String, Double>();
                File idfFIle = FileUtils.getFile(workDir + "/" + DIR_IDF_FILE, idfFilename);
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
                    //tasks.add(new SummaryBuilder(workDir, file.getName(), sentenceCount, idfValues, prop.getProperty(topicName), alpha, beta));
                    tasks.add(new SummaryBuilderByVector(workDir, file.getName(), sentenceCount, idfValues, prop.getProperty(topicName), ehCacheUtil, alpha, beta));
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
                EhCacheUtil.close();
            }

        } else {
            MTSBOEC.log.info("Build summary is not enabled!");
        }

    }

}
