package edu.whu.cs.nlp.mts.base.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * C3P0工具类
 *
 * @author ZhenchaoWang 2015-11-4 11:11:52
 *
 */
public class C3P0Util {

    private static Map<String, ComboPooledDataSource> map_cpds;

    /**
     * 获取数据库连接
     * @param namedConfig
     * @return
     * @throws SQLException
     */
    public static synchronized Connection getConnection(String cfg_name) throws SQLException{

        if(map_cpds == null){
            map_cpds = new HashMap<String, ComboPooledDataSource>();
        }

        if(map_cpds.get(cfg_name) == null){
            map_cpds.put(cfg_name, new ComboPooledDataSource(cfg_name));
        }

        return map_cpds.get(cfg_name).getConnection();

    }

}
