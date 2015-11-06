package edu.whu.cs.nlp.mts.base.nlp;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.whu.cs.nlp.mts.base.biz.ModelLoader;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.CoreferenceElement;
import edu.whu.cs.nlp.mts.base.utils.Encipher;

/**
 * 斯坦福NLP工具类
 *
 * @author ZhenchaoWang 2015-10-27 16:38:29
 *
 */
public class StanfordNLPTools implements SystemConstant{

    private static final Logger log = Logger.getLogger(StanfordNLPTools.class);

    /**
     * 对输入文本进行指代消解<br>
     * 以指代链中最先出现的指代作为被指代的词（短语)<br>
     * key:MD5(element + sentNum + startIndex + endIndex)
     *
     * @param input
     * @return
     */
    public static Map<String, CoreferenceElement> cr(String input) {

        Map<String, CoreferenceElement> result = new HashMap<String, CoreferenceElement>();

        StanfordCoreNLP pipeline = ModelLoader.getPipeLine();

        if (StringUtils.isNotBlank(input)) {

            Annotation document = new Annotation(input);
            pipeline.annotate(document);

            /*
             * 获取输入文本中的指代链，并执行指代消解
             */
            Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);

            Set<Map.Entry<Integer, CorefChain>> set = graph.entrySet();

            for (Iterator<Map.Entry<Integer, CorefChain>> it = set.iterator(); it.hasNext();) {

                Map.Entry<Integer, CorefChain> entry = it.next();

                if (entry.getValue().getMentionsInTextualOrder().size() > 1) {

                    CorefMention firstElement = entry.getValue().getMentionsInTextualOrder().get(0);
                    /*以第一个词（短语）作为被指代的词（短语）*/
                    CoreferenceElement ref = new CoreferenceElement(
                            firstElement.mentionSpan, firstElement.corefClusterID,
                            firstElement.startIndex, firstElement.endIndex, firstElement.sentNum, null);
                    for (int k = 1; k < entry.getValue().getMentionsInTextualOrder().size(); k++) {

                        CorefMention mention = entry.getValue().getMentionsInTextualOrder().get(k);

                        if (mention != null) {
                            CoreferenceElement element = new CoreferenceElement(
                                    mention.mentionSpan, mention.corefClusterID, mention.startIndex, mention.endIndex, mention.sentNum, ref);
                            try {
                                result.put(Encipher.MD5(element.getElement() + element.getSentNum() + element.getStartIndex() + element.getEndIndex()), element);
                                //System.out.println(Encipher.MD5(element.getElement() + element.getSentNum() + element.getStartIndex() + element.getEndIndex()) + "\t" + element.getElement() + "\t" + element.getSentNum() + "\t" + element.getStartIndex() + "\t" + element.getEndIndex() + "\t->\t" + element.getRef().getElement());
                            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                                log.error("MD5 encode error!", e);
                            }
                        }

                    }
                }
            }
        }
        return result;
    }

}
