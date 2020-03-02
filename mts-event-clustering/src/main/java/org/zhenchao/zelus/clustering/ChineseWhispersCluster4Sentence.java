package org.zhenchao.zelus.clustering;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.clustering.cw.CW;
import org.zhenchao.zelus.clustering.cw.graph.ArrayBackedGraph;
import org.zhenchao.zelus.clustering.cw.graph.Graph;
import org.zhenchao.zelus.clustering.domain.SentenceApprox;
import org.zhenchao.zelus.clustering.domain.SentenceVector;
import org.zhenchao.zelus.common.util.CommonUtil;
import org.zhenchao.zelus.common.util.SerializeUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * 口哨算法聚类，对句子进行聚类
 *
 * @author ZhenchaoWang 2015-11-29 19:35:00
 */
public class ChineseWhispersCluster4Sentence {

    private static Logger log = Logger.getLogger(ChineseWhispersCluster4Sentence.class);

    public static void main(String[] args) {

        // 数字格式化器
        DecimalFormat decimalFormat = new DecimalFormat("0.######");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

        String baseDir = "E:/workspace/test/sentences-clust";

        File topics = new File(baseDir + "/nodes");
        String[] nodes = topics.list(new FilenameFilter() {

            @Override
            public boolean accept(File file, String name) {
                if (name.endsWith(".obj")) {
                    return true;
                }
                return false;
            }
        });

        for (String nodeName : nodes) {
            String topicName = nodeName.substring(0, nodeName.length() - 9);

            // 加载当前主题下的句子集合
            List<SentenceVector> sentenceVectors;
            try {
                sentenceVectors = (List<SentenceVector>) SerializeUtil.readObj(baseDir + "/nodes/" + topicName + ".node.obj");
            } catch (ClassNotFoundException | IOException e1) {
                log.error("Load serilized file error!", e1);
                return;
                //e1.printStackTrace();
            }
            log.info("Load node file:" + topicName + ".node.obj, size:" + sentenceVectors.size());

            // 加载当前主题下的句子相似度
            List<SentenceApprox> sentenceApproxs;
            try {
                sentenceApproxs = (List<SentenceApprox>) SerializeUtil.readObj(baseDir + "/edges/" + topicName + ".edge.obj");
            } catch (ClassNotFoundException | IOException e1) {
                log.error("Load serilized file error!", e1);
                return;
                //e1.printStackTrace();
            }
            log.info("Load edge file:" + topicName + ".edge.obj, size:" + sentenceApproxs.size());

            /**
             * 构建事件图
             */
            Graph<Integer, Float> graph = new ArrayBackedGraph<Float>(sentenceVectors.size(), sentenceVectors.size());
            // 添加结点
            int num = 0;
            for (SentenceVector sentenceVector : sentenceVectors) {
                graph.addNode(++num);
            }
            // 计算所有边权值的均值
            double totalWeight = 0.0;
            for (SentenceApprox sentenceApprox : sentenceApproxs) {
                totalWeight += sentenceApprox.getApprox();
            }

            double avgWeight = totalWeight / sentenceApproxs.size();
            // 添加边
            for (SentenceApprox sentenceApprox : sentenceApproxs) {
                if (sentenceApprox.getApprox() >= avgWeight * 1.26) {
                    graph.addEdgeUndirected(sentenceApprox.getFrom(), sentenceApprox.getTo(), Float.parseFloat(decimalFormat.format(sentenceApprox.getApprox())));
                }
            }

            /**
             * 采用口哨算法进行聚类
             */
            log.info("Chinese Whispers clusting...[" + topicName + "]");
            CW<Integer> cw = new CW<Integer>();
            Map<Integer, Set<Integer>> clusterSentences = cw.findClusters(graph);
            log.info("Chinese Whispers clust finished[" + topicName + "], class size:" + clusterSentences.size());

            StringBuilder clustText = new StringBuilder();
            for (Entry<Integer, Set<Integer>> entry : clusterSentences.entrySet()) {
                if (entry.getValue().size() >= 5) {
                    clustText.append("classes_" + entry.getKey() + "\n");
                    for (Integer key : entry.getValue()) {
                        String sentence = sentenceVectors.get(key - 1).getSentence();
                        clustText.append(sentence + "\n");
                    }
                }
            }

            // 持久化
            File saveFile = FileUtils.getFile(baseDir + "/clust-sents", topicName);
            try {
                log.info("Saving file:" + saveFile.getAbsolutePath());
                FileUtils.writeStringToFile(saveFile, CommonUtil.cutLastLineSpliter(clustText.toString()), "UTF-8");
            } catch (IOException e) {
                log.error("Save file[" + saveFile.getAbsolutePath() + "] error!", e);
                //e.printStackTrace();
            }

        }

    }

}
