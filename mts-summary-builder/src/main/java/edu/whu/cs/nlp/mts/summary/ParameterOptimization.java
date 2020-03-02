package edu.whu.cs.nlp.mts.summary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.domain.CWRunParam;
import edu.whu.cs.nlp.mts.base.domain.RougeAvg;
import edu.whu.cs.nlp.mts.base.global.GlobalConstant;
import edu.whu.cs.nlp.mts.base.utils.C3P0Util;
import edu.whu.cs.nlp.mts.clustering.ClusterByChineseWhispers;

/**
 * 参数优化
 * @author Apache_xiaochao
 *
 */
public class ParameterOptimization implements GlobalConstant{

    private static Logger log = Logger.getLogger(ParameterOptimization.class);

    /**
     * 执行命令过程中的输出处理
     * @param in
     * @return
     * @throws IOException
     */
    private static String execStreamProcess(InputStream in) throws IOException{
        String output = "";
        if(in != null){
            final BufferedReader br = new BufferedReader(new InputStreamReader(in));
            final StringBuilder sb_tmp = new StringBuilder();
            String line = null;
            while((line = br.readLine()) != null){
                sb_tmp.append(line + "\n");
                //System.out.println(line);
            }
            br.close();
            if(sb_tmp.length() > 0){
                output = sb_tmp.toString();
            }
        }
        return output;
    }

    public static void main(String[] args) throws IOException, SQLException {
        if(args.length == 0){
            System.err.println("请指定配置文件！");
            return;
        }

        final String propFilePath = args[0];  //配置文件所在路径
        final ParameterOptimization po = new ParameterOptimization();
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

        //获取线程数
        final int threadNum = Integer.parseInt(properties.getProperty("threadNum", "2"));
        final String textDir = properties.getProperty("textDir");
        final String workDir = properties.getProperty("workDir");
        final String msc_py_path = properties.getProperty("msc_py");

        final String attribute = "keepClassRate";
        //获取当前数据库中的记录的最后一次的权值
        final float edgeSelectedWeight = 3.2f;  //边阈值增加权重，已经证明3.2是最优值
        float keepClassRate = 0.00f;
        float mutationRate = 0.00f;
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            connection = C3P0Util.getConnection("localhost-3306-rouge_eval");
            final String sql = "SELECT weight_kc, weight_mt FROM rouge_avg ORDER BY date DESC LIMIT 1";
            ps = connection.prepareStatement(sql);
            rs = ps.executeQuery();
            if(rs.next()){
                keepClassRate = rs.getFloat("weight_kc") + 0.01f;
                mutationRate = rs.getFloat("weight_mt") + 0.01f;
            }
        } catch (final SQLException e) {
            // TODO Auto-generated catch block
            throw new SQLException("获取最新的权值异常！", e);
        } finally{
            if(rs != null){
                rs.close();
            }
            if(ps != null){
                ps.close();
            }
            if(connection != null){
                connection.close();
            }
        }

        while(keepClassRate < 1){
            while(mutationRate < 1){
                log.info("优化参数：kc=" + keepClassRate + "\tmt=" + mutationRate);
                /*对事件进行聚类，同时按类别抽取时间所在子句*/
                log.info("正在进行事件聚类和子句抽取...");
                //nodes存放文件夹
                final String nodesDir = workDir + "/" + DIR_NODES;
                //对上面文件夹中的文件进行清理
                final File fileNodes = new File(nodesDir);
                if(!fileNodes.exists()){
                    log.error(nodesDir + "不存在！");
                }else{
                    //删除所有聚类算法产生的中间文件
                    final String[] filenames = fileNodes.list(new FilenameFilter() {

                        @Override
                        public boolean accept(File dir, String name) {
                            if(name.contains("renumbered")){
                                return true;
                            }
                            return false;
                        }
                    });
                    if(filenames != null && filenames.length > 0){
                        for (final String filename : filenames) {
                            final File fileRenumbered = new File(nodesDir + "/" + filename);
                            if(fileRenumbered != null){
                                fileRenumbered.delete();
                            }
                        }
                    }
                }

                //edges存放文件夹
                final String edgeDir = workDir + "/" + DIR_EDGES;
                //对上面的文件夹进行清理
                final File fileEdgesCW = new File(edgeDir + "/" + DIR_CW_PRETREAT);
                if(fileEdgesCW.exists()){
                    fileEdgesCW.delete();
                }

                //聚类结果存放文件夹
                final String clustResultDir = workDir + "/" + DIR_EVENTS_CLUST;
                final File fileClustResult = new File(clustResultDir);
                if(fileClustResult.exists()){
                    fileClustResult.delete();
                }
                fileClustResult.mkdirs();

                //句子抽取结果存放的文件夹
                final String sentencesSaveDir = workDir + "/" + DIR_SUB_SENTENCES_EXTRACTED;
                final File fileSentences = new File(sentencesSaveDir);
                if(fileSentences.exists()){
                    fileSentences.delete();
                }
                fileSentences.mkdirs();

                //句子压缩结果存放的文件夹
                final String sentencesCompressDir = workDir + "/" + DIR_SENTENCES_COMPRESSION;
                final File fileSentencesCompress = new File(sentencesCompressDir);
                if(fileSentencesCompress.exists()){
                    fileSentencesCompress.delete();
                }
                fileSentencesCompress.mkdirs();

                final String moduleFilePath = workDir + "/en-pos-maxent.bin";
                final String dictPath = properties.getProperty("dictPath");

                final ClusterByChineseWhispers cluster =
                        new ClusterByChineseWhispers(
                                nodesDir, edgeDir, clustResultDir, textDir,
                                sentencesSaveDir, moduleFilePath, threadNum, edgeSelectedWeight, true, true, dictPath);
                try {
                    //对事件进行聚类
                    //构建口哨算法运行参数
                    final CWRunParam cwRunParam = new CWRunParam();
                    cwRunParam.setJarPath(properties.getProperty("cwjarPath"));
                    cwRunParam.setKeepClassRate(keepClassRate);
                    cwRunParam.setMutation_rate(mutationRate);
                    cwRunParam.setIterationCount(100);
                    cluster.doCluster(cwRunParam);
                    //获取事件对应的句子，所有子句抽取结束之前，主线程阻塞
                    cluster.clusterSentencesByEvents();
                } catch (IOException | InterruptedException e) {
                    log.error("事件聚类出错！", e);
                    //e.printStackTrace();
                }

                /*多句子压缩*/
                final String commond_msc = "python " + msc_py_path;
                try {
                    final Process process = Runtime.getRuntime().exec(commond_msc);
                    process.waitFor();
                    final BufferedReader read = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = null;
                    while ((line = read.readLine()) != null) {
                        System.out.println(line);
                    }
                    if (read != null) {
                        read.close();
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("多语句压缩出错！", e);
                    //e.printStackTrace();
                }

                /*对结果进行评估*/
                //将压缩生成的文件复制到评估程序的peers目录下
                final String rougePath = properties.getProperty("rouge_path");
                final String commond_cp_peers = "\\cp " + sentencesCompressDir + "/* " + rougePath + "/peers";
                final String[] cp_command = {"/bin/sh", "-c", commond_cp_peers};  //不进行这样的封装会出错哦
                try {
                    final Process process = Runtime.getRuntime().exec(cp_command);
                    process.waitFor();
                    final BufferedReader read = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = null;
                    while ((line = read.readLine()) != null) {
                        System.out.println(line);
                    }
                    if (read != null) {
                        read.close();
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("复制压缩后文件到peers文件出错！", e);
                    //e.printStackTrace();
                }

                //执行rouge
                final String commond = "perl ROUGE-1.5.5.pl -e /home/eventChain/rouge_eval/data"
                        + " -a -n 2 -x -m -2 4 -u -c 95 -r 1000 -f A -p 0.5 -t 0"
                        + " -d /home/eventChain/rouge_eval/rougejk.in"
                        + " > /home/eventChain/rouge_eval/scores.out";
                final String[] commond_rouge = {"/bin/sh", "-c", commond};
                try {
                    final Process process = Runtime.getRuntime().exec(commond_rouge);
                    final String errMsg = ParameterOptimization.execStreamProcess(process.getErrorStream());
                    final String outMsg = ParameterOptimization.execStreamProcess(process.getInputStream());
                    if(!"".equals(errMsg)){
                        log.warn(errMsg);
                    }
                    if(!"".equals(outMsg)){
                        log.info(outMsg);
                    }
                    if(process.waitFor() != 0){
                        log.error("评估命令返回状态不正常！");
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("执行rouge出错，当前工作路径：" + System.getProperty("user.dir") , e);
                    //e.printStackTrace();
                }

                /*获取评估结果*/
                final String regex = "3\\s+(ROUGE-[SU124]+)\\s+(Average_[RPF]):\\s+([\\w\\.]+)\\s+\\([\\s\\S]*?\\)";
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(new FileInputStream(rougePath + "/scores.out"), "UTF-8"));
                    final Pattern pattern = Pattern.compile(regex);
                    String line = null;
                    while((line = br.readLine()) != null){
                        line = line.trim();
                        if(!"".equals(line)){
                            final Matcher matcher = pattern.matcher(line);
                            if(matcher.find()){
                                final String rouge = matcher.group(1);
                                final String average = matcher.group(2);
                                final String value = matcher.group(3);
                                final RougeAvg rougeAvg = new RougeAvg();
                                rougeAvg.setRougeType(rouge);
                                rougeAvg.setAvgType(average);
                                rougeAvg.setValue(Float.parseFloat(value));
                                try {
                                    po.orm2db(rougeAvg, keepClassRate, mutationRate, edgeSelectedWeight);
                                } catch (final SQLException e) {
                                    log.error("持久化数据出错：" + rouge.toString() + "\t" + keepClassRate + "\t" + attribute, e);
                                    //e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (final IOException e) {
                    log.error("解析评估结果文件出错：", e);
                    //e.printStackTrace();
                } finally{
                    if(br != null){
                        br.close();
                    }
                }
                mutationRate += 0.01f;
            }
            //参数递增
            keepClassRate += 0.01f;
        }

    }

    /**
     * 将结果持久化
     * @param rougeAvg
     * @param weight
     * @throws SQLException
     */
    private void orm2db(RougeAvg rougeAvg, float weight_kc, float weight_mt, float weight_es) throws SQLException{
        Connection connection = null;
        PreparedStatement ps =null;
        if(rougeAvg != null){
            try {
                connection = C3P0Util.getConnection("localhost-3306-rouge_eval");
                final String sql = "INSERT INTO rouge_avg"
                        + "(rougeType, avgType, `value`, weight_es, date, attribute, weight_kc, weight_mt) "
                        + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
                final Date now = new Date();
                ps = connection.prepareStatement(sql);
                ps.setString(1, rougeAvg.getRougeType());
                ps.setString(2, rougeAvg.getAvgType());
                ps.setFloat(3, rougeAvg.getValue());
                ps.setFloat(4, weight_es);
                ps.setTimestamp(5, new Timestamp(now.getTime()));
                ps.setString(6, "kc_mt");
                ps.setFloat(7, weight_kc);
                ps.setFloat(8, weight_mt);
                ps.executeUpdate();
            } finally{
                if(ps != null){
                    ps.close();
                }
                if(connection != null){
                    connection.close();
                }
            }
        }
    }

}
