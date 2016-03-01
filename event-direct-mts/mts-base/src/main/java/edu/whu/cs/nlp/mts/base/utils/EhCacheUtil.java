package edu.whu.cs.nlp.mts.base.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.log4j.Logger;

import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * 缓存框架工具类
 *
 * @author Apache_xiaochao
 *
 */
public class EhCacheUtil {

    private static Logger log = Logger.getLogger(EhCacheUtil.class);

    private static CacheManager cacheManager;
    private final String        cacheName;
    private final String        datasource;

    public EhCacheUtil(String cacheName, String datasource) {
        this.cacheName = cacheName;
        this.datasource = datasource;
        cacheManager = CacheManager.getInstance();
        if(cacheManager != null) {
            log.info("EhCache started!");
        }
    }

    /**
     * 从缓存中获取当前word对应的向量<br>
     *
     * @param word
     * @return
     * @throws SQLException
     */
    @Deprecated
    public synchronized List<Vector> getVec(Word word) throws SQLException {

        List<Vector> vecs = new ArrayList<Vector>();

        if (word == null) {
            return vecs;
        }

        // 获取指定word对应的词向量
        Cache cache = cacheManager.getCache(this.cacheName);
        Element element = cache.get(word.getName().toLowerCase());
        if (element != null) {

            /**
             * 命中
             */
            vecs = new ArrayList<Vector>((List<Vector>) element.getObjectValue());

        } else {

            /**
             * 未命中
             */
            Connection connection = null;

            String sql = "SELECT * FROM word2vec WHERE word = ?";

            try {

                connection = C3P0Util.getConnection(this.datasource);

                QueryRunner queryRunner = new QueryRunner();

                // 利用Name查询
                List<Vector> vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class),
                        word.getName());

                if (CollectionUtils.isEmpty(vectors)) {

                    // 利用Lemma查询
                    vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class),
                            word.getLemma());

                    if (CollectionUtils.isEmpty(vectors)) {

                        if (!"O".equalsIgnoreCase(word.getNer())) {
                            // 利用命名实体进行查询
                            vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class),
                                    word.getNer());

                        }
                    }
                }
                // 缓存当前得到的词向量
                cache.put(new Element(word.getName().toLowerCase(), vectors));
                if (CollectionUtils.isNotEmpty(vectors)) {
                    vecs = vectors;
                }

            } finally {

                if (connection != null) {

                    connection.close();

                }

            }
        }

        return vecs;
    }

    /**
     * 从缓存中获取当前word对应的最接近的向量<br>
     *
     * @param word
     * @return
     * @throws Exception
     */
    public synchronized Vector getMostSimilarVec(Word word) throws Exception {

        Vector vector = null;

        if (word == null) {
            return vector;
        }

        // 获取指定word对应的词向量
        Cache cache = null;
        try{
            cache = cacheManager.getCache(this.cacheName);
        } catch(Exception e) {
            log.error("Can't get cache by name[" + this.cacheName + "]", e);
            throw e;
        }

        Element element = cache.get(word.getName().toLowerCase());
        if (element != null) {
            /**
             * 命中
             */
            vector = (Vector) element.getObjectValue();
        } else {
            /**
             * 未命中
             */
            Connection connection = null;
            String sql = "SELECT * FROM word2vec WHERE word = ?";
            String queryType = null;
            try {
                connection = C3P0Util.getConnection(this.datasource);
                QueryRunner queryRunner = new QueryRunner();
                // 利用Name查询
                List<Vector> vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getName());
                queryType = word.getName();
                if (CollectionUtils.isEmpty(vectors)) {
                    // 利用Lemma查询
                    vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getLemma());
                    queryType = word.getLemma();
                    if (CollectionUtils.isEmpty(vectors)) {
                        if (!"O".equalsIgnoreCase(word.getNer())) {
                            // 利用命名实体进行查询
                            vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getNer());
                            if (CollectionUtils.isNotEmpty(vectors)) {
                                queryType = word.getNer();
                            }
                        }
                    }
                }

                if (null != queryType) {
                    // 计算得到最相近的向量
                    vector = this.mostSimilarVec(queryType, vectors);
                }

            } catch (SQLException e) {
                log.error("Get word[" + word.getName() + "] vector from database error!", e);
                throw e;
            } finally {
                if (null != connection) {
                    connection.close();
                }
            }
            // 缓存当前得到的词向量
            cache.put(new Element(word.getName().toLowerCase(), vector));
        }

        return vector;
    }

    /**
     * 关闭缓存
     */
    public static void close() {
        if(cacheManager != null) {
            cacheManager.shutdown();
            log.info("EhCache closed!");
        }
    }

    /**
     * 获取与查询词距离最近的向量
     *
     * @param queryKey
     * @param vecs
     * @return
     */
    private Vector mostSimilarVec(String queryKey, List<Vector> vecs) {
        Vector vec = null;
        if (CollectionUtils.isEmpty(vecs)) {
            return vec;
        }
        int minDis = Integer.MAX_VALUE;
        for (Vector vector : vecs) {
            int strDis = CommonUtil.strDistance(queryKey, vector.getWord());
            if (strDis < minDis) {
                minDis = strDis;
                vec = vector;
            }
        }
        return vec;
    }

    public static void main(String[] args) throws Exception {
        EhCacheUtil ehCacheUtil = new EhCacheUtil("db_cache_vec", "local");
        Word word = new Word();
        word.setName("sfgsgsgs");
        word.setLemma("be");
        word.setNer("O");
        /*List<Vector> vecs = ehCacheUtil.getVec(word);
        if (vecs != null) {
            System.out.println(word + "\t>>\t" + vecs.size());
        } else {
            System.out.println("未找到：" + word);
        }*/

        Vector vec = ehCacheUtil.getMostSimilarVec(word);
        if(vec != null) {
            System.out.println(vec.getWord() + "\t" + vec.getVec());
        }
        EhCacheUtil.close();
    }

}
