package org.zhenchao.zelus.clustering;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.InvalidFormatException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenNLP词性标注工具类
 *
 * @author Apache_xiaochao
 */
public class OpenNlpPOSTagger {

    private static POSModel model;
    private static OpenNlpPOSTagger openNlpPOSTagger;

    private OpenNlpPOSTagger() {

    }

    public static OpenNlpPOSTagger getInstance(String modulePath) throws IOException {
        if (openNlpPOSTagger == null) {
            synchronized (OpenNlpPOSTagger.class) {
                if (openNlpPOSTagger == null) {
                    openNlpPOSTagger = new OpenNlpPOSTagger();
                    InputStream modelIn = null;
                    try {
                        modelIn = new FileInputStream(modulePath);
                        model = new POSModel(modelIn);
                    } catch (final FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        throw e;
                    } catch (final InvalidFormatException e) {
                        // TODO Auto-generated catch block
                        throw e;
                    } catch (final IOException e) {
                        // TODO Auto-generated catch block
                        throw e;
                    } finally {
                        if (modelIn != null) {
                            modelIn.close();
                        }
                    }
                }
            }
        }
        return openNlpPOSTagger;
    }

    /**
     * 对输入的句子进行词性标注
     *
     * @param sentence
     * @return
     */
    public static List<String> tagger(String sentence) {
        List<String> taggedList = null;
        if (sentence != null) {
            final POSTaggerME tagger = new POSTaggerME(model);
            final String[] words = sentence.split("\\s+");
            final String tags[] = tagger.tag(words);
            taggedList = new ArrayList<>();
            final Pattern pattern = Pattern.compile("^[^A-Za-z0-9]*?$");
            for (int i = 0; i < words.length; ++i) {
                final Matcher matcher = pattern.matcher(words[i]);
                if (words[i].equals(tags[i]) || matcher.find()) {
                    tags[i] = "PUNCT";
                }
                taggedList.add(words[i] + "/" + tags[i]);
            }
        }
        return taggedList;
    }
}
