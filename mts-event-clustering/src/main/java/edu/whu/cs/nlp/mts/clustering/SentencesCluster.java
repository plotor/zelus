package edu.whu.cs.nlp.mts.clustering;

import edu.whu.cs.nlp.mts.clustering.domain.SentenceApprox;
import edu.whu.cs.nlp.mts.clustering.domain.SentenceVector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.util.SerializeUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 句子聚类，用于对比试验
 *
 * @author ZhenchaoWang 2015-11-29 18:30:10
 */
public class SentencesCluster {

    private static Logger log = Logger.getLogger(SentencesCluster.class);

    public static double cosineDistence(float[] vec1, float[] vec2) {

        double value = -1;

        if (vec1 == null || vec2 == null) {
            return value;
        }

        //利用向量余弦值来计算事件之间的相似度
        double scalar = 0;  //两个向量的内积
        double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
        for (int i = 0; i < 300; i++) {
            scalar += vec1[i] * vec2[i];
            module_1 += vec1[i] * vec1[i];
            module_2 += vec2[i] * vec2[i];
        }

        if (module_1 > 0 && module_2 > 0) {
            value = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)) + 1;
        }

        return value;

    }

    public static void main(String[] args) {

        String baseDir = "E:/workspace/test/sentences-clust";

        File topics = new File(baseDir + "/seg-pos");

        String[] topicNames = topics.list();

        for (String topicName : topicNames) {

            log.info("processing topic:" + topicName);

            // 加载当前主题下的句子和对应句子向量
            File texts = new File(baseDir + "/seg-pos/" + topicName);
            String[] filenames = texts.list();
            List<SentenceVector> sentenceVecs = new ArrayList<SentenceVector>();
            for (String filename : filenames) {

                // 加载句子
                List<String> sentences = new ArrayList<String>();
                File textFile = FileUtils.getFile(baseDir + "/seg-pos/" + topicName, filename);
                LineIterator lineIterator = null;
                try {
                    lineIterator = FileUtils.lineIterator(textFile, "UTF-8");
                    while (lineIterator.hasNext()) {
                        sentences.add(lineIterator.nextLine());
                    }

                } catch (IOException e) {
                    log.error("Load file[" + textFile.getAbsolutePath() + "] error!", e);
                    //e.printStackTrace();
                } finally {
                    if (lineIterator != null) {
                        lineIterator.close();
                    }
                }

                // 加载向量
                List<String> vectors = new ArrayList<String>();
                File vectorFile = FileUtils.getFile(baseDir + "/sent-vec/" + topicName, filename + ".vec");
                try {
                    lineIterator = FileUtils.lineIterator(vectorFile, "UTF-8");
                    int num = 0;
                    while (lineIterator.hasNext()) {
                        if (num++ == 0) {
                            lineIterator.nextLine();
                            continue;
                        }

                        String line = lineIterator.nextLine();
                        if (StringUtils.isNotBlank(line)) {
                            vectors.add(line);
                        }
                    }
                } catch (IOException e) {
                    log.error("Load file[" + vectorFile.getAbsolutePath() + "] error!", e);
                    //e.printStackTrace();
                } finally {
                    if (lineIterator != null) {
                        lineIterator.close();
                    }
                }

                if (sentences.size() != vectors.size()) {
                    log.error("The sentences is not match vectors in file:[" + filename + "], sentences:[" + sentences.size() + "], vectors:[" + vectors.size() + "]");
                    return;
                }

                for (int i = 0; i < sentences.size(); i++) {
                    String sentence = sentences.get(i);
                    String vector = vectors.get(i);

                    SentenceVector sentenceVector = new SentenceVector(sentence, vector);
                    sentenceVecs.add(sentenceVector);
                }

            }

            // 对当前事件按照编号保存
            log.info("saving node:" + topicName);
            StringBuilder senetncesInTopic = new StringBuilder();
            for (int i = 0; i < sentenceVecs.size(); i++) {
                senetncesInTopic.append((i + 1) + "\t" + sentenceVecs.get(i).getSentence() + "\n");
            }

            File nodeFile = FileUtils.getFile(baseDir + "/nodes", topicName + ".node.obj");
            try {
                //FileUtils.writeStringToFile(nodeFile, senetncesInTopic.toString(), "UTF-8");
                SerializeUtil.writeObj(sentenceVecs, nodeFile);
            } catch (IOException e) {
                log.error("Save file[" + nodeFile.getAbsolutePath() + "] error!", e);
                //e.printStackTrace();
            }

            // 计算当前主题下句子之间的相似度
            log.info("calculating approx:" + topicName);
            StringBuilder sentencesApprox = new StringBuilder();
            List<SentenceApprox> sentenceApproxs = new ArrayList<SentenceApprox>();
            for (int i = 0; i < sentenceVecs.size(); i++) {
                for (int j = i + 1; j < sentenceVecs.size(); j++) {
                    double approx = cosineDistence(sentenceVecs.get(i).getVector(), sentenceVecs.get(j).getVector());
                    sentencesApprox.append((i + 1) + "\t" + (j + 1) + "\t" + approx + "\n");
                    SentenceApprox sentenceApprox = new SentenceApprox(i + 1, j + 1, approx);
                    sentenceApproxs.add(sentenceApprox);
                }
            }

            log.info("saving edge:" + topicName);
            File edgeFile = FileUtils.getFile(baseDir + "/edges", topicName + ".edge.obj");
            try {
                //FileUtils.writeStringToFile(edgeFile, sentencesApprox.toString(), "UTF-8");
                SerializeUtil.writeObj(sentenceApproxs, edgeFile);
            } catch (IOException e) {
                log.error("Save file[" + edgeFile.getAbsolutePath() + "] error!", e);
                //e.printStackTrace();
            }

        }

    }

}
