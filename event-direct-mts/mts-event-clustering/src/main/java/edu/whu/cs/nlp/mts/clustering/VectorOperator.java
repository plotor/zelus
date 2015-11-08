package edu.whu.cs.nlp.mts.clustering;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.biz.SystemConstant;
import edu.whu.cs.nlp.mts.base.domain.EventType;
import edu.whu.cs.nlp.mts.base.domain.EventWithPhrase;
import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import edu.whu.cs.nlp.mts.base.utils.CommonUtil;
import edu.whu.cs.nlp.mts.base.utils.EhCacheUtil;

/**
 * 向量操作相关类
 * @author Apache_xiaochao
 *
 */
public class VectorOperator implements SystemConstant{

    private final Logger log = Logger.getLogger(this.getClass());

    private final EhCacheUtil ehCacheUtil;

    public VectorOperator(String cacheName, String datasource) {

        this.ehCacheUtil = new EhCacheUtil(cacheName, datasource);

    }

    /**
     * 根据事件中三个词的已知向量，来计算对应事件的向量
     *
     * @param vecs_left
     * @param vecs_middle
     * @param vecs_right
     * @return
     */
    public List<Double[]> eventToVecs(List<Float[]> vecs_left, List<Float[]> vecs_middle, List<Float[]> vecs_right){

        List<Double[]> eventVecs = new ArrayList<Double[]>();

        for (Float[] f_v_left : vecs_left) {
            for (Float[] f_v_middle : vecs_middle) {
                for (Float[] f_v_right : vecs_right) {
                    double[][] kronecker_left_right = new double[DIMENSION][DIMENSION];  //存储主语为宾语的克罗内克积
                    //计算主语和宾语的克罗内卡积
                    for(int i = 0 ; i < DIMENSION; ++i){
                        for(int j = 0; j < DIMENSION; ++j){
                            kronecker_left_right[i][j] = f_v_right[i] * f_v_left[j];
                        }
                    }
                    //将得到的克罗内卡积与谓语作矩阵乘法
                    Double[] eventVec = new Double[DIMENSION];
                    for(int i = 0; i < DIMENSION; ++i){
                        double product = 0;
                        for(int j = 0; j < DIMENSION; ++j){
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

        if(leftVec == null || middleVec == null || rightVec == null) {
            return null;
        }

        double[][] kronecker = new double[DIMENSION][DIMENSION];  //存储主语为宾语的克罗内克积
        //计算主语和宾语的克罗内卡积
        for(int i = 0 ; i < DIMENSION; ++i){
            for(int j = 0; j < DIMENSION; ++j){
                kronecker[i][j] = rightVec[i] * leftVec[j];
            }
        }

        //将得到的克罗内卡积与谓语作矩阵乘法
        Double[] eventVec = new Double[DIMENSION];
        for(int i = 0; i < DIMENSION; ++i){
            double product = 0;
            for(int j = 0; j < DIMENSION; ++j){
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

        if(EventType.TERNARY.equals(eventWithPhrase.eventType())) {
            //主-谓-宾结构
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases());
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases());
            Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases());

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if(EventType.RIGHT_MISSING.equals(eventWithPhrase.eventType())) {

            //主-谓，将宾语的向量全部用1代替
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases());
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases());
            Float[] rightVec = new Float[SystemConstant.DIMENSION];
            Arrays.fill(rightVec, 1.0f);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if(EventType.LEFT_MISSING.equals(eventWithPhrase.eventType())){

            //谓-宾，将主语的向量全部用1代替
            Float[] leftVec = new Float[SystemConstant.DIMENSION];
            Arrays.fill(leftVec, 1.0f);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases());
            Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases());

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else {

            this.log.warn("不支持该事件类型：" + eventWithPhrase);

        }

        return eventVec;

    }

    /**
     * 计算事件向量<br>
     * 相对于eventToVec的区别在于，如果某个短语的向量不存在，则随机生成一个向量，而不是用1代替
     * @param eventWithPhrase
     * @return
     */
    public Double[] eventToVecPlus(EventWithPhrase eventWithPhrase) {

        if(EventType.ERROR.equals(eventWithPhrase.eventType())) {
            this.log.error("不支持该事件类型：" + eventWithPhrase);
            return null;
        }

        Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases());
        Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases());
        Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases());

        if(leftVec == null) {
            leftVec = this.randomWordVec();
        }
        if(middleVec == null) {
            middleVec = this.randomWordVec();
        }
        if(rightVec == null) {
            rightVec = this.randomWordVec();
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
    public double cosineValue(Double[] vec1, Double[] vec2) {

        double value = -1;

        if(vec1 == null || vec2 == null) {
            return value;
        }

        //利用向量余弦值来计算事件之间的相似度
        double scalar = 0;  //两个向量的内积
        double module_1 = 0, module_2 = 0;  //向量vec_1和vec_2的模
        for(int i = 0; i < DIMENSION; ++i){
            scalar += vec1[i] * vec2[i];
            module_1 += vec1[i] * vec1[i];
            module_2 += vec2[i] * vec2[i];
        }

        if(module_1 > 0 && module_2 > 0) {
            value = Math.abs(scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)));
        }

        return value;

    }

    /**
     * 随机生成一个{@value SystemConstant.DIMENSION}维的向量，每一维的值在-1.5~1.5之间
     *
     * @return
     */
    private Float[] randomWordVec(){
        Float[] vec = new Float[DIMENSION];
        Random random = new Random(System.currentTimeMillis());
        for(int i = 0; i < DIMENSION; i++) {
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
     * @param event_vecs_1
     * @param event_vecs_2
     * @return
     * @throws SQLException
     */
    public double eventsApproximationDegree(List<Double[]> event_vecs_1, List<Double[]> event_vecs_2) throws SQLException{
        double approx = 0;  //默认以最大值来表示两个事件之间的最大值
        if(event_vecs_1 == null || event_vecs_2 == null){
            return approx;
        }
        //计算得到两个事件的向量
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
        return approx;
    }

    /**
     * 计算一个短语的向量
     *
     * @param phrase
     * @return
     */
    private Float[] phraseVector(List<Word> phrase) {

        Float[] phraseVec = null;

        if(CollectionUtils.isEmpty(phrase)) {

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
            if(STOPWORDS.contains(word.getLemma())) {
                // 跳过停用词
                continue;
            }
            try {
                List<Vector> vecs = this.ehCacheUtil.getVec(word);
                if(CollectionUtils.isNotEmpty(vecs)) {
                    int min = Integer.MAX_VALUE;
                    Float[] vec = null;
                    for (Vector vector : vecs) {
                        // 以最相近的单词的向量作为当前词的词向量
                        int dis = CommonUtil.strDistance(word.getName(), vector.getWord());
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
                    }
                } else {

                    this.log.warn("Can't find vector for word:" + word);

                }
            } catch (SQLException e) {
                this.log.error("Get vector error, word: " + word, e);
            }
        }

        if(count == 0) {
            this.log.warn("Can't find vector for phrase:" + phrase);
            return null;
        }

        for(int i = 0; i < DIMENSION; i++) {
            phraseVec[i] /= count;
        }

        return phraseVec;

    }

}
