package org.zhenchao.zelus.common.nlp;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.loader.ModelLoader;
import org.zhenchao.zelus.common.pojo.ChunkPhrase;
import org.zhenchao.zelus.common.pojo.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenNLP工具类
 *
 * @author zhenchao.Wang
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class OpenNLPTools implements Constants {

    private static final Logger LOG = LoggerFactory.getLogger(OpenNLPTools.class);

    private OpenNLPTools() {
    }

    /**
     * 利用open nlp进行chunk，并添加一些修正规则
     *
     * @param words 词列表
     * @return chunk短语列表
     * @throws Exception 异常
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static List<ChunkPhrase> chunk(List<Word> words) throws Exception {
        List<ChunkPhrase> phrases = new ArrayList<ChunkPhrase>();
        int wordsCount = words.size();
        String[] toks = new String[wordsCount - 1];
        String[] tags = new String[wordsCount - 1];
        for (int i = 1; i < words.size(); i++) {
            toks[i - 1] = words.get(i).getName();
            tags[i - 1] = words.get(i).getPos();
        }
        ChunkerModel chunkerModel;
        try {
            chunkerModel = ModelLoader.getChunkerModel();
        } catch (Exception e) {
            LOG.error("Failed to load chunk model!", e);
            throw e;
        }
        ChunkerME chunkerME = new ChunkerME(chunkerModel);
        Span[] spans = chunkerME.chunkAsSpans(toks, tags);
        for (Span span : spans) {
            Word word = words.get(span.getStart() + 1);
            if ("'s".equals(word.getName())) {
                ChunkPhrase prePhrase = phrases.get(phrases.size() - 1);
                prePhrase.setRightIndex(span.getEnd());
                prePhrase.getWords().addAll(
                        words.subList(span.getStart() + 1, span.getEnd() + 1));
                phrases.set(phrases.size() - 1, prePhrase);
            } else {
                ChunkPhrase chunkPhrase = new ChunkPhrase(
                        span.getStart() + 1, span.getEnd(),
                        new ArrayList<Word>(
                                words.subList(span.getStart() + 1, span.getEnd() + 1)));
                phrases.add(chunkPhrase);
            }
        }
        return phrases;
    }

}
