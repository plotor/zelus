package edu.whu.cs.nlp.mts.base.utils;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.Word;

/**
 * WordNet相关工具类
 * @author Apache_xiaochao
 *
 */
public class WordNetUtil implements SystemConstant{

    private volatile static IDictionary dict;

    /**
     * 打开词典
     * @param dictPath
     * @return
     * @throws IOException
     */
    public static IDictionary openDictionary(String dictPath) throws IOException{
        if(dict == null){
            synchronized (WordNetUtil.class) {
                if(dict == null){
                    final URL url=new URL("file", null, dictPath);
                    dict = new Dictionary(url);
                    dict.open();//打开词典
                }
            }
        }
        return dict;
    }

    /**
     * 根据一定规则来获取当前词的最佳同义词
     * 2015-6-9 19:47:30 添加synchronized关键字，jwi不是线程安全的
     * @param dict
     * @param word
     * @param selectedSynonymsWords 文章中已经选择的同义词集合
     * @return
     */
    public synchronized static Word getSynonyms(IDictionary dict, Word word, Set<String> selectedSynonymsWords){
        Word result = word;
        if(selectedSynonymsWords != null){
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

            if(idxWord != null){
                final IWordID wordID = idxWord.getWordIDs().get(0); // 1st meaning
                final IWord iword = dict.getWord(wordID);
                final ISynset synset = iword.getSynset (); //ISynset是一个词的同义词集的接口
                // iterate over words associated with the synset
                boolean flag = false;
                //System.out.print("当前词：" + word.getName() + "->");
                for(final IWord w : synset.getWords()){
                    //System.out.print(w.getLemma() + "\t");
                    //如果之前选中的词中已经有当前词的同义词，则用选中的词替换当前词
                    if(selectedSynonymsWords.contains(w.getLemma())){
                        result.setName(w.getLemma());
                        result.setLemma(w.getLemma());
                        flag = true;
                        break;
                    }
                }
                //System.out.println();
                if(!flag){
                    //当前词的同义词之前没有出现过，所以将当前词填入已经选中的同义词集合
                    selectedSynonymsWords.add(result.getLemma());
                    result.setName(result.getLemma());  //用原型替换当前词
                }
            }
        }
        return result;
    }

}
