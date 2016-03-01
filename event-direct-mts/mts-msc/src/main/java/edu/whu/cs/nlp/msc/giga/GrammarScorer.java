package edu.whu.cs.nlp.msc.giga;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * 语言模型打分器
 *
 * @author ZhenchaoWang 2015-11-20 14:53:59
 *
 */
public class GrammarScorer {

    private final Logger                       log = Logger.getLogger(this.getClass());

    private volatile static HashMap<String, NGramScore> models;

    /**
     * 加载 NGram model
     *
     * @param filepath
     * @return
     * @throws IOException
     * @throws FileNotFoundException
     */
    public HashMap<String, NGramScore> loadNgramModel(String filepath) throws IOException {

        if (MapUtils.isEmpty(GrammarScorer.models)) {
            synchronized (GrammarScorer.class) {
                if (MapUtils.isEmpty(GrammarScorer.models)) {
                    this.log.info("Loading nGram model:" + filepath);
                    GrammarScorer.models = new HashMap<String, NGramScore>();
                    LineIterator lineIterator = null;
                    //String numRegex = "[+-]*\\d+(\\.\\d*)*";
                    try {
                        lineIterator = FileUtils.lineIterator(FileUtils.getFile(filepath), "UTF-8");
                        while (lineIterator.hasNext()) {
                            String line = lineIterator.nextLine();
                            String[] strs = line.trim().split("\t");
                            if (strs.length < 2) {
                                this.log.info("The elements count is less than 2, ignore line:" + line);
                                continue;
                            }
                            /*if(!strs[0].matches(numRegex)) {
                                this.log.info("The first element can't be formatted to float, ignore line:" + line);
                                continue;
                            }*/
                            float probScore = Float.parseFloat(strs[0]);
                            String tmpTrigram = strs[1];
                            float backOffScore = 0.0f;
                            if (strs.length == 3) {
                                /*if(!strs[2].matches(numRegex)) {
                                    this.log.info("The third element can't be formatted to float, ignore line:" + line);
                                    continue;
                                }*/
                                backOffScore = Float.valueOf(strs[2]);
                            }
                            GrammarScorer.models.put(tmpTrigram, new NGramScore(probScore, backOffScore));
                        }

                        this.log.info("Loading nGram model success, model size:" + GrammarScorer.models.size());

                    } catch (IOException e) {
                        this.log.error("Load model file[" + filepath + "] error!", e);
                        throw e;
                    } finally {
                        if (lineIterator != null) {
                            lineIterator.close();
                        }
                    }
                }
            }
        }

        return GrammarScorer.models;

    }

    /**
     * 计算语句的语言模型得分
     *
     * @param sentence
     * @param model
     * @return
     */
    public float calculateFluency(String sentence, Map<String, NGramScore> model) {

        float score = 0.0f;

        if (MapUtils.isEmpty(model)) {
            this.log.error("The nGram model is empty!");
            return score;
        }

        String sentenceStr = "<s> " + sentence + " </s>";
        String[] strs = sentenceStr.split("\\s+");
        String w1, w2, w3;
        for (int i = 2; i < strs.length; i++) {

            w1 = strs[i - 2];
            w2 = strs[i - 1];
            w3 = strs[i];

            if (!model.containsKey(w1) && !"<s>".equals(w1) && !"</s>".equals(w1))
                w1 = "<unk>";
            if (!model.containsKey(w2))
                w2 = "<unk>";
            if (!model.containsKey(w3) && !"<s>".equals(w3) && !"</s>".equals(w3))
                w3 = "<unk>";

            score += (float) Math.pow(10, this.ExtractNGramScore(w1 + " " + w2 + " " + w3, model));

        }

        return score;

    }

    /**
     *
     * @param wordSequence
     * @param model
     * @return
     */
    private float ExtractNGramScore(String wordSequence, Map<String, NGramScore> model) {

        String[] words = wordSequence.split("\\s+");

        if (words.length == 3) {

            if (model.containsKey(wordSequence)) {
                return model.get(wordSequence).getProb();
            } else if (model.containsKey(words[0] + " " + words[1])) {
                return model.get(words[0] + " " + words[1]).getBackOffProb() + this.ExtractNGramScore(words[1] + " " + words[2], model);
            } else {
                return this.ExtractNGramScore(words[1] + " " + words[2], model);
            }

        } else if (words.length == 2) {

            if (model.containsKey(words[0] + " " + words[1])) {
                return model.get(words[0] + " " + words[1]).getProb();
            } else {
                return model.get(words[0]).getBackOffProb() + this.ExtractNGramScore(words[1], model);
            }

        } else {

            return model.get(words[0]).getProb();

        }

    }

    public static void main(String[] args) throws IOException {

        String modelpath = args[0];
        GrammarScorer gs = new GrammarScorer();
        HashMap<String, NGramScore> model = gs.loadNgramModel(modelpath);

        System.out.println("Please input a setence:(bank string exit)");
        Scanner sc = new Scanner(System.in);
        String sentence = sc.nextLine();
        while(StringUtils.isNotBlank(sentence)) {
            System.out.println("language score:\t" + gs.calculateFluency(sentence, model));
            System.out.println("Please input a setence:(bank string exit)");
            sentence = sc.nextLine();
        }

    }

}
