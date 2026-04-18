package org.zhenchao.zelus.cluster;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.cluster.domain.CWEdge;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.GlobalParam;
import org.zhenchao.zelus.common.pojo.EventWithPhrase;
import org.zhenchao.zelus.common.pojo.NumedEventWithPhrase;
import org.zhenchao.zelus.common.pojo.Vector;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.common.util.VectorOperator;
import org.zhenchao.zelus.common.util.ZelusUtils;

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

/**
 * Calculate similarity between events
 *
 * @author zhenchao
 */
public class CalculateSimilarityThread implements Callable<Boolean>, Constants {

    private static final Logger log = Logger.getLogger(CalculateSimilarityThread.class);

    private final String topicDir;

    private final VectorOperator vectorOperator;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.000000");

    public CalculateSimilarityThread(String topicDir) {
        this.topicDir = topicDir;
        this.vectorOperator = new VectorOperator();
    }

    @Override
    public Boolean call() throws Exception {

        log.info("Thread " + Thread.currentThread().getId() + " -> calculating event similarity, dir:" + this.topicDir);

        String objBaseDir = GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + OBJ;
        String textBaseDir = GlobalParam.workDir + "/" + DIR_EVENTS_CLUST + "/" + TEXT;

        /*
         * 加载Current topic下的Word vector dictionary
         */
        int index = Math.max(this.topicDir.lastIndexOf("/"), this.topicDir.lastIndexOf("\\"));
        String topicName = this.topicDir.substring(index);
        String seralizeFilepath = GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_WORDS_VECTOR + "/" + topicName + ".obj";
        Map<String, Vector> wordvecsInTopic = null;
        try {
            wordvecsInTopic = (Map<String, Vector>) SerializeUtils.readObj(seralizeFilepath);
        } catch (Exception e) {
            log.error("Load seralize file[" + seralizeFilepath + "] error!", e);
            throw e;
        }

        if (MapUtils.isEmpty(wordvecsInTopic)) {
            log.error("The word vector dict is empty:" + seralizeFilepath);
            return false;
        }

        int num = 0; // Event编号

        /*存放所有Event及其Pairs应的序号*/
        Map<Integer, NumedEventWithPhrase> eventWithNums = new TreeMap<Integer, NumedEventWithPhrase>();

        Collection<File> eventFiles = FileUtils.listFiles(FileUtils.getFile(this.topicDir), null, false);

        for (File eventFile : eventFiles) {
            try {
                log.info("Loading serialize file: " + eventFile.getAbsolutePath());
                @SuppressWarnings("unchecked")
                Map<Integer, List<EventWithPhrase>> eventsInFile = (Map<Integer, List<EventWithPhrase>>) SerializeUtils.readObj(eventFile.getAbsolutePath());

                //Perform event 编号
                for (Entry<Integer, List<EventWithPhrase>> event : eventsInFile.entrySet()) {

                    //Perform event 编号，然后封装成Pairs象存储
                    for (EventWithPhrase eventWithPhrase : event.getValue()) {

                        Double[] eventVec = this.vectorOperator.eventToVecPlus(eventWithPhrase, wordvecsInTopic);
                        if (eventVec == null) {
                            log.warn("The event[" + eventWithPhrase + "]'s vector is null, ignore it!");
                            continue;
                        }

                        NumedEventWithPhrase numedEventWithPhrase = new NumedEventWithPhrase();
                        numedEventWithPhrase.setNum(num);
                        numedEventWithPhrase.setEvent(eventWithPhrase);
                        // EventPairs应的Vector
                        numedEventWithPhrase.setVec(eventVec);
                        eventWithNums.put(num, numedEventWithPhrase);
                        ++num;

                    }
                }

            } catch (IOException e) {
                log.error("操作文件error：" + eventFile.getAbsolutePath(), e);
            }

        }

        //将编号的Event保存
        if (eventWithNums.size() > 0) {
            File nodeFile = FileUtils.getFile(objBaseDir + "/" + DIR_NODES, topicName + ".node.obj");
            try {
                SerializeUtils.writeObj(eventWithNums, nodeFile);
            } catch (IOException e) {
                log.error("Serilize file error:" + nodeFile.getAbsolutePath(), e);
                throw e;
            }
        } else {
            log.error("Can't find any event in[" + this.topicDir + "]");
        }

        // Calculate similarity between events，并保存成文件
        List<CWEdge> cwEdges = new ArrayList<CWEdge>();
        StringBuilder sb_nodes = new StringBuilder();
        StringBuilder sb_edges = new StringBuilder();
        for (int i = 0; i < num; ++i) {
            sb_nodes.append(i + "\t" + eventWithNums.get(i).getEvent().toShortString() + "\n");
            for (int j = i + 1; j < num; ++j) {
                try {
                    // CalculateVector的余弦值
                    double approx = VectorOperator.cosineDistence(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());

                    // CalculateVector的欧式距离
                    //double approx = this.vectorOperator.euclideanDistance(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());

                    approx = Float.parseFloat(DECIMAL_FORMAT.format(approx));

                    if (approx < 0.0f) {
                        log.warn("[approx=" + approx + "]There is an error when calculate distence between [" + eventWithNums.get(i) + "] and [" + eventWithNums.get(j) + "], ignore it!");
                        continue;
                    }

                    if (approx == 0.0f) {
                        continue;
                    }

                    int int_approx = (int) (approx * 1000000);
                    sb_edges.append(i + "\t" + j + "\t" + int_approx + "\n");

                    CWEdge cwEdge = new CWEdge(Integer.valueOf(i), Integer.valueOf(j), Float.valueOf((float) approx));
                    cwEdges.add(cwEdge);

                } catch (Exception e) {
                    log.error("CalculateEvent相似度error，Event1：" + eventWithNums.get(i).getEvent() + "， Event2：" + eventWithNums.get(j).getEvent(), e);
                }
            }
        }

        File text_nodeFile = FileUtils.getFile(textBaseDir + "/" + DIR_NODES, topicName + ".node.txt");
        FileUtils.writeStringToFile(text_nodeFile, ZelusUtils.cutLastLineSpliter(sb_nodes.toString()), DEFAULT_ENCODING);

        File text_edgeFile = FileUtils.getFile(textBaseDir + "/" + DIR_EDGES, topicName + ".edge.txt");
        FileUtils.writeStringToFile(text_edgeFile, ZelusUtils.cutLastLineSpliter(sb_edges.toString()), DEFAULT_ENCODING);

        File edgeFile = FileUtils.getFile(objBaseDir + "/" + DIR_EDGES, topicName + ".edge.obj");
        try {
            SerializeUtils.writeObj(cwEdges, edgeFile);
        } catch (IOException e) {
            log.error("Serilize file error:" + edgeFile.getAbsolutePath(), e);
            throw e;
        }

        return true;
    }

}
