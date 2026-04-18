package org.zhenchao.zelus.optimize;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.util.SerializeUtils;
import org.zhenchao.zelus.takahe.domain.CompressUnit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Optimizer
 *
 * @author zhenchao 2015-11-13 20:42:34
 */
public final class Optimizer {

    private static Logger log = Logger.getLogger(Optimizer.class);

    private Optimizer() {
    }

    //String[] topics = {"D0743", "D0734", "D0726", "D0737", "D0736", "D0709", "D0701", "D0739", "D0718", "D0706", "D0723", "D0708", "D0733", "D0728", "D0744", "D0719", "D0724", "D0741", "D0702", "D0720", "D0715", "D0714"};

    public static void main(String[] args) {

        if (args == null || args.length != 5) {
            log.error("Invalid parameters！");
            log.info("Parameter description：Compressed results directory\tWorking directory\tThread count\tMaximum candidate sentence count\tMaximum summary count");
            return;
        }

        String compressResultsDir = args[0];
        String workDir = args[1];
        int nThreads = Integer.parseInt(args[2]);
        int maxSentenceCount = Integer.parseInt(args[3]);
        int maxSummaryCount = Integer.parseInt(args[4]);

        File[] compressFiles = FileUtils.getFile(compressResultsDir).listFiles();

        List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();

        for (File compressFile : compressFiles) {
            try {
                Map<String, List<CompressUnit>> compressUnits = (Map<String, List<CompressUnit>>) SerializeUtils.readObj(compressFile.getAbsolutePath());
                if (MapUtils.isEmpty(compressUnits)) {
                    continue;
                }
                List<List<CompressUnit>> compressUnitsList = new ArrayList<List<CompressUnit>>();
                for (Entry<String, List<CompressUnit>> entry : compressUnits.entrySet()) {
                    if (CollectionUtils.isEmpty(entry.getValue())) {
                        continue;
                    }
                    compressUnitsList.add(new ArrayList<CompressUnit>(entry.getValue()));
                }

                if (CollectionUtils.isNotEmpty(compressUnitsList)) {
                    tasks.add(new EnumerationThread(compressUnitsList, workDir, compressFile.getName(), maxSentenceCount, maxSummaryCount));
                }

                compressUnits.clear();

            } catch (Exception e) {

                log.error("Build thread for compress file[" + compressFile.getAbsolutePath() + "] error!", e);

            }
        }

        ExecutorService es = Executors.newFixedThreadPool(nThreads);
        try {

            List<Future<Boolean>> futures = es.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                future.get();
            }

        } catch (InterruptedException | ExecutionException e) {

            log.error("", e);

        } finally {
            es.shutdown();
        }

    }

}
