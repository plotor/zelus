package edu.whu.cs.nlp.mts.base.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * 资源加载器
 *
 * @author ZhenchaoWang 2015-11-3 10:20:26
 *
 */
public class ResourceLoader {

    private static Logger log = Logger.getLogger(ResourceLoader.class);

    /**
     * 加载停用词列表，可以同时指定多个文件
     *
     * @param filenames
     * @return
     */
    public static Set<String> loadStopwords(String... filenames) {
        Set<String> stopwords = new HashSet<String>();
        for (String filename : filenames) {
            try {
                BufferedReader br = null;
                try{
                    log.info("Loading stopwords...");
                    br = new BufferedReader(new InputStreamReader(ResourceLoader.class.getClassLoader().getResourceAsStream(filename), "UTF-8"));
                    String line = null;
                    while((line = br.readLine()) != null) {
                        stopwords.add(line.trim());
                    }
                    log.info("load stopwords finished, count:" + stopwords.size());
                } finally {
                    if(br != null) {
                        br.close();
                    }
                }
            } catch (IOException e) {
                log.error("load stopwords error!", e);
            }
        }
        return stopwords;
    }

    public static void main(String[] args) {
        Set<String> set = ResourceLoader.loadStopwords("stopwords-en-default.txt", "stopwords-en-mysql.txt");
        int num = 0;
        for (String str : set) {
            System.out.println((++num) + "\t" + str);
        }
    }

}
