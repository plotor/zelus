package edu.whu.cs.nlp.mts.base.utils;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;

/**
 * WordNet相关工具类
 * @author Apache_xiaochao
 *
 */
public class WordNetUtil implements GlobalConstant{

    private static Logger log = Logger.getLogger(WordNetUtil.class);

    private static ConcurrentHashMap<String, IDictionary> dicts = new ConcurrentHashMap<String, IDictionary>();

    /**
     * 打开词典
     * @param dictPath
     * @return
     * @throws IOException
     */
    public static IDictionary openDictionary(String dictPath) throws IOException{
        if(dicts.get(dictPath) == null){
            synchronized (WordNetUtil.class) {
                if(dicts.get(dictPath) == null){
                    URL url=new URL("file", null, dictPath);
                    IDictionary dict = new Dictionary(url);
                    dict.open();//打开词典
                    dicts.put(dictPath, dict);
                }
            }
        }
        return dicts.get(dictPath);
    }

    /**
     * 根据一定规则来获取当前词的最佳同义词
     * 2015-6-9 19:47:30 添加synchronized关键字，jwi不是线程安全的
     * @param dict
     * @param word
     * @param selectedSynonymsWords 文章中已经选择的同义词集合
     * @return
     */
    public synchronized static List<Word> getSynonyms(IDictionary dict, Word word){

        List<Word> synonymsWords = new ArrayList<Word>();

        IIndexWord idxWord = null;
        if(POS_NOUN.contains(word.getPos())){
            idxWord = dict.getIndexWord(word.getLemma(), POS.NOUN);
        }else if(POS_VERB.contains(word.getPos())){
            idxWord = dict.getIndexWord(word.getLemma(), POS.VERB);
        }else if(POS_ADVERB.contains(word.getPos())){
            idxWord = dict.getIndexWord(word.getLemma(), POS.ADVERB);
        }else if(POS_ADJ.contains(word.getPos())){
            idxWord = dict.getIndexWord(word.getLemma(), POS.ADJECTIVE);
        }

        if(idxWord == null) {
            return synonymsWords;
        }

        IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
        IWord iword = dict.getWord(wordID);
        ISynset synset = iword.getSynset (); //ISynset是一个词的同义词集的接口

        for(IWord w : synset.getWords()){

            Word synonymsWord = null;
            try {
                synonymsWord = (Word) word.clone();
                synonymsWord.setName(w.getLemma());
                synonymsWord.setLemma(w.getLemma());
            } catch (CloneNotSupportedException e) {
                log.error("Clone word[" + word + "] error!", e);
            }
            if(null != synonymsWord) {
                synonymsWords.add(synonymsWord);
            }

        }

        return synonymsWords;
    }

}
