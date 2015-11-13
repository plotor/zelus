package edu.whu.cs.nlp.mts.optimize;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import edu.whu.cs.nlp.msc.domain.CompressUnit;

/**
 * 穷举选择句子构建摘要
 *
 * @author ZhenchaoWang 2015-11-13 20:43:50
 *
 */
public class EnumerationThread implements Callable<Boolean> {

    /** 按照类别组织的句子*/
    private final Map<String, List<CompressUnit>> rerankedClustedCompressUnits;

    public EnumerationThread(Map<String, List<CompressUnit>> rerankedClustedCompressUnits) {
        super();
        this.rerankedClustedCompressUnits = rerankedClustedCompressUnits;
    }

    /**
     * 是否继续
     *
     * @param counter
     * @return
     */
    private boolean isContinue(int[] counter, int[] clusterSizes) {
        boolean over = true;

        if(counter == null) {
            return false;
        }

        for(int i = 0; i < counter.length; i++) {
            if(counter[i] == clusterSizes[i] - 1){
                counter[i] = 0;
                if(i + 1 >= counter.length) {
                    return false;
                }
                counter[i + 1] += 1;
            } else {
                counter[i] += 1;
            }
        }

        return over;
    }

    @Override
    public Boolean call() throws Exception {

        for(int i = 0 ; i < this.rerankedClustedCompressUnits.size(); i++) {

        }

        return null;
    }

}
