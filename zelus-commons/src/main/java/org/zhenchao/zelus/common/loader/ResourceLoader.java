package org.zhenchao.zelus.common.loader;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Resource loader
 *
 * @author zhenchao 2015-11-3 10:20:26
 */
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor", "checkstyle:UncommentedMain",
        "checkstyle:JavadocMethod"})
public final class ResourceLoader {

    private static Logger log = Logger.getLogger(ResourceLoader.class);

    private ResourceLoader() {
    }

    /**
     * 加载Stopwords list，可以同时指定多个文件
     *
     * @param filenames 停用Word文件名
     * @return 停用WordCollection
     */
    public static Set<String> loadStopwords(String... filenames) {
        Set<String> stopwords = new HashSet<String>();
        for (String filename : filenames) {
            try {
                BufferedReader br = null;
                try {
                    log.info("Loading stopwords...");
                    br = new BufferedReader(new InputStreamReader(
                            ResourceLoader.class.getClassLoader()
                                    .getResourceAsStream(filename), "UTF-8"));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        stopwords.add(line.trim());
                    }
                    log.info("load stopwords finished, count:" + stopwords.size());
                } finally {
                    if (br != null) {
                        br.close();
                    }
                }
            } catch (IOException e) {
                log.error("load stopwords error!", e);
            }
        }
        return stopwords;
    }

    /** Test entry point */
    public static void main(String[] args) {
        Set<String> set = ResourceLoader.loadStopwords(
                "stopwords-en-default.txt", "stopwords-en-mysql.txt");
        int num = 0;
        for (String str : set) {
            log.info((++num) + "\t" + str);
        }
    }

}
