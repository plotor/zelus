package edu.whu.cs.nlp.mts.clustering;

import edu.whu.cs.nlp.mts.clustering.domain.CWEdge;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.domain.EventWithPhrase;
import org.zhenchao.zelus.common.domain.NumedEventWithPhrase;
import org.zhenchao.zelus.common.domain.Vector;
import org.zhenchao.zelus.common.global.GlobalConstant;
import org.zhenchao.zelus.common.global.GlobalParam;
import org.zhenchao.zelus.common.util.CommonUtil;
import org.zhenchao.zelus.common.util.SerializeUtil;
import org.zhenchao.zelus.common.util.VectorOperator;

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
 * 计算事件之间的相似度
 *
 * @author Apache_xiaochao
 */
public class CalculateSimilarityThread implements Callable<Boolean>, GlobalConstant {

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
         * 加载当前主题下的词向量字典
         */
        int index = Math.max(this.topicDir.lastIndexOf("/"), this.topicDir.lastIndexOf("\\"));
        String topicName = this.topicDir.substring(index);
        String seralizeFilepath = GlobalParam.workDir + "/" + DIR_EVENTS_EXTRACT + "/" + OBJ + "/" + DIR_WORDS_VECTOR + "/" + topicName + ".obj";
        Map<String, Vector> wordvecsInTopic = null;
        try {
            wordvecsInTopic = (Map<String, Vector>) SerializeUtil.readObj(seralizeFilepath);
        } catch (Exception e) {
            log.error("Load seralize file[" + seralizeFilepath + "] error!", e);
            throw e;
        }

        if (MapUtils.isEmpty(wordvecsInTopic)) {
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
                        if (eventVec == null) {
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
            File nodeFile = FileUtils.getFile(objBaseDir + "/" + DIR_NODES, topicName + ".node.obj");
            try {
                SerializeUtil.writeObj(eventWithNums, nodeFile);
            } catch (IOException e) {
                log.error("Serilize file error:" + nodeFile.getAbsolutePath(), e);
                throw e;
            }
        } else {
            log.error("Can't find any event in[" + this.topicDir + "]");
        }

        // 计算事件之间的相似度，并保存成文件
        List<CWEdge> cwEdges = new ArrayList<CWEdge>();
        StringBuilder sb_nodes = new StringBuilder();
        StringBuilder sb_edges = new StringBuilder();
        for (int i = 0; i < num; ++i) {
            sb_nodes.append(i + "\t" + eventWithNums.get(i).getEvent().toShortString() + "\n");
            for (int j = i + 1; j < num; ++j) {
                try {
                    // 计算向量的余弦值
                    double approx = VectorOperator.cosineDistence(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());

                    // 计算向量的欧式距离
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
                    log.error("计算事件相似度出错，事件1：" + eventWithNums.get(i).getEvent() + "， 事件2：" + eventWithNums.get(j).getEvent(), e);
                }
            }
        }

        File text_nodeFile = FileUtils.getFile(textBaseDir + "/" + DIR_NODES, topicName + ".node.txt");
        FileUtils.writeStringToFile(text_nodeFile, CommonUtil.cutLastLineSpliter(sb_nodes.toString()), DEFAULT_ENCODING);

        File text_edgeFile = FileUtils.getFile(textBaseDir + "/" + DIR_EDGES, topicName + ".edge.txt");
        FileUtils.writeStringToFile(text_edgeFile, CommonUtil.cutLastLineSpliter(sb_edges.toString()), DEFAULT_ENCODING);

        File edgeFile = FileUtils.getFile(objBaseDir + "/" + DIR_EDGES, topicName + ".edge.obj");
        try {
            SerializeUtil.writeObj(cwEdges, edgeFile);
        } catch (IOException e) {
            log.error("Serilize file error:" + edgeFile.getAbsolutePath(), e);
            throw e;
        }

        return true;
    }

}
