package edu.whu.cs.nlp.mts.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;

import edu.whu.cs.nlp.msc.KeySentenceSelector2;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;

/**
 * 计算压缩输出结果语句与问题相关性，并生成摘要
 *
 * @author Apache_xiaochao
 *
 */
public class SummaryBuilder implements SystemConstant{

    private static Logger log = Logger.getLogger(SummaryBuilder.class);

    public static void main(String[] args) {

        if(args.length == 0){
            System.err.println("请指定配置文件！");
            return;
        }

        String propFilePath = args[0];  //配置文件所在路径

        String questionsFilePath = args[1];  //问题所在文件

        /*
         * 加载配置文件
         */
        Properties properties = new Properties();
        try {

            properties.load(new FileInputStream(propFilePath));

        } catch (IOException e) {
            log.error("load properties failed!", e);
            System.exit(0);
        }

        Properties questions = new Properties();
        try {
            questions.load(new FileInputStream(questionsFilePath));
        } catch (IOException e) {
            log.error("load questions failed!", e);
            System.exit(0);
        }

        String workdir = properties.getProperty("workDir");
        Integer threadNum = Integer.parseInt(properties.getProperty("threadNum"));
        File compressDir = new File(workdir + "/" + DIR_SENTENCES_COMPRESSION);
        String[] filenames = compressDir.list();
        ExecutorService executorService = null;
        try{
            executorService = Executors.newFixedThreadPool(threadNum);
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            for (String filename : filenames) {
                String question = questions.getProperty(filename);
                //topic + ".M.250." + topic_selectorID + ".3"
                String summaryFilename =
                        filename.substring(0, filename.length() - 1)
                        + ".M.250." + filename.substring(filename.length() - 1) + ".3";
                tasks.add(new KeySentenceSelector2(workdir + "/" + DIR_SENTENCES_COMPRESSION + "/" + filename, workdir + "/summaries/" + summaryFilename, question));
            }

            List<Future<Boolean>> futures = executorService.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                future.get();
            }

        } catch(Exception e) {
            log.error("Building summary error!", e);
        } finally {
            if(executorService != null) {
                executorService.shutdown();
            }
        }

    }

}
