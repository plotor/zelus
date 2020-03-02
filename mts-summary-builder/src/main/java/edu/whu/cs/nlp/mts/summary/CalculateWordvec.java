package edu.whu.cs.nlp.mts.summary;

import org.apache.commons.io.FileUtils;
import org.zhenchao.zelus.common.domain.Vector;
import org.zhenchao.zelus.common.domain.Word;
import org.zhenchao.zelus.common.nlp.StanfordNLPTools;
import org.zhenchao.zelus.common.util.EhcacheUtils;
import org.zhenchao.zelus.common.util.SerializeUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculateWordvec {

    public static void main(String[] args) {

        String filepath = "E:/dev_workspace/experiment/nlp/event-guided-mts/corpus/duc2005_docs/duc2005_docs";

        File file = new File(filepath);

        String[] dirs = file.list();

        EhcacheUtils ehCacheUtil = new EhcacheUtils("db_cache_vec", "lab");

        Map<String, Vector> wordvecs = new HashMap<String, Vector>();

        for (String dir : dirs) {
            File innerFile = new File(file + "/" + dir);
            File[] files = innerFile.listFiles();
            for (File ff : files) {
                System.out.println("正在处理：" + ff.getAbsolutePath());
                try {
                    String text = FileUtils.readFileToString(ff, "UTF-8");
                    List<Word> words = StanfordNLPTools.segmentWord(text);
                    for (Word word : words) {
                        String key = word.getName() + "/-/" + word.getPos();
                        if (wordvecs.containsKey(key)) {
                            continue;
                        }

                        Vector vector = ehCacheUtil.getMostSimilarVec(word);

                        wordvecs.put(key, vector);

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("序列化...");
        File savefile = new File("E:/dev_workspace/experiment/nlp/event-guided-mts/corpus/duc2005_docs/duc2005.vec");

        try {
            SerializeUtils.writeObj(wordvecs, savefile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        EhcacheUtils.close();

    }

}
