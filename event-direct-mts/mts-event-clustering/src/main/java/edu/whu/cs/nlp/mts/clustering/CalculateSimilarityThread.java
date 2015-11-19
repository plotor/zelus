package edu.whu.cs.nlp.mts.clustering;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.biz.VectorOperator;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.NumedEventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.clustering.domain.CWEdge;

/**
 * 计算事件之间的相似度
 *
 * @author Apache_xiaochao
 */
public class CalculateSimilarityThread implements Callable<Boolean>, SystemConstant {

    private static Logger log = Logger.getLogger(CalculateSimilarityThread.class);

    private final String topicDir;

    private final String workDir;

    private final VectorOperator vectorOperator;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.000000");

    public CalculateSimilarityThread(String topicDir, String workDir) {
        this.topicDir = topicDir;
        this.workDir = workDir;
        this.vectorOperator = new VectorOperator();
    }

    @Override
    public Boolean call() throws Exception {

        log.info(Thread.currentThread().getId() +  " - calculating event similarity, dir:" + this.topicDir);

        /*
         * 加载当前主题下的词向量字典
         */
        int index = Math.max(this.topicDir.lastIndexOf("/"), this.topicDir.lastIndexOf("\\"));
        String topicName = this.topicDir.substring(index);

        String seralizeFilepath = this.workDir + "/" + DIR_WORDS_VECTOR + "/" + topicName + ".obj";
        Map<String, Vector> wordvecsInTopic = null;
        try{
            wordvecsInTopic = (Map<String, Vector>) SerializeUtil.readObj(this.workDir + "/" + DIR_WORDS_VECTOR + "/" + topicName + ".obj");
        } catch(Exception e) {

            log.error("Load seralize file[" + seralizeFilepath + "] error!", e);

            throw e;

        }

        if(MapUtils.isEmpty(wordvecsInTopic)) {
            log.error("The word vector dict is empty:" + seralizeFilepath);
            return false;
        }

        int num = 0; // 事件编号

        /*存放所有事件及其对应的序号*/
        Map<Integer, NumedEventWithPhrase> eventWithNums = new TreeMap<Integer, NumedEventWithPhrase>();

        Collection<File> eventFiles = FileUtils.listFiles(FileUtils.getFile(this.topicDir), null, false);

        for (File eventFile : eventFiles) {

            try {

                log.info("Loading serialize file: " + eventFile.getAbsolutePath());

                @SuppressWarnings("unchecked")
                Map<Integer, List<EventWithPhrase>> eventsInFile = (Map<Integer, List<EventWithPhrase>>) SerializeUtil.readObj(eventFile.getAbsolutePath());

                //对事件进行编号
                for (Entry<Integer, List<EventWithPhrase>> event : eventsInFile.entrySet()) {

                    //对事件进行编号，然后封装成对象存储
                    for (EventWithPhrase eventWithPhrase : event.getValue()) {

                        Double[] eventVec = this.vectorOperator.eventToVecPlus(eventWithPhrase, wordvecsInTopic);
                        if(eventVec == null) {
                            log.warn("The event[" + eventWithPhrase + "]'s vector is null, ignore it!");
                            continue;
                        }

                        NumedEventWithPhrase numedEventWithPhrase = new NumedEventWithPhrase();
                        numedEventWithPhrase.setNum(num);
                        numedEventWithPhrase.setEvent(eventWithPhrase);
                        // 事件对应的向量
                        numedEventWithPhrase.setVec(eventVec);
                        eventWithNums.put(num, numedEventWithPhrase);
                        ++num;

                    }
                }

            } catch (IOException e) {

                log.error("操作文件出错：" + eventFile.getAbsolutePath(), e);

            }

        }

        //将编号的事件保存
        if (eventWithNums.size() > 0) {
            File nodeFile = FileUtils.getFile(this.workDir + "/" + DIR_NODES, topicName + ".node.obj");
            try {
                SerializeUtil.writeObj(eventWithNums, nodeFile);
            } catch (IOException e) {
                log.error("Serilize file error:" + nodeFile.getAbsolutePath(), e);
                throw e;
            }

            // 将事件及其序号信息，写入文件
            /*StringBuilder sb_nodes = new StringBuilder();
            StringBuilder sb_nodes_simplify = new StringBuilder();
            for (Entry<Integer, NumedEventWithPhrase> entry : eventWithNums.entrySet()) {
                NumedEventWithPhrase numedEventWithPhrase = entry.getValue();
                sb_nodes.append((numedEventWithPhrase.getNum() + 1) + "\t" + numedEventWithPhrase.getEvent().toString() + LINE_SPLITER);
                sb_nodes_simplify.append((numedEventWithPhrase.getNum() + 1) + "\t" + numedEventWithPhrase.getEvent().toShortString() + LINE_SPLITER);
            }

            try {
                FileUtils.writeStringToFile(FileUtils.getFile(workDir + "/" + DIR_NODES, topicName + ".node"), CommonUtil.cutLastLineSpliter(sb_nodes.toString()), SystemConstant.DEFAULT_CHARSET);
                FileUtils.writeStringToFile(FileUtils.getFile(workDir + "/" + DIR_NODES, topicName + ".node.simplify"), CommonUtil.cutLastLineSpliter(sb_nodes_simplify.toString()), SystemConstant.DEFAULT_CHARSET);
            } catch (IOException e) {
                log.error("写文件出错：" + workDir + "/" + DIR_NODES + "/" + topicName + ".node", e);
            }*/

        } else {

            log.error("Can't find any event in[" + this.topicDir + "]");

        }

        // 计算事件之间的相似度，并保存成文件
        List<CWEdge> cwEdges = new ArrayList<CWEdge>();
        for (int i = 0; i < num; ++i) {
            for (int j = i + 1; j < num; ++j) {
                try {
                    // 计算向量的余弦值
                    double approx = VectorOperator.cosineDistence(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());

                    // 计算向量的欧式距离
                    //double approx = this.vectorOperator.euclideanDistance(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());

                    approx = Float.parseFloat(DECIMAL_FORMAT.format(approx));

                    if(approx < 0.0f) {
                        log.warn("[approx=" + approx + "]There is an error when calculate distence between [" + eventWithNums.get(i) + "] and [" + eventWithNums.get(j) + "], ignore it!");
                        continue;
                    }

                    if(approx == 0.0f) {
                        continue;
                    }

                    CWEdge cwEdge = new CWEdge(Integer.valueOf(i), Integer.valueOf(j), Float.valueOf((float)approx));
                    cwEdges.add(cwEdge);
                    //FileUtils.writeStringToFile(edgeFile, (i + 1) + "\t" + (j + 1) + "\t" + approx + LINE_SPLITER, DEFAULT_CHARSET, true);
                    /*FileUtils.writeStringToFile(edgeFile, (j + 1) + "\t" + (i + 1) + "\t" + approx + LINE_SPLITER, DEFAULT_CHARSET, true);*/
                    // 对数据进行整数化（*1000）
                    /*int intVal = (int) (approx * 1000);
                    FileUtils.writeStringToFile(intEdgeFile, (i + 1) + "\t" + (j + 1) + "\t" + intVal + LINE_SPLITER, DEFAULT_CHARSET, true);*/
                    /*FileUtils.writeStringToFile(intEdgeFile, (j + 1) + "\t" + (i + 1) + "\t" + intVal + LINE_SPLITER, DEFAULT_CHARSET, true);*/

                } catch (Exception e) {

                    log.error("计算事件相似度出错，事件1：" + eventWithNums.get(i).getEvent() + "， 事件2：" + eventWithNums.get(j).getEvent(), e);

                }
            }
        }

        File edgeFile = FileUtils.getFile(this.workDir + "/" + DIR_EDGES, topicName + ".edge.obj");
        try {

            SerializeUtil.writeObj(cwEdges, edgeFile);

        } catch (IOException e) {
            log.error("Serilize file error:" + edgeFile.getAbsolutePath(), e);
            throw e;
        }

        return true;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService es = Executors.newSingleThreadExecutor();
        //Future<Boolean> future = es.submit(new CalculateSimilarityThread("E:/workspace/test/serializable-events/D0732H", "db_cache_vec", "local"));
        Future<Boolean> future = es.submit(new CalculateSimilarityThread("E:/workspace/test/serializable-events/D0732H", "E:/workspace/test"));
        if(future.get()){
            System.out.println("success!");
        } else {
            System.out.println("failed!");
        }
        EhCacheUtil.close();
        es.shutdown();
    }

}
