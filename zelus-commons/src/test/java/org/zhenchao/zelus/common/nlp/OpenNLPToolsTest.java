package org.zhenchao.zelus.common.nlp;

import org.junit.Test;
import org.zhenchao.zelus.common.pojo.ChunkPhrase;
import org.zhenchao.zelus.common.pojo.Word;

import java.util.List;

/**
 * @author: zhenchao.Wang
 * @date: 2016/5/22 10:47
 */
public class OpenNLPToolsTest {

    @Test
    public void chunk() throws Exception {

        String sentence = "Bell, based in Los Angeles, makes and distributes electronic, computer and building products.";
        List<Word> words = StanfordNLPTools.segmentWord(sentence);
        List<ChunkPhrase> chunkPhrases = OpenNLPTools.chunk(words);
        for (ChunkPhrase chunkPhrase : chunkPhrases) {
            System.out.println(chunkPhrase);
        }

    }

}