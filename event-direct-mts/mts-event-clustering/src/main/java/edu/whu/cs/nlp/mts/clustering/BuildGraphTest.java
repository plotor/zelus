package edu.whu.cs.nlp.mts.clustering;

import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import edu.stanford.nlp.trees.Tree;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.utils.SerializeUtil;
import edu.whu.cs.nlp.mts.base.utils.VectorOperator;

/**
 * 口哨算法聚类，用于构图测试
 *
 * @author ZhenchaoWang 2015-11-10 14:23:27
 *
 */
public class BuildGraphTest implements GlobalConstant {

    private final Logger log                   = Logger.getLogger(this.getClass());

    private final VectorOperator vo = new VectorOperator();

    public void test() throws ClassNotFoundException, IOException {

        // 数字格式化器
        DecimalFormat decimalFormat = new DecimalFormat("0.######");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

        /**
         * 加载词向量字典文件
         */
        Map<String, Vector> vecDict = (Map<String, Vector>) SerializeUtil.readObj("E:/workspace/test/example/word-vector-dict/text.obj");

        /**
         * 加载当前主题下所有的文本
         */
        List<List<Word>> words = (List<List<Word>>) SerializeUtil.readObj("E:/workspace/test/example/text/obj/words/f16su24.obj");

        /**
         * 加载事件集合
         */
        Map<Integer, List<EventWithPhrase>> eventsMap = (Map<Integer, List<EventWithPhrase>>) SerializeUtil.readObj("E:/workspace/test/example/serializable-events/text/f16su24.obj");

        List<EventWithPhrase> events = new ArrayList<EventWithPhrase>();

        for (Entry<Integer, List<EventWithPhrase>> entry : eventsMap.entrySet()) {
            events.addAll(entry.getValue());
        }

        // 计算每个事件的向量
        List<Double[]> eventVecs = new ArrayList<Double[]>();
        for (EventWithPhrase eventWithPhrase : events) {
            eventVecs.add(this.vo.eventToVecPlus(eventWithPhrase, vecDict));
        }

        // 计算事件的中心向量
        Double[] centralVec = VectorOperator.centralVector(eventVecs);

        List<Double> eventsWeight = new ArrayList<Double>();
        // 计算每个事件的权重
        for (Double[] eventvec : eventVecs) {
            eventsWeight.add(VectorOperator.cosineDistence(centralVec, eventvec));
        }

        // 句子中每个词的权重
        for (List<Word> list : words) {
            StringBuilder inner = new StringBuilder();
            StringBuilder tagged = new StringBuilder();
            StringBuilder weighted = new StringBuilder();
            for (Word word : list) {
                if("root".equalsIgnoreCase(word.getName())) {
                    continue;
                }
                /* 计算当前词与当前类别中事件的加权距离
                 * 计算方式：
                 *     当前词与每个事件的距离*事件的权值，然后取平均
                 */
                Vector wordVec = vecDict.get(word.dictKey());
                double wordWeight = 0.0D;
                for(int i = 0; i < events.size(); i++) {
                    Double[] eventVec = eventVecs.get(i);
                    Double eventWeight = eventsWeight.get(i);
                    double distence = VectorOperator.cosineDistence(wordVec.doubleVecs(), eventVec);
                    wordWeight += distence * eventWeight;
                }
                wordWeight /= events.size();
                // 格式化
                wordWeight = Double.parseDouble(decimalFormat.format(wordWeight));

                inner.append(word.getName() + " ");
                tagged.append(word.getName() + "/" + (word.getPos().equals(word.getName()) ? "PUNCT" : word.getPos()) + " ");
                weighted.append(word.getName() + "/" + (word.getPos().equals(word.getName()) ? "PUNCT" : word.getPos()) + "/" + wordWeight + " ");
            }

            System.out.println(weighted);

        }

    }

    /**
     * 事件到子句的映射
     *
     * @param eventWithPhrase
     * @param subSentences
     *            当前事件所在句子的子句集合
     * @return
     */
    private String eventToSubSentence(EventWithPhrase eventWithPhrase, List<String> subSentList) {

        String subSentence = null;

        if (eventWithPhrase == null || CollectionUtils.isEmpty(subSentList)) {
            return subSentence;
        }

        List<Word> leftphrase = eventWithPhrase.getLeftPhrases();
        List<Word> middlephrase = eventWithPhrase.getMiddlePhrases();
        List<Word> rightphrase = eventWithPhrase.getRightPhrases();

        int i = subSentList.size() - 1;

        for (; i >= 0; i--) {
            String sentStr = subSentList.get(i);
            int count = 0;
            Set<String> wordSet = new HashSet<String>(Arrays.asList(sentStr.split("\\s+")));
            if (CollectionUtils.isNotEmpty(leftphrase)) {
                for (Word word : leftphrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }

            }
            if (CollectionUtils.isNotEmpty(middlephrase)) {
                for (Word word : middlephrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(rightphrase)) {
                for (Word word : rightphrase) {
                    if (!word.getName().equals(word.getPos()) && !STOPWORDS.contains(word.getLemma()) && wordSet.contains(word.getName())) {
                        ++count;
                        break;
                    }
                }
            }
            if (count >= 2) {
                subSentence = sentStr;
                break;
            }
        }

        return subSentence;
    }

    /**
     * 获取一句话中的所有子句集合
     *
     * @param tree
     * @param subSentList
     */
    private void subSentences(Tree tree, List<String> subSentList) {

        if ("S".equals(tree.label().toString()) || "SINV".equals(tree.label().toString())) {
            StringBuilder sb = new StringBuilder();
            this.subSentence(tree, sb);
            String strTmp = sb.toString().trim();
            if (StringUtils.isNotBlank(strTmp) && strTmp.split("\\s+").length >= 8) {
                // 长度大于8的子句进入候选
                subSentList.add(strTmp);
            }
        }

        List<Tree> childTrees = tree.getChildrenAsList();
        for (Tree childTree : childTrees) {
            this.subSentences(childTree, subSentList);
        }

    }

    /**
     * 返回一个s节点下面的完整子句
     *
     * @param tree
     * @param sb
     */
    private void subSentence(Tree tree, StringBuilder sb) {
        if (tree.isLeaf()) {
            sb.append(tree.nodeString() + " ");
            return;
        } else {
            List<Tree> childTrees = tree.getChildrenAsList();
            for (Tree child : childTrees) {
                this.subSentence(child, sb);
            }
        }
    }

    /**
     * 将字符串组织的句子替换成{@link Word}组织的句子
     *
     * @param strSentence
     * @param words
     * @return
     */
    private List<Word> sentenceObjectified(String strSentence, List<Word> words) {
        List<Word> objSentence = new ArrayList<Word>();
        if(StringUtils.isBlank(strSentence)) {
            return objSentence;
        }
        String[] strs = strSentence.split("\\s+");
        int num = 0;
        for (Word word : words) {
            if(strs[num].equals(word.getName())) {
                objSentence.add(word);
                num++;
                if(num == strs.length) {
                    break;
                }
            } else if(num > 0) {
                num = 0;
                objSentence.clear();
            }
        }
        if(num != strs.length) {
            objSentence.clear();
        }
        return objSentence;
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException {

        BuildGraphTest bgt = new BuildGraphTest();
        bgt.test();
    }

}
