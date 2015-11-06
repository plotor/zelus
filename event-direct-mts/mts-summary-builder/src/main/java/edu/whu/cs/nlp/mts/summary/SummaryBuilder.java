package edu.whu.cs.nlp.mts.summary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import edu.whu.cs.nlp.msc.CompressedSentencesSelectThread;
import edu.whu.cs.nlp.mts.base.biz.SystemConstant;

/**
 * 计算压缩输出结果语句与问题相关性，并生成摘要
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

        final String propFilePath = args[0];  //配置文件所在路径
        final String questionsFilePath = args[1];  //问题所在文件

        /*
         * 加载配置文件
         */
        final Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propFilePath));
        } catch (final IOException e) {
            log.error("load properties failed!", e);
            //e.printStackTrace();
        }

        final Properties questions = new Properties();
        try {
            questions.load(new FileInputStream(questionsFilePath));
        } catch (final IOException e) {
            log.error("load properties failed!", e);
            //e.printStackTrace();
        }

        final String workdir = properties.getProperty("workDir");
        final Integer threadNum = Integer.parseInt(properties.getProperty("threadNum"));
        final File compressDir = new File(workdir + "/" + DIR_SENTENCES_COMPRESSION);
        final String[] filenames = compressDir.list();
        final ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
        for (final String filename : filenames) {
            final String question = questions.getProperty(filename);
            //topic + ".M.250." + topic_selectorID + ".3"
            final String summaryFilename =
                    filename.substring(0, filename.length() - 1)
                    + ".M.250." + filename.substring(filename.length() - 1) + ".3";
            executorService.execute(
                    new CompressedSentencesSelectThread(
                            workdir + "/" + DIR_SENTENCES_COMPRESSION + "/" + filename,
                            workdir + "/summaries/" + summaryFilename, question));
        }
        executorService.shutdown();
    }

}
