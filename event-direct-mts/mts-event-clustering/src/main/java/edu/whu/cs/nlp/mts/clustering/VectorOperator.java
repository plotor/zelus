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
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
            Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if(EventType.RIGHT_MISSING.equals(eventWithPhrase.eventType())) {

            //主-谓，将宾语的向量全部用1代替
            Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
            Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
            Float[] rightVec = new Float[SystemConstant.DIMENSION];
            Arrays.fill(rightVec, 1.0f);

            eventVec = this.wordVecToEventVec(leftVec, middleVec, rightVec);

        } else if(EventType.LEFT_MISSING.equals(eventWithPhrase.eventType())){

            //谓-宾，将主语的向量全部用1代替
            Float[] leftVec = new Float[SystemConstant.DIMENSION];
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
     * @param eventWithPhrase
     * @return
     */
    public Double[] eventToVecPlus(EventWithPhrase eventWithPhrase) {

        if(EventType.ERROR.equals(eventWithPhrase.eventType())) {
            this.log.error("不支持该事件类型：" + eventWithPhrase);
            return null;
        }

        Float[] leftVec = this.phraseVector(eventWithPhrase.getLeftPhrases(), true);
        Float[] middleVec = this.phraseVector(eventWithPhrase.getMiddlePhrases(), false);
        Float[] rightVec = this.phraseVector(eventWithPhrase.getRightPhrases(), true);

        int randomCount = 0;

        if(middleVec == null) {
            // 对于谓语，如果不存在向量，则直接忽略该事件
            //middleVec = this.randomWordVec();
            //randomCount++;
            this.log.warn("The middle vector is null, ignore this event:" + eventWithPhrase);
            return null;
        }

        if(leftVec == null) {
            leftVec = this.randomWordVec();
            randomCount++;
            this.log.info("The left vector is null, build a random vector:" + Arrays.toString(leftVec));
        }

        if(rightVec == null) {
            rightVec = this.randomWordVec();
            randomCount++;
            this.log.info("The right vector is null, build a random vector:" + Arrays.toString(rightVec));
        }

        if(randomCount > 1) {
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
            value = scalar / (Math.sqrt(module_1) * Math.sqrt(module_2)) + 1;
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
     * @param ignoreStopwords
     * @return
     */
    private Float[] phraseVector(List<Word> phrase, boolean ignoreStopwords) {

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

            if(word.getName().equals(word.getPos())) {
                // 跳过标点符号
                continue;
            }

            if(ignoreStopwords && STOPWORDS.contains(word.getLemma())) {
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
                    }*/

                Vector vector = this.ehCacheUtil.getMostSimilarVec(word);
                if(vector != null) {
                    Float[] vec = vector.floatVecs();
                    for(int i = 0; i < DIMENSION; i++) {
                        phraseVec[i] += vec[i];
                    }
                    count++;
                } else {

                    this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for word:" + word);

                }
            } catch (SQLException e) {
                this.log.error("Get vector error, word: " + word, e);
            }
        }

        if(count == 0) {
            this.log.warn("[ignoreStopwords=" + ignoreStopwords + "] Can't find vector for phrase:" + phrase);
            return null;
        }

        for(int i = 0; i < DIMENSION; i++) {
            phraseVec[i] /= count;
        }

        return phraseVec;

    }

    public static void main(String[] args) {
        Float[] e_1_left = {0.658299f, 0.752380f, 0.177413f, -0.835892f, 1.311020f, -0.440589f, -0.414171f, -0.393853f, 1.127172f, -0.311357f, 0.202684f, 1.055474f, 0.231385f, -0.830468f, -1.149938f, -1.119932f, -1.841427f, 1.003495f, 0.378591f, -0.189750f, -0.502719f, 0.822308f, -0.903211f, 1.331163f, -0.759641f, 0.371335f, 1.539555f, 0.017556f, 0.866301f, -0.240457f, 0.876846f, -0.818108f, -1.311215f, -0.097679f, 1.217520f, -0.185588f, 0.972498f, 0.631509f, -0.334549f, 1.268417f, -0.253510f, -0.626754f, 0.654742f, 0.250416f, -1.950121f, 0.293325f, -0.914910f, -0.649901f, -0.776122f, 0.373830f, -0.253400f, -0.828415f, -0.015371f, -1.037621f, 0.845047f, 1.057827f, -1.297100f, -0.315455f, -0.003165f, 0.246889f, 1.200349f, 0.452917f, 0.113998f, -0.553606f, 0.197505f, -0.306839f, -0.505700f, -0.973848f, -0.469666f, -1.106179f, 0.259999f, 0.272065f, 0.459139f, 1.033768f, -0.042271f, 1.650575f, 1.170612f, -0.998304f, 1.265703f, -0.309982f, -0.239290f, -1.480924f, 0.317304f, 0.530687f, -0.575338f, -0.665513f, -1.527327f, -0.325691f, -1.201769f, 0.565130f, -0.693282f, 0.218020f, 0.188413f, -0.355743f, 0.441627f, 0.737099f, 0.524621f, 2.588552f, -2.588164f, -0.383396f, -1.064283f, 1.581082f, -0.059504f, 0.439303f, -1.158614f, -0.343903f, -0.622102f, 0.305737f, 1.186535f, 0.282253f, -1.076397f, 2.118175f, -2.458026f, -0.390465f, 0.786204f, -0.232895f, 0.274469f, -0.546283f, 1.484756f, -0.219780f, -0.296933f, 1.391413f, 0.751169f, -0.187119f, -0.469585f, -1.042126f, 1.044519f, 0.303961f, -2.163782f, -1.805128f, 0.926026f, 0.892583f, 0.094740f, 0.468239f, -0.691102f, -0.149435f, 0.041341f, 1.692475f, -1.215011f, 0.360980f, -1.474499f, -0.920362f, -0.914061f, -0.267541f, -0.616308f, 0.005852f, 1.025659f, -1.791489f, 1.529893f, -0.771262f, 1.812152f, 1.919458f, 0.727593f, 0.429756f, 0.193226f, -0.768158f, 0.237544f, -0.024075f, 1.296788f, -0.541999f, -0.266673f, -0.958251f, -0.240027f, -0.526099f, 0.127976f, 0.224345f, 0.689856f, -0.733588f, -1.645032f, -0.757955f, 0.291246f, 0.361724f, -0.373135f, -0.112309f, 0.232476f, 0.691329f, 0.736518f, -0.228608f, 0.413849f, -0.463198f, 1.211112f, 0.131677f, 0.614592f, -0.533218f, 0.454815f, 0.212908f, 1.467762f, 0.779914f, 0.112101f, -0.481986f, -0.190808f, 0.986371f, -0.062438f, 0.646550f, -0.669868f, 0.794915f, -1.230154f, -0.709927f, 0.597621f, -0.783261f, -0.381963f, -0.853328f, -0.183621f, -1.381821f, 0.589082f, 0.583936f, -0.543914f, 0.831840f, -0.328423f, 0.664434f, 0.225763f, -0.942598f, -0.179620f, 0.832318f, 0.033604f, 0.292545f, -1.335068f, 0.280070f, 0.043323f, -0.345884f, 0.731792f, 1.429730f, 0.569832f, 0.730175f, -0.586485f, 1.310843f, 0.030294f, -1.165172f, -0.667909f, 1.673806f, 0.456047f, 0.431119f, 0.979322f, -0.264845f, 0.085967f, -1.972574f, 1.014865f, 1.212431f, 0.536514f, 1.106573f, -0.408908f, -0.602880f, -0.348352f, 0.776163f, -1.245865f, -0.807449f, -0.861111f, 1.096681f, -0.177093f, 0.169384f, -1.902957f, -0.984281f, -1.812478f, 0.380089f, 0.481301f, 1.151779f, 0.394188f, -0.619681f, -0.569077f, 0.045092f, -0.315970f, -0.794507f, -0.978493f, 1.059446f, -1.804600f, 0.409733f, -1.289864f, -0.050416f, -1.700190f, -0.293974f, 0.042794f, -0.987004f, 0.758025f, 0.365415f, -0.855760f, 0.456084f, -0.431130f, -1.143337f, 0.350636f, -0.321040f, -0.748402f, -0.076930f, 0.387144f, 0.396985f, 0.024379f, 1.383966f, -0.013538f, -0.673351f, 0.328151f, -1.881675f, -1.220736f, -0.067740f, -0.182121f, 0.969996f, 0.422941f, -0.107674f, 0.737414f, 0.718491f, -1.056690f, -0.298115f};
        Float[] e_2_left = {0.658299f, 0.752380f, 0.177413f, -0.835892f, 1.311020f, -0.440589f, -0.414171f, -0.393853f, 1.127172f, -0.311357f, 0.202684f, 1.055474f, 0.231385f, -0.830468f, -1.149938f, -1.119932f, -1.841427f, 1.003495f, 0.378591f, -0.189750f, -0.502719f, 0.822308f, -0.903211f, 1.331163f, -0.759641f, 0.371335f, 1.539555f, 0.017556f, 0.866301f, -0.240457f, 0.876846f, -0.818108f, -1.311215f, -0.097679f, 1.217520f, -0.185588f, 0.972498f, 0.631509f, -0.334549f, 1.268417f, -0.253510f, -0.626754f, 0.654742f, 0.250416f, -1.950121f, 0.293325f, -0.914910f, -0.649901f, -0.776122f, 0.373830f, -0.253400f, -0.828415f, -0.015371f, -1.037621f, 0.845047f, 1.057827f, -1.297100f, -0.315455f, -0.003165f, 0.246889f, 1.200349f, 0.452917f, 0.113998f, -0.553606f, 0.197505f, -0.306839f, -0.505700f, -0.973848f, -0.469666f, -1.106179f, 0.259999f, 0.272065f, 0.459139f, 1.033768f, -0.042271f, 1.650575f, 1.170612f, -0.998304f, 1.265703f, -0.309982f, -0.239290f, -1.480924f, 0.317304f, 0.530687f, -0.575338f, -0.665513f, -1.527327f, -0.325691f, -1.201769f, 0.565130f, -0.693282f, 0.218020f, 0.188413f, -0.355743f, 0.441627f, 0.737099f, 0.524621f, 2.588552f, -2.588164f, -0.383396f, -1.064283f, 1.581082f, -0.059504f, 0.439303f, -1.158614f, -0.343903f, -0.622102f, 0.305737f, 1.186535f, 0.282253f, -1.076397f, 2.118175f, -2.458026f, -0.390465f, 0.786204f, -0.232895f, 0.274469f, -0.546283f, 1.484756f, -0.219780f, -0.296933f, 1.391413f, 0.751169f, -0.187119f, -0.469585f, -1.042126f, 1.044519f, 0.303961f, -2.163782f, -1.805128f, 0.926026f, 0.892583f, 0.094740f, 0.468239f, -0.691102f, -0.149435f, 0.041341f, 1.692475f, -1.215011f, 0.360980f, -1.474499f, -0.920362f, -0.914061f, -0.267541f, -0.616308f, 0.005852f, 1.025659f, -1.791489f, 1.529893f, -0.771262f, 1.812152f, 1.919458f, 0.727593f, 0.429756f, 0.193226f, -0.768158f, 0.237544f, -0.024075f, 1.296788f, -0.541999f, -0.266673f, -0.958251f, -0.240027f, -0.526099f, 0.127976f, 0.224345f, 0.689856f, -0.733588f, -1.645032f, -0.757955f, 0.291246f, 0.361724f, -0.373135f, -0.112309f, 0.232476f, 0.691329f, 0.736518f, -0.228608f, 0.413849f, -0.463198f, 1.211112f, 0.131677f, 0.614592f, -0.533218f, 0.454815f, 0.212908f, 1.467762f, 0.779914f, 0.112101f, -0.481986f, -0.190808f, 0.986371f, -0.062438f, 0.646550f, -0.669868f, 0.794915f, -1.230154f, -0.709927f, 0.597621f, -0.783261f, -0.381963f, -0.853328f, -0.183621f, -1.381821f, 0.589082f, 0.583936f, -0.543914f, 0.831840f, -0.328423f, 0.664434f, 0.225763f, -0.942598f, -0.179620f, 0.832318f, 0.033604f, 0.292545f, -1.335068f, 0.280070f, 0.043323f, -0.345884f, 0.731792f, 1.429730f, 0.569832f, 0.730175f, -0.586485f, 1.310843f, 0.030294f, -1.165172f, -0.667909f, 1.673806f, 0.456047f, 0.431119f, 0.979322f, -0.264845f, 0.085967f, -1.972574f, 1.014865f, 1.212431f, 0.536514f, 1.106573f, -0.408908f, -0.602880f, -0.348352f, 0.776163f, -1.245865f, -0.807449f, -0.861111f, 1.096681f, -0.177093f, 0.169384f, -1.902957f, -0.984281f, -1.812478f, 0.380089f, 0.481301f, 1.151779f, 0.394188f, -0.619681f, -0.569077f, 0.045092f, -0.315970f, -0.794507f, -0.978493f, 1.059446f, -1.804600f, 0.409733f, -1.289864f, -0.050416f, -1.700190f, -0.293974f, 0.042794f, -0.987004f, 0.758025f, 0.365415f, -0.855760f, 0.456084f, -0.431130f, -1.143337f, 0.350636f, -0.321040f, -0.748402f, -0.076930f, 0.387144f, 0.396985f, 0.024379f, 1.383966f, -0.013538f, -0.673351f, 0.328151f, -1.881675f, -1.220736f, -0.067740f, -0.182121f, 0.969996f, 0.422941f, -0.107674f, 0.737414f, 0.718491f, -1.056690f, -0.298115f};
        Float[] e_1_middle = {0.944771f, -0.476378f, 0.228246f, -0.643596f, -0.842081f, 0.547199f, 0.511207f, 0.054975f, -1.040445f, 0.140991f, 0.077592f, -0.092846f, 0.054988f, 0.883329f, -0.151948f, -0.383165f, 1.965845f, -0.029362f, 1.481658f, -0.025231f, 0.659829f, -0.792810f, 0.008468f, 0.150626f, -1.520630f, 0.353573f, 0.628460f, -0.746932f, 0.968071f, -0.660814f, 0.396249f, -0.428607f, -0.359079f, 1.397256f, 1.586379f, 0.034107f, -0.790858f, -0.414722f, -1.065576f, 0.483922f, -0.213097f, 1.120991f, 1.395474f, 0.193819f, -1.262504f, 1.134906f, 0.590796f, -0.098135f, -0.932006f, -0.239914f, 0.412954f, -0.748674f, 0.250437f, -0.229489f, 0.446187f, -0.689887f, -0.240817f, -0.621251f, 1.229514f, 0.239818f, 0.494965f, -0.261162f, 0.262120f, 1.116748f, 1.050472f, 0.824615f, 0.335565f, 0.021289f, 0.457805f, -0.494488f, 0.884276f, -0.015668f, 0.031432f, 0.896560f, 1.327293f, 1.377237f, -0.145198f, 1.246701f, -1.142588f, -0.811073f, 0.197866f, 0.182995f, 0.337184f, -0.001173f, -0.455520f, -1.606703f, -0.781185f, -1.768954f, 0.804384f, 1.593233f, -0.135144f, -1.143441f, -0.823094f, 0.300140f, -0.601505f, 0.631086f, 0.073104f, 1.847720f, 1.189491f, -0.174507f, 0.128797f, -0.889268f, -0.840059f, -0.672537f, -0.440962f, -0.223978f, -0.562037f, -0.591619f, -0.547280f, 0.146299f, -1.326701f, 0.572950f, -0.907918f, 0.810794f, 1.300062f, -1.316701f, -0.883874f, -0.582861f, 0.293077f, -0.172561f, -0.321720f, 0.366801f, -1.412848f, 0.608663f, -0.767382f, 0.432022f, 0.157302f, 1.961164f, -1.514405f, -0.260309f, -0.171101f, -1.062703f, -0.781990f, -0.771539f, 0.145059f, 0.110621f, -0.870099f, 0.432610f, 0.515595f, -1.030774f, 0.126818f, 1.305539f, 0.248942f, -0.922011f, 1.108269f, -1.350599f, 0.890284f, -0.891146f, 0.097570f, 0.022106f, -0.743568f, 0.393997f, 0.503060f, 0.882848f, -0.607037f, -0.800383f, 0.635350f, 0.397907f, -0.080933f, -0.033829f, 0.603187f, -1.916222f, 0.331389f, 0.634947f, -0.912154f, -0.416916f, 0.507239f, 0.433223f, -0.634196f, -0.276066f, -1.787453f, -1.238168f, 1.321969f, 1.069713f, 0.331060f, -0.565028f, 0.097604f, 0.195792f, -1.471560f, -0.375602f, 0.565224f, 0.560482f, 0.177436f, -0.403304f, 0.164083f, 1.080084f, 0.462634f, 0.492075f, -0.144124f, 0.375830f, -0.169136f, 1.016764f, 0.028455f, -0.309393f, -0.404348f, -1.331684f, -1.502637f, 0.820460f, 1.177122f, 0.885906f, -0.024938f, 0.268877f, -0.593951f, -1.098176f, 1.024331f, -0.575592f, -0.183929f, -0.941662f, -0.422444f, -0.587673f, 0.831207f, 0.999590f, 0.040044f, -0.125831f, 0.207671f, -1.417057f, 0.546742f, 1.031025f, 0.475578f, 0.104020f, 0.591130f, 1.451821f, -0.914677f, -0.689196f, 0.439378f, 0.869956f, 0.634622f, -0.776967f, 0.245014f, 0.128586f, 1.455336f, -0.001212f, -0.031663f, -0.839365f, 1.234577f, -0.463393f, 1.990119f, -1.258801f, 0.872146f, 1.266611f, -0.156907f, 0.418896f, 0.925550f, 0.413643f, -0.151451f, -0.136933f, -1.969723f, -0.629767f, 0.167366f, -0.023683f, 0.313173f, -0.876699f, 0.607526f, -0.939909f, -1.155153f, -0.090473f, -0.307708f, -0.165541f, -1.014951f, -0.903019f, 1.558989f, -1.024624f, 1.190512f, 0.607230f, 0.076921f, 0.511810f, -1.128526f, 0.945574f, -1.400761f, -0.076255f, 0.337976f, -0.178951f, 1.271106f, -0.440558f, -0.688031f, -0.042527f, -0.268903f, -0.699793f, 1.901978f, 0.359779f, 1.117339f, -0.562472f, 0.424030f, 1.666473f, -0.410804f, -0.455579f, 0.894074f, 0.364881f, -0.522421f, 0.500604f, 0.481377f, -0.498621f, -0.499283f, 1.332644f, -0.553267f, 0.018997f, -0.161250f, -0.360276f, 1.545055f, 0.258112f};
        Float[] e_2_middle = {0.253858f, 0.170911f, 0.794037f, -0.182364f, -0.678348f, 1.239148f, 0.923217f, -0.376164f, -0.066347f, -0.241592f, -0.626366f, -0.902522f, -0.496808f, 0.528252f, -0.331069f, 0.529799f, -0.088654f, -1.189210f, 0.105395f, 0.690598f, -0.163664f, -0.032500f, -0.227987f, 0.008763f, -0.068782f, 0.385798f, -0.157407f, -0.839528f, -0.157479f, -0.762016f, 0.268856f, -0.364920f, 0.807325f, 0.515399f, 0.779991f, -0.307475f, -0.376325f, 0.052208f, 0.375601f, -0.454874f, -0.471328f, -0.496606f, 0.818649f, 0.703632f, 0.336789f, -0.688744f, 0.185890f, -0.702165f, -0.164446f, 0.068032f, -0.022032f, 0.229778f, -0.518601f, -0.277798f, -0.227426f, 0.761112f, 0.040920f, -0.535718f, 1.519050f, 0.566358f, 0.518215f, -0.443292f, 0.059209f, 0.057473f, -0.125520f, -0.045791f, 1.203590f, 1.427514f, 0.164598f, -0.898617f, -0.025258f, 0.133636f, -0.229668f, 0.055531f, -0.443812f, -0.056887f, -0.182757f, -0.524564f, -0.076006f, 0.981559f, -1.098008f, 0.463806f, -1.003136f, 0.217397f, -0.060993f, 0.582420f, -0.288500f, -0.345744f, 0.141156f, 0.283896f, 0.048427f, -0.554031f, -0.267721f, 0.196006f, -1.528824f, 0.671039f, 0.224443f, 0.265420f, 0.690159f, 0.718998f, -0.286879f, 0.744399f, 0.231128f, -0.145445f, 0.361591f, 0.192980f, 0.000504f, -0.993301f, -0.334789f, -0.148465f, 0.896241f, 0.711281f, -0.927671f, 0.190282f, 0.775299f, -0.206681f, -0.074185f, -0.051527f, -1.104502f, -0.995849f, -1.082976f, -0.072783f, 0.318059f, -0.374642f, -0.218662f, 1.156856f, -0.524847f, 1.472391f, 0.806601f, 0.491087f, 0.031748f, 0.039828f, -0.394171f, -0.708283f, -0.362903f, -0.460837f, -0.618966f, -0.061795f, 0.392963f, -1.064755f, 1.000456f, -1.435042f, -0.099992f, -0.175896f, 0.849649f, -0.338779f, 1.014049f, -0.400052f, 1.060956f, -1.022188f, -0.424303f, 0.233876f, 0.619454f, 0.354292f, -0.851522f, -0.396132f, 0.240963f, -0.513724f, 0.848987f, -0.031297f, -0.198676f, -0.240619f, 0.761059f, 0.962796f, -1.150067f, -0.129106f, -0.041852f, 0.605213f, 0.463789f, -0.633171f, -0.075893f, -0.891631f, -0.062934f, 0.107493f, -0.320397f, 0.915092f, -1.396645f, -0.952909f, -0.120940f, 0.301092f, 0.047655f, -0.071618f, -0.049331f, -0.493185f, 0.079274f, 0.460107f, 0.357937f, 1.060682f, 1.288502f, 0.029534f, 0.501177f, 0.386205f, -0.573247f, -1.050743f, -1.102420f, 0.234687f, -0.382656f, 1.370801f, -0.388499f, -0.902559f, -0.909994f, -0.280511f, 0.058727f, -0.817593f, -0.046958f, -0.130235f, 0.082085f, -1.029347f, 0.467713f, -0.104733f, -0.170976f, -0.416270f, 1.082482f, 0.006972f, -0.140621f, -0.337260f, 0.233969f, -0.009124f, -1.402747f, 0.378814f, -0.175497f, -0.424215f, 0.876420f, -0.488717f, 1.415805f, 0.792714f, 0.276166f, 0.313555f, -1.117227f, -0.670856f, -0.279643f, -0.766939f, 0.495075f, 0.443998f, 0.405129f, -0.364213f, 1.035109f, -0.126673f, 0.181204f, 0.256407f, 0.292162f, 0.487537f, 0.465563f, -0.488841f, -0.404832f, -0.129081f, 0.462914f, -1.096095f, 0.354988f, 1.154963f, 0.038125f, 0.718139f, 0.167918f, 0.207684f, 0.477748f, -0.830527f, -0.604508f, 0.345942f, 0.279219f, -0.074423f, -1.139896f, -0.060360f, 0.176267f, 1.169290f, 0.137566f, -0.565259f, 0.009329f, 0.234747f, 0.846678f, 0.938144f, -0.229154f, -0.587428f, -0.095482f, 0.292070f, 0.009922f, -0.731219f, -0.220610f, -0.022241f, -0.804986f, 0.327278f, 0.557575f, -0.234738f, -0.185490f, -0.549170f, 0.181885f, 0.500693f, 0.131720f, -0.007577f, 0.042186f, -0.048788f, 0.573546f, 0.770798f, 0.295603f, 0.942157f, 0.343492f, -1.091851f, 0.450423f, -0.234578f, -0.591772f, -0.273291f};
        Float[] e_1_right = {4.184037f, -1.199576f, 1.571406f, -2.727934f, -1.098215f, -0.311309f, 1.774410f, -2.720841f, 1.757262f, -1.902167f, -0.941244f, 3.756385f, 0.663739f, -0.919110f, -1.444654f, -0.670229f, -0.551871f, -2.084898f, 1.301881f, -0.740477f, 0.461209f, 2.044645f, -1.198947f, -0.426632f, -0.332449f, 1.766643f, 0.816695f, 0.472409f, 0.375762f, 2.180987f, -2.429269f, 0.153294f, -0.939006f, -0.136057f, 2.978043f, 1.218323f, 1.550090f, -2.832956f, 0.730605f, -0.763588f, -1.167838f, -0.969003f, 0.770396f, -1.085186f, -2.757299f, 2.455479f, 0.243369f, -2.429735f, -0.522275f, -1.737370f, 1.519039f, 0.267903f, 1.556643f, 0.395121f, 0.096334f, 1.775977f, -3.610478f, -1.415804f, -1.835126f, -1.735513f, -0.440412f, 0.054900f, -1.040385f, 1.639442f, -1.299438f, -1.120122f, 0.944113f, -0.358013f, -2.307140f, -2.928299f, -0.456024f, -2.810246f, 0.780946f, 0.756468f, -1.228511f, 1.178244f, -0.515226f, 0.295689f, 1.495374f, 1.384996f, -0.582503f, -1.012689f, -0.661669f, 0.263997f, 0.572737f, 1.316839f, 1.586675f, -2.620870f, -0.104551f, 1.083957f, -2.059723f, -1.901051f, -0.218587f, -0.385861f, -1.168379f, 0.760031f, 0.425703f, 1.007019f, 0.689848f, 1.512899f, -0.498664f, -1.357881f, 0.537796f, -0.592098f, -1.994197f, -0.690916f, 0.652145f, 0.715033f, -0.254058f, -0.076900f, -2.994274f, -1.343887f, -4.436958f, -1.700912f, -2.251149f, -0.539127f, -0.221512f, -0.841714f, -2.281948f, 1.877907f, 0.441970f, -0.512533f, -0.116905f, 0.636567f, 1.168217f, -0.993268f, 1.743016f, -1.032539f, 0.298225f, -2.938678f, 1.634211f, 0.814649f, 0.087230f, 0.139455f, 0.612034f, -2.238465f, -0.407122f, 2.157765f, -0.807580f, -1.658031f, -0.026080f, 0.072779f, -0.387345f, 0.179634f, -0.146747f, 2.424113f, -0.054671f, -2.499411f, 0.719048f, 0.790261f, 2.755706f, 1.772306f, 0.658764f, -1.297005f, -1.364418f, -2.203425f, 0.163314f, 3.782975f, 0.521383f, 0.277300f, 1.545187f, -0.748884f, -3.294718f, -1.481851f, 0.150128f, 0.674450f, 0.429428f, -0.185738f, 0.268196f, -1.602027f, -2.222452f, 0.745611f, -0.903697f, -1.681274f, 1.824240f, -2.375390f, 3.372689f, 0.198041f, 0.137391f, 0.994201f, 1.603879f, -0.811650f, 2.264697f, -0.299823f, 1.697814f, -1.514413f, 0.307666f, 2.165526f, 0.130007f, -2.340596f, -1.154467f, -3.077786f, -1.037884f, 2.327460f, -0.375603f, -1.737768f, -0.253379f, -0.410208f, 1.266916f, -1.496732f, -2.150892f, -0.575730f, -1.505022f, 0.424495f, 0.122859f, 1.048254f, -0.490844f, 0.052521f, 0.409773f, 0.248477f, 0.830989f, 1.247679f, -1.488520f, -0.476190f, -0.764674f, 1.700591f, -1.740487f, 0.967020f, 1.663147f, -0.106982f, -0.808582f, 0.345651f, 0.578840f, 2.045384f, -0.435263f, 1.036150f, 1.617825f, -0.923353f, -1.014297f, 0.945377f, 0.901632f, 1.531221f, 0.384513f, 0.362844f, 0.958086f, 2.074165f, 0.258801f, -0.010588f, -1.434458f, 2.768487f, 0.684250f, -2.415879f, -0.646491f, -1.171228f, -0.094004f, -2.970155f, -3.057407f, 2.730444f, -2.878653f, -2.468586f, 1.152013f, -3.152411f, -0.073347f, -0.030125f, 0.516037f, -0.287877f, -1.088699f, 0.034259f, 1.021040f, 1.617845f, 1.555887f, 1.548638f, -1.104232f, 1.132160f, -0.064432f, 1.256731f, -3.674453f, 2.734138f, -1.284825f, -0.032371f, -1.958724f, 1.039407f, 0.158926f, 0.620966f, -1.649295f, -0.883894f, -3.811790f, -1.063353f, -1.274902f, 0.383132f, 1.743958f, -0.759907f, -0.193614f, -0.996118f, 1.584717f, 0.724412f, -1.618919f, 1.936250f, 1.547456f, 0.804375f, -0.909932f, -0.235703f, 1.484150f, 1.163271f, 1.251839f, -0.415713f, 0.233847f, 1.283999f, -2.361555f, 1.535102f};
        Float[] e_2_right = {0.392142f, 0.418739f, -0.024314f, -0.089826f, 0.043622f, -0.550482f, 0.310587f, 0.490770f, 0.328643f, 1.241790f, -0.011167f, 0.209484f, 0.422384f, -0.485653f, -0.170692f, -0.544984f, -0.189082f, 0.519424f, -0.411546f, -0.050461f, -0.617918f, -0.401349f, 0.158992f, 0.065969f, -0.152218f, 0.017825f, -0.092372f, 0.031563f, -0.290703f, -0.335866f, -0.053087f, -0.483301f, 0.282265f, 0.719793f, -0.047805f, -0.341351f, 0.141824f, 0.254981f, -0.306104f, -0.665207f, 0.628065f, -0.270003f, -0.985155f, 0.101184f, -0.492088f, -0.051892f, 0.085843f, -0.993999f, -0.043935f, 0.050696f, 0.127246f, -0.564593f, 0.092265f, -0.217681f, 0.334714f, 0.334479f, 0.975697f, -0.313571f, 0.346001f, 0.204741f, 0.791636f, -0.235181f, 0.495579f, 0.496090f, -0.160008f, -0.368852f, -0.518878f, 0.019928f, 0.668339f, 0.140273f, -0.621811f, 0.116199f, -0.094950f, -0.186002f, 0.182296f, -0.083090f, 0.435597f, 0.134405f, 0.439211f, -0.797592f, 0.250868f, -0.174175f, -0.114341f, 0.412201f, 0.094457f, -0.261429f, -0.217146f, 0.258849f, -0.097670f, 0.112295f, -0.036546f, 0.208428f, -0.091683f, 0.783938f, 0.809320f, -0.949285f, -0.393719f, -0.093307f, -0.618782f, 0.079280f, 0.420101f, 0.146005f, 0.556072f, -0.051268f, -0.289930f, 0.514796f, 0.304946f, 0.198856f, 0.155573f, -0.584694f, 0.181205f, 0.399767f, -0.451398f, 0.772912f, -0.845731f, 0.452683f, -0.747419f, -0.393794f, -0.338931f, 0.420074f, 0.140134f, -0.217569f, -0.311931f, -0.605461f, 0.201937f, -0.730875f, 0.732809f, 0.163830f, 0.918596f, -0.395154f, -0.167640f, 0.300342f, 0.327717f, 0.468037f, -0.529542f, 0.690301f, 0.718074f, 0.141611f, 0.401842f, 0.277220f, -0.181828f, 0.337125f, -0.665667f, 0.061341f, -0.114372f, -0.504195f, 0.600803f, -0.482736f, -0.288139f, -0.225071f, 0.543143f, -0.064488f, -0.006167f, -0.249966f, 0.326696f, -0.361373f, -0.302414f, 0.312512f, -0.013377f, -0.079626f, 0.387180f, 0.386802f, -0.149954f, 0.567405f, -0.087301f, 0.174180f, 0.275958f, 0.209769f, 0.109761f, 0.567375f, 0.533123f, 0.130152f, 0.351731f, -0.344264f, -0.122151f, 0.180735f, -0.089624f, -0.404739f, 0.153829f, -0.096688f, 1.000448f, 0.358411f, 0.751269f, 0.114197f, -0.074495f, 1.140755f, 0.238779f, 0.522812f, -0.916221f, -0.289751f, 0.134579f, -0.026834f, -0.268520f, 0.637853f, 0.436319f, -0.152854f, -0.481031f, -0.073891f, 0.630488f, -0.292926f, -0.015732f, 0.143472f, -0.964423f, -0.451400f, -0.138561f, -0.376970f, 0.686807f, 0.237144f, 0.238401f, 0.398251f, 0.359445f, -0.350884f, -0.035734f, 0.829357f, -0.511082f, -0.317399f, 0.339172f, 0.517059f, 0.232339f, 0.231033f, -0.209248f, -0.474420f, 0.146493f, -0.046030f, -0.326517f, 0.082011f, 0.357430f, 0.669586f, 0.316786f, 0.017678f, 0.051825f, 0.317456f, -0.022785f, -0.700276f, 0.576784f, 0.250736f, -0.003891f, 0.694330f, 0.079039f, 0.301438f, -0.401764f, 0.278435f, 0.047598f, 0.804159f, 0.377054f, 0.411276f, 0.172696f, -0.169260f, 0.436425f, -0.240149f, 0.004137f, 0.056808f, 0.101850f, -0.406787f, 0.101706f, 0.052668f, 0.032720f, 0.271617f, -0.036604f, 0.043016f, -0.344747f, 0.426008f, -0.085823f, 0.265022f, 0.717232f, -0.021383f, 0.011131f, 0.462305f, 0.076363f, 0.401381f, 0.391808f, 0.076330f, -0.402130f, 0.202680f, -0.366681f, 0.278500f, -0.050372f, -0.137565f, -0.643223f, 0.082602f, 0.116082f, 0.435115f, -0.244526f, 0.618592f, -0.125049f, -0.800418f, 0.014853f, 0.223909f, 0.531023f, 0.346490f, 0.057323f, 0.698886f, -0.561356f, -0.500219f, 0.132362f, 0.081533f, 0.756229f, -0.394592f, -0.343791f, 0.393810f};
        Float[] e_2_right_2 = {0.870254f, 0.475268f, 0.432734f, -0.123276f, -1.030524f, -0.802122f, 0.356644f, -0.144614f, 0.685152f, 0.107368f, -0.714960f, -0.815921f, 1.660482f, -1.059947f, 0.360798f, 0.908534f, -0.736671f, -0.450839f, -0.880613f, 0.795261f, 0.164588f, -0.184288f, -0.309600f, 0.620776f, 1.630520f, 1.004485f, -1.152256f, -0.114952f, -0.318696f, 0.119925f, -0.179156f, 0.956349f, 0.144909f, -0.621997f, 0.246113f, -0.793330f, 0.222768f, 0.254282f, 0.751536f, 0.640196f, -0.347455f, 0.035987f, -0.103356f, 0.131719f, -0.730525f, -1.464621f, 0.453867f, -0.872585f, 0.444979f, -0.507870f, 0.604493f, 0.597885f, -0.072931f, 0.162314f, -0.045902f, -0.443900f, 0.246372f, -0.309527f, 2.159172f, 0.605979f, 0.739986f, 0.055467f, -0.241042f, -0.316954f, -0.366960f, -0.235651f, -0.148892f, -0.044691f, 0.234455f, -0.512376f, 0.892074f, 0.467024f, -0.147989f, -1.532012f, -0.868242f, 0.255374f, 0.061046f, -1.663012f, 0.653662f, -0.216571f, 0.576953f, 0.497809f, -0.200209f, -0.684695f, 0.648431f, 0.709977f, -0.370452f, 0.452704f, 0.077280f, -0.719183f, 0.423196f, -0.154874f, 0.890158f, -1.183899f, -0.290643f, -0.420592f, 0.110600f, 0.672729f, -0.513569f, -0.132474f, -1.120878f, -0.559237f, 0.809794f, -0.065623f, 0.033809f, -0.257572f, -0.331877f, 0.388150f, -0.834339f, 0.599052f, 1.435649f, 0.579326f, 0.790594f, 0.128541f, -1.116131f, 0.945069f, -0.033531f, 0.125177f, -0.157047f, 0.396090f, -0.075765f, 0.517493f, 0.256565f, 0.298020f, -1.039461f, -0.987557f, -0.421365f, -1.259815f, -0.843987f, -0.489085f, 0.032316f, 1.187526f, -0.990426f, -0.390504f, -0.035768f, -0.930722f, -0.571130f, 0.188757f, 0.070137f, -0.836258f, 0.322629f, 0.605049f, 0.187392f, 0.534753f, -1.061496f, 1.351191f, -0.897189f, 0.043213f, -0.497717f, -1.053168f, -0.209786f, -0.167299f, -1.379510f, -0.875097f, 0.611112f, 0.694548f, 0.312805f, 0.020019f, -0.109865f, 0.075938f, 0.269044f, 0.668416f, -0.191963f, -0.485000f, 0.531271f, 0.172114f, -1.153137f, -0.627737f, 0.764900f, 0.953784f, 0.155370f, -1.390154f, 0.821711f, -0.856628f, 0.047072f, -0.995642f, 0.041010f, -0.019039f, 1.289689f, 0.421551f, 0.092246f, 0.442095f, -0.336003f, 0.800329f, -0.200642f, 0.621788f, -0.081920f, 0.997152f, 0.399102f, 0.170261f, 0.029477f, 0.314617f, -0.385094f, 0.653749f, -0.138439f, 0.056067f, 0.452689f, 0.281837f, -0.164519f, -0.463061f, -0.376079f, -1.319155f, 1.744013f, 0.127838f, -0.607748f, -0.931417f, -1.372557f, -0.216432f, 0.687588f, 0.032353f, -0.959196f, 1.058997f, 1.291691f, -0.854913f, -0.247436f, 0.888874f, -0.729738f, 0.476644f, 0.834696f, -0.478342f, -0.112286f, 0.244496f, -0.123072f, 0.362468f, -1.258649f, 0.289110f, -1.009769f, -0.607002f, -0.277451f, -0.040108f, 0.423313f, 0.290833f, 0.071672f, 0.376050f, 0.190715f, -0.395325f, 0.206590f, 0.225929f, -0.566553f, 0.213623f, -0.679663f, 0.357505f, 0.643428f, -0.336170f, 0.732800f, 0.851027f, 0.066539f, 0.722176f, 0.150583f, 0.358396f, -0.550322f, -0.549243f, -0.819034f, 0.963673f, 0.271918f, 0.154807f, 0.888215f, -0.010728f, 0.585230f, 0.437068f, 0.127471f, 0.095621f, 0.674358f, -0.296792f, -1.227944f, -0.316529f, -0.351933f, -0.007328f, 1.025398f, -0.432240f, -0.262473f, 0.907473f, -0.909215f, 1.556544f, -0.422721f, 0.347914f, 0.346036f, 0.138762f, -0.660605f, 1.888466f, 0.113527f, -0.027988f, 0.759060f, -0.410214f, -1.835949f, 0.636765f, -0.636870f, -0.994781f, 0.612655f, 0.115211f, 0.642163f, 0.830788f, 0.133661f, -0.883615f, 3.453504f, 0.274353f, 0.150324f, -0.157591f, -0.458617f, 0.504220f};
        for(int i = 0; i < 300; i++) {
            e_2_right[i] = (e_2_right[i] + e_2_right_2[i]) / 2.0f;
        }

        VectorOperator vo = new VectorOperator("db_cache_vec", "local");
        Double[] event1 = vo.wordVecToEventVec(e_1_left, e_1_middle, e_1_right);
        Double[] event2 = vo.wordVecToEventVec(e_2_left, e_2_middle, e_2_right);
        System.out.println(Arrays.toString(event1));
        System.out.println(Arrays.toString(event2));
        System.out.println(vo.cosineValue(event1, event2));

    }

}
