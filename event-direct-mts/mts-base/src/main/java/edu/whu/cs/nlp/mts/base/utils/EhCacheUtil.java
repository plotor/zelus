package edu.whu.cs.nlp.mts.base.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import edu.whu.cs.nlp.mts.base.domain.Vector;
import edu.whu.cs.nlp.mts.base.domain.Word;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * 缓存框架工具类
 * @author Apache_xiaochao
 *
 */
public class EhCacheUtil {

    private static CacheManager cacheManager;
    private final String cacheName;
    private final String datasource;

    public EhCacheUtil(String cacheName, String datasource) {
        this.cacheName = cacheName;
        this.datasource = datasource;
        cacheManager = CacheManager.getInstance();
    }

    /**
     * 从缓存中获取当前word对应的向量
     *
     * @param word
     * @return
     * @throws SQLException
     */
    public synchronized List<Vector> getVec(Word word) throws SQLException{

        List<Vector> vecs = new ArrayList<Vector>();

        if(word == null){
            return vecs;
        }

        //获取指定word对应的词向量
        Cache cache = cacheManager.getCache(this.cacheName);
        Element element = cache.get(word.getName().toLowerCase());
        if(element != null) {

            /**
             * 命中
             */
            vecs = new ArrayList<Vector>((List<Vector>)element.getObjectValue());

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
                List<Vector> vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getName());

                if(CollectionUtils.isEmpty(vectors)) {

                    // 利用Lemma查询
                    vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getLemma());

                    if(CollectionUtils.isEmpty(vectors)) {

                        if(!"O".equalsIgnoreCase(word.getNer())){
                            // 利用命名实体进行查询
                            vectors = queryRunner.query(connection, sql, new BeanListHandler<Vector>(Vector.class), word.getNer());

                        }
                    }
                }

                //缓存当前得到的词向量
                cache.put(new Element(word.getName().toLowerCase(), vectors));
                if(CollectionUtils.isNotEmpty(vectors)) {
                    vecs = vectors;
                }

            } finally {

                if(connection != null){

                    connection.close();

                }

            }
        }

        return vecs;
    }

}
