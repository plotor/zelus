package org.zhenchao.zelus.summary;

import org.apache.log4j.Logger;
import org.zhenchao.zelus.cluster.ClusterByChineseWhispers;
import org.zhenchao.zelus.common.Constants;
import org.zhenchao.zelus.common.pojo.CWRunParam;
import org.zhenchao.zelus.common.pojo.RougeAvg;
import org.zhenchao.zelus.common.util.C3p0Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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

/**
 * Parameter optimization
 *
 * @author zhenchao
 */
public class ParameterOptimization implements Constants {

    private static Logger log = Logger.getLogger(ParameterOptimization.class);

    /**
     * Process output from command execution
     *
     * @param in
     * @return
     * @throws IOException
     */
    private static String execStreamProcess(InputStream in) throws IOException {
        String output = "";
        if (in != null) {
            final BufferedReader br = new BufferedReader(new InputStreamReader(in));
            final StringBuilder sb_tmp = new StringBuilder();
            String line = null;
            while ((line = br.readLine()) != null) {
                sb_tmp.append(line + "\n");
                //log.debug(line);
            }
            br.close();
            if (sb_tmp.length() > 0) {
                output = sb_tmp.toString();
            }
        }
        return output;
    }

    public static void main(String[] args) throws IOException, SQLException {
        if (args.length == 0) {
            log.error("Please specify a configuration file！");
            return;
        }

        final String propFilePath = args[0];  //Configuration file path
        final ParameterOptimization po = new ParameterOptimization();
        /*
         * Load configuration file
         */
        final Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propFilePath));
        } catch (final IOException e) {
            log.error("load properties failed!", e);
            //e.printStackTrace();
        }

        //Get thread count
        final int threadNum = Integer.parseInt(properties.getProperty("threadNum", "2"));
        final String textDir = properties.getProperty("textDir");
        final String workDir = properties.getProperty("workDir");
        final String msc_py_path = properties.getProperty("msc_py");

        final String attribute = "keepClassRate";
        //Get the latest weight from database records
        final float edgeSelectedWeight = 3.2f;  //Edge threshold weight, 3.2 is proven optimal
        float keepClassRate = 0.00f;
        float mutationRate = 0.00f;
        Connection connection = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            connection = C3p0Utils.getConnection("localhost-3306-rouge_eval");
            final String sql = "SELECT weight_kc, weight_mt FROM rouge_avg ORDER BY date DESC LIMIT 1";
            ps = connection.prepareStatement(sql);
            rs = ps.executeQuery();
            if (rs.next()) {
                keepClassRate = rs.getFloat("weight_kc") + 0.01f;
                mutationRate = rs.getFloat("weight_mt") + 0.01f;
            }
        } catch (final SQLException e) {
            // TODO Auto-generated catch block
            throw new SQLException("Exception getting latest weight！", e);
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
            if (connection != null) {
                connection.close();
            }
        }

        while (keepClassRate < 1) {
            while (mutationRate < 1) {
                log.info("Optimizing parameters：kc=" + keepClassRate + "\tmt=" + mutationRate);
                /*Cluster events，同时按Class别抽取时间所在子句*/
                log.info("Performing event clustering and sub-sentence extraction...");
                //Nodes directory
                final String nodesDir = workDir + "/" + DIR_NODES;
                //Clean up files in the above directory
                final File fileNodes = new File(nodesDir);
                if (!fileNodes.exists()) {
                    log.error(nodesDir + "does not exist！");
                } else {
                    //Delete all intermediate files from clustering algorithm
                    final String[] filenames = fileNodes.list((dir, name) -> name.contains("renumbered"));
                    if (filenames != null && filenames.length > 0) {
                        for (final String filename : filenames) {
                            final File fileRenumbered = new File(nodesDir + "/" + filename);
                            if (fileRenumbered != null) {
                                fileRenumbered.delete();
                            }
                        }
                    }
                }

                //Edges directory
                final String edgeDir = workDir + "/" + DIR_EDGES;
                //Clean up the above directory
                final File fileEdgesCW = new File(edgeDir + "/" + DIR_CW_PRETREAT);
                if (fileEdgesCW.exists()) {
                    fileEdgesCW.delete();
                }

                //Clustering results directory
                final String clustResultDir = workDir + "/" + DIR_EVENTS_CLUST;
                final File fileClustResult = new File(clustResultDir);
                if (fileClustResult.exists()) {
                    fileClustResult.delete();
                }
                fileClustResult.mkdirs();

                //Sentence extraction results directory
                final String sentencesSaveDir = workDir + "/" + DIR_SUB_SENTENCES_EXTRACTED;
                final File fileSentences = new File(sentencesSaveDir);
                if (fileSentences.exists()) {
                    fileSentences.delete();
                }
                fileSentences.mkdirs();

                //Sentence compression results directory
                final String sentencesCompressDir = workDir + "/" + DIR_SENTENCES_COMPRESSION;
                final File fileSentencesCompress = new File(sentencesCompressDir);
                if (fileSentencesCompress.exists()) {
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
                    //Cluster events
                    //Build Chinese Whispers algorithm parameters
                    final CWRunParam cwRunParam = new CWRunParam();
                    cwRunParam.setJarPath(properties.getProperty("cwjarPath"));
                    cwRunParam.setKeepClassRate(keepClassRate);
                    cwRunParam.setMutationRate(mutationRate);
                    cwRunParam.setIterationCount(100);
                    cluster.doCluster(cwRunParam);
                    //Get sentences for events, main thread blocks until all sub-sentence extraction completes
                    cluster.clusterSentencesByEvents();
                } catch (IOException | InterruptedException e) {
                    log.error("Event clustering error！", e);
                    //e.printStackTrace();
                }

                /*Multi-sentence compression*/
                final String commond_msc = "python " + msc_py_path;
                try {
                    final Process process = Runtime.getRuntime().exec(commond_msc);
                    process.waitFor();
                    final BufferedReader read = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = null;
                    while ((line = read.readLine()) != null) {
                        log.info(line);
                    }
                    if (read != null) {
                        read.close();
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("Multi-sentence compression error！", e);
                    //e.printStackTrace();
                }

                /*Evaluate results*/
                //Copy compressed files to the peers directory of the evaluation program
                final String rougePath = properties.getProperty("rouge_path");
                final String commond_cp_peers = "\\cp " + sentencesCompressDir + "/* " + rougePath + "/peers";
                final String[] cp_command = {"/bin/sh", "-c", commond_cp_peers};  //Will fail without this wrapper
                try {
                    final Process process = Runtime.getRuntime().exec(cp_command);
                    process.waitFor();
                    final BufferedReader read = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = null;
                    while ((line = read.readLine()) != null) {
                        log.info(line);
                    }
                    if (read != null) {
                        read.close();
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("Error copying compressed files to peers directory！", e);
                    //e.printStackTrace();
                }

                //Executerouge
                final String commond = "perl ROUGE-1.5.5.pl -e /home/eventChain/rouge_eval/data"
                        + " -a -n 2 -x -m -2 4 -u -c 95 -r 1000 -f A -p 0.5 -t 0"
                        + " -d /home/eventChain/rouge_eval/rougejk.in"
                        + " > /home/eventChain/rouge_eval/scores.out";
                final String[] commond_rouge = {"/bin/sh", "-c", commond};
                try {
                    final Process process = Runtime.getRuntime().exec(commond_rouge);
                    final String errMsg = ParameterOptimization.execStreamProcess(process.getErrorStream());
                    final String outMsg = ParameterOptimization.execStreamProcess(process.getInputStream());
                    if (!"".equals(errMsg)) {
                        log.warn(errMsg);
                    }
                    if (!"".equals(outMsg)) {
                        log.info(outMsg);
                    }
                    if (process.waitFor() != 0) {
                        log.error("Abnormal return status from evaluation command！");
                    }
                } catch (IOException | InterruptedException e) {
                    log.error("ROUGE execution error，当前工作路径：" + System.getProperty("user.dir"), e);
                    //e.printStackTrace();
                }

                /*Get evaluation results*/
                final String regex = "3\\s+(ROUGE-[SU124]+)\\s+(Average_[RPF]):\\s+([\\w\\.]+)\\s+\\([\\s\\S]*?\\)";
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(new FileInputStream(rougePath + "/scores.out"), "UTF-8"));
                    final Pattern pattern = Pattern.compile(regex);
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!"".equals(line)) {
                            final Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
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
                                    log.error("Data persistence error：" + rouge.toString() + "\t" + keepClassRate + "\t" + attribute, e);
                                    //e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (final IOException e) {
                    log.error("Error parsing evaluation results file：", e);
                    //e.printStackTrace();
                } finally {
                    if (br != null) {
                        br.close();
                    }
                }
                mutationRate += 0.01f;
            }
            //Parameter递增
            keepClassRate += 0.01f;
        }

    }

    /**
     * Persist results
     *
     * @param rougeAvg
     * @throws SQLException
     */
    private void orm2db(RougeAvg rougeAvg, float weight_kc, float weight_mt, float weight_es) throws SQLException {
        Connection connection = null;
        PreparedStatement ps = null;
        if (rougeAvg != null) {
            try {
                connection = C3p0Utils.getConnection("localhost-3306-rouge_eval");
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
            } finally {
                if (ps != null) {
                    ps.close();
                }
                if (connection != null) {
                    connection.close();
                }
            }
        }
    }

}
