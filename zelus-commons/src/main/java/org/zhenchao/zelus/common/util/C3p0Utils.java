package org.zhenchao.zelus.common.util;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * C3P0工具类
 *
 * @author ZhenchaoWang 2015-11-4 11:11:52
 */
public class C3p0Utils {

    private static Map<String, ComboPooledDataSource> map_cpds;

    /**
     * 获取数据库连接
     *
     * @return
     * @throws SQLException
     */
    public static synchronized Connection getConnection(String cfgName) throws SQLException {

        if (map_cpds == null) {
            map_cpds = new HashMap<>();
        }

        if (map_cpds.get(cfgName) == null) {
            map_cpds.put(cfgName, new ComboPooledDataSource(cfgName));
        }

        return map_cpds.get(cfgName).getConnection();
    }

}
