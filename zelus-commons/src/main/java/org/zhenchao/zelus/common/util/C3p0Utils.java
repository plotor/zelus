package org.zhenchao.zelus.common.util;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * C3P0工具Class
 *
 * @author zhenchao 2015-11-4 11:11:52
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class C3p0Utils {

    private static Map<String, ComboPooledDataSource> mapCpds;

    private C3p0Utils() {
    }

    /**
     * 获取Data库连接
     *
     * @param cfgName Configuration名称
     * @return Data库连接
     * @throws SQLException SQL异常
     */
    public static synchronized Connection getConnection(String cfgName) throws SQLException {

        if (mapCpds == null) {
            mapCpds = new HashMap<>();
        }

        if (mapCpds.get(cfgName) == null) {
            mapCpds.put(cfgName, new ComboPooledDataSource(cfgName));
        }

        return mapCpds.get(cfgName).getConnection();
    }

}
