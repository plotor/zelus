package org.zhenchao.zelus.common.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.log4j.Logger;
import org.zhenchao.zelus.common.global.Constants;
import org.zhenchao.zelus.common.pojo.EventType;
import org.zhenchao.zelus.common.pojo.EventWithPhrase;
import org.zhenchao.zelus.common.pojo.Vector;
import org.zhenchao.zelus.common.pojo.Word;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 向量操作相关类
 *
 * @author Apache_xiaochao
 */
public class VectorOperator implements Constants {

    private final Logger log = Logger.getLogger(this.getClass());

    private EhcacheUtils ehCacheUtil;

    public VectorOperator() {
        super();
    }

    public VectorOperator(String cacheName, String datasource) {

        this.ehCacheUtil = new EhcacheUtils(cacheName, datasource);

    }

    /**
     * 根据事件中三个词的已知向量，来计算对应事件的向量
     *
     * @param vecs_left
     * @param vecs_middle
     * @param vecs_right
     * @return
     */
    public List<Double[]> eventToVecs(List<Float[]> vecs_left, List<Float[]> vecs_middle, List<Float[]> vecs_right) {

        List<Double[]> eventVecs = new ArrayList<Double[]>();

        for (Float[] f_v_left : vecs_left) {
            for (Float[] f_v_middle : vecs_middle) {
                for (Float[] f_v_right : vecs_right) {
                    double[][] kronecker_left_right = new double[DIMENSION][DIMENSION];  //存储主语为宾语的克罗内克积
                    //计算主语和宾语的克罗内卡积
                    for (int i = 0; i < DIMENSION; ++i) {
                        for (int j = 0; j < DIMENSION; ++j) {
                            kronecker_left_right[i][j] = f_v_right[i] * f_v_left[j];
                        }
                    }
                    //将得到的克罗内卡积与谓语作矩阵乘法
                    Double[] eventVec = new Double[DIMENSION];
                    for (int i = 0; i < DIMENSION; ++i) {
                        double product = 0;
                        for (int j = 0; j < DIMENSION; ++j) {
                            product += f_v_middle[j] * kronecker_left_right[j][i];
                        }
                        eventVec[i] = product;
                    }
                    eventVecs.add(eventVec);
                }
            }
        }

        return eventVecs;

    }

    /**
     * 利用组合语义得到事件向量
     *
     * @param leftVec
     * @param middleVec
     * @param rightVec
     * @return
     */
    public Double[] wordVecToEventVec(Float[] leftVec, Float[] middleVec, Float[] rightVec) {

        if (leftVec == null || middleVec == null || rightVec == null) {
            return null;
        }

        double[][] kronecker = new double[DIMENSION][DIMENSION];  //存储主语为宾语的克罗内克积
        //计算主语和宾语的克罗内卡积
        for (int i = 0; i < DIMENSION; ++i) {
            for (int j = 0; j < DIMENSION; ++j) {
                kronecker[i][j] = rightVec[i] * leftVec[j];
            }
        }

        //将得到的克罗内卡积与谓语作矩阵乘法
        Double[] eventVec = new Double[DIMENSION];
        for (int i = 0; i < DIMENSION; ++i) {
            double product = 0;
            for (int j = 0; j < DIMENSION; ++j) {
                product += middleVec[j] * kronecker[j][i];
            }
            eventVec[i] = product;
        }

        return eventVec;

    }

    /**
     * 将事件转化成向量
     * @param event
     * @return
     * @throws SQLException
     */
    /*public List<Double[]> eventToVecs(EventWithWord event) {

        List<Double[]> eventVecs = new ArrayList<Double[]>();

        if(event != null){
            //创建一个值全为1的词向量
            Float[] all_1_vec = new Float[DIMENSION];
            Arrays.fill(all_1_vec, 1f);

            if(EventType.TERNARY.equals(event.eventType())) {
                //主-谓-宾结构
                List<Float[]> vecs_left = null;
                List<Float[]> vecs_middle = null;
                List<Float[]> vecs_right = null;
                try {
                    vecs_left = this.ehCacheUtil.getVec(event.getLeftWord());
                    vecs_middle = this.ehCacheUtil.getVec(event.getMiddleWord());
                    vecs_right = this.ehCacheUtil.getVec(event.getRightWord());
                } catch (SQLException e) {
                    this.log.error("Get word vector error!", e);
                }

                if(CollectionUtils.isNotEmpty(vecs_left)
                        && CollectionUtils.isNotEmpty(vecs_middle)
                        && CollectionUtils.isNotEmpty(vecs_right)) {

                    eventVecs = this.eventToVecs(vecs_left, vecs_middle, vecs_right);

                }else{

                    this.log.warn("当前事件中存在未知的词向量：" + event);

                }

            } else if(EventType.RIGHT_MISSING.equals(event.eventType())) {

                //主-谓，将宾语的向量全部用1代替
                List<Float[]> vecs_left = null;
                List<Float[]> vecs_middle = null;
                List<Float[]> vecs_right = new ArrayList<Float[]>();
                vecs_right.add(all_1_vec);

                try {
                    vecs_left = this.ehCacheUtil.getVec(event.getLeftWord());
                    vecs_middle = this.ehCacheUtil.getVec(event.getMiddleWord());
                } catch (SQLException e) {
                    this.log.error("Get word vector error!", e);
                }

                if(CollectionUtils.isNotEmpty(vecs_left)
                        && CollectionUtils.isNotEmpty(vecs_middle)) {

                    eventVecs = this.eventToVecs(vecs_left, vecs_middle, vecs_right);

                }else{

                    this.log.warn("当前事件中存在未知的词向量：" + event);

                }

            } else if(EventType.LEFT_MISSING.equals(event.eventType())){

                //谓-宾，将主语的向量全部用1代替
                List<Float[]> vecs_left = new ArrayList<Float[]>();
                vecs_left.add(all_1_vec);
                List<Float[]> vecs_middle = null;
                List<Float[]> vecs_right = null;

                try {
                    vecs_middle = this.ehCacheUtil.getVec(event.getMiddleWord());
                    vecs_right = this.ehCacheUtil.getVec(event.getRightWord());
                } catch (SQLException e) {
                    this.log.error("Get word vector error!", e);
                }

                if(CollectionUtils.isNotEmpty(vecs_middle)
                        && CollectionUtils.isNotEmpty(vecs_right)){

                    eventVecs = this.eventToVecs(vecs_left, vecs_middle, vecs_right);

                }else{

                    this.log.warn("当前事件中存在未知的词向量：" + event);

                }

            }else{

                this.log.info("不支持该事件类型：" + event);

            }
        }
        return eventVecs;
    }*/

    /**
     * 将事件转化成向量
     *
     * @param eventWithPhrase
     * @return
     * @throws SQLException
     */
    public Double[] eventToVec(EventWithPhrase eventWithPhrase) {

        Double[] eventVec = null;

        if (EventType.TERNARY.equals(eventWithPhrase.eventType())) {
            //主-谓-宾结构
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
            Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if (EventType.RIGHT_MISSING.equals(eventWithPhrase.eventType())) {

            //主-谓，将宾语的向量全部用1代替
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
            Float[] rightVec = new Float[Constants.DIMENSION];
            Arrays.fill(rightVec, 1.0f);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if (EventType.LEFT_MISSING.equals(eventWithPhrase.eventType())) {

            //谓-宾，将主语的向量全部用1代替
            Float[] leftVec = new Float[Constants.DIMENSION];
            Arrays.fill(leftVec, 1.0f);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
            Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else {

            this.log.warn("不支持该事件类型：" + eventWithPhrase);

        }

        return eventVec;

    }

    /**
     * 计算事件向量<br>
     * 相对于eventToVec的区别在于，如果某个短语的向量不存在，则随机生成一个向量，而不是用1代替
     *
     * @param eventWithPhrase
     * @return
     */
    public Double[] eventToVecPlus(EventWithPhrase eventWithPhrase) {

        if (EventType.ERROR.equals(eventWithPhrase.eventType())) {
            this.log.error("不支持该事件类型：" + eventWithPhrase);
            return null;
        }

        Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
        Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
        Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true);

        int randomCount = 0;

        if (middleVec == null) {
            // 对于谓语，如果不存在向量，则直接忽略该事件
            //middleVec = this.randomWordVec();
            //randomCount++;
            this.log.warn("The middle vector is null, ignore this event:" + eventWithPhrase);
            return null;
        }

        if (leftVec == null) {
            leftVec = this.randomWordVec();
            randomCount++;
            this.log.info("The left vector is null, build a random vector:" + Arrays.toString(leftVec));
        }

        if (rightVec == null) {
            rightVec = this.randomWordVec();
            randomCount++;
            this.log.info("The right vector is null, build a random vector:" + Arrays.toString(rightVec));
        }

        if (randomCount > 1) {
            // 对于一个事件，如果存在两次以上的随机向量生成，则忽略该事件
            return null;
        }

        return this.wordVecToEventVec(leftVec, middleVec, rightVec);

    }

    /**
     * 计算事件向量<br>
     * 相对于eventToVec的区别在于，如果某个短语的向量不存在，则随机生成一个向量，而不是用1代替
     *
     * @param eventWithPhrase
     * @param wordvecsInTopic 词向量字典
     * @return
     */
    public Double[] eventToVecPlus(EventWithPhrase eventWithPhrase, Map<String, Vector> wordvecsInTopic) {

        if (EventType.ERROR.equals(eventWithPhrase.eventType())) {
            this.log.error("不支持该事件类型：" + eventWithPhrase);
            return null;
        }

        if (MapUtils.isEmpty(wordvecsInTopic)) {
            this.log.error("The word vector dict is empty!");
            return null;
        }

        /*
         * 计算短语的最佳向量
         */
        Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true, wordvecsInTopic);
        Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false, wordvecsInTopic);
        Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true, wordvecsInTopic);

        int randomCount = 0;

        if (middleVec == null) {
            // 对于谓语，如果不存在向量，则直接忽略该事件
            //middleVec = this.randomWordVec();
            //randomCount++;
            this.log.warn("The middle vector is null, ignore this event:" + eventWithPhrase);
            return null;
        }

        if (leftVec == null) {
            leftVec = this.randomWordVec();
            randomCount++;
            this.log.info("The left vector is null, build a random vector:" + Arrays.toString(leftVec));
        }

        if (rightVec == null) {
            rightVec = this.randomWordVec();
            randomCount++;
            this.log.info("The right vector is null, build a random vector:" + Arrays.toString(rightVec));
        }

        if (randomCount > 1) {
            // 对于一个事件，如果存在两次以上的随机向量生成，则忽略该事件
            return null;
        }

        return this.wordVecToEventVec(leftVec, middleVec, rightVec);

    }

    /**
     * 计算两个向量之间的余弦值<br>
     * 如果小于0，则说明计算出错
     *
     * @param vec1
     * @param vec2
     * @return
     */
    public static double cosineDistence(Double[] vec1, Double[] vec2) {

        double value = -1;

        if (vec1 == null || vec2 == null) {
            return value;
        }

        //利用向量余弦值来计算事件之间的相似度
        double scalar = 0;  //两个向量的内积
        double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
        for (int i = 0; i < DIMENSION; ++i) {
            scalar += vec1[i] * vec2[i];
            module_1 += vec1[i] * vec1[i];
            module_2 += vec2[i] * vec2[i];
        }

        if (module_1 > 0 && module_2 > 0) {
            value = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)) + 1;
        }

        return value;

    }

    /**
     * 计算两个向量之间的欧式距离<br>
     * 如果返回结果小于0，则说明计算出错
     *
     * @param vec1
     * @param vec2
     * @return
     */
    public double euclideanDistance(Double[] vec1, Double[] vec2) {

        if (vec1 == null || vec2 == null) {
            return -1.0D;
        }

        Double sum = 0.0D;
        for (int i = 0; i < DIMENSION; i++) {
            sum += Math.pow(vec1[i] - vec2[i], 2);
        }

        return Math.sqrt(sum);

    }

    /**
     * 标准化欧式距离
     *
     * @param vec1
     * @param vec2
     * @return
     */
    public double standardizedEuclideanDistance(Double[] vec1, Double[] vec2) {
        // TODO 完成标准欧式距离的计算，2015-11-10 11:14:28
        return 0;
    }

    /**
     * 计算输入向量集合的中心向量
     *
     * @param vectors
     * @return
     */
    public static Double[] centralVector(List<Double[]> vectors) {

        Double[] cv = null;

        if (CollectionUtils.isEmpty(vectors)) {
            return cv;
        }

        cv = new Double[DIMENSION];
        Arrays.fill(cv, 0.0D);
        for (Double[] vec : vectors) {
            for (int i = 0; i < DIMENSION; i++) {
                cv[i] += vec[i];
            }
        }

        for (int i = 0; i < DIMENSION; i++) {
            cv[i] /= vectors.size();
        }

        return cv;

    }

    /**
     * 随机生成一个{@value SystemConstant.DIMENSION}维的向量，每一维的值在-1.5~1.5之间
     *
     * @return
     */
    private Float[] randomWordVec() {
        Float[] vec = new Float[DIMENSION];
        Random random = new Random(System.currentTimeMillis());
        for (int i = 0; i < DIMENSION; i++) {
            vec[i] = random.nextFloat() * 3.0f - 1.5f;
        }
        return vec;
    }

    /**
     * 计算两个事件之间的近似度
     * 策略三：
     * 采用“Experimental Support for a Categorical Compositional Distributional Model of Meaning.pdf”中的方法
     * 采用数据库+缓存框架
     * @param event1
     * @param event2
     * @return
     * @throws SQLException
     */
    /*public double eventsApproximationDegree(EventWithWord event1, EventWithWord event2) throws SQLException{
        double approx = 0;  //默认以最大值来表示两个事件之间的最大值
        if(event1 != null && event2 != null){
            //计算得到两个事件的向量
            final List<Double[]> event_vecs_1 = this.eventToVecs(event1);
            final List<Double[]> event_vecs_2 = this.eventToVecs(event2);
            if(event_vecs_1.size() > 0 && event_vecs_2.size() > 0){
                final Random random = new Random(System.currentTimeMillis());
                final int r_value = random.nextInt(100);
                if(r_value >= VARIATION_WEIGHT){
                    //随机选择一个值作为相似度
                    Double[] event_vec_1 = null;
                    Double[] event_vec_2 = null;
                    int times = 0;
                    while((event_vec_1 = event_vecs_1.get(random.nextInt(event_vecs_1.size()))) == null){
                        if(++times >= event_vecs_1.size()){
                            return Double.MAX_VALUE;
                        }
                    }
                    times = 0;
                    while((event_vec_2 = event_vecs_2.get(random.nextInt(event_vecs_2.size()))) == null){
                        if(++times >= event_vecs_2.size()){
                            return Double.MAX_VALUE;
                        }
                    }
                    //利用向量余弦值来计算事件之间的相似度
                    double scalar = 0;  //两个向量的内积
                    double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
                    for(int i = 0; i < DIMENSION; ++i){
                        scalar += event_vec_1[i] * event_vec_2[i];
                        module_1 += event_vec_1[i] * event_vec_1[i];
                        module_2 += event_vec_2[i] * event_vec_2[i];
                    }
                    if(module_1 > 0 && module_2 > 0) {
                        approx = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2));
                    }
                }else{
                    //选择最大值作为相似度
                    double max_approxs = 0;  //记录计算过程中的最大相似度
                    double approxTmp = max_approxs;
                    for (final Double[] event_vec_1 : event_vecs_1) {
                        for (final Double[] event_vec_2 : event_vecs_2) {
                            //利用向量余弦值来计算事件之间的相似度
                            double scalar = 0;  //两个向量的内积
                            double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
                            for(int i = 0; i < DIMENSION; ++i){
                                scalar += event_vec_1[i] * event_vec_2[i];
                                module_1 += event_vec_1[i] * event_vec_1[i];
                                module_2 += event_vec_2[i] * event_vec_2[i];
                            }
                            if(module_1 > 0 && module_2 > 0) {
                                approxTmp = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2));
                                if(approxTmp > max_approxs){
                                    max_approxs = approxTmp;
                                }
                                //approxs.add(scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)));
                            }
                        }
                    }
                    approx = max_approxs;
                }
            }
        }
        return approx;
    }*/

    /**
     * 计算两个事件之间的近似度
     * 策略三：
     * 采用“Experimental Support for a Categorical Compositional Distributional Model of Meaning.pdf”中的方法
     * 采用数据库+缓存框架
     *
     * @param event_vecs_1
     * @param event_vecs_2
     * @return
     * @throws SQLException
     */
    public double eventsApproximationDegree(List<Double[]> event_vecs_1, List<Double[]> event_vecs_2) throws SQLException {
        double approx = 0;  //默认以最大值来表示两个事件之间的最大值
        if (event_vecs_1 == null || event_vecs_2 == null) {
            return approx;
        }
        //计算得到两个事件的向量
        if (event_vecs_1.size() > 0 && event_vecs_2.size() > 0) {
            final Random random = new Random(System.currentTimeMillis());
            final int r_value = random.nextInt(100);
            if (r_value >= VARIATION_WEIGHT) {
                //随机选择一个值作为相似度
                Double[] event_vec_1 = null;
                Double[] event_vec_2 = null;
                int times = 0;
                while ((event_vec_1 = event_vecs_1.get(random.nextInt(event_vecs_1.size()))) == null) {
                    if (++times >= event_vecs_1.size()) {
                        return Double.MAX_VALUE;
                    }
                }
                times = 0;
                while ((event_vec_2 = event_vecs_2.get(random.nextInt(event_vecs_2.size()))) == null) {
                    if (++times >= event_vecs_2.size()) {
                        return Double.MAX_VALUE;
                    }
                }
                //利用向量余弦值来计算事件之间的相似度
                double scalar = 0;  //两个向量的内积
                double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
                for (int i = 0; i < DIMENSION; ++i) {
                    scalar += event_vec_1[i] * event_vec_2[i];
                    module_1 += event_vec_1[i] * event_vec_1[i];
                    module_2 += event_vec_2[i] * event_vec_2[i];
                }
                if (module_1 > 0 && module_2 > 0) {
                    approx = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2));
                }
            } else {
                //选择最大值作为相似度
                double max_approxs = 0;  //记录计算过程中的最大相似度
                double approxTmp = max_approxs;
                for (final Double[] event_vec_1 : event_vecs_1) {
                    for (final Double[] event_vec_2 : event_vecs_2) {
                        //利用向量余弦值来计算事件之间的相似度
                        double scalar = 0;  //两个向量的内积
                        double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
                        for (int i = 0; i < DIMENSION; ++i) {
                            scalar += event_vec_1[i] * event_vec_2[i];
                            module_1 += event_vec_1[i] * event_vec_1[i];
                            module_2 += event_vec_2[i] * event_vec_2[i];
                        }
                        if (module_1 > 0 && module_2 > 0) {
                            approxTmp = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2));
                            if (approxTmp > max_approxs) {
                                max_approxs = approxTmp;
                            }
                            //approxs.add(scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)));
                        }
                    }
                }
                approx = max_approxs;
            }
        }
        return approx;
    }

    /**
     * 计算一个短语的向量
     *
     * @param phrase
     * @param ignoreStopwords
     * @return
     */
    private Float[] phraseVector(List<Word> phrase, boolean ignoreStopwords) {

        Float[] phraseVec = null;

        if (CollectionUtils.isEmpty(phrase)) {

            return phraseVec;

        }

        /**
         * 计算策略:<br>
         * 对短语中的非停用词的向量进行累加取平均
         */
        phraseVec = new Float[DIMENSION];
        Arrays.fill(phraseVec, 0.0f);  // 初始以0填充
        int count = 0;
        for (Word word : phrase) {

            if (word.getName().equals(word.getPos())) {
                // 跳过标点符号
                continue;
            }

            if (ignoreStopwords && STOPWORDS.contains(word.getLemma())) {
                // 跳过停用词
                continue;
            }

            try {
                /*List<Vector> vecs = this.ehCacheUtil.getVec(word);
                if(CollectionUtils.isNotEmpty(vecs)) {
                    this.log.info(">>" + word + "\t" + vecs.size());
                    int min = Integer.MAX_VALUE;
                    Float[] vec = null;
                    for (Vector vector : vecs) {
                        // 以最相近的单词的向量作为当前词的词向量
                        int dis = ZelusUtils.strDistance(word.getName(), vector.getWord());
                        if(dis < min) {
                            min = dis;
                            vec = vector.floatVecs();
                        }
                    }
                    if(vec != null) {
                        for(int i = 0; i < DIMENSION; i++) {
                            phraseVec[i] += vec[i];
                        }
                        count++;
                    }*/

                Vector vector = this.ehCacheUtil.getMostSimilarVec(word);
                if (vector != null) {
                    Float[] vec = vector.floatVecs();
                    for (int i = 0; i < DIMENSION; i++) {
                        phraseVec[i] += vec[i];
                    }
                    count++;
                } else {

                    this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for word:" + word);

                }
            } catch (Exception e) {
                this.log.error("Get vector error, word: " + word, e);
            }
        }

        if (count == 0) {
            this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for phrase:" + phrase);
            return null;
        }

        for (int i = 0; i < DIMENSION; i++) {
            phraseVec[i] /= count;
        }

        return phraseVec;

    }

    /**
     * 计算一个短语的向量
     *
     * @param phrase
     * @param ignoreStopwords
     * @param wordvecsInTopic 词向量字典
     * @return
     */
    private Float[] phraseVector(List<Word> phrase, boolean ignoreStopwords, Map<String, Vector> wordvecsInTopic) {

        Float[] phraseVec = null;

        if (CollectionUtils.isEmpty(phrase)) {

            return phraseVec;

        }

        /**
         * 计算策略:<br>
         * 对短语中的非停用词的向量进行累加取平均
         */
        phraseVec = new Float[DIMENSION];
        Arrays.fill(phraseVec, 0.0f);  // 初始以0填充
        int count = 0;
        for (Word word : phrase) {

            if (word.getName().equals(word.getPos())) {
                // 跳过标点符号
                continue;
            }

            if (ignoreStopwords && STOPWORDS.contains(word.getLemma())) {
                // 跳过停用词
                continue;
            }

            try {
                //Vector vector = this.ehCacheUtil.getMostSimilarVec(word);
                Vector vector = wordvecsInTopic.get(word.dictKey());
                if (vector != null) {
                    Float[] vec = vector.floatVecs();
                    for (int i = 0; i < DIMENSION; i++) {
                        phraseVec[i] += vec[i];
                    }
                    count++;
                } else {

                    this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for word:" + word);

                }
            } catch (Exception e) {
                this.log.error("Get vector error, word: " + word, e);
            }
        }

        if (count == 0) {
            this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for phrase:" + phrase);
            return null;
        }

        for (int i = 0; i < DIMENSION; i++) {
            phraseVec[i] /= count;
        }

        return phraseVec;

    }

    public Double[] sentenceVec(String sentence) {

        Double[] vector = new Double[DIMENSION];

        return vector;

    }

    public static void main(String[] args) {

        /*Float[][] left = {{0.335416f, 0.056155f, 0.228265f, -0.297574f, -1.276934f, 0.815965f, 0.410159f, -0.262553f, 0.793073f, 0.396776f, 0.598523f, 0.010059f, 0.120179f, -0.336114f, -0.102684f, 0.472767f, 0.217998f, 0.647894f, 0.942736f, -0.186825f, -0.351009f, 0.574076f, 0.367480f, 0.534193f, -0.676076f, -0.124686f, 0.279660f, -0.635803f, 0.737334f, 0.133044f, 0.187409f, 0.644680f, -0.018215f, 0.973174f, -0.363938f, 0.078989f, 0.424471f, 0.052268f, 0.624176f, -0.407638f, 0.457858f, 1.204380f, 0.145810f, 0.078935f, 0.935746f, -0.379727f, 0.238805f, -0.206280f, -0.239308f, -0.957847f, -0.048152f, 0.377440f, -0.089563f, -0.125682f, -0.213288f, -0.905956f, -0.050337f, 0.334237f, 0.939444f, -0.341294f, -0.373000f, -0.564402f, 0.814943f, 0.389412f, 0.286751f, 0.108027f, -0.271446f, 0.328058f, 0.786759f, 0.553270f, -0.511148f, -0.109515f, 0.169607f, 0.130867f, 0.007374f, 0.061471f, 0.508458f, -0.309874f, 0.302225f, 0.428544f, 0.973108f, 0.197022f, 0.139925f, -0.489951f, 0.530741f, -0.515608f, -0.264039f, -0.017346f, -0.123153f, 0.301523f, 0.977191f, 0.223071f, 0.781653f, -0.243309f, -0.323686f, -1.252008f, -0.427823f, -0.239605f, -0.736664f, -0.850782f, -1.460757f, -1.105427f, -0.018206f, 0.589601f, -0.797083f, -0.093676f, 0.600697f, 0.557563f, -0.551819f, 1.003270f, 0.125117f, 0.192604f, 0.437667f, -0.380839f, -0.561081f, -0.813269f, 0.120751f, 0.559493f, 0.105146f, -0.586530f, -0.388075f, 0.702227f, 0.329230f, 0.318060f, 0.275091f, 0.397347f, -0.040853f, -0.626400f, -1.135207f, -0.198979f, 0.882462f, -0.081439f, -0.335812f, -0.190198f, -0.939154f, 1.372933f, 0.464538f, -0.753677f, -0.925668f, 0.494384f, -0.703823f, -0.509289f, -0.335397f, -0.929536f, -0.230703f, -0.253637f, -1.015265f, -0.156893f, 0.754191f, -1.171471f, -1.113245f, 0.293943f, -0.814485f, 0.510707f, -0.117730f, 1.196193f, 0.308853f, 0.816809f, -0.010493f, -0.327471f, 0.557706f, 0.712754f, 0.350157f, 0.480823f, 1.155758f, 0.059992f, -0.190002f, -0.187068f, 0.839176f, -0.166699f, 1.380306f, 0.237143f, -0.276721f, -0.640343f, 0.315986f, -0.351007f, 0.095354f, 0.781874f, -0.453331f, -0.480572f, -0.334117f, 1.044654f, 0.586517f, 0.783460f, -0.519097f, 0.769296f, 0.039208f, 0.624990f, 1.031828f, 1.147254f, 0.973398f, 0.847248f, -0.333411f, -0.297128f, -0.884438f, 0.376330f, 0.131860f, -0.832971f, 0.004175f, 0.436492f, 0.903585f, -1.307891f, 1.148569f, 0.071399f, -0.336403f, 1.347697f, 0.443812f, 0.192402f, 0.605329f, -0.003476f, 0.371935f, 0.182736f, -1.169960f, 0.560383f, -0.057718f, -1.314890f, 0.472697f, -0.559236f, 0.553780f, -0.055206f, -0.194342f, 0.085876f, 1.606180f, -0.155008f, -0.274816f, 0.521641f, 0.666939f, 0.721091f, -0.220787f, -0.540011f, 0.717742f, 0.222611f, 0.945351f, 0.027851f, -0.530616f, 0.739893f, 0.721084f, 0.997397f, 0.082591f, 0.140792f, 1.221344f, -0.070578f, 0.436694f, 1.214052f, 0.693840f, 0.753080f, -0.717273f, -1.019776f, -0.498251f, 1.170253f, -1.308313f, -0.105985f, -0.756355f, 0.667434f, -0.578690f, 0.271698f, -0.832803f, 0.320806f, -0.434164f, -0.940345f, -0.747282f, -0.356184f, 0.299354f, -1.592906f, -0.384459f, 0.200776f, -0.651541f, 0.025183f, -0.980567f, -0.006319f, 0.002266f, -0.137904f, -0.746086f, -0.264618f, 0.549014f, 0.355633f, 1.292481f, 0.573901f, -0.591311f, -0.378357f, -0.429029f, -0.588979f, 0.294723f, 0.344040f, 0.184701f, 0.784700f, -0.519276f, -0.086161f, -0.814787f, 0.004979f, 0.275823f, -0.313210f, -1.122314f, 0.919033f, -0.208902f, -0.636328f, -0.323602f, 0.054010f, 0.305572f, -0.815597f},
                {-0.029020f, 0.137731f, 0.357814f, 0.392398f, 0.872402f, -0.604363f, 0.178206f, 0.154476f, 0.615896f, 0.339543f, -0.175707f, 0.453470f, 1.021835f, 0.546950f, -0.066533f, -0.229050f, -1.164404f, -0.160950f, -0.100600f, -0.061595f, -0.170656f, -1.490512f, -1.334438f, 0.013598f, -0.046990f, -0.188852f, -0.349681f, 1.195447f, -0.639022f, 1.359141f, 0.082533f, 1.472395f, 0.780011f, 0.630422f, -0.329373f, -0.687447f, -0.506106f, 0.289239f, 0.008185f, 0.445108f, -0.292650f, 0.792670f, -0.579820f, 0.626760f, -0.384068f, 0.665554f, 0.322762f, 1.365102f, -0.099829f, 0.350735f, 0.041939f, 1.015038f, -1.133614f, 0.195993f, -0.768869f, 1.308040f, 1.882896f, -0.018834f, -0.577796f, 0.261711f, 0.388245f, 1.822236f, -0.342601f, 0.344337f, -0.073965f, -1.262021f, -0.687977f, -0.574725f, -1.204941f, 0.878652f, -0.688153f, -0.144481f, 1.318935f, -0.158003f, 0.287484f, -0.634213f, -0.060580f, -0.588096f, 0.395154f, -0.555625f, 0.937368f, 1.033159f, 0.045261f, 0.814703f, -1.079035f, -0.331259f, -0.533717f, 0.313773f, 1.236937f, 0.703309f, -1.364633f, -0.178966f, 0.623243f, 0.110064f, -0.227717f, 0.671237f, -0.143594f, 0.713679f, 0.073766f, -1.084767f, -1.081409f, 0.301400f, 0.132104f, -1.115766f, 0.135199f, 1.864264f, 0.139528f, -1.097638f, -0.553435f, -1.085595f, -0.572427f, -0.002095f, -0.747863f, -0.549217f, -0.216627f, -2.352282f, 0.974429f, -0.599616f, 0.446108f, 0.191675f, 0.065055f, 0.560003f, -0.226220f, 0.053794f, -0.555136f, -1.204968f, 0.204267f, 1.621152f, -0.013527f, -0.084808f, -0.084129f, -0.344391f, -0.136914f, -0.225713f, -0.367351f, 0.260702f, 0.957805f, -0.727322f, -1.674627f, -0.113628f, 0.060993f, -0.401794f, -0.966725f, -0.938665f, -0.548556f, -1.019718f, 0.791411f, 0.195263f, -0.476936f, -0.193200f, 0.352006f, 0.152717f, 0.166465f, -0.506461f, -0.099459f, -1.148560f, 0.349798f, 0.507938f, 0.896964f, 1.450354f, 0.289939f, 0.776272f, 0.535872f, -0.902523f, 0.954881f, -0.403333f, 0.319294f, 0.918131f, -0.883584f, -1.079416f, -1.083259f, 0.744442f, 0.425287f, 0.026395f, -0.438418f, -1.220138f, 0.116565f, 0.347758f, -0.066361f, -0.039987f, -0.457048f, 0.001939f, -0.211833f, -0.945818f, 0.282828f, 0.160021f, 1.410816f, -0.048804f, -0.511766f, -0.188844f, 0.992290f, 1.072529f, 0.017483f, 0.417025f, 0.512580f, 0.264252f, -0.529802f, 0.031402f, 1.489383f, 0.108770f, 0.665756f, -0.978473f, -0.378109f, -1.020108f, -0.405451f, -0.878065f, 0.632855f, -0.926214f, -0.322480f, 0.499751f, 0.396603f, 0.502982f, 0.649060f, -0.286147f, 0.066702f, -0.053789f, -0.011272f, 0.276914f, 0.407215f, -0.650875f, 0.146598f, 0.207646f, 0.090020f, -1.279396f, 0.977738f, 0.532348f, 0.924727f, 0.044286f, -0.944549f, -1.521290f, 1.004590f, -0.334227f, -0.396965f, -0.498090f, -0.033583f, -1.201750f, 0.104230f, 0.542156f, -0.008132f, -0.429084f, -1.524448f, -0.413503f, -0.068026f, 0.466106f, 0.924258f, 0.201598f, 0.621574f, -0.612744f, 0.189163f, 0.044098f, -0.131929f, 0.639134f, -0.022228f, -0.337309f, -0.213220f, -1.075454f, 0.104725f, 0.871605f, -1.093778f, -0.522147f, 0.493242f, -1.435559f, 0.443049f, 0.157195f, 0.280905f, -1.421322f, 0.146734f, 0.289556f, 0.265061f, -0.931126f, 0.561341f, 0.957469f, 0.114791f, -0.057454f, 0.303324f, 1.001005f, 0.622770f, 0.239276f, -0.704304f, 0.091908f, 0.652886f, 0.465623f, -0.013367f, 0.776745f, 0.674622f, -0.266870f, 0.176870f, -0.200225f, 1.119116f, -0.315349f, 0.561484f, 0.554164f, -0.438238f, 0.280077f, -0.353051f, 1.134928f, -0.027845f, -0.514331f, 0.098009f, 0.996808f}};

        Float[] middle = {-0.981331f, 0.011972f, 0.547317f, -0.312119f, 0.107913f, 0.453000f, 0.042250f, 0.174620f, -0.036440f, -1.088559f, -0.238140f, -1.551067f, -0.127335f, 1.137584f, -0.388380f, -0.016857f, 0.205712f, 0.061312f, 0.432761f, 0.332033f, -0.338424f, -0.799711f, -0.005052f, -0.514077f, -0.129064f, -1.157337f, 0.306313f, 1.024529f, -0.899469f, -1.556255f, 0.692652f, -0.132831f, 0.792718f, -0.629723f, -0.090410f, -0.616972f, 0.095394f, -0.619672f, 0.002808f, -0.051982f, -0.036327f, -0.054140f, -0.620577f, -2.177630f, 0.287567f, 0.128940f, 0.388585f, 1.213756f, -0.895414f, 0.543178f, -0.298730f, -0.402183f, -0.075696f, -1.531823f, -0.984424f, 1.045642f, -0.572815f, -0.226131f, -0.595398f, 0.011352f, 0.043601f, -0.256503f, -0.227066f, 1.015053f, -1.291390f, -0.809098f, -0.346183f, 0.126557f, 0.033001f, -0.309777f, 0.175026f, -0.188191f, 0.392775f, 0.119340f, 1.177757f, -0.036918f, 0.651798f, -0.055696f, -0.217220f, -0.851479f, 0.694124f, 0.274312f, -0.325924f, 0.322634f, 0.361562f, -0.188144f, 0.530689f, -0.728494f, 0.513398f, -0.203212f, 0.193407f, 0.396416f, 0.056452f, 0.009131f, -0.400013f, 0.494757f, -0.979302f, 1.650710f, -0.403873f, -0.340178f, -0.946904f, -0.697805f, 0.657825f, -1.265691f, -0.820638f, 0.936010f, -0.453811f, -0.081094f, 0.076500f, -0.537429f, 0.761784f, 0.016510f, 0.429969f, 1.284607f, -0.006681f, -0.268908f, 0.043439f, 0.759738f, -1.388923f, -0.777690f, -0.620157f, -0.044821f, 0.184056f, 0.145618f, -0.317691f, 1.016585f, 0.553910f, 0.722854f, 0.574377f, 0.369547f, 1.000798f, -0.364192f, -0.053973f, -1.227147f, 1.499518f, -0.424163f, 0.822259f, -0.619403f, -0.568253f, -0.358385f, 0.621668f, 0.737656f, -0.227285f, -0.691548f, -0.597448f, -0.813168f, -0.085703f, 0.705926f, -0.251600f, 0.745577f, -0.453226f, -0.889994f, 0.463505f, -0.487018f, -0.767017f, -0.139764f, -0.773638f, 0.565187f, 1.195488f, 0.544440f, 0.420143f, 0.248955f, 1.067098f, 0.901908f, 0.089724f, 1.035709f, 0.279886f, -0.126623f, -0.335173f, -1.254566f, -0.885543f, 0.454440f, -0.799425f, -0.480164f, -0.387203f, -0.641459f, -0.570517f, -0.695048f, -0.738339f, 1.347781f, -0.580480f, -0.317908f, 0.082919f, 0.474659f, 0.574490f, 0.916713f, 0.365910f, 1.514630f, 1.107078f, -0.447954f, -0.424173f, 0.213646f, -0.043457f, -0.924151f, -0.263272f, 0.520191f, -0.248438f, 0.176085f, 0.310630f, -0.646390f, -0.539918f, 0.532171f, 0.283685f, -0.034616f, 1.060564f, -0.796839f, -0.926676f, -0.434412f, -0.665102f, -0.974607f, 0.287132f, -0.205917f, -0.024637f, -0.619544f, -1.663929f, -0.174134f, 0.397090f, 1.651435f, -0.378534f, 0.169485f, 1.913686f, -0.436988f, -0.674900f, -1.367506f, 0.433139f, -0.333439f, 1.307803f, 0.299198f, 0.554793f, -1.169378f, 0.062989f, -1.453306f, 0.352691f, 0.430425f, 0.918642f, -0.481036f, 1.753030f, 0.035308f, 1.370443f, 0.629933f, 1.105026f, 1.441693f, -0.576735f, 0.294038f, -0.612473f, -0.573598f, -0.016137f, -0.559140f, -0.033930f, -0.324608f, -0.682380f, 0.319523f, -0.077270f, -0.009278f, -0.147818f, -0.997046f, -0.802593f, 0.459537f, -0.197138f, -1.558253f, 0.094960f, 0.726595f, -0.649223f, 0.064233f, -0.272116f, -0.321184f, 0.702602f, -0.984664f, 0.150263f, 0.282078f, 0.070748f, -0.511690f, -0.060519f, 0.454502f, -0.773908f, -0.547863f, -1.080948f, 0.055928f, -0.358734f, -0.356553f, 0.361399f, -0.887507f, -1.505141f, 1.197436f, -1.317051f, 1.268797f, 0.245823f, 0.990493f, 0.046425f, -0.697316f, -0.583530f, 1.060818f, -0.674867f, 1.450865f, -0.307979f, 0.226320f, 0.799431f, 0.867068f, 0.166059f, 0.695225f};

        //Float[][] right = {}
        Float[] e_1_left = {-0.175929f, -0.402473f, -0.538017f, -0.933292f, -0.131545f, -0.046210f, -0.191201f, -0.204209f, 0.959824f, -0.041641f, 0.182947f, -0.173301f, 0.048972f, -0.619267f, 0.364093f, -0.862537f, 0.248357f, 1.141141f, -0.448282f, -0.580629f, 1.155632f, 0.516529f, -0.197044f, -0.351158f, -0.720036f, -0.549860f, 0.251558f, 0.303508f, -0.593316f, -0.380744f, 0.050845f, -0.501623f, 0.304972f, 0.048653f, -0.631201f, -0.835861f, -0.024487f, 0.695186f, -0.348709f, -0.000791f, 0.380809f, 0.766525f, -0.240773f, -0.184387f, 0.083996f, -0.411472f, 0.392901f, 0.937803f, 1.218632f, 0.252286f, -0.011758f, -0.488523f, -0.315353f, 0.032369f, -0.028400f, -0.170574f, -0.260701f, -0.605935f, 0.505818f, 0.470779f, 0.141168f, -0.432042f, -0.474021f, 0.467207f, 0.276671f, -0.356387f, 0.382345f, 0.031934f, 0.160787f, -0.099540f, -0.043733f, -0.487518f, 0.328500f, 0.280811f, -0.248201f, 0.790260f, 0.860382f, 0.911795f, 0.122064f, -0.194328f, 0.473721f, -1.442805f, -0.041745f, -0.121516f, -0.322662f, 0.225501f, -0.033438f, 0.586179f, 1.037279f, -0.378066f, 0.301904f, 0.708027f, 0.014694f, 0.856777f, -0.215629f, 0.358892f, 1.111795f, -0.493975f, -0.116163f, 0.138432f, 0.383268f, 0.328103f, -0.630803f, 0.314280f, -0.105781f, 0.663940f, 0.376715f, 0.782037f, 0.548491f, -0.501261f, -0.783622f, -0.018033f, -0.050198f, 0.282506f, -0.279510f, 0.240938f, 0.580592f, -0.359547f, -0.241918f, 0.457589f, 0.279421f, -0.813340f, 0.188721f, 0.284679f, -0.144145f, 0.110475f, -0.936211f, -0.170939f, 0.335230f, -0.936679f, 0.171722f, 0.052245f, -0.327602f, -0.904890f, -0.675604f, -0.349543f, 0.036146f, 0.649459f, 0.018932f, -0.096961f, 0.577065f, -0.257582f, -0.192749f, 0.002405f, -0.128480f, 0.997441f, -0.395538f, -0.414382f, -0.073347f, 0.774798f, 0.427953f, 0.487348f, 0.161006f, -0.008110f, -0.412464f, 0.559888f, -0.293380f, 0.273358f, 0.060297f, -0.067544f, -0.124310f, -0.472256f, 0.029813f, -0.189098f, 0.081476f, -0.282140f, 1.130831f, 0.262587f, 0.767182f, -1.017147f, -0.859382f, -0.482532f, -0.254956f, 0.416712f, -0.539876f, -1.064143f, -0.207269f, -0.079894f, 0.067704f, 0.587317f, -0.593573f, -0.013760f, 0.626445f, -0.630754f, 0.066053f, 0.130873f, -0.710305f, -0.204879f, -0.282535f, -0.452214f, 0.419170f, -0.623174f, -0.151209f, 0.103168f, 0.023253f, -0.625917f, -0.218533f, -0.456622f, -0.038140f, 0.407158f, 0.112767f, 1.036943f, 0.740496f, -0.205170f, 0.411718f, 0.476745f, -0.445022f, -0.005543f, -0.588826f, 1.623053f, -0.438288f, -0.020426f, 0.964796f, 0.315982f, 0.458793f, 0.353772f, -0.693419f, 0.252253f, -0.286700f, 0.240508f, 0.109290f, -0.033416f, -0.329057f, -0.246653f, -0.676549f, -0.000642f, 0.262810f, -0.400055f, -0.510829f, 0.282777f, -0.140547f, 0.939985f, -0.204930f, 0.113395f, 0.803292f, 0.105165f, 0.228300f, 0.072410f, 0.483708f, -0.196106f, -0.993193f, 0.325174f, -0.025312f, -0.115137f, -0.235744f, -0.177212f, 0.793116f, -0.535404f, 0.466893f, -0.712115f, -0.609969f, -0.377492f, 0.504743f, -0.303789f, -0.719198f, 0.059505f, 0.693185f, 0.798073f, 0.399529f, 0.138064f, 0.179078f, -0.190976f, 0.112420f, -0.125366f, 0.123872f, 0.106417f, -0.210086f, -0.294079f, 0.062257f, -0.891331f, -0.829988f, -0.779358f, -0.961983f, 0.209970f, 0.732901f, -0.106910f, 0.255636f, 0.298581f, 0.362522f, 0.271103f, 0.145646f, -0.832401f, -0.011956f, -0.261599f, 0.157297f, 0.283089f, -0.093317f, 1.003708f, -0.056247f, 0.584852f, 0.131608f, 0.726084f, 0.269596f, -0.594681f, -0.729193f, 0.791245f, -0.088714f, -0.309458f, -0.078603f, 0.582754f};

        Float[] e_1_left_2 = {1.809343f, -1.167354f, 2.588582f, 0.805829f, 0.271959f, -0.099778f, -0.526556f, -1.537335f, 0.522603f, 0.882539f, 1.380050f, 0.661142f, 2.695861f, 1.767267f, -0.429656f, -1.897886f, 0.688433f, -1.059158f, -0.789532f, -0.138478f, 1.147977f, 0.956787f, 0.153099f, -0.824897f, -0.163409f, -1.094339f, -1.558944f, 0.336529f, 1.354921f, 0.805784f, -1.150354f, 0.689826f, -0.974608f, 0.827324f, 0.535372f, 0.182303f, 0.617253f, -0.273698f, 0.532433f, -0.537072f, 1.981784f, 0.825938f, -0.710568f, -0.953410f, -0.791537f, 0.007315f, -0.168010f, -0.758037f, -0.437638f, 2.570931f, 1.156524f, -0.109405f, 0.624301f, 0.840563f, 1.081839f, 0.134608f, -0.651635f, 0.614563f, -0.079473f, -0.005558f, -1.167542f, 1.389118f, 0.500973f, 0.744059f, 0.134973f, -0.161780f, 0.924139f, 1.033737f, -0.736885f, -0.926558f, -1.166497f, 0.461983f, 0.032044f, -0.936723f, -2.000829f, 0.202208f, 1.608042f, 1.905783f, -0.773049f, -0.777668f, 0.969523f, 2.241340f, -0.683345f, -0.950284f, -0.021760f, -0.744461f, 0.663910f, -0.246627f, -0.582463f, 0.056834f, -0.600959f, 0.775554f, 0.806072f, 0.068955f, -0.060171f, 1.528387f, 1.727390f, -1.187811f, -0.484500f, -0.189723f, 2.173326f, 0.875969f, 0.035321f, -0.508267f, -0.140195f, -0.414792f, 0.901808f, 0.830669f, 0.037717f, -0.490691f, -1.919712f, 0.590798f, -0.605997f, -0.943846f, 0.380338f, -0.563188f, -0.665653f, -1.070025f, -0.323242f, -1.456411f, -0.247330f, 1.365921f, -0.099966f, -0.417380f, 0.513609f, 0.729693f, 0.282132f, -0.050906f, -1.067928f, 1.045004f, -0.864253f, 0.268810f, -0.465691f, -0.288870f, 0.383511f, -0.133285f, -1.349862f, 1.984566f, 0.201620f, 1.571960f, -0.286035f, -0.911569f, 0.345385f, 1.994866f, -0.699020f, 0.011842f, 0.605982f, -1.041102f, -0.868521f, -1.160542f, -0.357302f, 0.193685f, -0.796664f, -0.148956f, 0.192018f, 0.306475f, 0.352193f, -1.621438f, -0.452621f, -1.605953f, -1.085688f, -1.856356f, 0.850276f, 1.012135f, 1.247891f, -0.425844f, -0.824142f, -0.179185f, 1.178555f, 0.095461f, 0.106081f, 0.757416f, 0.350403f, -0.370491f, 1.139496f, -0.504593f, 2.159909f, -1.337501f, 1.215845f, -0.417911f, 1.132403f, -0.297594f, 3.176373f, -1.224742f, 0.071965f, -0.076731f, 0.232845f, 0.552302f, 0.209397f, -1.447209f, -0.436810f, 0.161985f, 0.958265f, -0.711195f, -0.416892f, 0.741100f, 1.414401f, 0.492522f, 0.683618f, 0.715362f, -1.091738f, 0.184023f, 0.827908f, -1.967001f, -0.387835f, 0.037729f, -0.415477f, 0.059682f, 0.170464f, 1.251588f, -0.085768f, 0.661040f, 0.454545f, 1.498618f, 0.747509f, 0.420602f, -0.908194f, -0.315021f, -0.930924f, -0.942464f, 0.629252f, 0.533267f, -1.391482f, 0.965496f, -0.283286f, 0.594382f, 2.562837f, 1.487598f, 0.608738f, -1.568924f, 0.534678f, -1.382199f, -0.797364f, 0.917986f, -0.788126f, -0.260432f, -0.155808f, 0.127026f, -0.553650f, 0.671343f, 1.029487f, 0.301511f, -1.046865f, 0.871910f, 0.463020f, -1.960143f, -0.157221f, 0.904393f, -0.719779f, -0.792358f, 0.800888f, 1.516497f, -1.967385f, 0.767601f, -1.841333f, 0.524969f, -1.323929f, 0.150508f, 0.719878f, 1.001984f, 1.354558f, -1.282675f, -1.099523f, 0.280878f, 0.567097f, -1.661911f, -1.527186f, 0.008523f, 1.340741f, -0.409677f, -0.783876f, 0.255557f, -0.218025f, -0.685267f, -0.639834f, -0.411078f, 0.957351f, -0.060436f, -1.776820f, 0.620929f, -1.074057f, -2.158816f, -0.575098f, -0.643822f, -1.572795f, -0.340036f, -1.860206f, -1.852305f, -0.727025f, 0.122138f, -0.393484f, -0.781825f, 0.321360f, -0.085490f, -0.592162f, -0.214815f, 2.064408f, 0.115090f, 1.126869f, 0.907341f};
        Float[] e_1_middle = {-0.327448f, -0.578851f, 0.707560f, 0.447382f, -0.407580f, 0.964867f, 0.012460f, 0.068890f, 0.396755f, 0.261801f, -0.815535f, -0.170622f, 0.038209f, -0.302225f, -0.537651f, -0.214758f, -0.425458f, -1.081693f, 0.078629f, 0.479447f, -0.104230f, -0.127688f, -0.187727f, -0.819318f, -1.229308f, 0.180553f, -0.343848f, -0.899047f, -0.299827f, 0.132556f, 0.326630f, -0.428105f, 0.697042f, -0.198580f, 0.399552f, -0.338321f, -1.050128f, 0.021107f, 0.815320f, -0.579182f, -0.621017f, -0.174917f, 0.340891f, -0.057926f, -0.167140f, 0.333315f, -0.030088f, 0.290010f, -0.476997f, -0.367688f, 0.517965f, -0.648066f, -0.401265f, 0.232117f, -0.001476f, 0.076930f, -0.459416f, 0.175516f, -0.296314f, 0.593434f, 0.411387f, 0.541729f, -0.325819f, -0.455411f, -0.630387f, 0.282960f, 0.015731f, 0.015980f, 0.197532f, -0.204390f, -0.867509f, 0.434882f, 0.264661f, -0.353822f, -0.607218f, -0.166679f, 1.490488f, 0.560638f, 0.721765f, -0.027428f, 0.465447f, 0.634314f, -0.433772f, -0.822363f, 0.489948f, -0.381155f, -0.570927f, -0.325165f, 0.630847f, -0.096453f, -0.147912f, 0.027179f, -0.557539f, -1.069446f, 0.224060f, 0.486389f, -0.587529f, 0.792774f, 0.632383f, 0.362659f, 0.545168f, 0.541791f, -0.058902f, 0.308498f, 0.066222f, 0.667046f, 0.147512f, 0.133248f, -0.445982f, 0.595013f, 0.179987f, 0.040365f, -0.174075f, 1.306348f, -0.435693f, 0.128800f, -0.281804f, -0.439505f, -0.362792f, -0.458782f, -0.363271f, 0.738193f, -0.177965f, -0.280880f, 1.068316f, 1.648598f, -0.134844f, 0.370174f, 0.229343f, -0.428665f, -0.425245f, -0.923816f, -0.422494f, 0.114064f, 0.289492f, -1.540800f, 0.374587f, 0.134299f, -0.027074f, 0.322159f, 0.100256f, -0.128598f, 0.657172f, -0.313901f, 0.771554f, -0.928796f, 0.565685f, 0.558789f, 1.341082f, -0.209278f, -0.547447f, 0.153285f, 0.396000f, 0.582509f, 0.202689f, -0.633410f, -0.567981f, -0.325527f, 0.597499f, 0.802261f, 0.049013f, -0.167190f, -0.604331f, 0.013485f, -0.908465f, -0.506283f, 0.302455f, 1.100800f, 0.563762f, -0.297367f, 0.310693f, -0.161572f, 0.839216f, 0.501283f, -0.065881f, 0.717306f, 0.195664f, -0.365270f, -0.963647f, 0.107130f, -0.417616f, 0.382270f, -0.798118f, -0.454647f, 0.222262f, 0.334274f, 0.052355f, -0.075320f, 0.935074f, 0.160989f, 0.371664f, 0.670613f, 0.125349f, -0.438635f, 0.156848f, 0.161182f, -0.514477f, 0.377298f, 0.151206f, 0.187254f, -0.709055f, -0.513074f, 0.166518f, -0.684743f, 0.267745f, -0.975845f, 0.111187f, -0.649048f, -0.273082f, 0.497851f, 0.338607f, -1.038597f, -0.244472f, -1.009748f, -0.199696f, -0.759912f, -0.373471f, 0.670364f, -1.059332f, 0.204843f, -0.308140f, 0.195796f, 0.436312f, -0.085676f, 0.260602f, 0.064007f, 0.259261f, 0.459013f, -0.313060f, -0.602597f, 1.172704f, -0.105790f, -0.584855f, -0.051573f, -0.168712f, -0.408602f, -0.078620f, -0.467700f, -0.115332f, 0.551709f, 0.499447f, 0.277016f, 0.019484f, -0.919423f, 0.133116f, -0.838934f, -0.975703f, -0.126044f, 0.994815f, -0.528658f, 0.130683f, -0.102801f, 0.414434f, 0.292601f, 0.073114f, -1.244310f, 0.268483f, -0.342756f, 0.218330f, 0.449178f, 0.245608f, -0.379416f, 0.348842f, 0.376759f, 0.165584f, 0.540744f, 0.576270f, 0.275244f, -0.713000f, -0.367014f, -0.809524f, -0.424146f, -0.046333f, -0.287333f, -0.600641f, -0.429840f, 0.005664f, -0.003344f, 0.611397f, -0.711440f, -0.054401f, -0.302568f, -0.830208f, -0.302123f, -0.213674f, -0.017372f, 0.557069f, -0.241301f, -0.454623f, 0.940017f, -0.304317f, -0.749020f, 0.706719f, 0.024571f, -0.308717f, -0.472602f, -0.888966f, 0.068836f, -0.721506f, 0.102102f};
        Float[] e_1_right = {0.094940f, 0.301191f, 0.166457f, 0.112011f, -0.665950f, -0.817093f, 0.223476f, -1.012916f, 0.076890f, -0.498239f, -0.381149f, -0.264812f, 1.143207f, -1.037326f, 0.766775f, 1.285259f, -1.039204f, -1.087107f, -1.143744f, 1.094107f, -0.128613f, 0.698209f, 0.536828f, 0.882936f, 1.915419f, 2.000967f, -2.043527f, -0.294487f, -0.889513f, -0.070507f, -0.102917f, 1.968212f, -0.194143f, -0.643294f, 0.423885f, -0.710783f, -0.135515f, 0.105445f, -0.197358f, 1.265252f, -0.427617f, 0.227421f, 0.209582f, 0.774818f, -0.506268f, -1.996230f, 0.113505f, -0.727185f, 1.917013f, -0.560952f, 0.457895f, 0.016329f, -0.067161f, 0.774000f, -0.022096f, -0.323692f, 0.031209f, -0.434868f, 2.332676f, 0.014288f, 1.165044f, -0.480775f, -0.074661f, -0.175010f, -0.099291f, 0.489435f, -0.184008f, -0.456835f, 0.169874f, -1.376791f, 1.080541f, 0.140009f, -0.929525f, -2.075780f, -0.033107f, 0.133533f, -0.546701f, -1.251113f, 1.524330f, -0.561455f, 0.830502f, -0.155590f, 0.163922f, -1.055290f, 0.637016f, 0.448453f, -0.888364f, 0.804993f, 0.042940f, -1.443278f, 0.799477f, 0.174197f, 0.345058f, -0.525392f, -1.190481f, 0.161664f, 0.830444f, 0.607793f, -0.152280f, 0.145155f, -0.705165f, 0.070476f, 0.386128f, -0.890089f, 0.083531f, -1.106124f, -0.134802f, 0.464501f, -1.038406f, 0.911079f, 1.676939f, 0.924700f, 0.694095f, -0.106581f, -1.403188f, 0.515479f, -0.543323f, 0.130981f, -1.553219f, -0.047106f, 0.032369f, 0.654574f, -0.300685f, 0.974203f, -0.881678f, -1.510358f, -1.318989f, 0.411663f, -1.246209f, -0.677057f, -0.208963f, 1.636607f, -0.379100f, -0.484993f, -0.467693f, -0.014459f, -0.221587f, 0.376405f, -0.702850f, -1.949307f, 0.614230f, 0.737994f, 0.153970f, 0.738116f, -1.316743f, 1.061438f, -0.977628f, 0.526200f, -1.474340f, -1.159230f, -0.768945f, -0.220628f, -1.913236f, -0.619483f, 0.301176f, 1.432122f, 0.859669f, -0.590280f, -0.027061f, 0.009466f, 0.071092f, 0.844862f, 0.020575f, -0.817876f, -0.098564f, 0.578567f, -0.746476f, -0.577679f, 0.660449f, 1.607860f, 0.155629f, -1.662456f, 0.075633f, -0.224418f, -0.159003f, -1.296277f, 0.209880f, 0.206771f, 0.652076f, 0.640859f, 0.611040f, -0.492980f, -0.573491f, 0.027391f, 0.003433f, 1.186149f, -0.226346f, 1.078320f, 0.263760f, 0.321225f, -1.132692f, 0.206117f, -1.562018f, 1.510418f, -0.698861f, -0.327000f, 0.642483f, 0.438626f, 0.169325f, -0.816219f, 0.815475f, -1.017544f, 1.836799f, 0.680611f, -0.571441f, -0.633177f, -1.080350f, -0.653296f, 0.800553f, -1.098608f, -1.318285f, 1.368871f, 0.956651f, -0.388709f, -0.160380f, 1.081991f, 0.023288f, 0.345605f, 0.709409f, -0.425535f, -0.515954f, 0.655691f, -0.151881f, -0.253342f, -1.158334f, 0.200882f, -0.369041f, -0.378783f, -0.709565f, 0.650587f, 0.171314f, 0.637588f, -0.096766f, 1.292597f, 0.640827f, 0.013766f, 0.020720f, -0.553780f, -0.831842f, 0.925151f, 0.064460f, 0.108722f, 1.104594f, -0.913142f, 1.094552f, 0.625262f, 0.220201f, 1.307363f, 0.196409f, 0.267084f, -0.225064f, -1.493422f, -0.840846f, 0.932132f, 0.118725f, 0.538510f, 0.561646f, 0.291462f, 0.506742f, 0.255131f, 0.366215f, -0.285203f, 0.430593f, -0.629811f, -1.408377f, -0.043475f, -0.252108f, -0.020267f, 0.732550f, 0.251601f, 0.407312f, 0.430425f, -0.477705f, 0.943520f, 0.085177f, 1.056916f, -0.052981f, 0.064105f, -0.398667f, 0.913869f, 0.213560f, -0.054561f, 1.662850f, -1.181489f, -1.354083f, 0.769098f, -0.816052f, -0.974989f, 0.859643f, -0.383380f, 0.831238f, 1.435038f, -0.416896f, -0.558389f, 2.918264f, -0.020230f, 0.203637f, -0.365946f, -0.488077f, 0.461922f};
        for(int i = 0; i < 300; i++) {
            e_1_left[i] = (e_1_left[i] + e_1_left_2[i]) / 2.0f;
        }

        Float[] e_2_left = {0.311956f, -0.097561f, -1.040493f, -0.794698f, -0.168841f, 0.391654f, -0.011523f, -0.435525f, 0.575240f, -0.187732f, 0.372691f, -0.123030f, 0.536876f, -0.571772f, 0.595535f, -0.714218f, 0.177980f, 0.983898f, -0.489692f, -0.658028f, 0.872599f, 0.130985f, -0.248569f, -0.314455f, -0.551340f, -0.349499f, 0.333505f, -0.239654f, -0.576633f, -0.172384f, -0.206066f, -0.462747f, 0.975846f, -0.300198f, -0.553829f, -0.719027f, -0.344751f, 0.592935f, -0.186279f, 0.111692f, 0.014561f, 0.911550f, -0.065795f, -0.477041f, -0.222435f, -0.371428f, -0.179475f, 0.887410f, 1.145800f, 0.163552f, 0.078559f, -0.284246f, -0.493981f, -0.374744f, 0.031144f, -0.010799f, -0.715995f, -0.459755f, 0.409073f, 0.195966f, 0.856292f, -0.309982f, -0.560496f, 0.455529f, 0.250398f, 0.077475f, 0.069802f, 0.219749f, -0.046207f, -0.211006f, -0.180231f, -0.670523f, 0.236088f, 0.464145f, 0.306484f, 1.100016f, 1.063612f, 0.418348f, 0.449981f, -0.204687f, 0.531063f, -1.670575f, -0.204413f, -0.023652f, -0.129201f, -0.080715f, -0.169779f, 0.373956f, 0.998094f, -0.173066f, 0.617412f, 1.188320f, 0.175144f, 0.289550f, -0.591757f, 0.541278f, 1.555627f, -0.318431f, -0.060114f, 0.414288f, 0.472249f, 0.345452f, -0.615876f, 0.348448f, -0.111778f, 0.414758f, 0.289640f, 0.580199f, 0.307474f, -0.667627f, -0.694050f, -0.254245f, 0.101450f, 0.638317f, -0.108130f, 0.846950f, 0.645822f, -0.330229f, -0.627484f, 0.160396f, 0.394039f, -0.844473f, 0.458161f, 0.734859f, -0.123267f, 0.121502f, -0.638280f, -0.389471f, 0.162152f, -0.510316f, 0.342849f, -0.483926f, -0.504338f, -0.578193f, -0.867396f, -0.052931f, -0.183172f, 0.732240f, 0.264425f, -0.199195f, 0.459632f, -0.151561f, -0.143425f, -0.009958f, -0.351022f, 0.710771f, 0.051753f, -1.194491f, -0.140288f, 0.943280f, 0.670880f, 0.268029f, 0.370794f, -0.542975f, -0.787454f, 0.318755f, 0.024760f, 0.103794f, 0.515357f, 0.322486f, -0.268681f, -0.287805f, 0.469548f, -0.527840f, 0.081435f, -0.353775f, 0.974958f, -0.098832f, 1.154823f, -0.212775f, -0.785308f, -0.618473f, -0.366557f, 0.645025f, -0.318597f, -0.195805f, -0.394749f, -0.635083f, -0.059728f, 0.178297f, -1.123899f, -0.120164f, 0.172393f, -0.419565f, -0.340125f, 0.174610f, -0.184180f, -0.087274f, -0.139243f, -0.312173f, 0.631420f, -0.858561f, -0.148930f, 0.063421f, 0.149955f, -0.773288f, -0.535301f, -0.393122f, 0.064078f, 0.159367f, 0.163499f, 0.423717f, 0.353836f, -0.374424f, 0.061034f, -0.175283f, -0.450750f, 0.069271f, -0.613523f, 1.590075f, -0.525685f, -0.458694f, 0.900698f, -0.029187f, 0.528583f, 0.724925f, -1.010246f, -0.028511f, -0.630546f, -0.177488f, 0.050194f, 0.585604f, -0.239027f, -0.735462f, -0.469061f, 0.004785f, 0.153314f, -0.355041f, 0.033777f, 0.499432f, 0.283880f, 0.866118f, -0.455962f, 0.032036f, 0.855432f, -0.268876f, -0.174703f, -0.340709f, 0.469099f, -0.393003f, -0.686660f, 0.283471f, 0.484653f, 0.332293f, -0.600081f, -0.255934f, 1.155608f, -0.877727f, 0.542273f, -0.361046f, -0.429422f, -0.499447f, 0.529771f, -0.570720f, -0.791014f, 0.020444f, 0.586246f, 0.529858f, 0.314189f, 0.168998f, -0.218452f, -0.065523f, -0.133423f, -0.326421f, 0.207326f, -0.268501f, -0.488070f, -0.004123f, 0.292608f, -0.237062f, -0.758681f, -0.539447f, -0.788191f, -0.228196f, 1.019009f, -0.510333f, 0.366128f, 0.676550f, 0.685400f, 0.003476f, -0.357020f, -0.429934f, -0.134494f, 0.002347f, -0.038369f, 0.124861f, -0.031089f, 1.386564f, -0.166858f, 0.409674f, -0.151305f, 0.244704f, 0.529843f, -0.715632f, -0.653198f, 0.410502f, -0.276321f, 0.235621f, -0.119189f, 0.470275f};
        Float[] e_2_left_2 = {1.809343f, -1.167354f, 2.588582f, 0.805829f, 0.271959f, -0.099778f, -0.526556f, -1.537335f, 0.522603f, 0.882539f, 1.380050f, 0.661142f, 2.695861f, 1.767267f, -0.429656f, -1.897886f, 0.688433f, -1.059158f, -0.789532f, -0.138478f, 1.147977f, 0.956787f, 0.153099f, -0.824897f, -0.163409f, -1.094339f, -1.558944f, 0.336529f, 1.354921f, 0.805784f, -1.150354f, 0.689826f, -0.974608f, 0.827324f, 0.535372f, 0.182303f, 0.617253f, -0.273698f, 0.532433f, -0.537072f, 1.981784f, 0.825938f, -0.710568f, -0.953410f, -0.791537f, 0.007315f, -0.168010f, -0.758037f, -0.437638f, 2.570931f, 1.156524f, -0.109405f, 0.624301f, 0.840563f, 1.081839f, 0.134608f, -0.651635f, 0.614563f, -0.079473f, -0.005558f, -1.167542f, 1.389118f, 0.500973f, 0.744059f, 0.134973f, -0.161780f, 0.924139f, 1.033737f, -0.736885f, -0.926558f, -1.166497f, 0.461983f, 0.032044f, -0.936723f, -2.000829f, 0.202208f, 1.608042f, 1.905783f, -0.773049f, -0.777668f, 0.969523f, 2.241340f, -0.683345f, -0.950284f, -0.021760f, -0.744461f, 0.663910f, -0.246627f, -0.582463f, 0.056834f, -0.600959f, 0.775554f, 0.806072f, 0.068955f, -0.060171f, 1.528387f, 1.727390f, -1.187811f, -0.484500f, -0.189723f, 2.173326f, 0.875969f, 0.035321f, -0.508267f, -0.140195f, -0.414792f, 0.901808f, 0.830669f, 0.037717f, -0.490691f, -1.919712f, 0.590798f, -0.605997f, -0.943846f, 0.380338f, -0.563188f, -0.665653f, -1.070025f, -0.323242f, -1.456411f, -0.247330f, 1.365921f, -0.099966f, -0.417380f, 0.513609f, 0.729693f, 0.282132f, -0.050906f, -1.067928f, 1.045004f, -0.864253f, 0.268810f, -0.465691f, -0.288870f, 0.383511f, -0.133285f, -1.349862f, 1.984566f, 0.201620f, 1.571960f, -0.286035f, -0.911569f, 0.345385f, 1.994866f, -0.699020f, 0.011842f, 0.605982f, -1.041102f, -0.868521f, -1.160542f, -0.357302f, 0.193685f, -0.796664f, -0.148956f, 0.192018f, 0.306475f, 0.352193f, -1.621438f, -0.452621f, -1.605953f, -1.085688f, -1.856356f, 0.850276f, 1.012135f, 1.247891f, -0.425844f, -0.824142f, -0.179185f, 1.178555f, 0.095461f, 0.106081f, 0.757416f, 0.350403f, -0.370491f, 1.139496f, -0.504593f, 2.159909f, -1.337501f, 1.215845f, -0.417911f, 1.132403f, -0.297594f, 3.176373f, -1.224742f, 0.071965f, -0.076731f, 0.232845f, 0.552302f, 0.209397f, -1.447209f, -0.436810f, 0.161985f, 0.958265f, -0.711195f, -0.416892f, 0.741100f, 1.414401f, 0.492522f, 0.683618f, 0.715362f, -1.091738f, 0.184023f, 0.827908f, -1.967001f, -0.387835f, 0.037729f, -0.415477f, 0.059682f, 0.170464f, 1.251588f, -0.085768f, 0.661040f, 0.454545f, 1.498618f, 0.747509f, 0.420602f, -0.908194f, -0.315021f, -0.930924f, -0.942464f, 0.629252f, 0.533267f, -1.391482f, 0.965496f, -0.283286f, 0.594382f, 2.562837f, 1.487598f, 0.608738f, -1.568924f, 0.534678f, -1.382199f, -0.797364f, 0.917986f, -0.788126f, -0.260432f, -0.155808f, 0.127026f, -0.553650f, 0.671343f, 1.029487f, 0.301511f, -1.046865f, 0.871910f, 0.463020f, -1.960143f, -0.157221f, 0.904393f, -0.719779f, -0.792358f, 0.800888f, 1.516497f, -1.967385f, 0.767601f, -1.841333f, 0.524969f, -1.323929f, 0.150508f, 0.719878f, 1.001984f, 1.354558f, -1.282675f, -1.099523f, 0.280878f, 0.567097f, -1.661911f, -1.527186f, 0.008523f, 1.340741f, -0.409677f, -0.783876f, 0.255557f, -0.218025f, -0.685267f, -0.639834f, -0.411078f, 0.957351f, -0.060436f, -1.776820f, 0.620929f, -1.074057f, -2.158816f, -0.575098f, -0.643822f, -1.572795f, -0.340036f, -1.860206f, -1.852305f, -0.727025f, 0.122138f, -0.393484f, -0.781825f, 0.321360f, -0.085490f, -0.592162f, -0.214815f, 2.064408f, 0.115090f, 1.126869f, 0.907341f};
        for(int i = 0; i < 300; i++) {
            e_2_left[i] = (e_2_left[i] + e_2_left_2[i]) / 2.0f;
        }
        Float[] e_2_middle = {-0.752429f, 0.529373f, -0.646663f, -0.243506f, 0.027683f, 1.768848f, 0.443095f, 0.200869f, 0.116152f, 0.301807f, -0.654300f, -0.523632f, 0.263960f, 0.029434f, -0.542679f, -0.488241f, 1.000029f, -1.344832f, -0.679351f, 0.802923f, 0.339258f, -0.108830f, 0.686580f, -0.590183f, -0.920300f, 0.098672f, -0.013293f, -0.632261f, 0.124633f, -0.886354f, 0.371358f, -0.329540f, 0.091126f, -1.146726f, -0.134538f, -0.946089f, 0.092616f, 0.105740f, -0.088284f, -1.451848f, -0.182794f, 0.684779f, 0.371764f, -0.447512f, 0.585046f, 0.402946f, -0.838823f, -0.382035f, -0.466227f, -0.569310f, 0.324173f, -0.166172f, 0.602685f, 0.518554f, -0.778480f, -0.400569f, -1.431729f, -0.717512f, 0.516530f, 0.087438f, 1.647981f, -0.121801f, 0.165323f, -0.223427f, 0.607710f, 0.008531f, 0.037590f, 0.163687f, -0.239940f, -0.303933f, -1.254013f, -0.072389f, -0.434733f, 0.074567f, 0.524174f, -0.504617f, 0.406449f, 0.399606f, 0.219921f, 0.394736f, 0.857255f, 0.837205f, 0.149369f, -1.277639f, 0.439458f, 0.016713f, -0.588182f, -0.447062f, 0.469768f, -0.173379f, 0.754655f, 0.296617f, -0.180537f, -0.102242f, 0.485398f, -0.035018f, -0.574272f, 1.358826f, 0.915489f, -0.273743f, -0.833649f, -0.263260f, -0.011185f, -0.329474f, -0.327114f, 0.321238f, -0.356173f, 0.151288f, -0.632424f, 0.256468f, -0.122466f, 0.057707f, -0.138950f, 0.143730f, 0.329113f, -0.101644f, 0.368531f, -0.474188f, -0.344549f, -0.351857f, -1.297845f, -0.613645f, -0.462001f, -0.234565f, 0.287215f, 1.777435f, 0.860102f, 0.362815f, 0.421672f, -0.520469f, 0.045093f, -1.392331f, -0.854667f, -0.072004f, -0.483029f, -1.427844f, 0.109983f, -0.419195f, -0.204320f, 0.520760f, 0.444994f, -0.046042f, 0.921985f, 0.428398f, 0.240921f, -1.331123f, 0.702328f, 0.127507f, 1.578113f, 1.121889f, -1.627273f, -0.033690f, 0.188476f, -0.115520f, -1.320019f, -0.723274f, -0.176427f, -1.059624f, 0.989889f, 0.560473f, 0.735793f, -0.536891f, 0.353707f, 0.645167f, -0.805328f, 0.161912f, 0.220639f, 0.590729f, -0.524644f, -1.043411f, -1.264472f, 0.184279f, 1.212994f, -0.234948f, -0.066066f, 1.626842f, 0.651191f, -0.430347f, -0.646706f, 0.797350f, -0.041594f, -0.278570f, 0.019444f, -1.396838f, 0.786258f, 0.016976f, -0.122674f, 0.579744f, 1.219015f, -0.329027f, 0.874314f, 0.326793f, 0.891041f, -0.483038f, 0.578178f, -0.168923f, -0.300118f, -0.763053f, 1.014030f, 0.060553f, -0.897327f, 0.206000f, -0.523454f, -0.670684f, 0.570227f, -1.105441f, -0.145196f, -0.474829f, 0.023377f, -0.740411f, -0.288299f, -1.532254f, -0.024262f, -0.845021f, -0.178223f, -1.271692f, 0.233118f, 1.002574f, -0.253090f, 0.264822f, -0.349222f, 0.253773f, -0.254056f, 0.456180f, 1.079289f, -0.609014f, -0.497818f, 0.140153f, 0.284949f, -1.019133f, 0.507917f, 0.281112f, -0.561522f, 0.899193f, 0.780636f, 0.131535f, 0.072788f, 0.671931f, -0.134362f, 1.451583f, 0.352240f, -0.471032f, 0.435505f, -0.286371f, -0.087706f, -0.372739f, -1.054063f, -1.300074f, 0.932626f, -0.977746f, -0.777596f, -0.041279f, 0.334974f, 0.428882f, -0.243620f, -1.291897f, 1.382072f, -0.419817f, 0.331057f, -0.575377f, -0.032607f, 0.779872f, -0.197910f, 0.212654f, -0.041706f, 0.821733f, -0.050128f, 0.695871f, -0.258280f, 0.423914f, -0.422296f, -0.381909f, 1.338600f, -0.605249f, -0.080315f, -0.185007f, 0.459661f, 0.659236f, -0.316230f, -0.864256f, -0.350682f, -0.522418f, -0.667448f, -0.735731f, -0.124385f, -0.929391f, -0.447917f, -0.430310f, -0.041727f, 0.585890f, 0.736527f, -0.406933f, 0.673944f, 0.641070f, -0.085193f, 0.291637f, -0.808140f, -0.888579f, -1.038478f, 0.460472f};
        Float[] e_2_right = {-0.871472f, 0.845523f, -1.957546f, -0.814148f, -0.192047f, -0.273759f, 1.099016f, 0.799193f, -0.383050f, 0.518588f, 1.472686f, 0.421964f, 2.359542f, -0.079691f, 1.858798f, -0.530821f, 0.028303f, -0.793105f, -0.404451f, -0.845883f, 0.398721f, -0.372734f, 0.993228f, -1.748596f, -2.039346f, -1.001244f, 2.015908f, 0.870554f, 1.488638f, -0.515050f, 0.065003f, -1.088447f, -0.512594f, -2.631148f, 1.263073f, -0.464794f, -0.339487f, 0.986725f, 0.781274f, 1.302703f, 0.305511f, -0.698121f, 1.335754f, -0.523703f, 1.228395f, -1.142220f, 0.033891f, -0.177390f, 1.988094f, 0.482495f, -0.349595f, 1.139490f, -1.081626f, -0.172209f, -0.011107f, 1.513681f, -0.107641f, 0.522848f, 0.216114f, -0.739494f, 1.108466f, -0.914645f, 0.048183f, 1.235669f, 1.779253f, 0.092975f, 0.966543f, -1.196378f, 0.146510f, 0.840312f, 1.430158f, -2.354344f, 1.180510f, 1.283949f, 0.839535f, 0.729901f, 0.827403f, 0.035595f, 0.621114f, -1.686754f, 0.737536f, -1.232293f, -0.425565f, 1.494646f, -0.067419f, -0.611767f, -0.712222f, 0.651536f, 1.579292f, 0.520008f, 0.559216f, 0.547948f, 1.018479f, -1.167451f, -1.513183f, -1.227382f, 0.469993f, 0.177140f, 0.390553f, 0.327869f, 0.379728f, -0.677259f, -0.187639f, -0.255955f, 0.578896f, -0.149308f, 0.108333f, -0.089965f, -0.540922f, -0.373368f, 0.521264f, 0.096202f, 2.213875f, 0.613301f, -0.964482f, 1.665144f, 1.188816f, 0.161584f, 0.449346f, 0.495089f, 0.751061f, -1.419235f, 1.388000f, -0.336739f, -0.484898f, -0.568350f, -0.738382f, -1.193869f, 2.195192f, 0.145341f, 1.437537f, -0.896708f, -0.131368f, -0.669143f, -0.038828f, 0.005342f, 0.354548f, -0.297908f, 0.131290f, -0.401332f, -0.449201f, 1.714585f, 1.337451f, -0.077990f, 0.858775f, 1.563991f, 0.520422f, -1.141944f, -0.216882f, -0.713692f, 1.625824f, -0.741439f, 1.191125f, -1.105081f, 0.123399f, -0.329702f, -1.306444f, -0.016461f, -0.469788f, -0.552846f, 0.072530f, 0.511007f, 0.838219f, -0.179377f, -0.000009f, -0.349408f, 1.491071f, -1.409151f, -1.395031f, -1.389915f, 0.047802f, -0.597020f, 0.113596f, 0.631612f, -0.366879f, 0.329403f, -1.393968f, 1.114092f, 0.737256f, 0.188412f, 0.219895f, 0.105725f, -0.466127f, -1.076338f, -1.152274f, 0.325495f, -0.896110f, -0.079630f, -1.016339f, 0.740833f, 0.351621f, -0.478979f, -1.048379f, -0.483770f, 1.461405f, -0.418502f, -1.118149f, -0.079873f, -0.243841f, 0.400522f, -2.147021f, 0.576163f, 0.360167f, -1.097800f, -1.244357f, -0.424404f, -1.818007f, 1.462431f, -0.005429f, 1.261353f, -0.242174f, -0.939116f, -0.908469f, -0.385160f, -1.265609f, 2.378276f, -1.421123f, -0.151263f, 0.713578f, 0.522489f, 0.821756f, 1.026621f, 1.311258f, -1.915462f, -1.048878f, 0.364049f, 1.251659f, 0.271667f, -0.580299f, 2.056443f, -0.352635f, 2.419541f, -0.602530f, -0.092642f, 0.822753f, 1.557242f, -0.067113f, 2.160417f, 0.923262f, 0.300636f, -0.007574f, 0.852589f, 0.973366f, 0.284859f, -0.749048f, -0.520600f, 2.791773f, -0.520156f, -1.067415f, 0.003644f, -1.205706f, 0.061589f, -0.065468f, -2.476025f, 1.192395f, -1.315866f, 1.781244f, -0.857376f, 0.719238f, 0.043731f, -0.171774f, 0.758363f, 0.031955f, -1.357697f, -0.350562f, -0.377813f, 0.404025f, -0.317064f, -2.962571f, 0.896302f, 0.427980f, -1.853670f, 0.657155f, -0.688705f, -0.035268f, -1.008730f, -2.043904f, -1.267600f, 0.682166f, 0.542353f, -1.236268f, -0.009414f, 0.992453f, 1.619416f, -0.655636f, 1.811741f, -0.131477f, 2.365790f, -0.715845f, 2.341763f, -0.298582f, -1.064521f, 1.074117f, 0.891640f, -0.659662f, -0.962101f, -0.835529f, 0.561434f, -1.818591f, -1.037394f};
        Float[] e_2_right_2 = {0.309827f, -0.167291f, -0.235449f, -0.537894f, 1.274457f, 1.148480f, 0.120878f, -0.613292f, -0.229017f, 0.087359f, 0.153967f, -1.064150f, 0.389128f, 1.020088f, -0.059794f, -0.395096f, -0.268912f, -1.083338f, -0.989561f, 0.840093f, -0.087077f, -0.392349f, -0.230405f, -0.398576f, 0.381553f, -0.650706f, -0.301988f, -0.705323f, 1.045960f, 0.238148f, 0.726590f, 0.458647f, 0.443417f, -0.052854f, -0.717309f, 0.656699f, -0.359692f, -0.304958f, -0.223606f, -0.516439f, -0.247236f, 0.864223f, 1.320990f, 0.439155f, 0.338914f, -0.522588f, -0.308787f, -0.237363f, -0.515891f, 0.211719f, -0.291139f, 0.514347f, -0.415765f, -1.251228f, -0.192700f, 0.817444f, 1.359376f, -0.582943f, 0.491488f, -0.003895f, 0.775429f, 0.725774f, -0.052758f, -0.052029f, 1.006824f, 1.820873f, 0.273481f, 1.285750f, -1.273268f, 0.183693f, 0.090159f, 1.153498f, 0.278922f, -0.405800f, 0.397151f, -0.120148f, -0.169555f, 0.360755f, 0.130539f, -0.102188f, 0.538213f, -0.236523f, 0.331528f, -1.117987f, 0.568519f, 0.170156f, 0.698106f, -0.041437f, -0.016608f, 0.696721f, -0.064046f, -0.392778f, 0.665779f, -0.034525f, 0.179222f, -0.560720f, -0.026799f, -0.076453f, -0.211873f, -0.557372f, -0.756917f, 0.609144f, 0.751379f, 0.364093f, 0.391319f, -0.279893f, 0.940285f, -0.317150f, 2.035748f, 0.521455f, -0.932746f, 1.046869f, 0.284195f, 0.226371f, 0.327920f, 0.090915f, -0.078608f, 0.843658f, 0.114637f, -0.336419f, 0.016177f, 0.183243f, -0.578048f, 0.896929f, -0.902867f, -0.291766f, -0.071366f, -1.228379f, 0.015387f, -0.566998f, -1.840922f, 0.552542f, 0.167298f, 0.553117f, -0.289509f, 0.277051f, 0.096060f, 0.129841f, 0.060709f, 0.325106f, -1.084904f, -0.561356f, 0.321328f, 0.208041f, 1.085981f, 0.050729f, 0.555780f, -0.374326f, -0.873802f, -0.410126f, 1.020009f, 0.945964f, -0.183700f, -0.685139f, 0.601090f, 0.725734f, 0.297882f, 1.271831f, 0.520164f, -1.189058f, 1.280779f, -0.018044f, 0.620237f, -0.595796f, 1.110769f, -0.253046f, -0.371880f, -0.999023f, 0.651508f, 0.938078f, -0.328173f, 0.569640f, -0.030666f, -0.828223f, -0.585160f, -0.228255f, 1.461863f, -0.911584f, 0.392505f, -0.853138f, 0.250506f, -0.519341f, 0.560981f, -1.362831f, -1.061473f, 0.700417f, 0.764837f, 0.503775f, 0.130666f, -1.857954f, -0.283421f, 0.237947f, -0.333965f, -0.665165f, -0.090449f, 0.159218f, -0.369131f, -0.894293f, 0.494600f, 0.040140f, -0.368001f, -0.437071f, 1.982537f, 0.246092f, -0.300071f, 0.368131f, -0.474936f, 0.060938f, 0.534922f, 0.057766f, -1.629345f, 0.395932f, 0.358920f, 0.548480f, -0.336880f, -0.107134f, 0.114458f, -0.063215f, -0.077646f, -1.297264f, -0.146780f, 0.570582f, 0.059955f, -0.782255f, 0.098375f, 0.785746f, -1.009072f, -0.535114f, 0.336833f, 0.405215f, -1.006541f, -0.313875f, 0.399730f, 0.817160f, -1.400531f, -0.370670f, 0.951017f, -0.391850f, -0.140771f, -0.093926f, 0.360859f, -0.787935f, -1.103208f, 1.626774f, -0.661059f, -0.524722f, -0.035893f, 0.708859f, -0.280415f, -0.406291f, -1.220105f, -0.158248f, 0.728335f, -1.381928f, -0.264916f, -0.242004f, -1.799728f, -1.770329f, 0.684608f, 1.537509f, -0.061000f, 0.945526f, -0.318625f, 0.917273f, -0.855837f, 0.592794f, 0.560268f, -0.001774f, -0.028683f, -0.475085f, 0.386369f, -0.473510f, 0.791148f, 0.232361f, 0.144247f, 0.810223f, 0.321734f, 0.160709f, 0.133327f, -0.366033f, 0.009817f, 0.039291f, 0.240579f, -0.040167f, -0.379963f, 0.321172f, 0.200529f, -1.339210f, -0.612661f, -1.520764f, 0.449375f, -0.233320f, -0.608166f, -0.335440f, 1.404866f, 0.115817f, -0.236812f, 0.816685f, -0.391880f, 0.136126f};
        for(int i = 0; i < 300; i++) {
            e_2_right[i] = (e_2_right[i] + e_2_right_2[i]) / 2.0f;
        }

        VectorOperator vo = new VectorOperator("db_cache_vec", "local");
        Double[] event1 = vo.wordVecToEventVec(e_1_left, e_1_middle, e_1_right);
        Double[] event2 = vo.wordVecToEventVec(e_2_left, e_2_middle, e_2_right);
        System.out.println(Arrays.toString(event1));
        System.out.println(Arrays.toString(event2));
        System.out.println("余弦距离：" + vo.cosineDistence(event1, event2));
        System.out.println("欧氏距离：" + 10000/vo.euclideanDistance(event1, event2));
        EhCacheUtil.close();*/

        List<Word> leftPhrases = new ArrayList<Word>();
        List<Word> middlePhrases = new ArrayList<Word>();
        List<Word> rightPhrases = new ArrayList<Word>();

        leftPhrases.add(new Word("Turkey", null, null, null, 0, 0));
        //leftPhrases.add(new Word("warplane", null, null, null, 0, 0));

        middlePhrases.add(new Word("shoot", null, null, null, 0, 0));

        rightPhrases.add(new Word("russian", null, null, null, 0, 0));
        rightPhrases.add(new Word("make", null, null, null, 0, 0));
        rightPhrases.add(new Word("aircraft", null, null, null, 0, 0));

        EventWithPhrase eventWithPhrase = new EventWithPhrase(leftPhrases, middlePhrases, rightPhrases, 0, "");

        VectorOperator vo = new VectorOperator("db_cache_vec", "local");
        Double[] eventVec = vo.eventToVecPlus(eventWithPhrase);

        System.out.println(Arrays.toString(eventVec));

        EhcacheUtils.close();

    }

}
