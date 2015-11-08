package edu.whu.cs.nlp.mts.clustering;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.NumedEventWithPhrase;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;

/**
 * 计算事件之间的相似度
 *
 * @author Apache_xiaochao
 */
public class CalculateSimilarityThread implements Callable<Boolean>, SystemConstant {

    private static Logger log = Logger.getLogger(CalculateSimilarityThread.class);

    private final String topicDir;

    private final VectorOperator vectorOperator;

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#0.0000000000");

    public CalculateSimilarityThread(String topicDir, String cacheName, String datasource) {
        this.topicDir = topicDir;
        this.vectorOperator = new VectorOperator(cacheName, datasource);
    }

    @Override
    public Boolean call() throws Exception {

        log.info(Thread.currentThread().getId() +  " - calculating event similarity, dir:" + this.topicDir);

        int index = Math.max(this.topicDir.lastIndexOf("/"), this.topicDir.lastIndexOf("\\"));
        String topicName = this.topicDir.substring(index);
        String workDir = this.topicDir.substring(0, index - DIR_SERIALIZE_EVENTS.length());

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

                        Double[] eventVec = this.vectorOperator.eventToVecPlus(eventWithPhrase);
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

            // 将事件及其序号信息，写入文件
            StringBuilder sb_nodes = new StringBuilder();
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
            }

        } else {
            log.error("Can't find any event in[" + this.topicDir + "]");
        }

        //如果存放文件路径不存在，则创建
        File edgeFile = FileUtils.getFile(workDir + "/" + DIR_EDGES, topicName + ".edge");
        if (edgeFile.exists()) {
            // 如果已存在则删除
            edgeFile.delete();
        }
        // 对相似值进行整数化之后的结果
        File intEdgeFile = FileUtils.getFile(workDir + "/" + DIR_EDGES + "/int-val", topicName + ".edge");
        if (intEdgeFile.exists()) {
            // 如果已存在则删除
            intEdgeFile.delete();
        }

        // 计算事件之间的相似度，并保存成文件
        for (int i = 0; i < num; ++i) {
            for (int j = i + 1; j < num; ++j) {
                try {
                    // 计算向量的余弦值
                    double approx = this.vectorOperator.cosineValue(eventWithNums.get(i).getVec(), eventWithNums.get(j).getVec());
                    if (approx >= 0 && approx <= 1) {
                        // 当前节点之间有边
                        approx = Float.parseFloat(DECIMAL_FORMAT.format(approx));
                        FileUtils.writeStringToFile(edgeFile, (i + 1) + "\t" + (j + 1) + "\t" + approx + LINE_SPLITER, DEFAULT_CHARSET, true);
                        /*FileUtils.writeStringToFile(edgeFile, (j + 1) + "\t" + (i + 1) + "\t" + approx + LINE_SPLITER, DEFAULT_CHARSET, true);*/
                        // 对数据进行整数化（*1000）
                        int intVal = (int) (approx * 1000);
                        FileUtils.writeStringToFile(intEdgeFile, (i + 1) + "\t" + (j + 1) + "\t" + intVal + LINE_SPLITER, DEFAULT_CHARSET, true);
                        /*FileUtils.writeStringToFile(intEdgeFile, (j + 1) + "\t" + (i + 1) + "\t" + intVal + LINE_SPLITER, DEFAULT_CHARSET, true);*/
                    } else {

                        log.warn("There is an error when calculate similarity[approx=" + approx + "] between events[" + eventWithNums.get(i) + "] and [" + eventWithNums.get(j) + "]");

                    }
                } catch (Exception e) {

                    log.error("计算事件相似度出错，事件1：" + eventWithNums.get(i).getEvent() + "， 事件2：" + eventWithNums.get(j).getEvent(), e);

                }
            }
        }

        return true;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService es = Executors.newSingleThreadExecutor();
        Future<Boolean> future = es.submit(new CalculateSimilarityThread("E:/workspace/test/serializable-events/D0709B", "db_cache_vec", "localhost-3306-vec"));
        future.get();
    }

}
