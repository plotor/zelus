package edu.whu.cs.nlp.mts.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.CWRunParam;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.clustering.CalculateSimilarityThread;
import edu.whu.cs.nlp.mts.clustering.ClusterByChineseWhispers;
import edu.whu.cs.nlp.mts.extraction.graph.EventsExtractBasedOnGraphV2;

/**
 * 驱动类
 * @author ZhenchaoWang 2015-10-20 10:55:06
 *
 */
public class MTSBOEC implements SystemConstant{

    private static final Logger log = Logger.getLogger(MTSBOEC.class);
    public static void main(String[] args) {

        if(args.length == 0){
            System.err.println("请指定配置文件！");
            return;
        }

        String propFilePath = args[0];  //配置文件所在路径

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

        //获取线程数
        int threadNum = Integer.parseInt(properties.getProperty("threadNum", "2"));
        String textDir = properties.getProperty("textDir");
        String workDir = properties.getProperty("workDir");

        /**
         * 执行事件抽取操作
         */
        if("y".equalsIgnoreCase(properties.getProperty("isExtractEvent"))){

            log.info("Starting event extract: " + textDir);

            File textDirFile = new File(textDir);

            List<Callable<Map<String, Map<Integer, List<EventWithPhrase>>>>> tasks = new ArrayList<Callable<Map<String, Map<Integer, List<EventWithPhrase>>>>>();

            for (File dir : textDirFile.listFiles()) {

                tasks.add(new EventsExtractBasedOnGraphV2(dir.getAbsolutePath()));

            }

            if(CollectionUtils.isNotEmpty(tasks)){

                /*执行完成之前，主线程阻塞*/

                ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
                try {

                    List<Future<Map<String, Map<Integer, List<EventWithPhrase>>>>> futures = executorService.invokeAll(tasks);

                    for (Future<Map<String, Map<Integer, List<EventWithPhrase>>>> future : futures) {

                        Map<String, Map<Integer, List<EventWithPhrase>>> eventsInTopic = future.get();

                        log.info("Starting seralize events...");
                        for (Entry<String, Map<Integer, List<EventWithPhrase>>> eventsInFile : eventsInTopic.entrySet()) {

                            String key = eventsInFile.getKey();
                            String[] strs = key.split("/");
                            String topic = strs[0];
                            String filename = strs[1];
                            try {
                                // 将事件抽取结果按照原文件的组织方式进行序列化存储
                                SerializeUtil.writeObj(eventsInFile.getValue(), FileUtils.getFile(workDir + "/" + DIR_SERIALIZE_EVENTS + "/" + topic, filename + SUFFIX_SERIALIZE_FILE));

                            } catch (IOException e) {

                                log.error("Seralize error:" + topic + "/" + filename + SUFFIX_SERIALIZE_FILE, e);

                            }
                        }
                        log.info("Finished seralize events.");

                    }

                } catch (InterruptedException | ExecutionException e) {

                    log.error("There is an exception when extract events!", e);

                    return;

                } finally{

                    executorService.shutdown();

                }
            }

        }else{

            log.info("Events extract is not enabled!");

        }

        /**
         * 计算事件之间的相似度
         */
        int nThreadSimiarity = Integer.parseInt(properties.getProperty("nThreadSimiarity"));  //计算事件相似度的线程数量
        if("y".equalsIgnoreCase(properties.getProperty("isCalculateSimilarity"))){

            log.info("Starting calculate events similarity...");

            String cacheName = properties.getProperty("cacheName");
            String datasource = properties.getProperty("datasource");
            File eventDirFile = new File(workDir + "/" + DIR_SERIALIZE_EVENTS);
            File[] topicDirs = eventDirFile.listFiles();
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            for (File topicDir : topicDirs) {
                tasks.add(new CalculateSimilarityThread(topicDir.getAbsolutePath(), cacheName, datasource));
            }
            if(CollectionUtils.isNotEmpty(tasks)) {
                ExecutorService executorService = Executors.newFixedThreadPool(nThreadSimiarity);
                try {
                    List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                    for (Future<Boolean> future : futures) {
                        future.get();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.error("There is an exception when calculate events similarity!", e);
                }finally{
                    executorService.shutdown();
                }
            }
        }else{
            log.info("Events similarity calculate is not enabled!");
        }

        /**
         * 对事件进行聚类，同时按类别抽取时间所在子句
         */
        if("y".equalsIgnoreCase(properties.getProperty("isEventCluster"))){
            log.info(">> events clusting & sub sentences extracting");
            final String nodesDir = workDir + "/" + DIR_NODES;
            final String edgeDir = workDir + "/" + DIR_EDGES;
            final String clustResultDir = workDir + "/" + DIR_EVENTS_CLUST;
            final String sentencesSaveDir = workDir + "/" + DIR_SUB_SENTENCES_EXTRACTED;
            final String moduleFilePath = workDir + "/en-pos-maxent.bin";
            final String dictPath = properties.getProperty("dictPath");
            final float edgeSelectedWeight = 3.2f;  //边阈值增加权重
            boolean isPret = true;
            boolean isClust = true;
            if("n".equalsIgnoreCase(properties.getProperty("isPret"))){
                isPret = false;
            }
            if("n".equalsIgnoreCase(properties.getProperty("isClust"))){
                isClust = false;
            }
            final ClusterByChineseWhispers cluster =
                    new ClusterByChineseWhispers(
                            nodesDir, edgeDir, clustResultDir, textDir,
                            sentencesSaveDir, moduleFilePath, threadNum, edgeSelectedWeight, isPret, isClust, dictPath);
            try {
                //对事件进行聚类
                //构建口哨算法运行参数
                final CWRunParam cwRunParam = new CWRunParam();
                cwRunParam.setJarPath(properties.getProperty("cwjarPath"));
                cwRunParam.setKeepClassRate(0.0f);
                cwRunParam.setMutation_rate(0.21f);
                cwRunParam.setIterationCount(100);
                cluster.doCluster(cwRunParam);
                //获取事件对应的句子
                cluster.clusterSentencesByEvents();
            } catch (IOException | InterruptedException e) {
                log.error("There is an exception when calculate events clusting!", e);
            }

        }else{
            log.info("Events clusting is not enabled!");
        }

    }

}
