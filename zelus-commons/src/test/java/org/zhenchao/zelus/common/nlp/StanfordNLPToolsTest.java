package org.zhenchao.zelus.common.nlp;

import org.junit.Test;
import org.zhenchao.zelus.common.pojo.Word;

import java.util.List;

/**
 * @author: zhenchao.Wang
 * @date: 2016/5/21 15:00
 */
public class StanfordNLPToolsTest {

    @Test
    public void coreOperate() throws Exception {

    }

    @Test
    public void segmentWord() throws Exception {

        String text = "Turkish warplanes have shot down a Russian military aircraft on the border with Syria. " +
                "Turkey says it has shot down a Russian made warplane on the Syrian border for violating Turkish airspace. " +
                "A Turkish Air Force F16 fighter jet shot down a Russian Sukhoi Su24M bomber aircraft near the Syria border on 24 November 2015. " +
                "A Russian warplane has crashed in Syria near the Turkish border on 24 November, according to local reports. " +
                "Turkey apparently shot down a Russian bomber which they say was in their air space this morning.";

        List<Word> words = StanfordNLPTools.segmentWord(text);
        for (Word word : words) {
            System.out.println(word);
        }

    }

}