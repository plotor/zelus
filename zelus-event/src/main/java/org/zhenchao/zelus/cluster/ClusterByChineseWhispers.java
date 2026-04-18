package org.zhenchao.zelus.cluster;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.pojo.CWRunParam;
import org.zhenchao.zelus.pretreat.Pretreatment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 采用Chinese Whispers algorithmCluster events
 *
 * @author zhenchao
 */
@Deprecated
@SuppressWarnings("checkstyle:Regexp")
public class ClusterByChineseWhispers implements Constants {

    private final Logger log = Logger.getLogger(this.getClass());

    private final String nodesDir; // 存放node文件的目录
    private final String edgeDir; // 存放edge文件的目录
    private final String resultDir; // 存放ClusteringResult的目录
    private final String textDir; // 按行分割之后的文本所在路径
    private final String extractedSentencesSaveDir; // 抽取的句子文件存放的目录
    private final String moduleFilePath;  //POS tagging工具模型所在路径
    private int threadNum = 1;  //Thread count，默认为1
    private final float edgeSelectedWeight;
    private final boolean isPret;
    private final boolean isClust;
    private final String dictPath;  //WordNetWord典路径

    public ClusterByChineseWhispers(String nodesDir, String edgeDir,
                                    String resultDir, String textDir, String extractedSentencesSaveDir,
                                    String moduleFilePath, int threadNum, float edgeSelectedWeight,
                                    boolean isPret, boolean isClust, String dictPath) {
        super();
        this.nodesDir = nodesDir;
        this.edgeDir = edgeDir;
        this.resultDir = resultDir;
        this.textDir = textDir;
        this.extractedSentencesSaveDir = extractedSentencesSaveDir;
        this.moduleFilePath = moduleFilePath;
        this.threadNum = threadNum;
        this.edgeSelectedWeight = edgeSelectedWeight;
        this.isPret = isPret;
        this.isClust = isClust;
        this.dictPath = dictPath;
    }

    /**
     * 根据边的权重Information来CalculateChinese Whispers algorithmParameter中的边的阈值
     *
     * @param filepath
     * @return
     * @throws IOException
     */
    private int calculateThreshold4edgeweight(String filepath) throws IOException {
        int threshhold = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filepath), DEFAULT_CHARSET));
            String line = null;
            long totalVal = 0;
            int totalEdges = 0;
            // 暂时采用平均值作为阈值
            while ((line = br.readLine()) != null) {
                line = line.trim();
                final String[] edgeInfos = line.split("\\s+");
                totalVal += Long.parseLong(edgeInfos[2]);
                ++totalEdges;
            }
            if (totalEdges > 0) {
                threshhold = (int) (totalVal / totalEdges * this.edgeSelectedWeight);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }

        return threshhold;
    }

    /**
     * ClusteringFunction
     *
     * @param cwRunParam Chinese Whispers algorithmRun parameters封装Pairs象：
     * 其中edgeWeightThreshold，nodeFileName，edgeFileName，resultFilePath在Function内部自动设置
     * @throws IOException
     * @throws InterruptedException
     */
    public void doCluster(CWRunParam cwRunParam) throws IOException, InterruptedException {

        if (this.isPret) {
            // Pairs文本进行Preprocessing
            this.log.info("正在Pairs文本进行Preprocessing，以满足Chinese Whispers algorithm的Input要求...");
            final Pretreatment pretreatment = new Pretreatment();
            pretreatment.pretreatment4ChineseWHispers(this.edgeDir);
        }

        if (this.isClust) {
            this.log.info("正在Cluster events...");
            final File nodeFile = new File(this.nodesDir);
            final String[] filenames = nodeFile.list(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name) {
                    if (name.endsWith(".node")) {
                        return true;
                    }
                    return false;
                }
            });

            for (final String filename : filenames) {
                final String nodeFileName = this.nodesDir + "/" + filename;
                final String edgeFileName = this.edgeDir + "/" + DIR_CW_PRETREAT + "/" + filename.replace(".node", ".edge");
                // Calculate边的阈值
                final int edgeThreshHole = this.calculateThreshold4edgeweight(edgeFileName);
                cwRunParam.setEdgeWeightThreshold(edgeThreshHole);
                cwRunParam.setNodeFilePath(nodeFileName);
                cwRunParam.setEdgeFilePath(edgeFileName);
                cwRunParam.setResultFilePath(this.resultDir + "/" + filename.replace(".node", ""));
                final String command = cwRunParam.toString();
                this.log.info("正在用Chinese Whispers algorithmPairs" + filename.replace(".node", "") + "进行Clustering...");
                final Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
                final BufferedReader read = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = null;
                while ((line = read.readLine()) != null) {
                    System.out.println(line);
                }
                if (read != null) {
                    read.close();
                }
            }
        }
    }

    /**
     * 按照Event clustering的Result来Pairs文本中的句子进行Clustering
     *
     * @throws IOException
     */
    public void clusterSentencesByEvents() throws IOException {
        final File clusterResultDir = new File(this.resultDir);
        // 获取所有的Event clusteringResult文件（.read）
        final String[] filenames_cluster_read = clusterResultDir.list(new FilenameFilter() {

            @Override
            public boolean accept(File file, String name) {
                if (name.endsWith(".read")) {
                    return true;
                }
                return false;
            }
        });

        // 加载POS tagging模型
        OpenNlpPOSTagger.getInstance(this.moduleFilePath);

        //加载Dependency parsing模型
        final String grammar = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
        final String[] options = {"-maxLength", "80", "-retainTmpSubcategories"};
        final LexicalizedParser lp = LexicalizedParser.loadModel(grammar, options);
        //TreebankLanguagePack tlp = lp.getOp().langpack();
        //GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

        final ExecutorService executorService = Executors.newFixedThreadPool(this.threadNum);
        final List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
        for (final String filename_cluster_read : filenames_cluster_read) {
            //添加任务到任务List
            tasks.add(new SentenceExtractThread(
                    this.resultDir, filename_cluster_read, this.extractedSentencesSaveDir, this.textDir, lp, this.dictPath));
        }

        if (tasks.size() > 0) {
            try {
                //Execute任务组，所有任务Execute完毕之前，主线程阻塞
                final List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                executorService.shutdown();
                if (futures != null) {
                    for (final Future<Boolean> future : futures) {
                        future.get();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                this.log.error("Execute任务组error！", e);
                //e.printStackTrace();
            }
        }
    }

    //测试
    public static void main(String[] args) throws IOException {
        final File clusterResultDir = new File("src/tmp");
        // 获取所有的Event clusteringResult文件（.read）
        final String[] filenames_cluster_read = clusterResultDir.list(new FilenameFilter() {

            @Override
            public boolean accept(File file, String name) {
                // TODO Auto-generated method stub
                if (name.endsWith(".read")) {
                    return true;
                }
                return false;
            }
        });

        // 加载POS tagging模型
        OpenNlpPOSTagger.getInstance("src/en-pos-maxent.bin");

        //加载Dependency parsing模型
        final String grammar = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
        final String[] options = {"-maxLength", "80", "-retainTmpSubcategories"};
        final LexicalizedParser lp = LexicalizedParser.loadModel(grammar, options);
        //TreebankLanguagePack tlp = lp.getOp().langpack();
        //GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
        for (final String filename_cluster_read : filenames_cluster_read) {
            //添加任务到任务List
            tasks.add(new SentenceExtractThread(
                    "src/tmp", filename_cluster_read, "src/tmp/extract_sent", "src/tmp/text_dir", lp, "D:/WordNet/2.1/dict"));
        }

        if (tasks.size() > 0) {
            try {
                //Execute任务组，所有任务Execute完毕之前，主线程阻塞
                final List<Future<Boolean>> futures = executorService.invokeAll(tasks);
                executorService.shutdown();
                if (futures != null) {
                    for (final Future<Boolean> future : futures) {
                        future.get();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                //log.error("Execute任务组error！", e);
                e.printStackTrace();
            }
        }
    }

}
