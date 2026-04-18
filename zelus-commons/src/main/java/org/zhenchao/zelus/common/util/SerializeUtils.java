package org.zhenchao.zelus.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Serializing工具Class
 *
 * @author zhenchao 2015-11-3 16:12:11
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class SerializeUtils {

    private SerializeUtils() {
    }

    /**
     * 反SerializingPairs象
     *
     * @param filename 文件名
     * @return 反Serializing后的Pairs象
     * @throws IOException            IO异常
     * @throws ClassNotFoundException Class未找到异常
     */
    public static Object readObj(String filename) throws IOException, ClassNotFoundException {

        Object obj = null;

        ObjectInputStream inner = null;
        try {
            inner = new ObjectInputStream(new FileInputStream(filename));
            obj = inner.readObject();
        } finally {
            if (inner != null) {
                inner.close();
            }
        }

        return obj;

    }

    /**
     * SerializingPairs象
     *
     * @param obj  待SerializingPairs象
     * @param file 目标文件
     * @throws IOException IO异常
     */
    public static void writeObj(Object obj, File file) throws IOException {

        ObjectOutputStream outer = null;
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            outer = new ObjectOutputStream(new FileOutputStream(file));
            outer.writeObject(obj);
            outer.flush();
        } finally {
            if (outer != null) {
                outer.close();
            }
        }

    }

}
